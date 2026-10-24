package com.example.audio

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.LocalAudioRepository
import com.example.data.db.NurAnjamDatabase
import com.example.data.db.TrackPlayRecordEntity
import com.example.model.AmbientPlayType
import com.example.model.AmbientType
import com.example.model.AudioOutputDevice
import com.example.model.AudioPreset
import com.example.model.AudioSortOrder
import com.example.model.AudioTrack
import com.example.model.PlaybackMode
import com.example.model.PlaybackState
import com.example.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * High-performance Dual-Audio Engine for NurAnjam.
 * Utilizes two synchronized AndroidX Media3 ExoPlayer instances:
 * 1. Primary Player: Dedicated to sacred Quran recitation / Surah audio.
 * 2. Secondary Looping Player: Continuous ambient soundscapes (Rain, Thunder, Wind, Stream, Noor).
 *
 * Provides thread-safe, independent hardware volume mixing and complete bridge controls
 * for lockscreen, Always-On Display (AOD), and Realme Capsule transport events.
 */
@OptIn(UnstableApi::class)
class DualAudioMixerBridge private constructor(private val context: Context) {

    companion object {
        private const val TAG = "DualAudioMixerBridge"
        private const val PREFS_NAME = "nur_anjam_dual_engine_prefs"
        private const val KEY_RECITATION_VOL = "key_recitation_vol"
        private const val KEY_AMBIENT_VOL = "key_ambient_vol"
        private const val KEY_SELECTED_AMBIENT = "key_selected_ambient"
        private const val KEY_AMBIENT_ACTIVE = "key_ambient_active"

        @Volatile
        private var INSTANCE: DualAudioMixerBridge? = null

        fun getInstance(context: Context): DualAudioMixerBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DualAudioMixerBridge(context.applicationContext).also { INSTANCE = it }
            }
        }

        // Bridge convenience delegates for service / notification callbacks
        fun onPlay() = INSTANCE?.onPlay()
        fun onPause() = INSTANCE?.onPause()
        fun onTogglePlayPause() = INSTANCE?.onTogglePlayPause()
        fun onSkipToNext() = INSTANCE?.onSkipToNext()
        fun onSkipToPrevious() = INSTANCE?.onSkipToPrevious()
        fun onSeekTo(positionMs: Long) = INSTANCE?.onSeekTo(positionMs)
        fun onStop() = INSTANCE?.onStop()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Base volume persistence & ambient default state (defaults to false / deactivated)
    private var baseRecitationVolume: Float = prefs.getFloat(KEY_RECITATION_VOL, 0.85f).coerceIn(0f, 1f)
    private var baseAmbientVolume: Float = prefs.getFloat(KEY_AMBIENT_VOL, 0.50f).coerceIn(0f, 1f)
    private var isAmbientActive: Boolean = prefs.getBoolean(KEY_AMBIENT_ACTIVE, false)

    // Media3 ExoPlayer Instances
    private var surahPlayer: ExoPlayer? = null
    private var ambientPlayer: ExoPlayer? = null

    // Audio Focus and Device Detection
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: Any? = null
    val outputDetector = AudioOutputDetector(context)

    // Master Auto-Resume Focus State Tracking (Case 1-18 Audio Focus Compliance)
    @Volatile
    private var wasPlayingBeforeInterruption: Boolean = false
    @Volatile
    private var pausedByAudioFocus: Boolean = false

    // Cold-Start Command Queue State
    @Volatile
    private var isPlayerReady: Boolean = false
    @Volatile
    private var pendingPlayTrack: AudioTrack? = null

    // State Flow
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val playlistMutex = Mutex()
    private var playlist: List<AudioTrack> = emptyList()
        set(value) {
            field = value
            _state.update { it.copy(playQueue = value) }
        }
    private var currentIndex: Int = -1
        set(value) {
            field = value
            _state.update { it.copy(currentMediaItemIndex = value.coerceAtLeast(0)) }
        }
    private var positionTrackerJob: Job? = null
    private var sleepTimerJob: Job? = null

    @Volatile
    private var isHighFrequencyProgressActive: Boolean = false

    fun setProgressTrackingActive(active: Boolean) {
        isHighFrequencyProgressActive = active
        Log.i(TAG, "High frequency progress updates requested: $active")
    }

    /**
     * Master System-Wide Audio Focus Listener
     * Strictly manages dual-player state transitions during external interruptions
     * (e.g., Phone calls ringing/active, WhatsApp voice/video messages, YouTube playback, Notifications).
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        val pos = surahPlayer?.currentPosition ?: _state.value.currentPositionMs
        val trackTitle = _state.value.currentTrack?.title ?: "Unknown"

        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification arrived (e.g. WhatsApp / SMS): gently duck volume so notification is clearly audible, then restore on GAIN
                surahPlayer?.volume = (baseRecitationVolume * 0.25f).coerceIn(0f, 1f)
                if (isAmbientActive) {
                    ambientPlayer?.volume = (baseAmbientVolume * 0.25f).coerceIn(0f, 1f)
                }
                Log.i(TAG, "[AUDIO_FOCUS] LOSS_TRANSIENT_CAN_DUCK (Notification) | track='$trackTitle' pos=${pos}ms -> action=DUCK_VOLUME")
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // CASE 2 & 3: Phone Ringing/Call Active, WhatsApp Video/Voice, Instagram, YouTube, etc.
                val currentlyPlaying = _state.value.isPlaying
                if (!pausedByAudioFocus) {
                    wasPlayingBeforeInterruption = currentlyPlaying
                }
                if (wasPlayingBeforeInterruption) {
                    pausedByAudioFocus = true
                }
                Log.i(TAG, "[AUDIO_FOCUS] LOSS_TRANSIENT (Call / External Media) | track='$trackTitle' pos=${pos}ms | wasPlaying=$currentlyPlaying pausedByFocus=$pausedByAudioFocus -> action=PAUSE")
                pauseDualPlayback()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent Loss: another app requested permanent audio focus
                val currentlyPlaying = _state.value.isPlaying
                if (!pausedByAudioFocus) {
                    wasPlayingBeforeInterruption = currentlyPlaying
                }
                if (wasPlayingBeforeInterruption) {
                    pausedByAudioFocus = true
                }
                Log.i(TAG, "[AUDIO_FOCUS] LOSS (Permanent loss to external app) | track='$trackTitle' pos=${pos}ms | wasPlaying=$currentlyPlaying pausedByFocus=$pausedByAudioFocus -> action=PAUSE")
                pauseDualPlayback()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus after notification or phone call ends
                Log.i(TAG, "[AUDIO_FOCUS] GAIN | track='$trackTitle' pos=${pos}ms | wasPlayingBeforeLoss=$wasPlayingBeforeInterruption, pausedByFocus=$pausedByAudioFocus")
                applyRecitationVolume(baseRecitationVolume)
                applyAmbientVolume(baseAmbientVolume)
                if (wasPlayingBeforeInterruption && pausedByAudioFocus) {
                    wasPlayingBeforeInterruption = false
                    pausedByAudioFocus = false
                    Log.i(TAG, "[AUDIO_FOCUS] Restoring playback from exact position ${pos}ms -> action=RESUME")
                    resumeDualPlayback()
                } else {
                    wasPlayingBeforeInterruption = false
                    pausedByAudioFocus = false
                    Log.i(TAG, "[AUDIO_FOCUS] Restored normal volume after ducking / focus gain")
                }
            }
        }
    }

    // Audio Becoming Noisy BroadcastReceiver (Headphones unplugged / BT disconnected)
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: android.content.Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i(TAG, "Audio becoming noisy: headphones disconnected. Pausing dual playback.")
                onPause()
            }
        }
    }

    init {
        val savedAmbientName = prefs.getString(KEY_SELECTED_AMBIENT, AmbientType.RAIN.name) ?: AmbientType.RAIN.name
        val savedAmbient = try {
            AmbientType.valueOf(savedAmbientName)
        } catch (_: Exception) {
            AmbientType.RAIN
        }

        _state.update {
            it.copy(
                recitationVolume = baseRecitationVolume,
                ambientVolume = if (isAmbientActive) baseAmbientVolume else 0.0f,
                selectedAmbient = savedAmbient,
                isAmbientPlaying = isAmbientActive,
                strictVolumeControl = true
            )
        }

        // Register Becoming Noisy Receiver safely
        try {
            val filter = android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(noisyReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering noisy receiver: ${e.message}")
        }

        initPlayers()
        startPositionTracker()

        scope.launch {
            outputDetector.currentDevice.collect { device ->
                _state.update { it.copy(audioOutputDevice = device) }
            }
        }

        // Dynamically re-sort active queue when user changes sort order
        scope.launch {
            LocalAudioRepository.getInstance(context).sortOrder.collect { newSortOrder ->
                if (_state.value.playbackMode == PlaybackMode.LOOP_FOLDER) {
                    val current = _state.value.currentTrack
                    if (current != null && playlist.isNotEmpty()) {
                        val repoTracks = LocalAudioRepository.getInstance(context).tracks.value
                        val folderTracks = repoTracks.filter { it.folderName.equals(current.folderName, ignoreCase = true) }
                            .ifEmpty { playlist }
                        val newOrderedList = sortFolderTracks(folderTracks)
                        playlist = newOrderedList
                        currentIndex = newOrderedList.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                        Log.i(TAG, "Re-sorted active loop folder queue according to $newSortOrder")
                    }
                }
            }
        }
    }

    fun ensurePlaybackEngineReady() {
        val player = surahPlayer
        if (player == null || !isPlayerReady || player.playbackState == Player.STATE_IDLE) {
            Log.i(TAG, "[NurAnjamPlayback] [PLAYER] Player instance missing or in IDLE state. Re-initializing playback engine.")
            initPlayers()
        } else {
            Log.i(TAG, "[NurAnjamPlayback] [PLAYER] Player instance already valid and ready.")
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayers() {
        try {
            // Release existing ExoPlayer instances to prevent memory/codec leaks
            try {
                outputDetector.unbindPlayerAudioTrack()
                surahPlayer?.stop()
                surahPlayer?.clearMediaItems()
                surahPlayer?.release()
                surahPlayer = null

                ambientPlayer?.stop()
                ambientPlayer?.clearMediaItems()
                ambientPlayer?.release()
                ambientPlayer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing old ExoPlayer instances prior to re-init: ${e.message}")
            }

            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build()

            val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink {
                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioTrackProvider { config, attrs, sessionId ->
                            val track = androidx.media3.exoplayer.audio.DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config, attrs, sessionId)
                            outputDetector.bindPlayerAudioTrack(track)
                            track
                        }
                        .build()
                }
            }

            // 1. Initialize Primary Surah ExoPlayer with strict system AudioAttributes & automatic audio focus / notification ducking
            surahPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                setAudioAttributes(audioAttributes, true)
                playWhenReady = false
                volume = baseRecitationVolume
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _state.update { it.copy(durationMs = duration.coerceAtLeast(0L)) }
                                updateService()
                            }
                            Player.STATE_ENDED -> {
                                handleTrackEnded()
                            }
                            else -> {}
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val trackId = mediaItem?.mediaId ?: _state.value.currentTrack?.id
                        if (!trackId.isNullOrBlank()) {
                            val currentTime = System.currentTimeMillis()
                            scope.launch(Dispatchers.IO) {
                                try {
                                    NurAnjamDatabase.getInstance(context).cachedTrackDao()
                                        .updateLastPlayedTimestamp(trackId, currentTime)
                                    LocalAudioRepository.getInstance(context).recordTrackPlayedTimestamp(trackId, currentTime)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error updating lastPlayedTimestamp onMediaItemTransition: ${e.message}")
                                }
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (isPlaying) {
                            startAmbientPlayback()
                        }
                        updateService()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "[NurAnjamPlayback] [PLAYBACK] Primary Surah ExoPlayer Error [${error.errorCodeName} / ${error.errorCode}]: ${error.message}", error)
                        val current = _state.value.currentTrack
                        if (current != null) {
                            val cleanPath = current.dataUri.removePrefix("file://")
                            val directFile = File(cleanPath)
                            val currentUriScheme = surahPlayer?.currentMediaItem?.localConfiguration?.uri?.scheme

                            if (currentUriScheme == "content" && directFile.exists() && directFile.length() > 0) {
                                Log.i(TAG, "[NurAnjamPlayback] [PLAYBACK] Content URI failed. Attempting fallback recovery with direct File URI: ${directFile.absolutePath}")
                                try {
                                    val fallbackUri = Uri.fromFile(directFile)
                                    val fallbackMediaItem = MediaItem.fromUri(fallbackUri)
                                    surahPlayer?.stop()
                                    surahPlayer?.clearMediaItems()
                                    surahPlayer?.setMediaItem(fallbackMediaItem)
                                    surahPlayer?.prepare()
                                    surahPlayer?.play()
                                    return
                                } catch (e: Exception) {
                                    Log.e(TAG, "[NurAnjamPlayback] [PLAYBACK] Fallback recovery failed: ${e.message}")
                                }
                            }
                        }
                        _state.update { it.copy(isPlaying = false) }
                        updateService()
                    }
                })
            }

            // 2. Initialize Secondary Looping Ambient ExoPlayer (REPEAT_MODE_ALL for continuous infinite looping)
            ambientPlayer = ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(audioAttributes, false) // Ignore focus interruptions to allow concurrent dual output
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = false
                volume = if (isAmbientActive) baseAmbientVolume else 0.0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "[NurAnjamPlayback] [PLAYBACK] Ambient ExoPlayer Error [${error.errorCodeName} / ${error.errorCode}]: ${error.message}", error)
                        // Reset the ambient state on error to prevent frozen UI / play buttons
                        _state.update {
                            it.copy(
                                isAmbientPlaying = false,
                                activeAmbientType = AmbientPlayType.NONE,
                                activePreviewSoundId = null
                            )
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                Log.i(TAG, "Custom Atmosphere sound source READY. Duration: ${duration}ms, Volume: $volume, RepeatMode: REPEAT_MODE_ALL")
                            }
                            Player.STATE_BUFFERING -> Log.d(TAG, "Custom Atmosphere sound BUFFERING...")
                            Player.STATE_ENDED -> Log.d(TAG, "Custom Atmosphere sound ENDED (REPEAT_MODE_ALL active)")
                            Player.STATE_IDLE -> Log.d(TAG, "Custom Atmosphere sound IDLE")
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.i(TAG, "Custom Atmosphere sound isPlaying: $isPlaying at volume $volume")
                    }
                })
            }

            // Restore saved custom atmosphere file if previously selected
            val savedCustomPath = prefs.getString("key_custom_ambient_path", null)
            val savedCustomId = prefs.getString("key_custom_ambient_id", null)
            val savedCustomTitle = prefs.getString("key_custom_ambient_title", null)
            if (!savedCustomPath.isNullOrBlank() && File(savedCustomPath).exists()) {
                currentPreviewUriOrPath = savedCustomPath
                _state.update {
                    it.copy(
                        activePreviewSoundId = savedCustomId,
                        activeAmbientTitle = savedCustomTitle,
                        activeAmbientFilePath = savedCustomPath
                    )
                }
            }

            // Mark player as ready and flush any pending cold-start play command
            isPlayerReady = true
            Log.i(TAG, "[NurAnjamPlayback] [PLAYER] ExoPlayer initialized and ready.")
            val pending = pendingPlayTrack
            if (pending != null) {
                pendingPlayTrack = null
                Log.i(TAG, "[NurAnjamPlayback] [PLAY_REQUEST] Executing pending cold-start play command for '${pending.title}'.")
                playTrack(pending)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[NurAnjamPlayback] [PLAYER] Error initializing Media3 ExoPlayers: ${e.message}", e)
        }
    }

    private var currentPreparedAmbient: AmbientType? = null

    private fun prepareAmbientMediaSource(type: AmbientType, forceReload: Boolean = false) {
        val ambient = ambientPlayer ?: return
        val customPath = currentPreviewUriOrPath ?: prefs.getString("key_custom_ambient_path", null)
        if (customPath.isNullOrBlank()) return
        val localFile = File(customPath)
        if (!localFile.exists()) return
        try {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(localFile))
            ambient.stop()
            ambient.clearMediaItems()
            ambient.setMediaItem(mediaItem)
            ambient.repeatMode = Player.REPEAT_MODE_ALL
            ambient.prepare()
            currentPreparedAmbient = type
        } catch (e: Exception) {
            Log.e(TAG, "Error setting custom atmosphere media source: ${e.message}", e)
        }
    }

    private fun startAmbientPlayback() {
        val ambient = ambientPlayer ?: return
        val uriOrPath = currentPreviewUriOrPath ?: prefs.getString("key_custom_ambient_path", null)
        if (_state.value.isAmbientPlaying && !uriOrPath.isNullOrBlank()) {
            try {
                val uri = if (uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://") || uriOrPath.startsWith("content://") || uriOrPath.startsWith("android.resource://")) {
                    Uri.parse(uriOrPath)
                } else {
                    val file = File(uriOrPath)
                    if (!file.exists()) {
                        pauseAmbientPlayback()
                        return
                    }
                    Uri.fromFile(file)
                }
                if (ambient.currentMediaItem == null || ambient.playbackState == Player.STATE_IDLE) {
                    val mediaItem = MediaItem.fromUri(uri)
                    ambient.setMediaItem(mediaItem)
                    ambient.repeatMode = Player.REPEAT_MODE_ALL
                    ambient.prepare()
                } else {
                    ambient.repeatMode = Player.REPEAT_MODE_ALL
                }
                val savedVol = if (baseAmbientVolume > 0f) baseAmbientVolume else 0.50f
                ambient.volume = savedVol
                ambient.playWhenReady = true
                if (!ambient.isPlaying) {
                    ambient.play()
                }
                Log.i(TAG, "Looping custom atmosphere playback active (REPEAT_MODE_ALL): $uriOrPath")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting custom atmosphere playback: ${e.message}", e)
            }
        } else {
            pauseAmbientPlayback()
        }
    }

    private fun pauseAmbientPlayback() {
        ambientPlayer?.playWhenReady = false
        ambientPlayer?.pause()
        ambientPlayer?.stop()
        ambientPlayer?.clearMediaItems()
        currentPreparedAmbient = null
        Log.i(TAG, "Stopped and cleared ambient player to release hardware decoders and stop processing silence")
    }

    private fun pauseDualPlayback() {
        surahPlayer?.playWhenReady = false
        surahPlayer?.pause()
        pauseAmbientPlayback()
        _state.update { it.copy(isPlaying = false) }
        updateService()
    }

    private fun resumeDualPlayback() {
        val player = surahPlayer ?: return
        val focusGranted = requestAudioFocus()
        if (focusGranted) {
            player.playWhenReady = true
            player.play()
            startAmbientPlayback()
            _state.update { it.copy(isPlaying = true) }
            updateService()
        }
    }

    fun setPlaylist(tracks: List<AudioTrack>, startIndex: Int = 0, autoPlay: Boolean = false) {
        scope.launch(Dispatchers.Main.immediate) {
            if (tracks.isEmpty()) return@launch
            ensurePlaybackEngineReady()
            val targetTrack = tracks.getOrNull(startIndex) ?: tracks.first()
            val folderTracks = tracks.filter { it.folderName.equals(targetTrack.folderName, ignoreCase = true) }
                .ifEmpty { listOf(targetTrack) }
            buildAndSetFolderQueue(targetTrack, folderTracks, _state.value.playbackMode, autoPlay)
        }
    }

    fun playTrack(track: AudioTrack) {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePlaybackEngineReady()

            val player = surahPlayer
            if (player == null || !isPlayerReady) {
                pendingPlayTrack = track
                Log.i(TAG, "Player is connecting/initializing on cold start. Queued '${track.title}' into command queue.")
                return@launch
            }

            val current = _state.value.currentTrack
            val hasActiveMediaItem = player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE

            if (hasActiveMediaItem && current != null && (current.id == track.id || (current.dataUri.isNotBlank() && current.dataUri == track.dataUri))) {
                // Idempotent Check: Currently loaded track in ExoPlayer is the exact same track!
                if (player.isPlaying) {
                    Log.i(TAG, "playTrack: '${track.title}' is already playing. Bypassing reset.")
                    return@launch
                } else if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    Log.i(TAG, "playTrack: '${track.title}' is loaded but paused. Resuming playback idempotently.")
                    onPlay()
                    return@launch
                }
            }

            val repoTracks = LocalAudioRepository.getInstance(context).tracks.value
            val folderTracks = repoTracks.filter { it.folderName.equals(track.folderName, ignoreCase = true) }
                .ifEmpty { listOf(track) }
            buildAndSetFolderQueue(track, folderTracks, _state.value.playbackMode, autoPlay = true)
        }
    }

    private fun sortFolderTracks(folderTracks: List<AudioTrack>): List<AudioTrack> {
        val sortOrder = LocalAudioRepository.getInstance(context).sortOrder.value
        return when (sortOrder) {
            AudioSortOrder.TITLE_AZ -> folderTracks.sortedBy { it.title.lowercase() }
            AudioSortOrder.TITLE_ZA -> folderTracks.sortedByDescending { it.title.lowercase() }
            AudioSortOrder.DATE_NEWEST -> folderTracks.sortedByDescending { it.customSortTimestamp }
            AudioSortOrder.DATE_OLDEST -> folderTracks.sortedBy { it.customSortTimestamp }
            AudioSortOrder.MOST_PLAYED -> folderTracks.sortedWith(
                compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
            )
        }
    }

    private fun buildAndSetFolderQueue(
        currentTrack: AudioTrack,
        folderTracks: List<AudioTrack>,
        mode: PlaybackMode,
        autoPlay: Boolean
    ) {
        val orderedList = when (mode) {
            PlaybackMode.LOOP_TRACK -> {
                listOf(currentTrack) + folderTracks.filter { it.id != currentTrack.id }
            }
            PlaybackMode.LOOP_FOLDER -> {
                sortFolderTracks(folderTracks)
            }
            PlaybackMode.SHUFFLE_FOLDER -> {
                val remaining = folderTracks.filter { it.id != currentTrack.id }.shuffled()
                listOf(currentTrack) + remaining
            }
            PlaybackMode.FREQUENCY_PLAY -> {
                folderTracks.sortedWith(
                    compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
                )
            }
        }

        playlist = orderedList
        currentIndex = orderedList.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
        Log.i(TAG, "Initialized strict folder queue for '${currentTrack.folderName}' with ${orderedList.size} tracks under mode ${mode.name}")
        prepareTrack(orderedList[currentIndex], autoPlay = autoPlay)
    }

    private var recordedPlayForCurrentTrack: Boolean = false

    private fun resolveTrackUri(track: AudioTrack): Uri {
        return when {
            track.id.startsWith("local_") -> {
                val numericId = track.id.removePrefix("local_").toLongOrNull()
                if (numericId != null) {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, numericId)
                } else if (track.dataUri.startsWith("content://") || track.dataUri.startsWith("file://")) {
                    Uri.parse(track.dataUri)
                } else {
                    val file = File(track.dataUri)
                    if (file.exists()) Uri.fromFile(file) else Uri.parse(track.dataUri)
                }
            }
            track.dataUri.startsWith("content://") || track.dataUri.startsWith("file://") -> {
                Uri.parse(track.dataUri)
            }
            track.dataUri.startsWith("asset:") || track.dataUri.startsWith("assets:") || track.dataUri.startsWith("asset://") -> {
                val assetPath = track.dataUri
                    .removePrefix("assets:")
                    .removePrefix("asset:")
                    .removePrefix("asset:///")
                    .removePrefix("asset://")
                val file = AssetMediaHelper.getOrExtractAssetFile(context, assetPath)
                Uri.fromFile(file)
            }
            else -> {
                val file = File(track.dataUri)
                if (file.exists()) Uri.fromFile(file) else Uri.parse(track.dataUri)
            }
        }
    }

    private fun prepareTrack(track: AudioTrack, autoPlay: Boolean) {
        val player = surahPlayer ?: run {
            pendingPlayTrack = track
            Log.w(TAG, "surahPlayer is null in prepareTrack. Saved '${track.title}' to command queue.")
            return
        }
        try {
            wasPlayingBeforeInterruption = false
            pausedByAudioFocus = false
            recordedPlayForCurrentTrack = false
            requestAudioFocus()

            val uri = resolveTrackUri(track)
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(uri)
                .build()

            // Strict Finite State Machine Transition Protocol on Main Thread:
            // 1. Stop current active playback & clear old media items from pipeline
            player.stop()
            player.clearMediaItems()

            // 2. Set new MediaItem into singleton player instance
            player.setMediaItem(mediaItem)

            // 3. Transition player state machine to STATE_BUFFERING -> STATE_READY
            player.prepare()

            val currentTime = System.currentTimeMillis()
            scope.launch(Dispatchers.IO) {
                try {
                    NurAnjamDatabase.getInstance(context).cachedTrackDao()
                        .updateLastPlayedTimestamp(track.id, currentTime)
                    LocalAudioRepository.getInstance(context).recordTrackPlayedTimestamp(track.id, currentTime)
                } catch (e: Exception) {
                    Log.w(TAG, "Error recording lastPlayedTimestamp in prepareTrack: ${e.message}")
                }
            }

            // 4. Set playWhenReady flag
            player.playWhenReady = autoPlay

            if (autoPlay) {
                player.play()
            }

            _state.update {
                it.copy(
                    currentTrack = track,
                    isPlaying = autoPlay,
                    currentPositionMs = 0L,
                    durationMs = track.durationMs
                )
            }

            if (autoPlay) {
                startAmbientPlayback()
            }

            updateService()
        } catch (e: Exception) {
            Log.e(TAG, "Failed preparing track '${track.title}': ${e.message}", e)
            _state.update { it.copy(isPlaying = false) }
            updateService()
        }
    }

    // ==========================================
    // Playback Bridge Methods
    // ==========================================

    fun onPlay() {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePlaybackEngineReady()
            wasPlayingBeforeInterruption = false
            pausedByAudioFocus = false
            resumeDualPlayback()
        }
    }

    fun onPause() {
        scope.launch(Dispatchers.Main.immediate) {
            wasPlayingBeforeInterruption = false
            pausedByAudioFocus = false
            pauseDualPlayback()
        }
    }

    fun onTogglePlayPause() {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePlaybackEngineReady()
            wasPlayingBeforeInterruption = false
            pausedByAudioFocus = false
            if (_state.value.isPlaying) {
                pauseDualPlayback()
            } else {
                resumeDualPlayback()
            }
        }
    }

    fun onSkipToNext() {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePlaybackEngineReady()
            if (playlist.isEmpty()) return@launch
            if (_state.value.isShuffle) {
                val nextIdx = (playlist.indices).random()
                currentIndex = nextIdx
            } else {
                currentIndex = (currentIndex + 1) % playlist.size
            }
            val next = playlist.getOrNull(currentIndex) ?: return@launch
            prepareTrack(next, autoPlay = true)
        }
    }

    fun onSkipToPrevious() {
        scope.launch(Dispatchers.Main.immediate) {
            ensurePlaybackEngineReady()
            if (playlist.isEmpty()) return@launch
            currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
            val prev = playlist.getOrNull(currentIndex) ?: return@launch
            prepareTrack(prev, autoPlay = true)
        }
    }

    fun onSeekTo(positionMs: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            val dur = surahPlayer?.duration ?: _state.value.durationMs
            val clamped = positionMs.coerceIn(0L, if (dur > 0) dur else positionMs)
            surahPlayer?.seekTo(clamped)
            _state.update { it.copy(currentPositionMs = clamped) }
            updateService()
        }
    }

    fun onStop() {
        scope.launch(Dispatchers.Main.immediate) {
            try {
                surahPlayer?.stop()
                surahPlayer?.clearMediaItems()
                surahPlayer?.playWhenReady = false
                ambientPlayer?.stop()
                ambientPlayer?.clearMediaItems()
                ambientPlayer?.playWhenReady = false
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping players: ${e.message}")
            }
            _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
            abandonAudioFocus()
            NurAnjamPlaybackService.stop(context)
        }
    }

    // ==========================================
    // Precise Independent Volume Control
    // ==========================================

    fun setRecitationVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        baseRecitationVolume = clamped
        prefs.edit().putFloat(KEY_RECITATION_VOL, clamped).apply()
        applyRecitationVolume(clamped)
        _state.update { it.copy(recitationVolume = clamped) }
    }

    private fun applyRecitationVolume(volume: Float) {
        surahPlayer?.volume = volume
    }

    fun setAmbientVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        baseAmbientVolume = clamped
        prefs.edit().putFloat(KEY_AMBIENT_VOL, clamped).apply()

        val isActive = clamped > 0f && _state.value.isAmbientPlaying
        applyAmbientVolume(if (isActive) clamped else 0f)
        _state.update { it.copy(ambientVolume = clamped) }

        if (clamped > 0f && _state.value.isPlaying && _state.value.isAmbientPlaying) {
            startAmbientPlayback()
        } else if (clamped <= 0f) {
            pauseAmbientPlayback()
        }
    }

    private fun applyAmbientVolume(volume: Float) {
        ambientPlayer?.volume = volume
    }

    fun selectAmbient(type: AmbientType) {
        setAmbientType(type)
    }

    @Volatile
    private var currentPreviewSessionId: Int = 0
    private var currentPreviewUriOrPath: String? = null
    private var currentPreviewIsOnline: Boolean = false

    fun setAtmosphereEnabled(enabled: Boolean) {
        isAmbientActive = enabled
        prefs.edit().putBoolean(KEY_AMBIENT_ACTIVE, enabled).apply()
        
        if (enabled) {
            val savedVol = if (baseAmbientVolume > 0f) baseAmbientVolume else 0.50f
            val savedId = _state.value.activePreviewSoundId ?: prefs.getString("key_custom_ambient_id", null)
            val savedTitle = _state.value.activeAmbientTitle ?: prefs.getString("key_custom_ambient_title", null)
            val savedPath = currentPreviewUriOrPath ?: prefs.getString("key_custom_ambient_path", null)
            currentPreviewUriOrPath = savedPath
            _state.update {
                it.copy(
                    isAmbientPlaying = !savedPath.isNullOrBlank(),
                    ambientVolume = savedVol,
                    activeAmbientType = if (!savedPath.isNullOrBlank()) AmbientPlayType.DOWNLOADED_PREVIEW else AmbientPlayType.NONE,
                    activePreviewSoundId = savedId,
                    activeAmbientTitle = savedTitle,
                    activeAmbientFilePath = savedPath
                )
            }
            applyAmbientVolume(savedVol)
            startAmbientPlayback()
        } else {
            // Authoritative OFF for all ambient audio paths
            currentPreviewSessionId++ // Invalidate any running async loads
            _state.update {
                it.copy(
                    isAmbientPlaying = false,
                    ambientVolume = 0.0f,
                    activeAmbientType = AmbientPlayType.NONE
                )
            }
            pauseAmbientPlayback() // releasing decoders
        }
    }

    fun toggleAmbientPlayback() {
        setAtmosphereEnabled(!_state.value.isAmbientPlaying)
    }

    fun playAmbientPreview(uriOrPath: String, soundId: String, isOnline: Boolean, title: String? = null) {
        val ambient = ambientPlayer ?: return
        
        // Stop any running sound and invalidate running async loads
        val sessionId = ++currentPreviewSessionId
        currentPreviewUriOrPath = uriOrPath
        currentPreviewIsOnline = isOnline
        isAmbientActive = true

        if (!isOnline) {
            prefs.edit()
                .putString("key_custom_ambient_path", uriOrPath)
                .putString("key_custom_ambient_id", soundId)
                .putString("key_custom_ambient_title", title ?: "Custom Atmosphere")
                .putBoolean(KEY_AMBIENT_ACTIVE, true)
                .apply()
        }

        ambient.stop()
        ambient.clearMediaItems()
        currentPreparedAmbient = null
        
        try {
            val uri = if (uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://") || uriOrPath.startsWith("content://") || uriOrPath.startsWith("android.resource://")) {
                Uri.parse(uriOrPath)
            } else {
                Uri.fromFile(java.io.File(uriOrPath))
            }

            val playType = if (isOnline) AmbientPlayType.ONLINE_PREVIEW else AmbientPlayType.DOWNLOADED_PREVIEW
            
            // Mark state as active custom atmosphere
            _state.update {
                it.copy(
                    isAmbientPlaying = true,
                    ambientVolume = if (baseAmbientVolume > 0f) baseAmbientVolume else 0.50f,
                    activeAmbientType = playType,
                    activePreviewSoundId = soundId,
                    activeAmbientTitle = title ?: it.activeAmbientTitle ?: "Custom Atmosphere",
                    activeAmbientFilePath = uriOrPath
                )
            }
            
            val mediaItem = MediaItem.fromUri(uri)
            ambient.setMediaItem(mediaItem)
            ambient.repeatMode = Player.REPEAT_MODE_ALL
            ambient.prepare()
            
            val savedVol = if (baseAmbientVolume > 0f) baseAmbientVolume else 0.50f
            ambient.volume = savedVol
            
            // Session ID protection to prevent race conditions
            if (sessionId == currentPreviewSessionId) {
                ambient.playWhenReady = true
                ambient.play()
                Log.i(TAG, "Started custom atmosphere looping playback (REPEAT_MODE_ALL) for $soundId from $uri [Session $sessionId]")
            } else {
                ambient.stop()
                ambient.clearMediaItems()
                Log.i(TAG, "Prevented async preview load race condition for sound $soundId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom atmosphere: ${e.message}", e)
            stopAmbientPreview()
        }
    }

    fun stopAmbientPreview() {
        currentPreviewSessionId++ // Invalidate any running async loads
        isAmbientActive = false
        prefs.edit().putBoolean(KEY_AMBIENT_ACTIVE, false).apply()
        _state.update {
            it.copy(
                isAmbientPlaying = false,
                activeAmbientType = AmbientPlayType.NONE
            )
        }
        pauseAmbientPlayback() // releasing decoders
    }

    fun setAmbientType(type: AmbientType) {
        prefs.edit().putString(KEY_SELECTED_AMBIENT, type.name).apply()
        isAmbientActive = true
        prefs.edit().putBoolean(KEY_AMBIENT_ACTIVE, true).apply()
        val savedVol = if (baseAmbientVolume > 0f) baseAmbientVolume else 0.50f
        val activeVol = if (_state.value.ambientVolume > 0f) _state.value.ambientVolume else savedVol
        _state.update {
            it.copy(
                selectedAmbient = type,
                isAmbientPlaying = true,
                ambientVolume = activeVol,
                activeAmbientType = AmbientPlayType.PRESET_ATMOSPHERE,
                activePreviewSoundId = null
            )
        }
        applyAmbientVolume(activeVol)
        prepareAmbientMediaSource(type)
        startAmbientPlayback()
    }

    fun applyPresetMix(recitationVol: Float, ambientVol: Float, ambient: AmbientType) {
        setRecitationVolume(recitationVol)
        setAmbientVolume(ambientVol)
        setAmbientType(ambient)
    }

    fun setAudioPreset(preset: AudioPreset) {
        _state.update { it.copy(audioPreset = preset) }
    }

    fun toggleShuffle() {
        if (_state.value.playbackMode == PlaybackMode.SHUFFLE_FOLDER) {
            setPlaybackMode(PlaybackMode.LOOP_FOLDER)
        } else {
            setPlaybackMode(PlaybackMode.SHUFFLE_FOLDER)
        }
    }

    fun cycleRepeatMode() {
        cyclePlaybackMode()
    }

    fun cyclePlaybackMode(): PlaybackMode {
        val nextMode = when (_state.value.playbackMode) {
            PlaybackMode.LOOP_FOLDER -> PlaybackMode.LOOP_TRACK
            PlaybackMode.LOOP_TRACK -> PlaybackMode.SHUFFLE_FOLDER
            PlaybackMode.SHUFFLE_FOLDER -> PlaybackMode.FREQUENCY_PLAY
            PlaybackMode.FREQUENCY_PLAY -> PlaybackMode.LOOP_FOLDER
        }
        setPlaybackMode(nextMode)
        return nextMode
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        _state.update {
            it.copy(
                playbackMode = mode,
                repeatMode = if (mode == PlaybackMode.LOOP_TRACK) RepeatMode.ONE else RepeatMode.ALL,
                isShuffle = (mode == PlaybackMode.SHUFFLE_FOLDER)
            )
        }

        val current = _state.value.currentTrack ?: return
        val repoTracks = LocalAudioRepository.getInstance(context).tracks.value
        val folderTracks = repoTracks.filter { it.folderName.equals(current.folderName, ignoreCase = true) }
            .ifEmpty { listOf(current) }

        val orderedList = when (mode) {
            PlaybackMode.LOOP_TRACK -> {
                listOf(current) + folderTracks.filter { it.id != current.id }
            }
            PlaybackMode.LOOP_FOLDER -> {
                sortFolderTracks(folderTracks)
            }
            PlaybackMode.SHUFFLE_FOLDER -> {
                val remaining = folderTracks.filter { it.id != current.id }.shuffled()
                listOf(current) + remaining
            }
            PlaybackMode.FREQUENCY_PLAY -> {
                folderTracks.sortedWith(
                    compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
                )
            }
        }

        playlist = orderedList
        currentIndex = orderedList.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        Log.i(TAG, "Rebuilt folder queue under mode ${mode.name} (Folder: ${current.folderName}, Size: ${orderedList.size})")
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        surahPlayer?.playbackParameters = PlaybackParameters(clamped)
        _state.update { it.copy(playbackSpeed = clamped) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingSec = null, isSleepTimerActive = false) }
            return
        }

        val targetEndTimeMs = android.os.SystemClock.elapsedRealtime() + (minutes * 60 * 1000L)
        val initialRemainingSec = minutes * 60
        _state.update { it.copy(sleepTimerRemainingSec = initialRemainingSec, isSleepTimerActive = true) }

        sleepTimerJob = scope.launch {
            while (isActive) {
                val currentRealtimeMs = android.os.SystemClock.elapsedRealtime()
                val remainingMs = targetEndTimeMs - currentRealtimeMs
                val remainingSec = (remainingMs / 1000L).coerceAtLeast(0L).toInt()

                if (remainingSec <= 0) {
                    onStop()
                    _state.update { it.copy(sleepTimerRemainingSec = null, isSleepTimerActive = false) }
                    break
                } else {
                    if (_state.value.sleepTimerRemainingSec != remainingSec) {
                        _state.update { it.copy(sleepTimerRemainingSec = remainingSec, isSleepTimerActive = true) }
                    }
                }
                delay(1000L)
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _state.update { it.copy(sleepTimerRemainingSec = null, isSleepTimerActive = false) }
    }

    private fun handleTrackEnded() {
        when (_state.value.playbackMode) {
            PlaybackMode.LOOP_TRACK -> {
                onSeekTo(0L)
                onPlay()
            }
            PlaybackMode.LOOP_FOLDER,
            PlaybackMode.SHUFFLE_FOLDER,
            PlaybackMode.FREQUENCY_PLAY -> {
                onSkipToNext()
            }
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch {
            while (isActive) {
                val player = surahPlayer
                if (player != null && _state.value.isPlaying) {
                    try {
                        val currentPos = player.currentPosition
                        if (Math.abs(currentPos - _state.value.currentPositionMs) >= 200L || currentPos == 0L) {
                            _state.update { it.copy(currentPositionMs = currentPos) }
                        }

                        // Room Database Play Count Tracking (Triggers at 30 seconds mark)
                        if (currentPos >= 30000L && !recordedPlayForCurrentTrack) {
                            recordedPlayForCurrentTrack = true
                            val currentTrack = _state.value.currentTrack
                            if (currentTrack != null) {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val db = NurAnjamDatabase.getInstance(context)
                                        val existingCount = db.trackPlayDao().getPlayCountByUri(currentTrack.dataUri) ?: 0
                                        val newCount = existingCount + 1
                                        db.trackPlayDao().insertOrUpdate(
                                            TrackPlayRecordEntity(
                                                trackUri = currentTrack.dataUri,
                                                trackId = currentTrack.id,
                                                playCount = newCount,
                                                lastPlayedTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                        LocalAudioRepository.getInstance(context).recordPlay(currentTrack.id)
                                        Log.i(TAG, "Incremented Room play count for '${currentTrack.title}'. New count: $newCount")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error recording play count in Room: ${e.message}", e)
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    val delayMs = if (isHighFrequencyProgressActive) 350L else 3000L
                    delay(delayMs)
                } else {
                    delay(1000L)
                }
            }
        }
    }

    private fun updateService() {
        val current = _state.value.currentTrack ?: return
        try {
            NurAnjamPlaybackService.startOrUpdate(
                context = context,
                track = current,
                isPlaying = _state.value.isPlaying,
                positionMs = _state.value.currentPositionMs
            )
        } catch (e: Exception) {
            Log.w(TAG, "Cannot update service: ${e.message}")
        }
    }

    private fun requestAudioFocus(): Boolean {
        // Primary surahPlayer has setAudioAttributes(audioAttributes, true) enabled,
        // which automatically handles system audio focus and notification ducking (AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK).
        if (surahPlayer != null) return true
        val am = audioManager ?: return true
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
            Log.d(TAG, "Audio focus requested, result: $result")
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting audio focus: ${e.message}", e)
            return true
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = audioFocusRequest as? AudioFocusRequest
                if (req != null) am.abandonAudioFocusRequest(req)
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
            audioFocusRequest = null
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        }
    }

    fun release() {
        positionTrackerJob?.cancel()
        sleepTimerJob?.cancel()
        outputDetector.release()
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}
        abandonAudioFocus()
        try {
            surahPlayer?.release()
            ambientPlayer?.release()
        } catch (_: Exception) {}
        surahPlayer = null
        ambientPlayer = null
    }

    fun handleTrackDeleted(trackId: String) {
        val current = _state.value.currentTrack
        if (current != null && current.id == trackId) {
            val player = surahPlayer
            try {
                player?.stop()
                player?.clearMediaItems()
            } catch (_: Exception) {}

            val nextTrack = playlist.firstOrNull { it.id != trackId }
            if (nextTrack != null) {
                playTrack(nextTrack)
            } else {
                _state.update {
                    it.copy(
                        currentTrack = null,
                        isPlaying = false,
                        currentPositionMs = 0L,
                        durationMs = 0L
                    )
                }
            }
        }
        playlist = playlist.filter { it.id != trackId }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                if (playlist.isEmpty()) return@withLock
                val current = _state.value.currentTrack
                val list = playlist.toMutableList()
                if (fromIndex in list.indices && toIndex in list.indices) {
                    val track = list.removeAt(fromIndex)
                    list.add(toIndex, track)
                    playlist = list
                    if (current != null) {
                        currentIndex = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                    }
                }
            }
        }
    }

    fun removeTrackFromQueue(trackId: String) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                val current = _state.value.currentTrack
                if (current != null && current.id == trackId) {
                    onSkipToNext()
                }
                playlist = playlist.filter { it.id != trackId }
                val newCurrent = _state.value.currentTrack
                if (newCurrent != null) {
                    currentIndex = playlist.indexOfFirst { it.id == newCurrent.id }.coerceAtLeast(0)
                }
            }
        }
    }

    fun playTrackNext(track: AudioTrack) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                val current = _state.value.currentTrack
                val list = playlist.filter { it.id != track.id }.toMutableList()
                val targetIdx = if (currentIndex in list.indices) currentIndex + 1 else 0
                list.add(targetIdx, track)
                playlist = list
                if (current != null) {
                    currentIndex = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                }
            }
        }
    }

    fun addToQueueEnd(track: AudioTrack) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                val current = _state.value.currentTrack
                val list = playlist.filter { it.id != track.id }.toMutableList()
                list.add(track)
                playlist = list
                if (current != null) {
                    currentIndex = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                }
            }
        }
    }

    fun updateTrackFavoriteState(trackId: String, isFavorite: Boolean) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                playlist = playlist.map {
                    if (it.id == trackId) it.copy(isFavorite = isFavorite) else it
                }
                _state.update { currentState ->
                    val updatedCurrent = currentState.currentTrack?.let { current ->
                        if (current.id == trackId) current.copy(isFavorite = isFavorite) else current
                    }
                    currentState.copy(
                        currentTrack = updatedCurrent,
                        playQueue = playlist
                    )
                }
            }
        }
    }

    fun updateTrackDate(trackId: String, newTimestamp: Long) {
        scope.launch(Dispatchers.Main.immediate) {
            playlistMutex.withLock {
                val updatedList = playlist.map {
                    if (it.id == trackId) it.copy(customSortTimestamp = newTimestamp) else it
                }
                playlist = if (_state.value.playbackMode == PlaybackMode.LOOP_FOLDER) {
                    sortFolderTracks(updatedList)
                } else {
                    updatedList
                }
                val current = _state.value.currentTrack
                if (current != null) {
                    currentIndex = playlist.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                }
                _state.update { currentState ->
                    val updatedCurrent = currentState.currentTrack?.let { cur ->
                        if (cur.id == trackId) cur.copy(customSortTimestamp = newTimestamp) else cur
                    }
                    currentState.copy(
                        currentTrack = updatedCurrent,
                        playQueue = playlist,
                        currentMediaItemIndex = currentIndex
                    )
                }
            }
        }
    }
}
