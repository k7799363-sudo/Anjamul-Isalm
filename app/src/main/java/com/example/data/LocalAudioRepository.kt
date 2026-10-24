package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.CachedAudioTrackEntity
import com.example.data.db.NurAnjamDatabase
import com.example.model.AudioSortOrder
import com.example.model.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class LocalAudioRepository(private val context: Context) {

    companion object {
        private const val TAG = "LocalAudioRepo"

        @Volatile
        private var INSTANCE: LocalAudioRepository? = null

        fun getInstance(context: Context): LocalAudioRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalAudioRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * Sorts a list of [AudioTrack] deterministically according to [AudioSortOrder].
         * - DATE_NEWEST: sortedByDescending { it.customSortTimestamp }
         * - DATE_OLDEST: sortedBy { it.customSortTimestamp }
         * - TITLE_AZ: sortedBy { it.title.lowercase() }
         * - TITLE_ZA: sortedByDescending { it.title.lowercase() }
         * - MOST_PLAYED: sortedByDescending { it.playCount } then A -> Z
         */
        fun sortTracks(tracks: List<AudioTrack>, sortOrder: AudioSortOrder): List<AudioTrack> {
            return when (sortOrder) {
                AudioSortOrder.DATE_NEWEST -> tracks.sortedByDescending { it.customSortTimestamp }
                AudioSortOrder.DATE_OLDEST -> tracks.sortedBy { it.customSortTimestamp }
                AudioSortOrder.TITLE_AZ -> tracks.sortedBy { it.title.lowercase() }
                AudioSortOrder.TITLE_ZA -> tracks.sortedByDescending { it.title.lowercase() }
                AudioSortOrder.MOST_PLAYED -> tracks.sortedWith(
                    compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
                )
            }
        }

        /**
         * Converts a raw MediaStore timestamp (which Android stores in Unix seconds, or milliseconds on
         * certain OEM implementations) into a normalized Unix epoch millisecond `Long`.
         * Falls back to `DATE_MODIFIED` and then `File(dataPath).lastModified()` if `DATE_ADDED` is 0.
         */
        fun resolveMediaStoreTimestampMs(
            rawDateAdded: Long,
            rawDateModified: Long,
            dataPath: String
        ): Long {
            val normalizedAddedMs = normalizeToEpochMillis(rawDateAdded)
            if (normalizedAddedMs > 0L) {
                return normalizedAddedMs
            }

            val normalizedModifiedMs = normalizeToEpochMillis(rawDateModified)
            if (normalizedModifiedMs > 0L) {
                return normalizedModifiedMs
            }

            if (dataPath.isNotBlank()) {
                val fileModifiedMs = try {
                    File(dataPath).lastModified()
                } catch (_: Exception) {
                    0L
                }
                if (fileModifiedMs > 0L) {
                    return fileModifiedMs
                }
            }

            return 0L
        }

        private fun normalizeToEpochMillis(rawTimestamp: Long): Long {
            if (rawTimestamp <= 0L) return 0L
            // Values under 100_000_000_000L are Unix timestamps in seconds -> multiply by 1000L
            return if (rawTimestamp < 100_000_000_000L) {
                rawTimestamp * 1000L
            } else {
                rawTimestamp
            }
        }
    }

    private val repoMutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("nur_anjam_prefs", Context.MODE_PRIVATE)
    private val cachedTrackDao by lazy { NurAnjamDatabase.getInstance(context).cachedTrackDao() }

    private val favoriteIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val deletedTrackIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _sortOrder = MutableStateFlow(loadSortOrder())
    val sortOrder: StateFlow<AudioSortOrder> = _sortOrder.asStateFlow()

    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    val recentTracks: Flow<List<AudioTrack>> = cachedTrackDao.observeRecentTracks()
        .map { entities ->
            entities
                .filter { !deletedTrackIds.contains(it.id) }
                .map { entity ->
                    val domain = entity.toDomainModel()
                    val prefTs = getSavedCustomSortTimestamp(entity.id)
                    domain.copy(
                        isFavorite = favoriteIds.contains(entity.id) || entity.isFavorite,
                        playCount = getPlayCount(entity.id, entity.playCount),
                        customSortTimestamp = if (prefTs > 0L) prefTs else domain.customSortTimestamp
                    )
                }
        }

    init {
        loadFavorites()
        loadDeletedTrackIds()
        // Hydrate cached tracks from Room immediately on cold boot before MediaStore finishes scanning
        ioScope.launch {
            hydrateFromRoomCache()
            scanDeviceAudio(force = true)
        }
    }

    private suspend fun hydrateFromRoomCache() {
        try {
            val cachedEntities = cachedTrackDao.getAllCachedTracksNewestFirst()
            if (cachedEntities.isNotEmpty() && _tracks.value.isEmpty()) {
                val restored = cachedEntities
                    .filter { !deletedTrackIds.contains(it.id) }
                    .map { entity ->
                        val fav = favoriteIds.contains(entity.id) || entity.isFavorite
                        val plays = getPlayCount(entity.id, entity.playCount)
                        val domain = entity.toDomainModel()
                        val prefTs = getSavedCustomSortTimestamp(entity.id)
                        domain.copy(
                            isFavorite = fav,
                            playCount = plays,
                            customSortTimestamp = if (prefTs > 0L) prefTs else domain.customSortTimestamp
                        )
                    }
                _tracks.value = sortTracks(restored, _sortOrder.value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to hydrate cached tracks from Room: ${e.message}")
        }
    }

    private fun loadSortOrder(): AudioSortOrder {
        val savedName = prefs.getString("audio_sort_order", AudioSortOrder.TITLE_AZ.name)
        return try {
            AudioSortOrder.valueOf(savedName ?: AudioSortOrder.TITLE_AZ.name)
        } catch (_: Exception) {
            AudioSortOrder.TITLE_AZ
        }
    }

    fun setSortOrder(order: AudioSortOrder) {
        prefs.edit().putString("audio_sort_order", order.name).apply()
        _sortOrder.value = order
        _tracks.update { currentList -> sortTracks(currentList, order) }
    }

    private fun loadFavorites() {
        val stored = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        favoriteIds.clear()
        favoriteIds.addAll(stored)
    }

    private fun saveFavorites() {
        prefs.edit().putStringSet("favorite_ids", favoriteIds.toSet()).apply()
    }

    private fun loadDeletedTrackIds() {
        val stored = prefs.getStringSet("deleted_track_ids", emptySet()) ?: emptySet()
        deletedTrackIds.clear()
        deletedTrackIds.addAll(stored)
    }

    private fun saveDeletedTrackIds() {
        prefs.edit().putStringSet("deleted_track_ids", deletedTrackIds.toSet()).apply()
    }

    fun removeTrack(trackId: String) {
        deletedTrackIds.add(trackId)
        saveDeletedTrackIds()
        favoriteIds.remove(trackId)
        saveFavorites()
        _tracks.update { currentList -> currentList.filter { it.id != trackId } }
        ioScope.launch {
            try {
                cachedTrackDao.deleteTrackById(trackId)
            } catch (_: Exception) {}
        }
    }

    fun toggleFavorite(trackId: String): Boolean {
        val newState = if (favoriteIds.contains(trackId)) {
            favoriteIds.remove(trackId)
            false
        } else {
            favoriteIds.add(trackId)
            true
        }
        saveFavorites()
        _tracks.update { currentList ->
            currentList.map {
                if (it.id == trackId) it.copy(isFavorite = newState) else it
            }
        }
        ioScope.launch {
            try {
                cachedTrackDao.updateFavorite(trackId, newState)
            } catch (_: Exception) {}
        }
        return newState
    }

    private fun getPlayCount(trackId: String, defaultCount: Int = 0): Int {
        return prefs.getInt("play_count_$trackId", defaultCount)
    }

    fun recordPlay(trackId: String) {
        val current = prefs.getInt("play_count_$trackId", 0)
        val newCount = current + 1
        prefs.edit().putInt("play_count_$trackId", newCount).apply()
        _tracks.update { currentList ->
            val updated = currentList.map {
                if (it.id == trackId) it.copy(playCount = newCount) else it
            }
            if (_sortOrder.value == AudioSortOrder.MOST_PLAYED) {
                sortTracks(updated, _sortOrder.value)
            } else {
                updated
            }
        }
        ioScope.launch {
            try {
                cachedTrackDao.updatePlayCount(trackId, newCount)
            } catch (_: Exception) {}
        }
    }

    private fun getSavedCustomSortTimestamp(trackId: String): Long {
        return prefs.getLong("custom_sort_ts_$trackId", 0L)
    }

    fun updateTrackDate(trackId: String, newTimestamp: Long) {
        prefs.edit().putLong("custom_sort_ts_$trackId", newTimestamp).apply()
        _tracks.update { currentList ->
            val updated = currentList.map { track ->
                if (track.id == trackId) {
                    track.copy(customSortTimestamp = newTimestamp)
                } else {
                    track
                }
            }
            sortTracks(updated, _sortOrder.value)
        }
        ioScope.launch {
            try {
                cachedTrackDao.updateTrackDate(trackId, newTimestamp)
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating customSortTimestamp in Room: ${e.message}")
            }
        }
    }

    suspend fun recordTrackPlayedTimestamp(trackId: String, currentTime: Long = System.currentTimeMillis()) {
        withContext(Dispatchers.IO) {
            try {
                cachedTrackDao.updateLastPlayedTimestamp(trackId, currentTime)
                _tracks.update { currentList ->
                    currentList.map {
                        if (it.id == trackId) it.copy(lastPlayedTimestamp = currentTime) else it
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed recording lastPlayedTimestamp: ${e.message}")
            }
        }
    }

    suspend fun scanLocalAudio(): List<AudioTrack> = scanDeviceAudio(force = true)

    suspend fun scanDeviceAudio(force: Boolean = false): List<AudioTrack> = withContext(Dispatchers.IO) {
        repoMutex.withLock {
            if (!force && _tracks.value.isNotEmpty()) {
                Log.d(TAG, "Returning ${_tracks.value.size} cached tracks instead of rescanning MediaStore")
                return@withLock _tracks.value
            }
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasPermission) {
                return@withLock _tracks.value
            }

            // Read existing customSortTimestamp values from Room so user-defined dates are preserved on rescan,
            // while songs fetched from MediaStore for the very first time initialize customSortTimestamp = OS DATE_ADDED.
            val existingRoomTimestamps: Map<String, Long> = try {
                cachedTrackDao.getAllCachedTracksNewestFirst().associate { it.id to it.customSortTimestamp }
            } catch (_: Exception) {
                emptyMap()
            }

            val scannedList = mutableListOf<AudioTrack>()
            // Step 1: Explicitly include DATE_ADDED and DATE_MODIFIED in the MediaStore projection
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
            val querySortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            try {
                val cursor = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    querySortOrder
                )

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val dateModifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val trackId = "local_$id"
                        if (deletedTrackIds.contains(trackId)) continue

                        val title = c.getString(titleCol) ?: "Unknown Track"
                        val artist = c.getString(artistCol) ?: "Unknown Artist"
                        val duration = c.getLong(durCol)
                        val dataPath = c.getString(dataCol) ?: ""
                        val album = c.getString(albumCol) ?: "Local Audio"
                        val size = c.getLong(sizeCol)

                        // Strictly fetch DATE_ADDED (and DATE_MODIFIED fallback) as Long and convert seconds -> ms
                        val rawDateAdded = c.getLong(dateAddedCol)
                        val rawDateModified = c.getLong(dateModifiedCol)
                        val dateAddedMs = resolveMediaStoreTimestampMs(
                            rawDateAdded = rawDateAdded,
                            rawDateModified = rawDateModified,
                            dataPath = dataPath
                        )

                        // Initial Scan Logic: When fetching a song from MediaStore for the very first time,
                        // save OS DATE_ADDED into customSortTimestamp. If already present in Room or user prefs,
                        // keep the existing customSortTimestamp.
                        val prefCustomTs = getSavedCustomSortTimestamp(trackId)
                        val roomCustomTs = existingRoomTimestamps[trackId] ?: 0L
                        val resolvedCustomSortTimestamp = when {
                            prefCustomTs > 0L -> prefCustomTs
                            roomCustomTs > 0L -> roomCustomTs
                            else -> dateAddedMs
                        }

                        val parentFolder = try {
                            val file = File(dataPath)
                            file.parentFile?.name ?: "Audio"
                        } catch (_: Exception) {
                            "Audio"
                        }

                        scannedList.add(
                            AudioTrack(
                                id = trackId,
                                title = title,
                                reciter = artist,
                                durationMs = duration,
                                dataUri = dataPath,
                                album = album,
                                folderName = parentFolder,
                                sizeBytes = size,
                                bitrateKbps = 320,
                                isFavorite = favoriteIds.contains(trackId),
                                dateAdded = dateAddedMs,
                                dateAddedMs = dateAddedMs,
                                customSortTimestamp = resolvedCustomSortTimestamp,
                                playCount = getPlayCount(trackId, 0)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying MediaStore audio: ${e.message}", e)
            }

            val filteredScanned = scannedList.filter { !deletedTrackIds.contains(it.id) }
            val sortedResult = sortTracks(filteredScanned, _sortOrder.value)
            _tracks.value = sortedResult

            // Persist scanned tracks & dateAdded timestamps into Room for instant cold-boot sorting
            try {
                val entities = sortedResult.map { CachedAudioTrackEntity.fromDomainModel(it) }
                cachedTrackDao.insertAll(entities)
                if (entities.isNotEmpty()) {
                    cachedTrackDao.pruneRemovedTracks(entities.map { it.id })
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed caching scanned tracks into Room: ${e.message}")
            }

            sortedResult
        }
    }

    fun addImportedUri(uri: Uri, displayName: String) {
        val now = System.currentTimeMillis()
        val trackId = "imported_$now"
        val newTrack = AudioTrack(
            id = trackId,
            title = displayName.substringBeforeLast("."),
            reciter = "Local Device",
            durationMs = 180000L,
            dataUri = uri.toString(),
            album = "Imported Audio",
            folderName = "Audio",
            sizeBytes = 4500000L,
            bitrateKbps = 320,
            isFavorite = false,
            dateAdded = now,
            dateAddedMs = now,
            playCount = 0
        )
        _tracks.update { currentList ->
            sortTracks(listOf(newTrack) + currentList, _sortOrder.value)
        }
        ioScope.launch {
            try {
                cachedTrackDao.insertTrack(CachedAudioTrackEntity.fromDomainModel(newTrack))
            } catch (_: Exception) {}
        }
    }

    fun addExternalTrack(track: AudioTrack) {
        _tracks.update { currentList ->
            sortTracks(listOf(track) + currentList.filter { it.id != track.id }, _sortOrder.value)
        }
        ioScope.launch {
            try {
                cachedTrackDao.insertTrack(CachedAudioTrackEntity.fromDomainModel(track))
            } catch (_: Exception) {}
        }
    }
}
