package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.app.Activity
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.example.ui.NurAnjamPhysics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ui.ArtworkBounds
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AudioTrack
import com.example.ui.AppScreen
import com.example.ui.AtmosphereScreen
import com.example.ui.LibraryScreen
import com.example.ui.NurAnjamViewModel
import com.example.ui.PlayerMenuBottomSheet
import com.example.ui.PlayerScreen
import com.example.ui.SharedElementPlayerContainer
import com.example.ui.SplashScreen
import com.example.ui.theme.NurAnjamTheme

import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var greetingManager: com.example.ui.UserGreetingManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        // Strict Cold-Boot TTS Voice Greeting (only runs once per process creation, never on recents resume)
        greetingManager = com.example.ui.UserGreetingManager(this).also { manager ->
            manager.triggerColdBootGreetingIfEligible(isFreshActivityLaunch = (savedInstanceState == null))
        }

        setContent {
            NurAnjamTheme(dynamicColor = false) {
                NurAnjamApp(
                    onQueryDocumentName = { uri -> getFileNameFromUri(uri) },
                    onFirstTimeNameSaved = { enteredName ->
                        greetingManager?.speakGreetingForUser(enteredName)
                    }
                )
            }
        }

        handleIntent(intent)
    }

    override fun onDestroy() {
        greetingManager?.shutdown()
        greetingManager = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            
            // Try to extract metadata from the Uri using MediaMetadataRetriever
            val displayName = getFileNameFromUri(uri)
            val cleanName = displayName.substringBeforeLast(".")
            var title = cleanName
            var artist = "Unknown Artist"
            var album = "External File"
            var durationMs = 0L

            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                val mTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                val mArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val mAlbum = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val mDuration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                
                if (!mTitle.isNullOrBlank()) title = mTitle
                if (!mArtist.isNullOrBlank()) artist = mArtist
                if (!mAlbum.isNullOrBlank()) album = mAlbum
                if (!mDuration.isNullOrBlank()) {
                    durationMs = mDuration.toLongOrNull() ?: 0L
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error retrieving media metadata: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            // Create custom AudioTrack
            val now = System.currentTimeMillis()
            val trackId = "external_$now"
            val externalTrack = com.example.model.AudioTrack(
                id = trackId,
                title = title,
                reciter = artist,
                durationMs = if (durationMs > 0) durationMs else 180000L,
                dataUri = uri.toString(),
                album = album,
                folderName = "External Files",
                sizeBytes = 0L,
                bitrateKbps = 320,
                isFavorite = false,
                dateAddedMs = now,
                playCount = 0
            )

            // Play track via ViewModel
            try {
                val vm = androidx.lifecycle.ViewModelProvider(this)[com.example.ui.NurAnjamViewModel::class.java]
                vm.playExternalUriTrack(externalTrack)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error playing external track through ViewModel: ${e.message}")
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var name = "Recitation_${System.currentTimeMillis()}.mp3"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val displayName = cursor.getString(nameIndex)
                    if (!displayName.isNullOrBlank()) {
                        name = displayName
                    }
                }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun configureDisplayPacing(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay
                }
                if (display != null) {
                    val currentMode = display.mode
                    val supportedModes = display.supportedModes ?: emptyArray()

                    // Match current display resolution and find highest stable refresh rate (120Hz / 90Hz / 60Hz)
                    val targetMode = supportedModes
                        .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                        .maxByOrNull { it.refreshRate }
                        ?: supportedModes.maxByOrNull { it.refreshRate }

                    if (targetMode != null && targetMode.refreshRate > 60f) {
                        val lp = activity.window.attributes
                        lp.preferredDisplayModeId = targetMode.modeId
                        activity.window.attributes = lp

                        android.util.Log.i("MainActivity", "Display pacing configured: ${targetMode.refreshRate}Hz mode ${targetMode.modeId}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Could not configure display pacing: ${e.message}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NurAnjamApp(
    onQueryDocumentName: (Uri) -> String,
    onFirstTimeNameSaved: (String) -> Unit = {},
    viewModel: NurAnjamViewModel = viewModel()
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val baseScreen by viewModel.baseScreen.collectAsStateWithLifecycle()
    val selectedMenuTrack by viewModel.selectedMenuTrack.collectAsStateWithLifecycle()
    val showFinalDeleteConfirmTrack by viewModel.showFinalDeleteConfirmTrack.collectAsStateWithLifecycle()

    val savedNavLocation by viewModel.savedNavigationLocation.collectAsStateWithLifecycle()

    // Observe screen visibility and activity foreground/background lifecycle state
    // to dynamically regulate high-frequency position polling (350ms vs 3000ms),
    // saving immense CPU and battery resources when in the background or off-screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, currentScreen) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val isNowPlayingVisible = (currentScreen == AppScreen.PLAYER)
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    viewModel.setProgressTrackingActive(isNowPlayingVisible)
                    viewModel.refreshAudioOutput()
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.setProgressTrackingActive(false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        // Regulate high-frequency progress tracking without blocking UI thread or interrupting transitions
        val isNowPlayingVisible = (currentScreen == AppScreen.PLAYER)
        if (isNowPlayingVisible) {
            // Defer high-frequency progress tracking until the 380ms opening transition completes
            // to eliminate any progress state updates or recompositions during animation frames
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    viewModel.setProgressTrackingActive(true)
                }
            }, 400L)
        } else {
            viewModel.setProgressTrackingActive(false)
        }
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setProgressTrackingActive(false)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    // Runtime Notification Permission launcher for Android 13+ (Required for Lock Screen Media & Realme Capsule)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Active audio state will be immediately pushed to lock screen and Realme Capsule via MediaSessionCompat
    }

    // Runtime Bluetooth Permission launcher for Android 12+ (API 31+) for live accurate Buds / Bluetooth route detection
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.refreshAudioOutput()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotifPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBtPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBtPermission) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    // Storage permission launcher
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            viewModel.scanStorage(force = true)
        }
    }

    // SAF Document picker launcher for direct file imports
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val name = onQueryDocumentName(uri)
            viewModel.importAudioFile(uri, name)
        }
    }

    fun requestScanOrImport() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermission) {
            viewModel.scanStorage(force = true)
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            storagePermissionLauncher.launch(permissions)
        }
    }

    // OS-level MediaStore delete confirmation request launcher (Android 10 & 11+)
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeletePermissionGranted()
        } else {
            viewModel.onDeletePermissionDenied()
        }
    }

    val pendingDeleteIntentSender by viewModel.pendingDeleteIntentSender.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDeleteIntentSender) {
        pendingDeleteIntentSender?.let { request ->
            deleteRequestLauncher.launch(request)
            viewModel.clearPendingDeleteIntentSender()
        }
    }

    // User Feedback Toasts (e.g. file deletion, scan updates)
    LaunchedEffect(Unit) {
        viewModel.uiUserMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-check audio files on first launch
    LaunchedEffect(Unit) {
        viewModel.scanStorage()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val activeCurrentTrack = playbackState.currentTrack ?: tracks.firstOrNull()
        val context = androidx.compose.ui.platform.LocalContext.current
        var sourceArtworkBounds by remember { mutableStateOf<com.example.ui.ArtworkBounds?>(null) }

        // Preload destination artwork, theme, and blur in advance before opening player
        LaunchedEffect(activeCurrentTrack?.id) {
            activeCurrentTrack?.let { track ->
                com.example.ui.preloadArtworkAndTheme(context, track)
            }
        }



        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = baseScreen,
                transitionSpec = {
                    if (targetState == AppScreen.ATMOSPHERE || initialState == AppScreen.ATMOSPHERE) {
                        (slideInVertically(animationSpec = NurAnjamPhysics.FluidOffsetSpring) { it } +
                                fadeIn(animationSpec = spring(stiffness = 500f)))
                            .togetherWith(
                                slideOutVertically(animationSpec = NurAnjamPhysics.FluidOffsetSpring) { it } +
                                        fadeOut(animationSpec = spring(stiffness = 500f))
                            )
                    } else {
                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    }
                },
                label = "screen_navigation"
            ) { screen ->
                when (screen) {
                    AppScreen.LIBRARY, AppScreen.PLAYER -> {
                        LibraryScreen(
                            tracks = tracks,
                            playbackState = playbackState,
                            currentSortOrder = sortOrder,
                            isPlayerOpen = (currentScreen == AppScreen.PLAYER),
                            viewModel = viewModel,
                            onSortOrderSelected = { order -> viewModel.setSortOrder(order) },
                            onTrackSelected = { track, bounds ->
                                sourceArtworkBounds = bounds
                                viewModel.playTrack(track)
                                viewModel.navigateTo(AppScreen.PLAYER)
                            },
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                            onOpenPlayer = { bounds ->
                                sourceArtworkBounds = bounds
                                viewModel.navigateTo(AppScreen.PLAYER)
                            },
                            onOpenAtmosphere = { viewModel.navigateTo(AppScreen.ATMOSPHERE) },
                            onOpenIntelligence = { viewModel.onLogoClicked() },
                            onScanAudio = { requestScanOrImport() },
                            onOpenTrackMenu = { track -> viewModel.openTrackMenu(track) },
                            initialNavLocation = savedNavLocation,
                            onNavLocationChanged = { tab, containerType, containerId, containerName, filterFavorites, folder, album, playlist ->
                                viewModel.saveNavigationLocation(tab, containerType, containerId, containerName, filterFavorites, folder, album, playlist)
                            }
                        )
                    }

                    AppScreen.ATMOSPHERE -> {
                        BackHandler {
                            viewModel.navigateTo(AppScreen.LIBRARY)
                        }

                        val downloadedAmbients by viewModel.downloadedAmbients.collectAsStateWithLifecycle()
                        val favoriteAmbients by viewModel.favoriteAmbients.collectAsStateWithLifecycle()
                        val recentAmbients by viewModel.recentAmbients.collectAsStateWithLifecycle()
                        val totalStorageBytes by viewModel.totalStorageBytes.collectAsStateWithLifecycle()
                        val ambientSearchQuery by viewModel.ambientSearchQuery.collectAsStateWithLifecycle()
                        val isSearchingAmbients by viewModel.isSearchingAmbients.collectAsStateWithLifecycle()
                        val ambientSearchResults by viewModel.ambientSearchResults.collectAsStateWithLifecycle()
                        val ambientSearchError by viewModel.ambientSearchError.collectAsStateWithLifecycle()
                        val previewingSound by viewModel.previewingSound.collectAsStateWithLifecycle()

                        AtmosphereScreen(
                            playbackState = playbackState,
                            downloadedAmbients = downloadedAmbients,
                            favoriteAmbients = favoriteAmbients,
                            recentAmbients = recentAmbients,
                            totalStorageBytes = totalStorageBytes,
                            searchQuery = ambientSearchQuery,
                            isSearching = isSearchingAmbients,
                            searchResults = ambientSearchResults,
                            searchError = ambientSearchError,
                            previewingSound = previewingSound,
                            onSearchQueryChange = { query -> viewModel.searchOnlineAmbients(query) },
                            onPreviewSound = { sound -> viewModel.previewAmbientSound(sound) },
                            onPlayDownloaded = { entity -> viewModel.playDownloadedAmbient(entity) },
                            onDownloadSound = { sound -> viewModel.downloadAmbientSound(sound) },
                            onDeleteDownloaded = { soundId -> viewModel.deleteDownloadedAmbient(soundId) },
                            onToggleFavorite = { soundId, currentFav -> viewModel.toggleFavoriteAmbient(soundId, currentFav) },
                            onImportCustomAudio = { uri, customName -> viewModel.importCustomAtmosphereAudio(uri, customName) },
                            getDownloadProgress = { soundId -> viewModel.getDownloadProgressFlow(soundId) },
                            getDownloadState = { soundId -> viewModel.getDownloadStateFlow(soundId) },
                            onClose = { viewModel.navigateTo(AppScreen.LIBRARY) },
                            onToggleAmbientPlayback = { viewModel.toggleAmbientPlayback() },
                            onAmbientVolumeChange = { vol -> viewModel.setAmbientVolume(vol) },
                            onRecitationVolumeChange = { vol -> viewModel.setRecitationVolume(vol) },
                            onLeaveScreen = { viewModel.stopAmbientPreviewIfActive() }
                        )
                    }

                    AppScreen.INTELLIGENCE_ONBOARDING -> {
                        BackHandler {
                            viewModel.navigateTo(AppScreen.LIBRARY)
                        }

                        com.example.ui.IntelligenceOnboardingScreen(
                            onCompleteOnboarding = { viewModel.completeOnboarding() }
                        )
                    }

                    AppScreen.INTELLIGENCE -> {
                        BackHandler {
                            viewModel.navigateTo(AppScreen.LIBRARY)
                        }

                        val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()
                        val personalProfile by viewModel.personalProfile.collectAsStateWithLifecycle()
                        val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
                        val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()
                        val selectedTimeRange by viewModel.selectedTimeRange.collectAsStateWithLifecycle()

                        com.example.ui.IntelligenceDashboardScreen(
                            statsSummary = statsSummary,
                            profile = personalProfile,
                            recommendations = recommendations,
                            topTracks = topTracks,
                            selectedTimeRange = selectedTimeRange,
                            onTimeRangeSelected = { range -> viewModel.setTimeRangeFilter(range) },
                            onPlayTrack = { track -> viewModel.playTrack(track) },
                            onOpenDetailedReport = { viewModel.navigateTo(AppScreen.INTELLIGENCE_REPORT) },
                            onOpenPrivacySettings = { viewModel.navigateTo(AppScreen.INTELLIGENCE_REPORT) },
                            onClose = { viewModel.navigateTo(AppScreen.LIBRARY) }
                        )
                    }

                    AppScreen.INTELLIGENCE_REPORT -> {
                        BackHandler {
                            viewModel.navigateTo(AppScreen.INTELLIGENCE)
                        }

                        val statsSummary by viewModel.statsSummary.collectAsStateWithLifecycle()
                        val personalProfile by viewModel.personalProfile.collectAsStateWithLifecycle()
                        val dailyReport by viewModel.dailyReport.collectAsStateWithLifecycle()
                        val topTracks by viewModel.topTracks.collectAsStateWithLifecycle()

                        com.example.ui.IntelligenceDetailedReportScreen(
                            statsSummary = statsSummary,
                            profile = personalProfile,
                            dailyReport = dailyReport,
                            topTracks = topTracks,
                            onClearHistory = { viewModel.clearIntelligenceHistory() },
                            onResetRecommendations = { viewModel.resetRecommendations() },
                            onClose = { viewModel.navigateTo(AppScreen.INTELLIGENCE) }
                        )
                    }
                }
            }

            // Shared Element Container Transform Overlay for Now Playing Screen
            if (activeCurrentTrack != null) {
                SharedElementPlayerContainer(
                    isOpen = (currentScreen == AppScreen.PLAYER),
                    track = activeCurrentTrack,
                    playbackState = playbackState,
                    allTracks = tracks,
                    sourceBounds = sourceArtworkBounds,
                    onClose = { viewModel.navigateTo(AppScreen.LIBRARY) },
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    onSeekTo = { pos -> viewModel.seekTo(pos) },
                    onPlayNext = { viewModel.playNext() },
                    onPlayPrevious = { viewModel.playPrevious() },
                    onToggleShuffle = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.cycleRepeat() },
                    onCyclePlaybackMode = { viewModel.cyclePlaybackMode() },
                    onSelectPlaybackMode = { mode -> viewModel.setPlaybackMode(mode) },
                    onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                    onRecitationVolumeChange = { vol -> viewModel.setRecitationVolume(vol) },
                    onAmbientVolumeChange = { vol -> viewModel.setAmbientVolume(vol) },
                    onSelectAmbient = { ambient -> viewModel.selectAmbient(ambient) },
                    onToggleAmbientPlayback = { viewModel.toggleAmbientPlayback() },
                    onOpenMenu = { viewModel.openTrackMenu(activeCurrentTrack) },
                    onSelectTrack = { tr -> viewModel.playTrack(tr) },

                    onSimulateDevice = { dev -> viewModel.simulateAudioDevice(dev) },
                    onReorderQueue = { from, to -> viewModel.reorderQueue(from, to) },
                    onRemoveTrackFromQueue = { id -> viewModel.removeTrackFromQueue(id) },
                    onPlayTrackNext = { tr -> viewModel.playTrackNext(tr) },
                    onAddToQueueEnd = { tr -> viewModel.addToQueueEnd(tr) }
                )
            }

            // First-Time Animated Glassmorphic Name Onboarding Overlay (unmounts completely once saved)
            var savedUserName by remember {
                mutableStateOf(com.example.ui.UserGreetingManager.getSavedUserName(context))
            }
            if (savedUserName == null) {
                com.example.ui.GlassmorphicNameOnboardingOverlay(
                    onNameSubmitted = { enteredName ->
                        com.example.ui.UserGreetingManager.saveUserName(context, enteredName)
                        savedUserName = enteredName
                        onFirstTimeNameSaved(enteredName)
                    }
                )
            }
        }

        // Bottom Sheet Options Menu (Sleep Timer, Speed, Equalizer, Track Info)
        selectedMenuTrack?.let { menuTrack ->
            PlayerMenuBottomSheet(
                track = menuTrack,
                playbackState = playbackState,
                sheetState = sheetState,
                onDismiss = { viewModel.closeTrackMenu() },
                onSetSleepTimer = { minutes -> viewModel.setSleepTimer(minutes) },
                onSetPlaybackSpeed = { speed -> viewModel.setPlaybackSpeed(speed) },
                onSetAudioPreset = { preset -> viewModel.setAudioPreset(preset) },
                onUpdateTrackDate = { trackId, newTimestamp ->
                    viewModel.updateTrackDate(trackId, newTimestamp)
                },
                onDeleteTrack = { track ->
                    if (activity != null) {
                        viewModel.onDeleteFromDeviceClicked(activity, track)
                    }
                }
            )
        }

        // Final Permanent File Deletion Confirmation Dialog
        showFinalDeleteConfirmTrack?.let { confirmTrack ->
            com.example.ui.DeletePermanentlyConfirmDialog(
                track = confirmTrack,
                onConfirmDelete = { viewModel.confirmPermanentDelete(confirmTrack) },
                onDismiss = { viewModel.cancelFinalDeleteConfirm() }
            )
        }
    }
}
