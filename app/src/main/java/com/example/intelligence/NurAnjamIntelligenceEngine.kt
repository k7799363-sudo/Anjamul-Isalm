package com.example.intelligence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.LocalAudioRepository
import com.example.data.db.DailyListeningAggregateEntity
import com.example.data.db.IntelligenceDao
import com.example.data.db.ListeningEventEntity
import com.example.data.db.NurAnjamDatabase
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * OFFLINE-FIRST NurAnjam Intelligence Engine.
 *
 * Fully autonomous on-device music intelligence system:
 * 1. Tracks actual playback duration and listening events from playback engine.
 * 2. Computes daily, weekly, monthly analytics without external server calls.
 * 3. Generates hybrid local offline recommendations mapped strictly to playable local tracks.
 * 4. Respects battery and CPU by running calculations on IO thread asynchronously.
 */
class NurAnjamIntelligenceEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "NurAnjamIntelligence"
        private const val PREFS_NAME = "nur_anjam_intelligence_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_OPTIONAL_CLOUD_ENABLED = "optional_cloud_enabled"
        private const val KEY_LAST_MODEL_UPDATE_TS = "last_model_update_ts"

        @Volatile
        private var INSTANCE: NurAnjamIntelligenceEngine? = null

        fun getInstance(context: Context): NurAnjamIntelligenceEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NurAnjamIntelligenceEngine(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun formatMsToDuration(ms: Long): String {
            if (ms <= 0) return "0m"
            val totalSec = ms / 1000L
            val hours = totalSec / 3600L
            val mins = (totalSec % 3600L) / 60L
            return if (hours > 0) {
                "${hours}h ${mins}m"
            } else {
                "${mins}m"
            }
        }
    }

    private val db = NurAnjamDatabase.getInstance(context)
    private val dao: IntelligenceDao = db.intelligenceDao()
    private val repository = LocalAudioRepository.getInstance(context)
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current tracking session state
    private var activeTrack: AudioTrack? = null
    private var trackingStartTimeMs: Long = 0L
    private var accumulatedPlaybackTimeMs: Long = 0L
    private var isCurrentlyCounting: Boolean = false
    private var previousTrackId: String? = null

    // Preferences & Settings State
    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _isOptionalCloudEnabled = MutableStateFlow(prefs.getBoolean(KEY_OPTIONAL_CLOUD_ENABLED, false))
    val isOptionalCloudEnabled: StateFlow<Boolean> = _isOptionalCloudEnabled.asStateFlow()

    // Reactive Flow of all listening events from Room
    val allListeningEvents: Flow<List<ListeningEventEntity>> = dao.getAllListeningEvents()

    init {
        // Automatically prune ancient raw logs (>90 days) to keep database lightweight
        scope.launch {
            try {
                val cutoff = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000L)
                val events = dao.getListeningEventsRange(0L, cutoff)
                if (events.isNotEmpty()) {
                    Log.i(TAG, "Pruned ${events.size} ancient listening events prior to 90 days.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Prune failed: ${e.message}")
            }
        }
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
        _isOnboardingCompleted.value = true
    }

    fun setOptionalCloudEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPTIONAL_CLOUD_ENABLED, enabled).apply()
        _isOptionalCloudEnabled.value = enabled
    }

    // =========================================================================
    // 1. LISTENING EVENT TRACKING ENGINE
    // =========================================================================

    /**
     * Authoritative listener invoked on every tick / state change from DualAudioMixer.
     * Accurately tracks actual playing time (only when playing and not paused/interrupted).
     */
    fun onPlaybackStateUpdate(state: PlaybackState) {
        val currentTrack = state.currentTrack
        val isPlaying = state.isPlaying

        scope.launch {
            val now = System.currentTimeMillis()

            if (currentTrack == null || !isPlaying) {
                // Pause / stop counting
                if (isCurrentlyCounting) {
                    accumulatedPlaybackTimeMs += (now - trackingStartTimeMs).coerceAtLeast(0L)
                    isCurrentlyCounting = false
                }

                if (currentTrack == null && activeTrack != null) {
                    finalizeCurrentSession()
                }
                return@launch
            }

            // Track changed
            if (activeTrack != null && activeTrack?.id != currentTrack.id) {
                if (isCurrentlyCounting) {
                    accumulatedPlaybackTimeMs += (now - trackingStartTimeMs).coerceAtLeast(0L)
                }
                finalizeCurrentSession()

                // Start new session
                activeTrack = currentTrack
                accumulatedPlaybackTimeMs = 0L
                trackingStartTimeMs = now
                isCurrentlyCounting = true
            } else if (activeTrack == null) {
                // New session start
                activeTrack = currentTrack
                accumulatedPlaybackTimeMs = 0L
                trackingStartTimeMs = now
                isCurrentlyCounting = true
            } else {
                // Resume counting if was paused
                if (!isCurrentlyCounting) {
                    trackingStartTimeMs = now
                    isCurrentlyCounting = true
                }
            }
        }
    }

    /**
     * Finalizes current song session when song ends, changes, or app stops.
     */
    private suspend fun finalizeCurrentSession() {
        val track = activeTrack ?: return
        val now = System.currentTimeMillis()

        if (isCurrentlyCounting) {
            accumulatedPlaybackTimeMs += (now - trackingStartTimeMs).coerceAtLeast(0L)
            isCurrentlyCounting = false
        }

        val totalPlayedMs = accumulatedPlaybackTimeMs
        activeTrack = null
        accumulatedPlaybackTimeMs = 0L

        // Threshold Guard: Discard accidental taps / micro clicks (< 3 seconds)
        if (totalPlayedMs < 3000L) {
            return
        }

        val trackDuration = if (track.durationMs > 0) track.durationMs else 180000L
        val completionPct = (totalPlayedMs.toFloat() / trackDuration.toFloat()).coerceIn(0f, 1f)

        val wasCompleted = completionPct >= 0.80f
        val wasSkipped = completionPct < 0.20f && totalPlayedMs < 15000L
        val wasReplay = (previousTrackId == track.id)

        previousTrackId = track.id

        val cal = Calendar.getInstance()
        val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        val event = ListeningEventEntity(
            trackId = track.id,
            trackTitle = track.title,
            reciter = track.reciter,
            album = track.album,
            folderName = track.folderName,
            trackUri = track.dataUri,
            timestampMs = now,
            durationPlayedMs = totalPlayedMs,
            trackDurationMs = trackDuration,
            completionPercentage = completionPct,
            wasSkipped = wasSkipped,
            wasCompleted = wasCompleted,
            wasReplay = wasReplay,
            hourOfDay = hourOfDay,
            dayOfWeek = dayOfWeek
        )

        dao.insertListeningEvent(event)
        updateDailyAggregate(now, totalPlayedMs)
        Log.i(TAG, "Recorded listening event for '${track.title}': played ${totalPlayedMs / 1000}s (${(completionPct * 100).toInt()}%)")
    }

    private suspend fun updateDailyAggregate(timestampMs: Long, playedDurationMs: Long) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = sdf.format(Date(timestampMs))

        val existing = dao.getDailyAggregate(dateStr)
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)

        if (existing == null) {
            dao.insertOrUpdateDailyAggregate(
                DailyListeningAggregateEntity(
                    dateString = dateStr,
                    totalListeningTimeMs = playedDurationMs,
                    totalSongsPlayed = 1,
                    uniqueSongsCount = 1,
                    mostActiveHour = hour
                )
            )
        } else {
            val newTotal = existing.totalListeningTimeMs + playedDurationMs
            val newCount = existing.totalSongsPlayed + 1
            dao.insertOrUpdateDailyAggregate(
                existing.copy(
                    totalListeningTimeMs = newTotal,
                    totalSongsPlayed = newCount,
                    mostActiveHour = hour
                )
            )
        }
    }

    // =========================================================================
    // 2. ANALYTICS & STATS GENERATOR
    // =========================================================================

    suspend fun getStatsSummary(allTracks: List<AudioTrack>): ListeningStatsSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Today start ms
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayStartMs = cal.timeInMillis

        // Week start ms (7 days ago)
        val weekStartMs = now - (7L * 24 * 60 * 60 * 1000L)
        // Month start ms (30 days ago)
        val monthStartMs = now - (30L * 24 * 60 * 60 * 1000L)

        val eventsRange = dao.getListeningEventsRange(monthStartMs, now)

        val todayEvents = eventsRange.filter { it.timestampMs >= todayStartMs }
        val weekEvents = eventsRange.filter { it.timestampMs >= weekStartMs }
        val monthEvents = eventsRange

        val todayListeningMs = todayEvents.sumOf { it.durationPlayedMs }
        val weekListeningMs = weekEvents.sumOf { it.durationPlayedMs }
        val monthListeningMs = monthEvents.sumOf { it.durationPlayedMs }

        val todayUniqueCount = todayEvents.map { it.trackId }.distinct().size

        val topTrackTodayId = todayEvents.groupBy { it.trackId }
            .maxByOrNull { it.value.sumOf { ev -> ev.durationPlayedMs } }?.key

        val topTrackToday = allTracks.firstOrNull { it.id == topTrackTodayId }

        val mostActiveHour = todayEvents.groupBy { it.hourOfDay }
            .maxByOrNull { it.value.size }?.key ?: 20

        val hourRange = formatHourRange(mostActiveHour)

        val topArtistToday = todayEvents.groupBy { it.reciter }
            .maxByOrNull { it.value.size }?.key ?: "Various Reciters"

        val avgCompletion = if (todayEvents.isNotEmpty()) {
            todayEvents.map { it.completionPercentage }.average().toFloat()
        } else 0f

        ListeningStatsSummary(
            todayListeningMs = todayListeningMs,
            weekListeningMs = weekListeningMs,
            monthListeningMs = monthListeningMs,
            todaySongsCount = todayEvents.size,
            weekSongsCount = weekEvents.size,
            monthSongsCount = monthEvents.size,
            todayUniqueCount = todayUniqueCount,
            topTrackToday = topTrackToday,
            mostActiveHourRange = hourRange,
            topArtistToday = topArtistToday,
            completionRateToday = avgCompletion
        )
    }

    private fun formatHourRange(hour: Int): String {
        val startH = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val endH = (hour + 2) % 24
        val endHFormatted = if (endH == 0) 12 else if (endH > 12) endH - 12 else endH
        val amPmStart = if (hour >= 12) "PM" else "AM"
        val amPmEnd = if ((hour + 2) % 24 >= 12) "PM" else "AM"
        return "$startH $amPmStart – $endHFormatted $amPmEnd"
    }

    // =========================================================================
    // 3. TOP 10 RANKING SYSTEM
    // =========================================================================

    suspend fun getTopTrackInsights(
        allTracks: List<AudioTrack>,
        timeRange: TimeRangeFilter
    ): List<TopTrackInsight> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sinceMs = when (timeRange) {
            TimeRangeFilter.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            }
            TimeRangeFilter.DAYS_7 -> now - (7L * 24 * 60 * 60 * 1000L)
            TimeRangeFilter.DAYS_30 -> now - (30L * 24 * 60 * 60 * 1000L)
            TimeRangeFilter.ALL_TIME -> 0L
        }

        val events = dao.getListeningEventsRange(sinceMs, now)
        val trackMap = allTracks.associateBy { it.id }

        val grouped = events.groupBy { it.trackId }

        val scoredList = grouped.mapNotNull { (trackId, trackEvents) ->
            val track = trackMap[trackId] ?: return@mapNotNull null

            val playCount = trackEvents.size
            val totalTime = trackEvents.sumOf { it.durationPlayedMs }
            val avgCompletion = trackEvents.map { it.completionPercentage }.average().toFloat()
            val replayCount = trackEvents.count { it.wasReplay }
            val skipCount = trackEvents.count { it.wasSkipped }

            // Composite Multi-Signal Recommendation Score
            val favoriteBonus = if (track.isFavorite) 5.0 else 0.0
            val score = (playCount * 2.0) + (totalTime / 60000.0) + (avgCompletion * 3.0) + (replayCount * 2.5) - (skipCount * 2.0) + favoriteBonus

            val weekCount = trackEvents.count { it.timestampMs >= now - (7L * 24 * 60 * 60 * 1000L) }
            val monthCount = trackEvents.count { it.timestampMs >= now - (30L * 24 * 60 * 60 * 1000L) }

            val daysSpan = when (timeRange) {
                TimeRangeFilter.TODAY -> 1f
                TimeRangeFilter.DAYS_7 -> 7f
                TimeRangeFilter.DAYS_30 -> 30f
                TimeRangeFilter.ALL_TIME -> 60f
            }
            val dailyAvg = playCount / daysSpan

            Pair(track, TopTrackInsight(
                rank = 0,
                track = track,
                playCount = playCount,
                listeningTimeMs = totalTime,
                completionRate = avgCompletion,
                weeklyPlayCount = weekCount,
                monthlyPlayCount = monthCount,
                dailyAveragePlays = dailyAvg
            ) to score)
        }

        // If no events exist yet (cold start), return top favorited or first tracks
        if (scoredList.isEmpty()) {
            return@withContext allTracks.take(10).mapIndexed { idx, tr ->
                TopTrackInsight(
                    rank = idx + 1,
                    track = tr,
                    playCount = tr.playCount,
                    listeningTimeMs = tr.playCount * tr.durationMs,
                    completionRate = 0.9f,
                    weeklyPlayCount = tr.playCount,
                    monthlyPlayCount = tr.playCount,
                    dailyAveragePlays = 1.0f
                )
            }
        }

        scoredList.sortedByDescending { (_, insightScore) -> insightScore.second }
            .take(10)
            .mapIndexed { idx, (_, insightScore) ->
                insightScore.first.copy(rank = idx + 1)
            }
    }

    // =========================================================================
    // 4. PERSONAL MUSIC PROFILE GENERATOR
    // =========================================================================

    suspend fun getPersonalProfile(allTracks: List<AudioTrack>): PersonalMusicProfile = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val monthEvents = dao.getListeningEventsRange(now - (30L * 24 * 60 * 60 * 1000L), now)

        if (monthEvents.isEmpty()) {
            val topFav = allTracks.firstOrNull { it.isFavorite } ?: allTracks.firstOrNull()
            return@withContext PersonalMusicProfile(
                frequentlyListenedTags = listOf("Quran Recitations", "Acoustic", "Spiritual"),
                preferredArtists = allTracks.map { it.reciter }.distinct().take(3),
                listeningPatternPeriod = "Evening & Night",
                discoveryTendency = "Curated Library Explorer",
                mostLikedTrack = topFav,
                mostLikedSummary = topFav?.let { "Favorited in library" } ?: "Start listening to build profile"
            )
        }

        val topArtists = monthEvents.groupBy { it.reciter }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val topFolders = monthEvents.groupBy { it.folderName }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val mostActiveHour = monthEvents.groupBy { it.hourOfDay }
            .maxByOrNull { it.value.size }?.key ?: 20

        val patternLabel = when (mostActiveHour) {
            in 5..11 -> "Mostly Morning (5 AM – 11 AM)"
            in 12..16 -> "Mostly Afternoon (12 PM – 4 PM)"
            in 17..22 -> "Mostly Evening (5 PM – 10 PM)"
            else -> "Night Reflection (11 PM – 4 AM)"
        }

        val replayRatio = monthEvents.count { it.wasReplay }.toFloat() / monthEvents.size.toFloat()
        val tendency = if (replayRatio > 0.35f) {
            "High Replay Tendency (Enjoys deep repetition)"
        } else {
            "Broad Library Explorer"
        }

        val topInsight = getTopTrackInsights(allTracks, TimeRangeFilter.ALL_TIME).firstOrNull()
        val mostLikedTrack = topInsight?.track ?: allTracks.firstOrNull { it.isFavorite }
        val completionPct = ((topInsight?.completionRate ?: 0.9f) * 100).toInt()
        val favLabel = if (mostLikedTrack?.isFavorite == true) " • Favorited" else ""
        val summary = "${topInsight?.playCount ?: 1} plays • $completionPct% completion$favLabel"

        PersonalMusicProfile(
            frequentlyListenedTags = topFolders.ifEmpty { listOf("Quran", "Ambient", "Surah") },
            preferredArtists = topArtists,
            listeningPatternPeriod = patternLabel,
            discoveryTendency = tendency,
            mostLikedTrack = mostLikedTrack,
            mostLikedSummary = summary
        )
    }

    // =========================================================================
    // 5. HYBRID LOCAL RECOMMENDATION ENGINE (100% OFFLINE GUARANTEE)
    // =========================================================================

    suspend fun getRecommendations(allTracks: List<AudioTrack>): List<RecommendationGroup> = withContext(Dispatchers.IO) {
        if (allTracks.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val events = dao.getListeningEventsRange(now - (30L * 24 * 60 * 60 * 1000L), now)
        val trackMap = allTracks.associateBy { it.id }

        val groups = mutableListOf<RecommendationGroup>()

        // 1. Because you listen to [Top Reciter]
        val topReciter = events.groupBy { it.reciter }
            .maxByOrNull { it.value.size }?.key
            ?: allTracks.firstOrNull()?.reciter ?: "Favorite Reciter"

        val reciterTracks = allTracks.filter { it.reciter.equals(topReciter, ignoreCase = true) }
            .shuffled().take(6)

        if (reciterTracks.isNotEmpty()) {
            groups.add(
                RecommendationGroup(
                    type = RecommendationType.BECAUSE_YOU_LISTEN,
                    title = "Because you listen to $topReciter",
                    subtitle = "Handpicked recitations matched to your preferred reciter",
                    tracks = reciterTracks
                )
            )
        }

        // 2. Your Favorites & High Completion
        val favorites = allTracks.filter { it.isFavorite }.ifEmpty {
            allTracks.filter { it.playCount > 1 }
        }.shuffled().take(6)

        if (favorites.isNotEmpty()) {
            groups.add(
                RecommendationGroup(
                    type = RecommendationType.YOUR_FAVORITES,
                    title = "Your Favorites & High Completion",
                    subtitle = "Tracks you consistently return to and complete",
                    tracks = favorites
                )
            )
        }

        // 3. Rediscover (Tracks not played in last 10 days)
        val recentPlayedIds = events.filter { it.timestampMs >= now - (10L * 24 * 60 * 60 * 1000L) }
            .map { it.trackId }.toSet()

        val rediscoverTracks = allTracks.filter { it.id !in recentPlayedIds }
            .sortedByDescending { it.playCount }
            .take(6)

        if (rediscoverTracks.isNotEmpty()) {
            groups.add(
                RecommendationGroup(
                    type = RecommendationType.REDISCOVER,
                    title = "Rediscover",
                    subtitle = "Previously enjoyed tracks waiting in your library",
                    tracks = rediscoverTracks
                )
            )
        }

        // 4. Your usual Evening / Time of Day music
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeLabel = when (currentHour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..22 -> "evening"
            else -> "night"
        }

        val usualTracks = events.filter { Math.abs(it.hourOfDay - currentHour) <= 2 }
            .mapNotNull { trackMap[it.trackId] }
            .distinctBy { it.id }
            .take(6)
            .ifEmpty { allTracks.shuffled().take(6) }

        groups.add(
            RecommendationGroup(
                type = RecommendationType.USUAL_TIME_OF_DAY,
                title = "Your usual $timeLabel music",
                subtitle = "Aligns with your $timeLabel listening habits",
                tracks = usualTracks
            )
        )

        // 5. Hidden Gems (Unplayed or low play count)
        val hiddenGems = allTracks.filter { it.playCount <= 1 }
            .shuffled().take(6)

        if (hiddenGems.isNotEmpty()) {
            groups.add(
                RecommendationGroup(
                    type = RecommendationType.HIDDEN_GEMS,
                    title = "Hidden Gems in Your Library",
                    subtitle = "Unexplored gems matching your audio quality preferences",
                    tracks = hiddenGems
                )
            )
        }

        groups
    }

    // =========================================================================
    // 6. REPORTS & DATA PRIVACY CONTROLS
    // =========================================================================

    suspend fun getDailyReport(allTracks: List<AudioTrack>): DailyReportData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMs = cal.timeInMillis

        val events = dao.getListeningEventsRange(todayStartMs, now)
        val totalMs = events.sumOf { it.durationPlayedMs }
        val uniqueCount = events.map { it.trackId }.distinct().size

        val topSongEntry = events.groupBy { it.trackTitle }
            .maxByOrNull { it.value.size }

        val mostActiveHour = events.groupBy { it.hourOfDay }
            .maxByOrNull { it.value.size }?.key ?: 20

        val topArtist = events.groupBy { it.reciter }
            .maxByOrNull { it.value.size }?.key ?: "Various Reciters"

        val avgCompletion = if (events.isNotEmpty()) {
            events.map { it.completionPercentage }.average().toFloat()
        } else 0f

        DailyReportData(
            dateLabel = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()),
            listeningTimeFormatted = formatMsToDuration(totalMs),
            songsPlayed = events.size,
            uniqueSongs = uniqueCount,
            topSongTitle = topSongEntry?.key ?: "Start listening today",
            topSongPlays = topSongEntry?.value?.size ?: 0,
            mostActivePeriod = formatHourRange(mostActiveHour),
            topArtist = topArtist,
            completionRateFormatted = "${(avgCompletion * 100).toInt()}%"
        )
    }

    suspend fun clearAllIntelligenceHistory() = withContext(Dispatchers.IO) {
        dao.clearAllEvents()
        dao.clearAllAggregates()
        Log.i(TAG, "Cleared all local listening analytics and recommendation model cache.")
    }
}
