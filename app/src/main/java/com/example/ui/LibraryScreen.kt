package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.rememberHighResArtworkModel
import com.example.data.rememberTrackArtwork
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.example.model.AudioSortOrder
import com.example.model.AudioTrack
import com.example.model.LibraryTab
import com.example.model.PlaybackState
import com.example.ui.theme.AmbientRainBlue
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.AmbientThunderPurple
import com.example.ui.theme.AmbientWindAmber
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary

@Composable
fun LibraryScreen(
    tracks: List<AudioTrack>,
    playbackState: PlaybackState,
    currentSortOrder: AudioSortOrder = AudioSortOrder.TITLE_AZ,
    isPlayerOpen: Boolean = false,
    viewModel: NurAnjamViewModel = viewModel(),
    onSortOrderSelected: (AudioSortOrder) -> Unit = {},
    onTrackSelected: (AudioTrack, ArtworkBounds?) -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenPlayer: (ArtworkBounds?) -> Unit,
    onOpenAtmosphere: () -> Unit,
    onOpenIntelligence: () -> Unit = {},
    onScanAudio: () -> Unit = {},
    onOpenTrackMenu: (AudioTrack) -> Unit,
    initialNavLocation: com.example.data.PersistedNavigationLocation = com.example.data.PersistedNavigationLocation(),
    onNavLocationChanged: (
        tab: LibraryTab,
        containerType: String,
        containerId: String?,
        containerName: String?,
        filterFavorites: Boolean,
        currentFolder: String?,
        currentAlbum: String?,
        currentPlaylist: String?
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    var showRecentSheet by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    // Step 1: Storage permission handling (READ_MEDIA_AUDIO for Android 13+, READ_EXTERNAL_STORAGE for older)
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalAudio()
        }
    }

    val checkAndScan: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            permissionToRequest
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.scanLocalAudio()
        } else {
            storagePermissionLauncher.launch(permissionToRequest)
        }
    }

    val initialFolder = initialNavLocation.currentFolder ?: if (initialNavLocation.containerType == "FOLDER") initialNavLocation.containerName else null
    val initialAlbum = initialNavLocation.currentAlbum ?: if (initialNavLocation.containerType == "ALBUM") initialNavLocation.containerName else null
    val initialPlaylist = initialNavLocation.currentPlaylist ?: if (initialNavLocation.containerType == "PLAYLIST") initialNavLocation.containerName else null
    val initialContainer = initialFolder ?: initialAlbum ?: initialPlaylist ?: initialNavLocation.containerName

    var selectedTab by rememberSaveable(key = "lib_selected_tab") { androidx.compose.runtime.mutableStateOf(initialNavLocation.tab) }
    var selectedFolder by rememberSaveable(key = "lib_selected_folder") { androidx.compose.runtime.mutableStateOf<String?>(initialContainer) }
    var searchQuery by rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var isSearchActive by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var filterFavoritesOnly by rememberSaveable { androidx.compose.runtime.mutableStateOf(initialNavLocation.filterFavoritesOnly) }
    var showSortBottomSheet by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Sync restored location from initialNavLocation when restored from Recents
    androidx.compose.runtime.LaunchedEffect(initialNavLocation) {
        val navFolder = initialNavLocation.currentFolder ?: if (initialNavLocation.containerType == "FOLDER") initialNavLocation.containerName else null
        val navAlbum = initialNavLocation.currentAlbum ?: if (initialNavLocation.containerType == "ALBUM") initialNavLocation.containerName else null
        val navPlaylist = initialNavLocation.currentPlaylist ?: if (initialNavLocation.containerType == "PLAYLIST") initialNavLocation.containerName else null
        if (selectedFolder == null && navFolder != null) {
            selectedFolder = navFolder
            selectedTab = LibraryTab.FOLDERS
        } else if (selectedFolder == null && navAlbum != null) {
            selectedFolder = navAlbum
            selectedTab = LibraryTab.ALBUMS
        } else if (selectedFolder == null && navPlaylist != null) {
            selectedFolder = navPlaylist
            selectedTab = LibraryTab.PLAYLISTS
        }
    }

    val haptic = rememberHapticController()
    val isDark = true // Locked glassmorphic theme (ignores system Dark/Light mode)

    // Persist browsing location whenever tab, folder, or favorites filter changes
    androidx.compose.runtime.LaunchedEffect(selectedTab, selectedFolder, filterFavoritesOnly) {
        val folder = if (selectedTab == LibraryTab.FOLDERS || (selectedFolder != null && selectedTab != LibraryTab.ALBUMS && selectedTab != LibraryTab.PLAYLISTS)) selectedFolder else null
        val album = if (selectedTab == LibraryTab.ALBUMS) selectedFolder else null
        val playlist = if (selectedTab == LibraryTab.PLAYLISTS) selectedFolder else null
        val containerType = when {
            folder != null -> "FOLDER"
            album != null -> "ALBUM"
            playlist != null -> "PLAYLIST"
            else -> "NONE"
        }
        val effectiveTab = when {
            folder != null -> LibraryTab.FOLDERS
            album != null -> LibraryTab.ALBUMS
            playlist != null -> LibraryTab.PLAYLISTS
            else -> selectedTab
        }
        onNavLocationChanged(
            effectiveTab,
            containerType,
            selectedFolder,
            selectedFolder,
            filterFavoritesOnly,
            folder,
            album,
            playlist
        )
    }

    // 1. Sanitize track data to prevent empty gaps / null entries (derivedStateOf for zero GC on 120Hz)
    val cleanTracks by remember(tracks) {
        derivedStateOf {
            tracks.filter { it.id.isNotBlank() && it.title.isNotBlank() }
        }
    }

    val filteredTracks by remember(cleanTracks, searchQuery, filterFavoritesOnly, selectedFolder, selectedTab) {
        derivedStateOf {
            cleanTracks.filter { track ->
                val matchesSearch = searchQuery.isBlank() ||
                        track.title.contains(searchQuery, ignoreCase = true) ||
                        track.reciter.contains(searchQuery, ignoreCase = true) ||
                        track.album.contains(searchQuery, ignoreCase = true)
                val matchesFav = !filterFavoritesOnly || track.isFavorite
                val matchesFolder = selectedFolder == null || when (selectedTab) {
                    LibraryTab.ALBUMS -> track.album.equals(selectedFolder, ignoreCase = true)
                    LibraryTab.FOLDERS -> track.folderName.equals(selectedFolder, ignoreCase = true)
                    else -> track.folderName.equals(selectedFolder, ignoreCase = true) || track.album.equals(selectedFolder, ignoreCase = true)
                }
                matchesSearch && matchesFav && matchesFolder
            }
        }
    }

    val sortedTracks by remember(filteredTracks, currentSortOrder) {
        derivedStateOf {
            when (currentSortOrder) {
                AudioSortOrder.TITLE_AZ -> filteredTracks.sortedBy { it.title.lowercase() }
                AudioSortOrder.TITLE_ZA -> filteredTracks.sortedByDescending { it.title.lowercase() }
                AudioSortOrder.DATE_NEWEST -> filteredTracks.sortedByDescending { it.customSortTimestamp }
                AudioSortOrder.DATE_OLDEST -> filteredTracks.sortedBy { it.customSortTimestamp }
                AudioSortOrder.MOST_PLAYED -> filteredTracks.sortedWith(
                    compareByDescending<AudioTrack> { it.playCount }.thenBy { it.title.lowercase() }
                )
            }
        }
    }

    val folderGroups by remember(cleanTracks) {
        derivedStateOf {
            cleanTracks.filter { it.folderName.isNotBlank() && !it.folderName.startsWith(".") }
                .groupBy { it.folderName }
                .filter { it.key.isNotBlank() && it.value.isNotEmpty() }
        }
    }

    val sortedFolderGroups by remember(folderGroups, currentSortOrder) {
        derivedStateOf {
            when (currentSortOrder) {
                AudioSortOrder.TITLE_AZ -> folderGroups.entries.sortedBy { it.key.lowercase() }.associate { it.key to it.value }
                AudioSortOrder.TITLE_ZA -> folderGroups.entries.sortedByDescending { it.key.lowercase() }.associate { it.key to it.value }
                AudioSortOrder.DATE_NEWEST -> folderGroups.entries.sortedByDescending { entry ->
                    entry.value.maxOfOrNull { it.customSortTimestamp } ?: 0L
                }.associate { it.key to it.value }
                AudioSortOrder.DATE_OLDEST -> folderGroups.entries.sortedBy { entry ->
                    entry.value.minOfOrNull { it.customSortTimestamp } ?: 0L
                }.associate { it.key to it.value }
                AudioSortOrder.MOST_PLAYED -> folderGroups.entries.sortedByDescending { entry ->
                    entry.value.sumOf { it.playCount }
                }.associate { it.key to it.value }
            }
        }
    }

    val albumGroups by remember(cleanTracks) {
        derivedStateOf {
            cleanTracks.filter { it.album.isNotBlank() }
                .groupBy { it.album }
                .filter { it.key.isNotBlank() && it.value.isNotEmpty() }
        }
    }

    val sortedAlbumGroups by remember(albumGroups, currentSortOrder) {
        derivedStateOf {
            when (currentSortOrder) {
                AudioSortOrder.TITLE_AZ -> albumGroups.entries.sortedBy { it.key.lowercase() }.associate { it.key to it.value }
                AudioSortOrder.TITLE_ZA -> albumGroups.entries.sortedByDescending { it.key.lowercase() }.associate { it.key to it.value }
                AudioSortOrder.DATE_NEWEST -> albumGroups.entries.sortedByDescending { entry ->
                    entry.value.maxOfOrNull { it.customSortTimestamp } ?: 0L
                }.associate { it.key to it.value }
                AudioSortOrder.DATE_OLDEST -> albumGroups.entries.sortedBy { entry ->
                    entry.value.minOfOrNull { it.customSortTimestamp } ?: 0L
                }.associate { it.key to it.value }
                AudioSortOrder.MOST_PLAYED -> albumGroups.entries.sortedByDescending { entry ->
                    entry.value.sumOf { it.playCount }
                }.associate { it.key to it.value }
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = !isPlayerOpen && selectedFolder != null) {
        selectedFolder = null
    }

    androidx.activity.compose.BackHandler(enabled = !isPlayerOpen && selectedFolder == null && isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    androidx.activity.compose.BackHandler(enabled = !isPlayerOpen && selectedFolder == null && !isSearchActive && filterFavoritesOnly) {
        filterFavoritesOnly = false
    }

    // Root BackHandler: Push task to background without destroying MainActivity or stopping Foreground Audio Service
    androidx.activity.compose.BackHandler(enabled = !isPlayerOpen && selectedFolder == null && !isSearchActive && !filterFavoritesOnly) {
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }

    IslamicSacredBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar & Header
            if (selectedFolder != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                haptic.tap()
                                selectedFolder = null
                            },
                            modifier = Modifier.testTag("back_to_folders")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Folders",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = selectedFolder ?: "",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${filteredTracks.size} track(s)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                haptic.tap()
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            },
                            modifier = Modifier.testTag("search_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    haptic.tap()
                                    onOpenIntelligence()
                                }
                                .padding(vertical = 2.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NurAnjamLogoWithLedGlow(
                                size = 34.dp,
                                isAnimated = !isPlayerOpen,
                                modifier = Modifier.testTag("app_logo_led_glow")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "NurAnjam",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "NurAnjam Intelligence",
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    haptic.tap()
                                    isSearchActive = !isSearchActive
                                    if (!isSearchActive) searchQuery = ""
                                },
                                modifier = Modifier.testTag("search_button")
                            ) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.tap()
                                    onOpenAtmosphere()
                                },
                                modifier = Modifier.testTag("atmosphere_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = "Atmosphere & Ambient Sound",
                                    tint = GoldPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.tap()
                                    showRecentSheet = true
                                },
                                modifier = Modifier.testTag("recent_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Recently Played",
                                    tint = GoldPrimary
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.tap()
                                    checkAndScan()
                                },
                                modifier = Modifier.testTag("scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCodeScanner,
                                    contentDescription = "Scan Storage",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Custom Animated Typography Hero Branding Element
                    AnimatedHeroBranding(
                        onBrandingClick = {
                            haptic.tap()
                            onOpenIntelligence()
                        }
                    )
                }
            }

            // Search Bar (Expandable)
            AnimatedVisibility(visible = isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search Surah, reciter, or file...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .testTag("search_input")
                )
            }

            // Quick Action Grid
            if (selectedFolder == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionTile(
                        title = "Favorites",
                        icon = Icons.Default.Favorite,
                        gradientColors = listOf(Color(0xFFFF5252), Color(0xFFFF7A85)),
                        isActive = filterFavoritesOnly,
                        onClick = {
                            haptic.tap()
                            filterFavoritesOnly = !filterFavoritesOnly
                            if (filterFavoritesOnly) {
                                selectedTab = LibraryTab.TRACKS
                            }
                        },
                        modifier = Modifier.testTag("quick_favorites")
                    )
                    QuickActionTile(
                        title = "Atmosphere",
                        icon = Icons.Default.Waves,
                        gradientColors = listOf(Color(0xFF7E57C2), Color(0xFF9575CD)),
                        isActive = false,
                        onClick = {
                            haptic.tap()
                            onOpenAtmosphere()
                        },
                        modifier = Modifier.testTag("quick_atmosphere")
                    )
                    QuickActionTile(
                        title = "Recent",
                        icon = Icons.Default.History,
                        gradientColors = listOf(Color(0xFFFF9800), Color(0xFFFFB74D)),
                        isActive = showRecentSheet,
                        onClick = {
                            haptic.tap()
                            showRecentSheet = true
                        },
                        modifier = Modifier.testTag("quick_recent")
                    )
                    QuickActionTile(
                        title = "Scan",
                        icon = Icons.Outlined.QrCodeScanner,
                        gradientColors = listOf(Color(0xFF26A69A), Color(0xFF80CBC4)),
                        isActive = isScanning,
                        onClick = {
                            haptic.tap()
                            checkAndScan()
                        },
                        modifier = Modifier.testTag("quick_scan")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    edgePadding = 20.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            height = 3.dp,
                            color = if (isDark) GoldPrimary else MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    LibraryTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = {
                                haptic.tap()
                                selectedTab = tab
                                selectedFolder = null
                            },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onBackground
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            },
                            modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (selectedFolder != null || selectedTab == LibraryTab.TRACKS || filterFavoritesOnly) {
                    // Track list for selected folder or direct tab view
                    TrackListView(
                        tracks = sortedTracks,
                        currentSortOrder = currentSortOrder,
                        currentPlayingId = playbackState.currentTrack?.id,
                        isPlaying = playbackState.isPlaying,
                        onOpenSort = { showSortBottomSheet = true },
                        onScanAudio = checkAndScan,
                        onTrackSelected = { track, bounds ->
                            haptic.tap()
                            onTrackSelected(track, bounds)
                        },
                        onToggleFavorite = { id ->
                            haptic.confirm()
                            onToggleFavorite(id)
                        },
                        onOpenTrackMenu = { track ->
                            haptic.tap()
                            onOpenTrackMenu(track)
                        }
                    )
                } else if (selectedTab == LibraryTab.ALBUMS) {
                    // Album list
                    AlbumListView(
                        albums = sortedAlbumGroups,
                        currentSortOrder = currentSortOrder,
                        onOpenSort = { showSortBottomSheet = true },
                        onAlbumSelected = { albumName ->
                            haptic.tap()
                            selectedFolder = albumName
                        }
                    )
                } else {
                    // Folders list
                    FolderListView(
                        folders = sortedFolderGroups,
                        currentSortOrder = currentSortOrder,
                        onOpenSort = { showSortBottomSheet = true },
                        onFolderSelected = { folder ->
                            haptic.tap()
                            selectedFolder = folder
                        }
                    )
                }
            }

            // Smartphone-Optimized Crystal Glass Persistent Play Bar
            if (playbackState.currentTrack != null) {
                CrystalGlassPlayBar(
                    track = playbackState.currentTrack,
                    isPlaying = playbackState.isPlaying,
                    currentPositionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    isAmbientActive = playbackState.isAmbientPlaying,
                    ambientVolume = playbackState.ambientVolume,
                    selectedAmbient = playbackState.selectedAmbient,
                    audioOutputDevice = playbackState.audioOutputDevice,
                    isPlayerOpen = isPlayerOpen,
                    onTogglePlay = {
                        haptic.tap()
                        onTogglePlayPause()
                    },
                    onOpenPlayer = { bounds ->
                        haptic.tap()
                        onOpenPlayer(bounds)
                    },
                    onOpenAtmosphere = {
                        haptic.tap()
                        onOpenAtmosphere()
                    }
                )
            }
        }
    }

    // Dynamic Audio Sorting Bottom Sheet
    if (showSortBottomSheet) {
        AudioSortingBottomSheet(
            currentSortOrder = currentSortOrder,
            onSortOrderSelected = { newOrder ->
                onSortOrderSelected(newOrder)
            },
            onDismissRequest = {
                showSortBottomSheet = false
            }
        )
    }

    // Crystal Glass Recently Played Bottom Sheet
    if (showRecentSheet) {
        RecentTracksBottomSheet(
            recentTracks = recentTracks,
            currentTrack = playbackState.currentTrack,
            isPlaying = playbackState.isPlaying,
            onTrackSelected = { track ->
                onTrackSelected(track, null)
            },
            onDismiss = {
                showRecentSheet = false
            }
        )
    }
}

@Composable
private fun SortHeaderBar(
    totalCount: Int,
    isFoldersView: Boolean,
    currentSortOrder: AudioSortOrder,
    onOpenSort: () -> Unit
) {
    val isDark = true // Locked glassmorphic theme
    val haptic = rememberHapticController()

    val sortLabel = when (currentSortOrder) {
        AudioSortOrder.TITLE_AZ -> "A → Z"
        AudioSortOrder.TITLE_ZA -> "Z → A"
        AudioSortOrder.DATE_NEWEST -> "Newest"
        AudioSortOrder.DATE_OLDEST -> "Earliest"
        AudioSortOrder.MOST_PLAYED -> "Most Played"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isFoldersView) "$totalCount folder(s)" else "$totalCount track(s)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Sort Pill Chip Button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isDark) Color(0x24FFFFFF) else Color(0x14000000)
                )
                .border(
                    width = 1.dp,
                    color = GoldPrimary.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
                .tactilePress(
                    scaleDown = 0.94f,
                    accentGlow = GoldLight,
                    onClick = {
                        haptic.tap()
                        onOpenSort()
                    }
                )
                .padding(horizontal = 11.dp, vertical = 5.dp)
                .testTag("sort_menu_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sort Menu",
                    tint = GoldPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Sort: $sortLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) GoldLight else GoldPrimary
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = GoldPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .tactilePress(
                scaleDown = 0.93f,
                accentGlow = gradientColors.firstOrNull() ?: GoldPrimary,
                onClick = onClick
            )
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isActive) Brush.linearGradient(listOf(GoldPrimary, GoldLight))
                    else Brush.linearGradient(gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun FolderListView(
    folders: Map<String, List<AudioTrack>>,
    currentSortOrder: AudioSortOrder,
    onOpenSort: () -> Unit,
    onFolderSelected: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            SortHeaderBar(
                totalCount = folders.size,
                isFoldersView = true,
                currentSortOrder = currentSortOrder,
                onOpenSort = onOpenSort
            )
        }

        items(
            items = folders.keys.toList(),
            key = { "folder_$it" },
            contentType = { "folder_item" }
        ) { folderName ->
            val folderTracks = folders[folderName] ?: emptyList()
            val count = folderTracks.size
            val folderPlays = folderTracks.sumOf { it.playCount }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .tactilePress(
                        scaleDown = 0.985f,
                        onClick = { onFolderSelected(folderName) }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("folder_item_$folderName"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Glass folder thumbnail box matching the track art container
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = 0.18f),
                                    Color(0xFF0F1E19)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = GoldPrimary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Folder Icon",
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = folderName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (currentSortOrder == AudioSortOrder.MOST_PLAYED && folderPlays > 0) {
                            "$count track(s) • $folderPlays total plays"
                        } else {
                            "$count recitation track(s)"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AlbumListView(
    albums: Map<String, List<AudioTrack>>,
    currentSortOrder: AudioSortOrder,
    onOpenSort: () -> Unit,
    onAlbumSelected: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            SortHeaderBar(
                totalCount = albums.size,
                isFoldersView = true,
                currentSortOrder = currentSortOrder,
                onOpenSort = onOpenSort
            )
        }

        items(
            items = albums.keys.toList(),
            key = { "album_$it" },
            contentType = { "album_item" }
        ) { albumName ->
            val albumTracks = albums[albumName] ?: emptyList()
            val count = albumTracks.size
            val firstTrack = albumTracks.firstOrNull()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .tactilePress(
                        scaleDown = 0.985f,
                        onClick = { onAlbumSelected(albumName) }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("album_item_$albumName"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GoldPrimary.copy(alpha = 0.18f),
                                    Color(0xFF0F1E19)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            color = GoldPrimary.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val artworkData by rememberHighResArtworkModel(firstTrack)
                    if (artworkData != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artworkData)
                                .size(coil.size.Size.ORIGINAL)
                                .allowHardware(true)
                                .crossfade(500)
                                .build(),
                            contentDescription = albumName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = "Album Icon",
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Text(
                        text = albumName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$count recitation(s) • ${firstTrack?.reciter ?: "Sacred Collection"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open Album",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TrackListView(
    tracks: List<AudioTrack>,
    currentSortOrder: AudioSortOrder,
    currentPlayingId: String?,
    isPlaying: Boolean,
    onOpenSort: () -> Unit,
    onScanAudio: () -> Unit,
    onTrackSelected: (AudioTrack, ArtworkBounds?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenTrackMenu: (AudioTrack) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                GoldPrimary.copy(alpha = 0.50f),
                                EmeraldMint.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .testTag("empty_library_card"),
                shape = RoundedCornerShape(28.dp),
                backgroundColor = Color.Black.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GoldPrimary.copy(alpha = 0.25f),
                                        EmeraldMint.copy(alpha = 0.10f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .border(1.dp, GoldPrimary.copy(alpha = 0.30f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Library Empty",
                            tint = GoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Library Empty",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "No local audio files or Quran recitations were detected on this device. Scan your device storage to load your audio files.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    GlassCard(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onScanAudio() }
                            .testTag("scan_storage_button"),
                        shape = RoundedCornerShape(16.dp),
                        borderColor = GoldPrimary.copy(alpha = 0.60f),
                        backgroundColor = GoldPrimary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan Local Storage",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }
        }
    } else {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        androidx.compose.runtime.LaunchedEffect(currentSortOrder) {
            listState.scrollToItem(0)
        }
        val isScrolledPastHeader by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 24
            }
        }
        val headerAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isScrolledPastHeader) 0.88f else 1.0f,
            animationSpec = NurAnjamPhysics.AquamorphicBounceSpring,
            label = "sortHeaderAlpha"
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item(key = "sort_header_bar", contentType = "header") {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = headerAlpha
                    }
                ) {
                    SortHeaderBar(
                        totalCount = tracks.size,
                        isFoldersView = false,
                        currentSortOrder = currentSortOrder,
                        onOpenSort = onOpenSort
                    )
                }
            }

            items(
                items = tracks,
                key = { it.id },
                contentType = { "track_item" }
            ) { track ->
                val isCurrent = track.id == currentPlayingId

                TrackRowItem(
                    track = track,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onTrackSelected = { bounds -> onTrackSelected(track, bounds) },
                    onToggleFavorite = { onToggleFavorite(track.id) },
                    onOpenTrackMenu = { onOpenTrackMenu(track) }
                )
            }
        }
    }
}

@Composable
private fun TrackRowItem(
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onTrackSelected: (ArtworkBounds) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenTrackMenu: () -> Unit
) {
    var bounds by remember { mutableStateOf<ArtworkBounds?>(null) }
    val artworkData by rememberHighResArtworkModel(track)
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Battery-Saver resistant spring physics (ignores system duration scale throttling)
    val artworkScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "artworkScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp) // premium outer tile gap/margins
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .tactilePress(
                scaleDown = 0.985f,
                onClick = { onTrackSelected(bounds ?: ArtworkBounds(0f, 0f, 0f, 0f)) },
                interactionSource = interactionSource,
                isGlassTile = false
            )
            .padding(horizontal = 12.dp, vertical = 10.dp) // comfortable inner tile content padding
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Embedded ID3 cover photo with subtle border radius and zero shadow artifacts
        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    bounds = ArtworkBounds(
                        left = pos.x,
                        top = pos.y,
                        width = coords.size.width.toFloat(),
                        height = coords.size.height.toFloat()
                    )
                }
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                }
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.18f),
                            Color(0xFF0F1E19)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (isCurrent) GoldPrimary else GoldPrimary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (artworkData != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artworkData)
                        .size(coil.size.Size.ORIGINAL)
                        .allowHardware(true)
                        .crossfade(500)
                        .build(),
                    contentDescription = "Cover Art for ${track.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isCurrent) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Visualizer overlay when currently playing
            if (isCurrent && isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    AudioVisualizerBars(
                        isPlaying = true,
                        modifier = Modifier.size(24.dp, 16.dp),
                        barCount = 4,
                        activeColor = GoldPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title & Metadata (Strict single line, no half names, no relative timestamps)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
                .graphicsLayer {
                    // Title and artist text remain spatially stable, translating down by less than 1dp during press
                    translationY = if (isPressed) 0.5f * density else 0f
                }
        ) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) GoldPrimary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (track.durationMs > 0) "${track.reciter} • ${formatDuration(track.durationMs)}" else track.reciter,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )
        }

        // Favorite Button
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .size(36.dp)
                .testTag("fav_btn_${track.id}")
        ) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (track.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }

        // 3-dots Menu Button
        IconButton(
            onClick = onOpenTrackMenu,
            modifier = Modifier
                .size(36.dp)
                .testTag("menu_btn_${track.id}")
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun MiniPlayerBar(
    track: AudioTrack,
    isPlaying: Boolean,
    isAmbientActive: Boolean,
    ambientVolume: Float,
    audioOutputDevice: com.example.model.AudioOutputDevice,
    onTogglePlay: () -> Unit,
    onOpenPlayer: (ArtworkBounds?) -> Unit,
    modifier: Modifier = Modifier
) {
    CrystalGlassPlayBar(
        track = track,
        isPlaying = isPlaying,
        isAmbientActive = isAmbientActive,
        ambientVolume = ambientVolume,
        audioOutputDevice = audioOutputDevice,
        onTogglePlay = onTogglePlay,
        onOpenPlayer = onOpenPlayer,
        modifier = modifier
    )
}
