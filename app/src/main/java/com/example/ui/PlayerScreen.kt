package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.rememberHighResArtworkModel
import com.example.data.rememberTrackArtwork
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.model.PlaybackMode
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AmbientType
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import com.example.model.PlayerViewMode
import com.example.ui.theme.EmeraldMint
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import com.example.data.AudioArtworkExtractor
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
fun PlayerScreen(
    track: AudioTrack,
    playbackState: PlaybackState,
    allTracks: List<AudioTrack> = emptyList(),
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onCyclePlaybackMode: () -> Unit = onCycleRepeat,
    onSelectPlaybackMode: (com.example.model.PlaybackMode) -> Unit = {},
    onToggleFavorite: (String) -> Unit,
    onRecitationVolumeChange: (Float) -> Unit,
    onAmbientVolumeChange: (Float) -> Unit,
    onSelectAmbient: (AmbientType) -> Unit,
    onToggleAmbientPlayback: () -> Unit,
    onOpenMenu: () -> Unit,
    onSelectTrack: (AudioTrack) -> Unit = {},

    onSimulateDevice: (com.example.model.AudioOutputDevice?) -> Unit = {},
    onReorderQueue: (Int, Int) -> Unit = { _, _ -> },
    onRemoveTrackFromQueue: (String) -> Unit = {},
    onPlayTrackNext: (AudioTrack) -> Unit = {},
    onAddToQueueEnd: (AudioTrack) -> Unit = {},
    artworkAlphaProvider: () -> Float = { 1f },
    onArtworkPositioned: (ArtworkBounds) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var viewMode by remember { mutableStateOf(PlayerViewMode.COVER) }
    val haptic = rememberHapticController()
    val isDark = true // Locked glassmorphic theme

    var showOutputSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showPlayQueue by remember { mutableStateOf(false) }
    var modeNotificationMessage by remember { mutableStateOf<String?>(null) }
    val outputSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var sliderSeekingPosition by remember { mutableFloatStateOf(-1f) }

    val context = LocalContext.current

    // Step 1: Resolve active playlist and current track index for HorizontalPager
    val playlistTracks = remember(playbackState.playQueue, allTracks, track) {
        val baseList = playbackState.playQueue.ifEmpty { allTracks }
        when {
            baseList.isEmpty() -> listOf(track)
            baseList.none { it.id == track.id } -> listOf(track) + baseList
            else -> baseList
        }
    }

    val activeTrackIndex = remember(playlistTracks, track.id, playbackState.currentMediaItemIndex) {
        val idxById = playlistTracks.indexOfFirst { it.id == track.id }
        if (idxById >= 0) {
            idxById
        } else {
            playbackState.currentMediaItemIndex.coerceIn(0, (playlistTracks.size - 1).coerceAtLeast(0))
        }
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = activeTrackIndex,
        pageCount = { playlistTracks.size }
    )

    // Preload adjacent tracks' 5-point solid themes and blurred backgrounds on Dispatchers.IO/Default
    // so swiping left or right crossfades at 120 FPS with zero frame drops.
    androidx.compose.runtime.LaunchedEffect(activeTrackIndex, playlistTracks) {
        val prevTrack = playlistTracks.getOrNull(activeTrackIndex - 1)
        val nextTrack = playlistTracks.getOrNull(activeTrackIndex + 1)
        if (prevTrack != null) {
            preloadArtworkAndTheme(context, prevTrack)
        }
        if (nextTrack != null) {
            preloadArtworkAndTheme(context, nextTrack)
        }
    }

    // Step 2: Sync UI Swipes -> Audio Playback (Next / Previous / Direct Track Play)
    androidx.compose.runtime.LaunchedEffect(pagerState.settledPage) {
        val settledPage = pagerState.settledPage
        val targetTrack = playlistTracks.getOrNull(settledPage) ?: return@LaunchedEffect
        if (settledPage != activeTrackIndex && targetTrack.id != track.id) {
            haptic.tick()
            val hasSyncedQueue = playbackState.playQueue.isNotEmpty() && !playbackState.isShuffle
            when {
                hasSyncedQueue && settledPage == activeTrackIndex + 1 -> onPlayNext()
                hasSyncedQueue && settledPage == activeTrackIndex - 1 -> onPlayPrevious()
                else -> onSelectTrack(targetTrack)
            }
        }
    }

    // Step 3: Sync Audio Playback -> UI (Auto-advance when track finishes or skips externally)
    androidx.compose.runtime.LaunchedEffect(activeTrackIndex) {
        if (pagerState.settledPage != activeTrackIndex && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(activeTrackIndex)
        }
    }

    // Step 4: Smooth Glassmorphic Transitions during finger drag
    val artworkBitmap by rememberTrackArtwork(track)
    val basePlayerTheme = rememberDynamicPlayerTheme(track, artworkBitmap)

    val pageOffset = pagerState.currentPageOffsetFraction
    val targetPageIndex = remember(pagerState.currentPage, pageOffset, playlistTracks.size) {
        when {
            pageOffset > 0.001f -> (pagerState.currentPage + 1).coerceAtMost((playlistTracks.size - 1).coerceAtLeast(0))
            pageOffset < -0.001f -> (pagerState.currentPage - 1).coerceAtLeast(0)
            else -> pagerState.currentPage
        }
    }
    val targetSwipeTrack = playlistTracks.getOrNull(targetPageIndex)?.takeIf { it.id != track.id }
    val targetSwipeTheme = remember(targetSwipeTrack?.id, basePlayerTheme) {
        targetSwipeTrack?.let { ArtworkThemeCache.get(it.id) } ?: basePlayerTheme
    }
    val swipeFraction = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
    val playerTheme = lerpDynamicPlayerTheme(
        start = basePlayerTheme,
        stop = targetSwipeTheme,
        fraction = swipeFraction
    )

    val currentPosition = if (sliderSeekingPosition >= 0f) {
        sliderSeekingPosition.toLong()
    } else {
        playbackState.currentPositionMs
    }

    androidx.activity.compose.BackHandler {
        if (showPlayQueue) {
            showPlayQueue = false
        } else {
            onClose()
        }
    }

    DynamicBlurredBackgroundContainer(
        bitmap = artworkBitmap,
        theme = playerTheme,
        track = track,
        targetSwipeTrack = targetSwipeTrack,
        swipeFraction = swipeFraction
    ) {
        InteractiveDragToDismissContainer(
            onDismiss = onClose,
            modifier = modifier
        ) { dragProgress ->
            val staggeredAlpha = (1f - (dragProgress / 0.50f)).coerceIn(0f, 1f)
            val staggeredOffsetY = dragProgress * 60f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .graphicsLayer {
                        alpha = staggeredAlpha
                        translationY = staggeredOffsetY
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tactile Drag Indicator Pill
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDark) Color(0x44FFFFFF) else Color(0x33000000)
                        )
                )

                // Top App Bar matching reference image:
                // Back/Down Chevron (left), COVER / LYRIC / MIXER toggle (center), 3-Dots (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            haptic.tap()
                            onClose()
                        },
                        modifier = Modifier.testTag("player_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = playerTheme.textPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Segmented Pill Selector (COVER / LYRIC / MIXER)
                    GlassCard(
                        shape = RoundedCornerShape(20.dp),
                        borderColor = playerTheme.border,
                        backgroundColor = playerTheme.surface,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PillTabItem(
                                title = "COVER",
                                isSelected = viewMode == PlayerViewMode.COVER,
                                accentColor = playerTheme.accent,
                                onClick = {
                                    haptic.tap()
                                    viewMode = PlayerViewMode.COVER
                                }
                            )
                            PillTabItem(
                                title = "LYRIC",
                                isSelected = viewMode == PlayerViewMode.LYRIC,
                                accentColor = playerTheme.accent,
                                onClick = {
                                    haptic.tap()
                                    viewMode = PlayerViewMode.LYRIC
                                }
                            )
                            PillTabItem(
                                title = "MIXER",
                                isSelected = viewMode == PlayerViewMode.AMBIENT_MIXER,
                                accentColor = playerTheme.accent,
                                onClick = {
                                    haptic.tap()
                                    viewMode = PlayerViewMode.AMBIENT_MIXER
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.tap()
                            onOpenMenu()
                        },
                        modifier = Modifier.testTag("player_overflow_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options Menu",
                            tint = playerTheme.textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // HorizontalPager wrapping Album Art / Center View + Song Title & Reciter Details
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = viewMode == PlayerViewMode.COVER && playlistTracks.size > 1,
                    key = { page -> playlistTracks.getOrNull(page)?.id ?: page },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("now_playing_horizontal_pager")
                ) { pageIndex ->
                    val pageTrack = playlistTracks.getOrNull(pageIndex) ?: track
                    val pageOffsetForCard = (
                        (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    ).let { kotlin.math.abs(it).coerceIn(0f, 1f) }
                    val cardScale = 1f - (pageOffsetForCard * 0.08f)
                    val cardAlpha = 1f - (pageOffsetForCard * 0.30f)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                                alpha = cardAlpha
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Animated Center View: Cover, Lyric, or Ambient Mixer
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = viewMode,
                                transitionSpec = {
                                    val isMovingForward = targetState.ordinal > initialState.ordinal
                                    val enterTowards = if (isMovingForward) {
                                        AnimatedContentTransitionScope.SlideDirection.Left
                                    } else {
                                        AnimatedContentTransitionScope.SlideDirection.Right
                                    }
                                    val exitTowards = if (isMovingForward) {
                                        AnimatedContentTransitionScope.SlideDirection.Left
                                    } else {
                                        AnimatedContentTransitionScope.SlideDirection.Right
                                    }
                                    (slideIntoContainer(
                                        towards = enterTowards,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeIn(animationSpec = tween(220))) togetherWith
                                    (slideOutOfContainer(
                                        towards = exitTowards,
                                        animationSpec = spring(
                                            dampingRatio = 0.75f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ) + fadeOut(animationSpec = tween(180)))
                                },
                                label = "player_center_view"
                            ) { mode ->
                                when (mode) {
                                    PlayerViewMode.COVER -> {
                                        CoverArtworkView(
                                            track = pageTrack,
                                            isPlaying = playbackState.isPlaying && pageTrack.id == track.id,
                                            selectedAmbient = playbackState.selectedAmbient,
                                            theme = playerTheme,
                                            artworkAlphaProvider = artworkAlphaProvider,
                                            isActive = viewMode == PlayerViewMode.COVER,
                                            onArtworkPositioned = if (pageIndex == activeTrackIndex) {
                                                onArtworkPositioned
                                            } else {
                                                {}
                                            }
                                        )
                                    }
                                    PlayerViewMode.LYRIC -> {
                                        LyricView(
                                            lyrics = pageTrack.lyrics,
                                            currentPositionMs = currentPosition,
                                            isActive = viewMode == PlayerViewMode.LYRIC
                                        )
                                    }
                                    PlayerViewMode.AMBIENT_MIXER -> {
                                        DualAmbientMixerView(
                                            recitationVolume = playbackState.recitationVolume,
                                            ambientVolume = playbackState.ambientVolume,
                                            selectedAmbient = playbackState.selectedAmbient,
                                            isAmbientPlaying = playbackState.isAmbientPlaying,
                                            activeAmbientTitle = playbackState.activeAmbientTitle,
                                            isActive = viewMode == PlayerViewMode.AMBIENT_MIXER,
                                            onRecitationVolumeChange = onRecitationVolumeChange,
                                            onAmbientVolumeChange = onAmbientVolumeChange,
                                            onSelectAmbient = onSelectAmbient,
                                            onToggleAmbientPlayback = onToggleAmbientPlayback
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Track Title & Reciter Info + Favorite Glowing Heart
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pageTrack.title,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${pageTrack.reciter} • ${pageTrack.album}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Glowing Favorite Heart Button
                            val isTrackFavorite = allTracks.find { it.id == pageTrack.id }?.isFavorite ?: pageTrack.isFavorite
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x30000000))
                                    .border(
                                        1.dp,
                                        if (isTrackFavorite) Color(0xFFFF5252).copy(alpha = 0.85f) else playerTheme.accent.copy(alpha = 0.6f),
                                        CircleShape
                                    )
                                    .clickable {
                                        haptic.confirm()
                                        onToggleFavorite(pageTrack.id)
                                    }
                                    .testTag(if (pageIndex == activeTrackIndex) "player_favorite_button" else "player_favorite_button_$pageIndex"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isTrackFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isTrackFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Symmetrical Frosted Glass Action Controls (Audio Routing Output Device & Add to Playlist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Audio Routing Output Device - LIVE Automatic Detection
                val currentOutput = playbackState.audioOutputDevice
                val animatedBorderColor by animateColorAsState(
                    targetValue = if (currentOutput.isWireless) EmeraldMint.copy(alpha = 0.60f) else playerTheme.border.copy(alpha = 0.50f),
                    animationSpec = tween(220),
                    label = "border_color_anim"
                )
                val animatedTint by animateColorAsState(
                    targetValue = if (currentOutput.isWireless) EmeraldMint else Color.White.copy(alpha = 0.95f),
                    animationSpec = tween(220),
                    label = "icon_tint_anim"
                )
                GlassIconActionButton(
                    contentDescription = "Active audio output device: ${currentOutput.name}",
                    borderColor = animatedBorderColor,
                    onClick = {
                        haptic.tap()
                        showOutputSheet = true
                    },
                    testTag = "btn_output_device_action"
                ) {
                    SmartAudioOutputIcon(
                        device = currentOutput,
                        iconSize = 22.dp,
                        tint = animatedTint
                    )
                }

                // 2. Add to Playlist
                GlassIconActionButton(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Add to playlist",
                    borderColor = playerTheme.border.copy(alpha = 0.50f),
                    onClick = {
                        haptic.tap()
                        onOpenMenu()
                    },
                    testTag = "btn_playlist_action"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Custom Tactile Fluid Capsule Progress Bar & Timestamps
            val duration = playbackState.durationMs.coerceAtLeast(1L)

            FluidProgressBar(
                currentPositionMs = currentPosition,
                durationMs = duration,
                accentColor = playerTheme.accent,
                activeGradientStart = playerTheme.progress,
                glowColor = playerTheme.glow,
                onScrubChange = { newPosMs ->
                    sliderSeekingPosition = newPosMs.toFloat()
                },
                onSeekFinished = { finalPosMs ->
                    haptic.tick()
                    onSeekTo(finalPosMs)
                    sliderSeekingPosition = -1f
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_progress_slider")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f)
                )
                Text(
                    text = formatDuration(duration),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.70f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Active Mode Notification HUD Pill
            androidx.compose.animation.AnimatedVisibility(
                visible = modeNotificationMessage != null,
                enter = fadeIn() + androidx.compose.animation.scaleIn(),
                exit = fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF142721))
                        .border(1.dp, playerTheme.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = modeNotificationMessage ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = playerTheme.accent,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Playback Controls Row (matching reference image)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Multi-State Dynamic Playback Mode Selector Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                haptic.tick()
                                onCyclePlaybackMode()
                                val nextMode = when (playbackState.playbackMode) {
                                    PlaybackMode.LOOP_FOLDER -> PlaybackMode.LOOP_TRACK
                                    PlaybackMode.LOOP_TRACK -> PlaybackMode.SHUFFLE_FOLDER
                                    PlaybackMode.SHUFFLE_FOLDER -> PlaybackMode.FREQUENCY_PLAY
                                    PlaybackMode.FREQUENCY_PLAY -> PlaybackMode.LOOP_FOLDER
                                }
                                modeNotificationMessage = "${nextMode.title} • Strictly in '${track.folderName}'"
                            },
                            onLongClick = {
                                haptic.confirm()
                                showModeSheet = true
                            }
                        )
                        .testTag("btn_playback_mode"),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = playbackState.playbackMode,
                        label = "playback_mode_crossfade"
                    ) { mode ->
                        when (mode) {
                            PlaybackMode.LOOP_TRACK -> {
                                Icon(
                                    imageVector = Icons.Default.RepeatOne,
                                    contentDescription = "Loop Track",
                                    tint = playerTheme.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            PlaybackMode.LOOP_FOLDER -> {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = "Loop Folder",
                                    tint = playerTheme.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            PlaybackMode.SHUFFLE_FOLDER -> {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle Folder",
                                    tint = playerTheme.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            PlaybackMode.FREQUENCY_PLAY -> {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = "Most Played First",
                                    tint = playerTheme.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Previous button
                IconButton(
                    onClick = {
                        haptic.tap()
                        onPlayPrevious()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_previous")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous song",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Frosted Crystal Glass Play/Pause Button
                GlassPlayPauseButton(
                    isPlaying = playbackState.isPlaying,
                    onClick = onTogglePlayPause,
                    size = 72.dp,
                    iconSize = 38.dp,
                    testTag = "btn_play_pause"
                )

                // Next button (Visually consistent with Previous button)
                IconButton(
                    onClick = {
                        haptic.tap()
                        onPlayNext()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next song",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                // Dedicated Play Queue Button synced with media controls (frosted glass & solid white icon)
                val queueInteractionSource = remember { MutableInteractionSource() }
                val queuePressed by queueInteractionSource.collectIsPressedAsState()
                val queueScale by animateFloatAsState(
                    targetValue = if (queuePressed) 0.90f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "queue_button_scale"
                )

                Box(
                    modifier = Modifier
                        .scale(queueScale)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (queuePressed) 0.18f else 0.10f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                        .clickable(
                            interactionSource = queueInteractionSource,
                            indication = null,
                            onClick = {
                                haptic.confirm()
                                showPlayQueue = true
                            }
                        )
                        .testTag("play_queue_button")
                        .testTag("btn_queue"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Open Play Queue",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }

    // Floating auto-dismiss effect for HUD banner
    androidx.compose.runtime.LaunchedEffect(modeNotificationMessage) {
        if (modeNotificationMessage != null) {
            kotlinx.coroutines.delay(2600L)
            modeNotificationMessage = null
        }
    }

    // Playback Mode Selection Bottom Sheet
    if (showModeSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            containerColor = Color(0xFF0F1E19),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Playback Mode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Queue strictly isolated to '${track.folderName}' folder",
                    fontSize = 12.sp,
                    color = GoldPrimary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                )

                PlaybackMode.values().forEach { mode ->
                    val isSelected = playbackState.playbackMode == mode
                    val icon = when (mode) {
                        PlaybackMode.LOOP_FOLDER -> Icons.Default.Repeat
                        PlaybackMode.LOOP_TRACK -> Icons.Default.RepeatOne
                        PlaybackMode.SHUFFLE_FOLDER -> Icons.Default.Shuffle
                        PlaybackMode.FREQUENCY_PLAY -> Icons.Default.TrendingUp
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) GoldPrimary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable {
                                haptic.confirm()
                                onSelectPlaybackMode(mode)
                                modeNotificationMessage = "${mode.title} • Strictly in '${track.folderName}'"
                                showModeSheet = false
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.title,
                            tint = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = when (mode) {
                                    PlaybackMode.LOOP_FOLDER -> "Plays '${track.folderName}' sequentially respecting active sort order, then repeats"
                                    PlaybackMode.LOOP_TRACK -> "Continuously loops the current recitation"
                                    PlaybackMode.SHUFFLE_FOLDER -> "Randomizes queue strictly within '${track.folderName}'"
                                    PlaybackMode.FREQUENCY_PLAY -> "Sorts tracks in '${track.folderName}' by historical listening count"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showOutputSheet) {
        SmartAudioOutputModalSheet(
            currentDevice = playbackState.audioOutputDevice,
            sheetState = outputSheetState,
            onDismiss = { showOutputSheet = false },
            onSimulateDevice = { dev ->
                onSimulateDevice(dev)
                showOutputSheet = false
            }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showPlayQueue,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        PlayQueueView(
            playbackState = playbackState,
            theme = playerTheme,
            onClose = { showPlayQueue = false },
            onSelectTrack = { onSelectTrack(it) },
            onReorderQueue = onReorderQueue,
            onRemoveTrackFromQueue = onRemoveTrackFromQueue,
            onPlayTrackNext = onPlayTrackNext,
            onAddToQueueEnd = onAddToQueueEnd,
            isDark = isDark,
            artworkBitmap = artworkBitmap
        )
    }
}

@Composable
private fun PillTabItem(
    title: String,
    isSelected: Boolean,
    accentColor: Color = Color(0xFFDC2626),
    onClick: () -> Unit
) {
    val tabScale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 0.92f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tab_scale_pop"
    )
    val tabAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.65f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tab_alpha"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.85f else 0.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "tab_border_glow"
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .scale(tabScale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) {
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.38f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                }
            )
            .border(
                width = if (isSelected) 1.2.dp else 0.dp,
                brush = Brush.linearGradient(
                    colors = if (isSelected) {
                        listOf(
                            Color.White.copy(alpha = 0.70f),
                            accentColor.copy(alpha = borderAlpha),
                            Color.White.copy(alpha = 0.40f)
                        )
                    } else {
                        listOf(Color.Transparent, Color.Transparent)
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("tab_${title.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color.White.copy(alpha = tabAlpha)
        )
    }
}

@Composable
private fun CoverArtworkView(
    track: AudioTrack,
    isPlaying: Boolean,
    selectedAmbient: AmbientType,
    theme: DynamicPlayerTheme,
    artworkAlphaProvider: () -> Float = { 1f },
    isActive: Boolean = true,
    onArtworkPositioned: (ArtworkBounds) -> Unit
) {
    val context = LocalContext.current
    val isDark = true
    val artworkData by rememberHighResArtworkModel(track)
    val isCustomCover = remember(track.id) { AudioArtworkExtractor.hasCustomCover(context, track.id) }

    var hasArrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasArrived = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive && hasArrived) 1.0f else 0.85f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "cover_arrival_pop"
    )

    Column(
        modifier = Modifier.scale(animatedScale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(326.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Classic Vinyl Record Graphic Sliding Out Right
            VinylRecordGraphic(
                isPlaying = isPlaying,
                modifier = Modifier
                    .size(284.dp)
                    .offset(x = 68.dp)
            )

            // 2. Dynamic Neon Glowing Drop Shadow Halo
            Box(
                modifier = Modifier
                    .size(310.dp)
                    .offset(x = (-16).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                theme.glow.copy(alpha = 0.55f),
                                theme.primary.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            )

            // 3. Central Squircle Cover Card (~28.dp Rounded Corners) with Dynamic Neon Border & Shadow
            Box(
                modifier = Modifier
                    .size(302.dp)
                    .offset(x = (-16).dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        onArtworkPositioned(
                            ArtworkBounds(
                                left = pos.x,
                                top = pos.y,
                                width = coords.size.width.toFloat(),
                                height = coords.size.height.toFloat()
                            )
                        )
                    }
                    .graphicsLayer {
                        alpha = artworkAlphaProvider()
                    }
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = Color.Black.copy(alpha = 0.55f),
                        ambientColor = theme.glow.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .border(
                        width = 1.2.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                theme.glow.copy(alpha = 0.90f),
                                theme.accent.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.20f),
                                theme.primary.copy(alpha = 0.70f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                val rawArtwork = artworkData
                if (rawArtwork != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(rawArtwork)
                            .size(coil.size.Size.ORIGINAL)
                            .allowHardware(true)
                            .crossfade(500)
                            .build(),
                        contentDescription = "Cover Artwork for ${track.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle dark gradient vignette for depth
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        Color(0x70000000)
                                    )
                                )
                            )
                    )
                } else {
                    // Graceful Fallback (Deep Crimson / Sanctuary Pattern)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        theme.primary.copy(alpha = 0.35f),
                                        Color(0xFF140204),
                                        Color(0xFF080002)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = track.arabicTitle ?: "ٱلْقُرْآنُ ٱلْكَرِيمُ",
                                color = GoldLight,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = track.reciter,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Top-Left Badge: Small sparkle/star logo indicator (clean logo-only frosted pill)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .size(24.dp)
                        .shadow(
                            elevation = 3.dp,
                            shape = CircleShape,
                            spotColor = theme.glow.copy(alpha = 0.6f),
                            ambientColor = theme.accent.copy(alpha = 0.4f)
                        )
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.42f))
                        .border(
                            width = 0.8.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    theme.accent.copy(alpha = 0.85f),
                                    theme.glow.copy(alpha = 0.45f)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Artwork Indicator",
                        tint = theme.accent,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Ambient visualizer bars overlay across bottom
                AudioVisualizerBars(
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    barCount = 14,
                    activeColor = theme.accent
                )
            }
        }
    }
}

/**
 * Photorealistic Rotating Vinyl Record Disc Graphic.
 * Renders concentric vinyl grooves and rotating spindle ring.
 */
@Composable
private fun VinylRecordGraphic(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = if (isPlaying) rememberInfiniteTransition(label = "vinyl_rotation") else null
    val rotation by if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "vinyl_angle"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
            }
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f

                // 1. Vinyl Base Outer Disc
                drawCircle(
                    color = Color(0xFF141414),
                    radius = radius
                )

                // Outer Bevel Edge Rim
                drawCircle(
                    color = Color(0xFF282828),
                    radius = radius,
                    style = Stroke(width = 2f)
                )

                // 2. Concentric Vinyl Grooves
                val grooveStep = (radius * 0.65f) / 10f
                for (i in 1..9) {
                    drawCircle(
                        color = Color(0xFF252525),
                        radius = radius * 0.35f + (grooveStep * i),
                        style = Stroke(width = 1.2f)
                    )
                }

                // 3. Inner Center Label Ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2A4B),
                            Color(0xFF80001A),
                            Color(0xFF1A1A1A)
                        ),
                        center = center,
                        radius = radius * 0.35f
                    ),
                    radius = radius * 0.35f
                )

                // 4. Center Spindle Hole
                drawCircle(
                    color = Color(0xFF080808),
                    radius = radius * 0.08f
                )
                drawCircle(
                    color = Color(0xFF505050),
                    radius = radius * 0.08f,
                    style = Stroke(width = 1.5f)
                )
            }
    )
}

@Composable
private fun LyricView(
    lyrics: List<com.example.model.LyricLine>,
    currentPositionMs: Long,
    isActive: Boolean = true
) {
    var hasArrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasArrived = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive && hasArrived) 1.0f else 0.85f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "lyric_arrival_pop"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(animatedScale)
    ) {
        if (lyrics.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reflective Silence\nFocus on the divine acoustic recitation",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(lyrics) { line ->
                    val isLineActive = currentPositionMs >= line.timestampMs
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        borderColor = if (isLineActive) GoldPrimary.copy(alpha = 0.5f) else null
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = line.arabic,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLineActive) GoldPrimary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = line.transliteration,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = line.translation,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dual Ambient Sound Mixer Panel:
 * Sliders for Surah recitation & ambient layer, plus ambient presets
 */
@Composable
fun DualAmbientMixerView(
    recitationVolume: Float,
    ambientVolume: Float,
    selectedAmbient: AmbientType,
    isAmbientPlaying: Boolean,
    activeAmbientTitle: String? = null,
    isActive: Boolean = true,
    onRecitationVolumeChange: (Float) -> Unit,
    onAmbientVolumeChange: (Float) -> Unit,
    onSelectAmbient: (AmbientType) -> Unit,
    onToggleAmbientPlayback: () -> Unit
) {
    val haptic = rememberHapticController()
    val currentTitle = activeAmbientTitle ?: "Custom Atmosphere Track"

    var hasArrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hasArrived = true
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isActive && hasArrived) 1.0f else 0.85f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "mixer_arrival_pop"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        borderColor = GoldPrimary.copy(alpha = 0.3f)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Custom Atmosphere Mixer",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Infinite Loop Engine (REPEAT_MODE_ALL)",
                            fontSize = 12.sp,
                            color = EmeraldMint
                        )
                    }

                    // Ambient On/Off Toggle Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isAmbientPlaying) EmeraldMint.copy(alpha = 0.25f)
                                else Color.Gray.copy(alpha = 0.2f)
                            )
                            .clickable {
                                haptic.tap()
                                onToggleAmbientPlayback()
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isAmbientPlaying) "Looping Active" else "Muted",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAmbientPlaying) EmeraldMint else Color.Gray
                        )
                    }
                }
            }

            // 1. Recitation Volume Slider
            item {
                VolumeSliderCard(
                    title = "Primary Music Volume",
                    volume = recitationVolume,
                    icon = Icons.Default.VolumeUp,
                    accentColor = GoldPrimary,
                    onVolumeChange = {
                        haptic.tick()
                        onRecitationVolumeChange(it)
                    }
                )
            }

            // 2. Custom Atmosphere Volume Slider
            item {
                VolumeSliderCard(
                    title = "Custom Atmosphere ($currentTitle)",
                    volume = ambientVolume,
                    icon = Icons.Default.Spa,
                    accentColor = EmeraldMint,
                    onVolumeChange = {
                        haptic.tick()
                        onAmbientVolumeChange(it)
                    }
                )
            }

            // Active Custom Atmosphere Loop Status Card
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = EmeraldMint.copy(alpha = 0.10f),
                    borderColor = EmeraldMint.copy(alpha = 0.40f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldMint.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Repeat,
                                        contentDescription = "Infinite Loop",
                                        tint = EmeraldMint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldMint
                                    )
                                    Text(
                                        text = if (isAmbientPlaying) "● Looping infinitely from internal storage" else "▶ Paused (Upload & select tracks in Atmosphere tab)",
                                        fontSize = 11.sp,
                                        color = if (isAmbientPlaying) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldMint)
                                    .clickable {
                                        haptic.confirm()
                                        onToggleAmbientPlayback()
                                    }
                                    .testTag("player_toggle_ambient_preview"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAmbientPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isAmbientPlaying) "Pause Atmosphere" else "Play Atmosphere",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (isAmbientPlaying) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AudioVisualizerBars(
                                isPlaying = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                                barCount = 24,
                                activeColor = EmeraldMint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeSliderCard(
    title: String,
    volume: Float,
    icon: ImageVector,
    accentColor: Color,
    onVolumeChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "${(volume * 100).toInt()}%",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }

        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSec = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
private fun GlassIconActionButton(
    icon: ImageVector,
    contentDescription: String,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 80.dp, height = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x25000000))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun GlassIconActionButton(
    contentDescription: String,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = 80.dp, height = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x25000000))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AnimatedEqualizerBars(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val heights = listOf(
        infiniteTransition.animateFloat(
            initialValue = 0.2f, targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar1"
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(300, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar2"
        ),
        infiniteTransition.animateFloat(
            initialValue = 0.1f, targetValue = 0.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "bar3"
        )
    )

    Row(
        modifier = Modifier.size(16.dp, 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { anim ->
            Box(
                modifier = Modifier
                    .fillMaxHeight(anim.value)
                    .weight(1f)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun PlayQueueView(
    playbackState: PlaybackState,
    theme: DynamicPlayerTheme,
    onClose: () -> Unit,
    onSelectTrack: (AudioTrack) -> Unit,
    onReorderQueue: (Int, Int) -> Unit,
    onRemoveTrackFromQueue: (String) -> Unit,
    onPlayTrackNext: (AudioTrack) -> Unit,
    onAddToQueueEnd: (AudioTrack) -> Unit,
    isDark: Boolean,
    artworkBitmap: android.graphics.Bitmap?
) {
    val haptic = rememberHapticController()
    val totalTracks = playbackState.playQueue.size
    val totalDurationMs = playbackState.playQueue.sumOf { it.durationMs }
    
    val formattedTotalDuration = remember(totalDurationMs) {
        val totalSeconds = totalDurationMs / 1000
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        if (hours > 0) {
            "${hours} hr ${minutes} min"
        } else {
            "${minutes} min"
        }
    }

    val currentTrack = playbackState.currentTrack
    val remainingQueue = remember(playbackState.playQueue, currentTrack) {
        if (currentTrack == null) {
            playbackState.playQueue
        } else {
            val idx = playbackState.playQueue.indexOfFirst { it.id == currentTrack.id }
            if (idx != -1) {
                playbackState.playQueue.drop(idx + 1)
            } else {
                playbackState.playQueue
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1E19).copy(alpha = 0.95f))
    ) {
        artworkBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.15f },
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.confirm()
                        onClose()
                    },
                    modifier = Modifier.testTag("btn_queue_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAY QUEUE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "$totalTracks recitations • $formattedTotalDuration",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                IconButton(
                    onClick = { haptic.tap() },
                    modifier = Modifier.testTag("btn_queue_more")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentTrack != null) {
                    item {
                        Text(
                            text = "NOW PLAYING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.accent,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .border(1.dp, theme.accent.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                                .clickable { onSelectTrack(currentTrack) }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val rowArtworkData by rememberHighResArtworkModel(currentTrack)
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rowArtworkData != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(rowArtworkData)
                                                .size(coil.size.Size.ORIGINAL)
                                                .allowHardware(true)
                                                .crossfade(500)
                                                .build(),
                                            contentDescription = currentTrack.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = theme.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = currentTrack.title,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (playbackState.isPlaying) {
                                            AnimatedEqualizerBars(color = theme.accent)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = currentTrack.reciter,
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.70f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${formatDuration(playbackState.currentPositionMs)} / ${formatDuration(playbackState.durationMs)}",
                                        fontSize = 11.sp,
                                        color = theme.accent.copy(alpha = 0.85f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (remainingQueue.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "PLAYING NEXT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }

                    items(remainingQueue.size) { index ->
                        val trackItem = remainingQueue[index]
                        val queueIndexInFullPlaylist = playbackState.playQueue.indexOfFirst { it.id == trackItem.id }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic.tap()
                                    onSelectTrack(trackItem)
                                }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%02d", index + 1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.width(24.dp)
                                )

                                val rowArtworkItemData by rememberHighResArtworkModel(trackItem)
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rowArtworkItemData != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(rowArtworkItemData)
                                                .size(coil.size.Size.ORIGINAL)
                                                .allowHardware(true)
                                                .crossfade(500)
                                                .build(),
                                            contentDescription = trackItem.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = trackItem.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = trackItem.reciter,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.60f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            haptic.tick()
                                            if (queueIndexInFullPlaylist > 0) {
                                                onReorderQueue(queueIndexInFullPlaylist, queueIndexInFullPlaylist - 1)
                                            }
                                        },
                                        enabled = (queueIndexInFullPlaylist > 0),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (queueIndexInFullPlaylist > 0) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.tick()
                                            if (queueIndexInFullPlaylist < totalTracks - 1) {
                                                onReorderQueue(queueIndexInFullPlaylist, queueIndexInFullPlaylist + 1)
                                            }
                                        },
                                        enabled = (queueIndexInFullPlaylist < totalTracks - 1),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (queueIndexInFullPlaylist < totalTracks - 1) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    var showItemMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = {
                                                haptic.tap()
                                                showItemMenu = true
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        androidx.compose.material3.DropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false },
                                            modifier = Modifier.background(Color(0xFF0F1E19))
                                        ) {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Play Next", color = Color.White) },
                                                onClick = {
                                                    haptic.confirm()
                                                    onPlayTrackNext(trackItem)
                                                    showItemMenu = false
                                                }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Add to Queue End", color = Color.White) },
                                                onClick = {
                                                    haptic.confirm()
                                                    onAddToQueueEnd(trackItem)
                                                    showItemMenu = false
                                                }
                                            )
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text("Remove from Queue", color = Color.White) },
                                                onClick = {
                                                    haptic.confirm()
                                                    onRemoveTrackFromQueue(trackItem.id)
                                                    showItemMenu = false
                                                }
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.confirm()
                                            onRemoveTrackFromQueue(trackItem.id)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove from Queue",
                                            tint = Color(0xFFEF4444).copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Tactile "Fluid Capsule" Progress Bar (`FluidProgressBar`).
 *
 * Step 1: Fluid Capsule Design
 * - Inactive State: Thick pill-shaped capsule track (8.dp height) with fully rounded corners in
 *   translucent frosted glass (`#1AFFFFFF`).
 * - Active State: Fills the played portion with a rich, glowing horizontal gradient powered by the
 *   5-point extracted UI color (`accentColor`).
 *
 * Step 2: Tactile Spring Physics (Interaction)
 * - Uses `awaitEachGesture { awaitFirstDown ... waitForUpOrCancellation }` and
 *   `detectHorizontalDragGestures` to detect touch-down, scrubbing, and release.
 * - Track height expands smoothly from `8.dp` to `16.dp` when touched via `spring(dampingRatio = 0.6f)`
 *   and springs back to `8.dp` on release.
 * - Scrubber thumb is completely hidden (`scale = 0f`) when inactive, and scales into a glowing
 *   white orb at the exact scrub X-coordinate when pressed.
 *
 * Step 3: Floating Time Bubble (Scrubbing Indicator)
 * - While touched/dragging, a glassmorphic rounded pill tooltip floats directly above the user's
 *   thumb X-position displaying the live scrubbed timestamp (e.g., `01:45`), smoothly fading in
 *   and sliding up on touch and fading out on release.
 *
 * Step 4: Safe Seeking Logic
 * - Dragging updates the visual UI progress and timestamp immediately with zero audio engine calls,
 *   and only invokes `onSeekFinished(scrubPositionMs)` (`mediaController.seekTo(position)`) when the
 *   user releases their finger (`onDragEnd` / `onDragCancel` / pointer up).
 */
@Composable
fun FluidProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    accentColor: Color,
    activeGradientStart: Color = accentColor.copy(alpha = 0.72f),
    glowColor: Color = accentColor,
    onScrubChange: (Long) -> Unit = {},
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val externalProgress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

    var isUserDragging by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(externalProgress) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    var bubbleWidthPx by remember { mutableFloatStateOf(0f) }

    // Keep scrubFraction synced with playback when user is not dragging
    androidx.compose.runtime.LaunchedEffect(externalProgress, isUserDragging) {
        if (!isUserDragging) {
            scrubFraction = externalProgress
        }
    }

    val activeFraction = if (isUserDragging) scrubFraction else externalProgress
    val displayedPositionMs = (activeFraction * safeDuration).toLong().coerceIn(0L, safeDuration)

    // Step 2: Spring physics for capsule thickness (8.dp -> 16.dp) with dampingRatio = 0.6f
    val trackHeight by animateDpAsState(
        targetValue = if (isUserDragging) 16.dp else 8.dp,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fluid_capsule_height"
    )

    // Step 2: Glowing white orb thumb scale (hidden at 0f when inactive, springs to 1f when touched)
    val thumbScale by animateFloatAsState(
        targetValue = if (isUserDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fluid_thumb_scale"
    )

    // Step 3: Floating glassmorphic timestamp bubble fade + vertical slide animation
    val bubbleProgress by animateFloatAsState(
        targetValue = if (isUserDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fluid_time_bubble_progress"
    )

    val density = LocalDensity.current
    val slideOffsetPx = with(density) { ((1f - bubbleProgress) * 10.dp.toPx()).roundToInt() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Step 3: Floating Glassmorphic Time Bubble directly above the user's thumb X-position
        if (bubbleProgress > 0.01f) {
            val thumbCenterX = (trackWidthPx * activeFraction).coerceIn(0f, trackWidthPx)
            val halfBubble = bubbleWidthPx / 2f
            val maxBubbleOffsetX = (trackWidthPx - bubbleWidthPx).coerceAtLeast(0f)
            val clampedBubbleX = (thumbCenterX - halfBubble).coerceIn(0f, maxBubbleOffsetX)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { bubbleWidthPx = it.width.toFloat() }
                    .offset {
                        IntOffset(
                            x = clampedBubbleX.roundToInt(),
                            y = slideOffsetPx
                        )
                    }
                    .graphicsLayer {
                        alpha = bubbleProgress.coerceIn(0f, 1f)
                        scaleX = 0.85f + (0.15f * bubbleProgress)
                        scaleY = 0.85f + (0.15f * bubbleProgress)
                    }
                    .shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = glowColor,
                        spotColor = glowColor
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC1E2925),
                                Color(0xE6101815)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                accentColor.copy(alpha = 0.75f),
                                Color.White.copy(alpha = 0.35f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = formatDuration(displayedPositionMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Step 1, 2 & 4: Custom Fluid Capsule Canvas with press & horizontal drag pointerInput
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                // Decoupled pointerInput: sets isUserDragging = true on down, tracks drag smoothly, commits seek on release
                .pointerInput(safeDuration) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isUserDragging = true
                        val width = size.width.coerceAtLeast(1)
                        val initialFraction = (down.position.x / width).coerceIn(0f, 1f)
                        scrubFraction = initialFraction
                        val initialMs = (initialFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                        onScrubChange(initialMs)

                        var currentFraction = initialFraction
                        val pointerId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()

                            if (change == null || change.isConsumed) {
                                break
                            }

                            if (change.pressed) {
                                change.consume()
                                currentFraction = (change.position.x / width).coerceIn(0f, 1f)
                                scrubFraction = currentFraction
                                val scrubMs = (currentFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                                onScrubChange(scrubMs)
                            } else {
                                change.consume()
                                break
                            }
                        }

                        val finalMs = (currentFraction * safeDuration).toLong().coerceIn(0L, safeDuration)
                        onSeekFinished(finalMs)
                        isUserDragging = false
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barHeightPx = trackHeight.toPx().coerceAtMost(canvasHeight)
            val topY = (canvasHeight - barHeightPx) / 2f
            val centerY = canvasHeight / 2f
            val cornerRadius = CornerRadius(barHeightPx / 2f, barHeightPx / 2f)

            // 1. Inactive State: Translucent frosted glass capsule track (#1AFFFFFF)
            drawRoundRect(
                color = Color(0x1AFFFFFF),
                topLeft = Offset(0f, topY),
                size = Size(canvasWidth, barHeightPx),
                cornerRadius = cornerRadius
            )

            // Subtle crystal-glass specular rim along the inactive capsule
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(0f, topY),
                size = Size(canvasWidth, barHeightPx),
                cornerRadius = cornerRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            // 2. Active State (Played Portion): Rich glowing horizontal gradient from 5-point UI color
            val rawPlayedWidth = (canvasWidth * activeFraction).coerceIn(0f, canvasWidth)
            val playedWidth = if (activeFraction > 0.001f) {
                rawPlayedWidth.coerceAtLeast(barHeightPx)
            } else {
                0f
            }

            if (playedWidth > 0f) {
                // Soft outer ambient bloom behind the active capsule
                val glowPad = if (isUserDragging) 4.dp.toPx() else 2.dp.toPx()
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            activeGradientStart.copy(alpha = 0.18f),
                            glowColor.copy(alpha = if (isUserDragging) 0.45f else 0.28f)
                        ),
                        startX = 0f,
                        endX = playedWidth
                    ),
                    topLeft = Offset(0f, (topY - glowPad).coerceAtLeast(0f)),
                    size = Size(playedWidth, (barHeightPx + glowPad * 2f).coerceAtMost(canvasHeight)),
                    cornerRadius = CornerRadius(
                        (barHeightPx + glowPad * 2f) / 2f,
                        (barHeightPx + glowPad * 2f) / 2f
                    )
                )

                // Main active fluid capsule fill with vibrant 5-point extracted horizontal gradient
                val brightTipColor = Color(
                    red = (accentColor.red + 0.25f).coerceAtMost(1f),
                    green = (accentColor.green + 0.25f).coerceAtMost(1f),
                    blue = (accentColor.blue + 0.25f).coerceAtMost(1f),
                    alpha = 1f
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            activeGradientStart,
                            accentColor,
                            brightTipColor
                        ),
                        startX = 0f,
                        endX = playedWidth.coerceAtLeast(1f)
                    ),
                    topLeft = Offset(0f, topY),
                    size = Size(playedWidth, barHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            // 3. Tactile Glowing White Orb Thumb (Hidden when inactive; scales up when pressed)
            if (thumbScale > 0.01f) {
                val thumbX = rawPlayedWidth.coerceIn(barHeightPx / 2f, (canvasWidth - barHeightPx / 2f).coerceAtLeast(barHeightPx / 2f))
                val maxOrbRadius = (barHeightPx * 0.56f).coerceAtLeast(7.dp.toPx())
                val currentOrbRadius = maxOrbRadius * thumbScale

                // Outer colored halo bloom
                drawCircle(
                    color = glowColor.copy(alpha = 0.42f * thumbScale.coerceIn(0f, 1f)),
                    radius = currentOrbRadius * 1.85f,
                    center = Offset(thumbX, centerY)
                )

                // Mid white soft aura
                drawCircle(
                    color = Color.White.copy(alpha = 0.38f * thumbScale.coerceIn(0f, 1f)),
                    radius = currentOrbRadius * 1.32f,
                    center = Offset(thumbX, centerY)
                )

                // Pure glowing white orb core
                drawCircle(
                    color = Color.White,
                    radius = currentOrbRadius,
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}



