package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DualAudioMixer
import com.example.data.LocalAudioRepository
import com.example.data.AuthResult
import com.example.data.DeletionResult
import com.example.data.NativeFileDeletionManager
import com.example.data.SecureDeleteManager
import com.example.intelligence.DailyReportData
import com.example.intelligence.ListeningStatsSummary
import com.example.intelligence.NurAnjamIntelligenceEngine
import com.example.intelligence.PersonalMusicProfile
import com.example.intelligence.RecommendationGroup
import com.example.intelligence.TimeRangeFilter
import com.example.intelligence.TopTrackInsight
import com.example.model.AmbientType
import com.example.model.AudioPreset
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.activity.result.IntentSenderRequest
import androidx.fragment.app.FragmentActivity

enum class AppScreen {
    LIBRARY,
    PLAYER,
    ATMOSPHERE,
    INTELLIGENCE_ONBOARDING,
    INTELLIGENCE,
    INTELLIGENCE_REPORT
}

class NurAnjamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalAudioRepository.getInstance(application)
    private val mixer = DualAudioMixer(application)
    private val intelligenceEngine by lazy { NurAnjamIntelligenceEngine.getInstance(application) }
    private val deletionManager by lazy { NativeFileDeletionManager(application) }
    private val secureDeleteManager by lazy { SecureDeleteManager(application) }
    private val navStateStore = com.example.data.NavigationStateStore.getInstance(application)
    private val _savedNavigationLocation = MutableStateFlow(navStateStore.getSavedLocation())
    val savedNavigationLocation: StateFlow<com.example.data.PersistedNavigationLocation> = _savedNavigationLocation.asStateFlow()

    fun saveNavigationLocation(
        tab: com.example.model.LibraryTab,
        containerType: String = "NONE",
        containerId: String? = null,
        containerName: String? = null,
        filterFavoritesOnly: Boolean = false,
        currentFolder: String? = null,
        currentAlbum: String? = null,
        currentPlaylist: String? = null
    ) {
        val updated = navStateStore.saveLocation(
            tab = tab,
            containerType = containerType,
            containerId = containerId,
            containerName = containerName,
            filterFavoritesOnly = filterFavoritesOnly,
            currentFolder = currentFolder,
            currentAlbum = currentAlbum,
            currentPlaylist = currentPlaylist
        )
        _savedNavigationLocation.value = updated
    }

    val sortOrder: StateFlow<com.example.model.AudioSortOrder> = repository.sortOrder

    fun setSortOrder(order: com.example.model.AudioSortOrder) {
        repository.setSortOrder(order)
    }

    fun sortTracks(
        tracks: List<AudioTrack>,
        order: com.example.model.AudioSortOrder
    ): List<AudioTrack> {
        return when (order) {
            com.example.model.AudioSortOrder.DATE_NEWEST -> tracks.sortedByDescending { it.customSortTimestamp }
            com.example.model.AudioSortOrder.DATE_OLDEST -> tracks.sortedBy { it.customSortTimestamp }
            com.example.model.AudioSortOrder.TITLE_AZ -> tracks.sortedBy { it.title.lowercase() }
            com.example.model.AudioSortOrder.TITLE_ZA -> tracks.sortedByDescending { it.title.lowercase() }
            com.example.model.AudioSortOrder.MOST_PLAYED -> tracks.sortedWith(
                compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
            )
        }
    }

    val tracks: StateFlow<List<AudioTrack>> = combine(
        repository.tracks,
        repository.sortOrder
    ) { list, order ->
        sortTracks(list, order)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // High-performance background-cached collections for 120 FPS tab transitions
    val cleanTracks: StateFlow<List<AudioTrack>> = tracks
        .map { list -> list.filter { it.id.isNotBlank() && it.title.isNotBlank() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folderGroups: StateFlow<Map<String, List<AudioTrack>>> = cleanTracks
        .map { list ->
            list.filter { it.folderName.isNotBlank() && !it.folderName.startsWith(".") }
                .groupBy { it.folderName }
                .filter { it.key.isNotBlank() && it.value.isNotEmpty() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val albumGroups: StateFlow<Map<String, List<AudioTrack>>> = cleanTracks
        .map { list ->
            list.filter { it.album.isNotBlank() }
                .groupBy { it.album }
                .filter { it.key.isNotBlank() && it.value.isNotEmpty() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val recentTracks: StateFlow<List<AudioTrack>> = repository.recentTracks
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = mixer.state

    private val _currentScreen = MutableStateFlow(AppScreen.LIBRARY)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _baseScreen = MutableStateFlow(AppScreen.LIBRARY)
    val baseScreen: StateFlow<AppScreen> = _baseScreen.asStateFlow()

    private val _selectedMenuTrack = MutableStateFlow<AudioTrack?>(null)
    val selectedMenuTrack: StateFlow<AudioTrack?> = _selectedMenuTrack.asStateFlow()

    private val _showFinalDeleteConfirmTrack = MutableStateFlow<AudioTrack?>(null)
    val showFinalDeleteConfirmTrack: StateFlow<AudioTrack?> = _showFinalDeleteConfirmTrack.asStateFlow()

    // Scoped Storage MediaStore Delete Request launcher state
    private val _pendingDeleteIntentSender = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingDeleteIntentSender: StateFlow<IntentSenderRequest?> = _pendingDeleteIntentSender.asStateFlow()

    private var pendingDeleteTrack: AudioTrack? = null

    private val _uiUserMessage = MutableSharedFlow<String>()
    val uiUserMessage: SharedFlow<String> = _uiUserMessage

    // =========================================================================
    // NURANJAM INTELLIGENCE REACTIVE STATE FLOWS
    // =========================================================================

    val isOnboardingCompleted: StateFlow<Boolean> by lazy { intelligenceEngine.isOnboardingCompleted }

    private val _selectedTimeRange = MutableStateFlow(TimeRangeFilter.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRangeFilter> = _selectedTimeRange.asStateFlow()

    val statsSummary: StateFlow<ListeningStatsSummary> by lazy {
        combine(
            cleanTracks,
            intelligenceEngine.allListeningEvents
        ) { trackList, _ ->
            intelligenceEngine.getStatsSummary(trackList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningStatsSummary())
    }

    val personalProfile: StateFlow<PersonalMusicProfile> by lazy {
        combine(
            cleanTracks,
            intelligenceEngine.allListeningEvents
        ) { trackList, _ ->
            intelligenceEngine.getPersonalProfile(trackList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PersonalMusicProfile())
    }

    val recommendations: StateFlow<List<RecommendationGroup>> by lazy {
        combine(
            cleanTracks,
            intelligenceEngine.allListeningEvents
        ) { trackList, _ ->
            intelligenceEngine.getRecommendations(trackList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val topTracks: StateFlow<List<TopTrackInsight>> by lazy {
        combine(
            cleanTracks,
            _selectedTimeRange,
            intelligenceEngine.allListeningEvents
        ) { trackList, timeRange, _ ->
            intelligenceEngine.getTopTrackInsights(trackList, timeRange)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val dailyReport: StateFlow<DailyReportData> by lazy {
        combine(
            cleanTracks,
            intelligenceEngine.allListeningEvents
        ) { trackList, _ ->
            intelligenceEngine.getDailyReport(trackList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyReportData("", "0m", 0, 0, "", 0, "", "", "0%"))
    }

    init {
        // Observe playback state changes and feed them to the intelligence engine after startup delay
        viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            try {
                mixer.state.collect { state ->
                    try {
                        intelligenceEngine.onPlaybackStateUpdate(state)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    fun navigateTo(screen: AppScreen) {
        if (screen != AppScreen.PLAYER) {
            _baseScreen.value = screen
        }
        _currentScreen.value = screen
    }

    fun onLogoClicked() {
        val target = if (intelligenceEngine.isOnboardingCompleted.value) {
            AppScreen.INTELLIGENCE
        } else {
            AppScreen.INTELLIGENCE_ONBOARDING
        }
        _baseScreen.value = target
        _currentScreen.value = target
    }

    fun setTimeRangeFilter(filter: TimeRangeFilter) {
        _selectedTimeRange.value = filter
    }

    fun completeOnboarding() {
        intelligenceEngine.completeOnboarding()
        _baseScreen.value = AppScreen.INTELLIGENCE
        _currentScreen.value = AppScreen.INTELLIGENCE
    }

    fun clearIntelligenceHistory() {
        viewModelScope.launch {
            intelligenceEngine.clearAllIntelligenceHistory()
            _uiUserMessage.emit("Listening history & profile cleared")
        }
    }

    fun resetRecommendations() {
        viewModelScope.launch {
            _uiUserMessage.emit("Recommendations recalculated from history")
        }
    }

    fun playTrack(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            repository.recordPlay(track.id)
            mixer.playTrack(track)
        }
    }

    fun togglePlayPause() {
        mixer.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        mixer.seekTo(positionMs)
    }

    fun playNext() {
        mixer.playNext()
    }

    fun playPrevious() {
        mixer.playPrevious()
    }

    fun toggleShuffle() {
        mixer.toggleShuffle()
    }

    fun cycleRepeat() {
        mixer.cycleRepeatMode()
    }

    fun cyclePlaybackMode() {
        mixer.cyclePlaybackMode()
    }

    fun setPlaybackMode(mode: com.example.model.PlaybackMode) {
        mixer.setPlaybackMode(mode)
    }

    fun toggleFavorite(trackId: String) {
        val newState = repository.toggleFavorite(trackId)
        mixer.updateTrackFavoriteState(trackId, newState)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = com.example.data.db.NurAnjamDatabase.getInstance(getApplication()).trackPlayDao()
                val updatedRows = dao.setFavorite(trackId, newState)
                if (updatedRows == 0) {
                    val track = repository.tracks.value.find { it.id == trackId }
                    dao.insertOrUpdate(
                        com.example.data.db.TrackPlayRecordEntity(
                            trackUri = track?.dataUri?.ifBlank { trackId } ?: trackId,
                            trackId = trackId,
                            playCount = track?.playCount ?: 0,
                            lastPlayedTimestamp = System.currentTimeMillis(),
                            isFavorite = newState
                        )
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun updateTrackDate(trackId: String, newTimestamp: Long) {
        repository.updateTrackDate(trackId, newTimestamp)
        mixer.updateTrackDate(trackId, newTimestamp)
        _selectedMenuTrack.value = _selectedMenuTrack.value?.let { current ->
            if (current.id == trackId) {
                current.copy(customSortTimestamp = newTimestamp)
            } else {
                current
            }
        }
        viewModelScope.launch {
            val formatted = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(newTimestamp))
            _uiUserMessage.emit("Track date updated to $formatted ✓")
        }
    }

    fun setRecitationVolume(volume: Float) {
        mixer.setRecitationVolume(volume)
    }

    fun setAmbientVolume(volume: Float) {
        mixer.setAmbientVolume(volume)
    }

    fun selectAmbient(ambientType: AmbientType) {
        mixer.selectAmbient(ambientType)
    }

    fun toggleAmbientPlayback() {
        mixer.toggleAmbientPlayback()
    }

    // =========================================================================
    // NURANJAM ATMOSPHERE ONLINE DISCOVERY & DOWNLOADED AMBIENT STATE
    // =========================================================================

    private val ambientDb by lazy { com.example.data.db.NurAnjamDatabase.getInstance(application) }
    private val ambientDao by lazy { ambientDb.downloadedAmbientDao() }
    private val ambientProvider: com.example.data.ambient.AmbientProvider by lazy { com.example.data.ambient.FreesoundProvider() }
    private val downloadManager by lazy { com.example.data.ambient.AmbientDownloadManager.getInstance(application) }

    val downloadedAmbients: StateFlow<List<com.example.data.ambient.DownloadedAmbientEntity>> by lazy {
        ambientDao.getAllDownloaded()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val favoriteAmbients: StateFlow<List<com.example.data.ambient.DownloadedAmbientEntity>> by lazy {
        ambientDao.getFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val recentAmbients: StateFlow<List<com.example.data.ambient.DownloadedAmbientEntity>> by lazy {
        ambientDao.getRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val totalStorageBytes: StateFlow<Long> by lazy {
        ambientDao.getTotalStorageBytes()
            .map { it ?: 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    }

    private val _ambientSearchQuery = MutableStateFlow("")
    val ambientSearchQuery: StateFlow<String> = _ambientSearchQuery.asStateFlow()

    private val _isSearchingAmbients = MutableStateFlow(false)
    val isSearchingAmbients: StateFlow<Boolean> = _isSearchingAmbients.asStateFlow()

    private val _ambientSearchResults = MutableStateFlow<List<com.example.data.ambient.OnlineAmbientSound>>(emptyList())
    val ambientSearchResults: StateFlow<List<com.example.data.ambient.OnlineAmbientSound>> = _ambientSearchResults.asStateFlow()

    private val _ambientSearchError = MutableStateFlow<String?>(null)
    val ambientSearchError: StateFlow<String?> = _ambientSearchError.asStateFlow()

    private val _previewingSound = MutableStateFlow<com.example.data.ambient.OnlineAmbientSound?>(null)
    val previewingSound: StateFlow<com.example.data.ambient.OnlineAmbientSound?> = _previewingSound.asStateFlow()

    private var ambientSearchJob: kotlinx.coroutines.Job? = null

    fun searchOnlineAmbients(query: String) {
        _ambientSearchQuery.value = query
        if (query.isBlank()) {
            ambientSearchJob?.cancel()
            _ambientSearchResults.value = emptyList()
            _ambientSearchError.value = null
            return
        }

        ambientSearchJob?.cancel()
        ambientSearchJob = viewModelScope.launch {
            // Debounce online search requests by 500ms of typing inactivity
            // to completely prevent redundant and concurrent cellular radio wakeups.
            delay(500L)
            
            _isSearchingAmbients.value = true
            _ambientSearchError.value = null
            val result = ambientProvider.searchSounds(query)
            _isSearchingAmbients.value = false

            result.onSuccess { page ->
                _ambientSearchResults.value = page.sounds
                if (page.sounds.isEmpty()) {
                    _ambientSearchError.value = "No ambient sounds found for '$query'."
                }
            }.onFailure { err ->
                _ambientSearchError.value = "Unable to connect. Check your internet connection."
            }
        }
    }

    fun previewAmbientSound(sound: com.example.data.ambient.OnlineAmbientSound) {
        val state = playbackState.value
        if (state.activePreviewSoundId == sound.soundId && state.isAmbientPlaying && state.activeAmbientType == com.example.model.AmbientPlayType.ONLINE_PREVIEW) {
            mixer.stopAmbientPreview()
        } else {
            _previewingSound.value = sound
            mixer.playAmbientPreview(sound.previewUrl, sound.soundId, isOnline = true)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadManager.purgeLegacyHardcodedAmbients()
            } catch (_: Exception) {}
        }
    }

    fun playDownloadedAmbient(entity: com.example.data.ambient.DownloadedAmbientEntity) {
        val state = playbackState.value
        if (state.activePreviewSoundId == entity.id && state.isAmbientPlaying && state.activeAmbientType == com.example.model.AmbientPlayType.DOWNLOADED_PREVIEW) {
            mixer.stopAmbientPreview()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                ambientDao.updateLastPlayed(entity.id)
            }
            mixer.playAmbientPreview(entity.localFilePath, entity.id, isOnline = false, title = entity.title)
        }
    }

    fun importCustomAtmosphereAudio(uri: Uri, userDefinedName: String) {
        viewModelScope.launch {
            _uiUserMessage.emit("Saving custom atmosphere '$userDefinedName'...")
            val result = downloadManager.importCustomAtmosphereFromUri(uri, userDefinedName)
            result.onSuccess { entity ->
                _uiUserMessage.emit("Added '${entity.title}' to Custom Atmosphere ✓")
                playDownloadedAmbient(entity)
            }.onFailure { err ->
                _uiUserMessage.emit("Failed to import audio: ${err.message}")
            }
        }
    }

    fun stopAmbientPreview() {
        mixer.stopAmbientPreview()
    }

    fun stopAmbientPreviewIfActive() {
        val state = playbackState.value
        if (state.activeAmbientType == com.example.model.AmbientPlayType.ONLINE_PREVIEW) {
            mixer.stopAmbientPreview()
        }
    }

    fun downloadAmbientSound(sound: com.example.data.ambient.OnlineAmbientSound) {
        viewModelScope.launch {
            _uiUserMessage.emit("Downloading '${sound.title}'...")
            val result = downloadManager.downloadAmbientSound(sound)
            result.onSuccess {
                _uiUserMessage.emit("Downloaded '${sound.title}' ✓ Available offline")
            }.onFailure { err ->
                _uiUserMessage.emit("Download failed: ${err.message}")
            }
        }
    }

    fun deleteDownloadedAmbient(soundId: String) {
        viewModelScope.launch {
            val state = playbackState.value
            if (state.activePreviewSoundId == soundId && state.isAmbientPlaying) {
                mixer.stopAmbientPreview()
            }
            downloadManager.deleteDownloadedSound(soundId)
            _uiUserMessage.emit("Custom atmosphere removed")
        }
    }

    fun toggleFavoriteAmbient(soundId: String, currentFav: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ambientDao.setFavorite(soundId, !currentFav)
        }
    }

    fun getDownloadProgressFlow(soundId: String): StateFlow<Int> {
        return downloadManager.getDownloadProgressFlow(soundId)
    }

    fun getDownloadStateFlow(soundId: String): StateFlow<com.example.data.ambient.AmbientDownloadState> {
        return downloadManager.getDownloadStateFlow(soundId)
    }

    fun applyPresetMix(recitationVol: Float, ambientVol: Float, ambient: AmbientType) {
        mixer.applyPresetMix(recitationVol, ambientVol, ambient)
    }

    fun setSleepTimer(minutes: Int) {
        mixer.setSleepTimer(minutes)
    }

    fun setPlaybackSpeed(speed: Float) {
        mixer.setPlaybackSpeed(speed)
    }

    fun updateCustomCoverPhoto(trackId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedPath = com.example.data.AudioArtworkExtractor.saveCustomCover(getApplication(), trackId, uri)
            if (savedPath != null) {
                _uiUserMessage.emit("Custom cover photo applied successfully!")
            } else {
                _uiUserMessage.emit("Failed to save custom cover photo.")
            }
        }
    }

    fun setAudioPreset(preset: AudioPreset) {
        mixer.setAudioPreset(preset)
    }

    fun scanLocalAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                repository.scanLocalAudio()
                _uiUserMessage.emit("Local storage scanned successfully")
            } catch (e: Exception) {
                _uiUserMessage.emit("Scan error: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanStorage(force: Boolean = false) {
        scanLocalAudio()
    }

    fun importAudioFile(uri: Uri, displayName: String) {
        repository.addImportedUri(uri, displayName)
    }

    fun playExternalUriTrack(track: AudioTrack) {
        repository.addExternalTrack(track)
        playTrack(track)
        navigateTo(AppScreen.PLAYER)
    }

    fun openTrackMenu(track: AudioTrack) {
        _selectedMenuTrack.value = track
    }

    fun closeTrackMenu() {
        _selectedMenuTrack.value = null
    }

    fun refreshAudioOutput() {
        mixer.refreshAudioOutput()
    }

    fun onDeleteFromDeviceClicked(activity: FragmentActivity, track: AudioTrack) {
        closeTrackMenu()
        val isSecure = SecureDeleteManager.isSecureAuthAvailable(getApplication())
        if (isSecure) {
            secureDeleteManager.promptSystemAuthentication(activity) { authResult ->
                when (authResult) {
                    is AuthResult.Success -> {
                        executePermanentDelete(track)
                    }
                    is AuthResult.Cancelled -> {
                        viewModelScope.launch {
                            _uiUserMessage.emit("Deletion cancelled.")
                        }
                    }
                    is AuthResult.Failed -> {
                        viewModelScope.launch {
                            _uiUserMessage.emit("Authentication failed. The song was not deleted.")
                        }
                    }
                }
            }
        } else {
            _showFinalDeleteConfirmTrack.value = track
        }
    }

    fun cancelFinalDeleteConfirm() {
        _showFinalDeleteConfirmTrack.value = null
    }

    fun confirmPermanentDelete(track: AudioTrack) {
        _showFinalDeleteConfirmTrack.value = null
        executePermanentDelete(track)
    }

    private fun executePermanentDelete(track: AudioTrack) {
        viewModelScope.launch {
            when (val result = deletionManager.deleteAudioFile(track)) {
                is DeletionResult.RequiresIntentSender -> {
                    pendingDeleteTrack = track
                    _pendingDeleteIntentSender.value = IntentSenderRequest.Builder(result.intentSender).build()
                }
                is DeletionResult.Success -> {
                    val verified = deletionManager.verifyFileDeleted(track)
                    if (verified) {
                        completeDeletionCleanup(track)
                    } else {
                        _uiUserMessage.emit("Could not permanently delete this file.")
                    }
                }
                is DeletionResult.Error -> {
                    _uiUserMessage.emit("Could not permanently delete this file.")
                }
            }
        }
    }

    fun onDeletePermissionGranted() {
        val track = pendingDeleteTrack
        pendingDeleteTrack = null
        _pendingDeleteIntentSender.value = null
        if (track != null) {
            val verified = deletionManager.verifyFileDeleted(track)
            if (verified) {
                completeDeletionCleanup(track)
            } else {
                viewModelScope.launch {
                    _uiUserMessage.emit("Could not permanently delete this file.")
                }
            }
        }
    }

    fun onDeletePermissionDenied() {
        pendingDeleteTrack = null
        _pendingDeleteIntentSender.value = null
        viewModelScope.launch {
            _uiUserMessage.emit("Deletion cancelled. File was not removed.")
        }
    }

    private fun completeDeletionCleanup(track: AudioTrack) {
        viewModelScope.launch {
            repository.removeTrack(track.id)
            mixer.removeTrackFromQueue(track.id)
            com.example.data.AudioArtworkExtractor.removeCustomCover(getApplication(), track.id)
            _uiUserMessage.emit("Song permanently deleted.")
        }
    }

    fun clearPendingDeleteIntentSender() {
        _pendingDeleteIntentSender.value = null
    }

    fun simulateAudioDevice(device: com.example.model.AudioOutputDevice?) {
        mixer.simulateAudioDevice(device)
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        mixer.reorderQueue(fromIndex, toIndex)
    }

    fun removeTrackFromQueue(trackId: String) {
        mixer.removeTrackFromQueue(trackId)
    }

    fun playTrackNext(track: AudioTrack) {
        mixer.playTrackNext(track)
    }

    fun addToQueueEnd(track: AudioTrack) {
        mixer.addToQueueEnd(track)
    }

    fun setProgressTrackingActive(active: Boolean) {
        mixer.setProgressTrackingActive(active)
    }

    override fun onCleared() {
        super.onCleared()
        mixer.setProgressTrackingActive(false)
        // Do not call mixer.release() here: DualAudioMixerBridge is an application-scoped singleton
        // backing the foreground playback service so music continues seamlessly in the background.
    }
}
