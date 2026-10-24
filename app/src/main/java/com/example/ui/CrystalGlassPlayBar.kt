package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.rememberHighResArtworkModel
import com.example.data.rememberTrackArtwork
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AmbientType
import com.example.model.AudioOutputDevice
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import com.example.ui.theme.AmbientRainBlue
import com.example.ui.theme.EmeraldDarkPrimary
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import kotlin.math.sin

/**
 * Ultra-detailed Smartphone-Optimized Persistent Bottom Playback Controller.
 *
 * Engineered with:
 * 1. Multi-layered frosted crystal glass backdrop with refractive caustics.
 * 2. Specular top-edge lighting & tactile bevel borders that catch light under the thumb.
 * 3. Integrated track progress micro-indicator along top curve.
 * 4. Resilient one-handed ergonomic layout that prevents text overlapping or letter wrapping.
 * 5. 3D Floating Crystal Play/Pause Orb with spring bounce and haptic click.
 */
@Composable
fun CrystalGlassPlayBar(
    track: AudioTrack,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    isAmbientActive: Boolean,
    ambientVolume: Float,
    selectedAmbient: AmbientType = AmbientType.RAIN,
    audioOutputDevice: AudioOutputDevice,
    isPlayerOpen: Boolean = false,
    onTogglePlay: () -> Unit,
    onOpenPlayer: (ArtworkBounds?) -> Unit,
    onOpenAtmosphere: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = true // Locked glassmorphic theme
    val haptic = rememberHapticController()
    var miniArtworkBounds by remember { mutableStateOf<ArtworkBounds?>(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bar_press_scale"
    )

    // Calculate playback progress fraction (0.0 to 1.0)
    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Ambient theme color
    val ambientAccent = selectedAmbient.accentColor

    // Multi-layer frosted glass color palette
    val glassBgColors = if (isDark) {
        listOf(
            Color(0xF0081C16), // Deep emerald glass
            Color(0xE0051410), // Translucent body
            Color(0xEA092019)  // Refractive base
        )
    } else {
        listOf(
            Color(0xFAFFFFFF), // Bright crystal surface
            Color(0xEEF3F8F5), // Soft mint reflection
            Color(0xF6FFFFFF)  // Base glass
        )
    }

    // Specular border lighting (bright catch-light on top edge, subtle shadow below)
    val borderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color(0x99FFFFFF),              // Crisp white specular catch
                GoldPrimary.copy(alpha = 0.55f),// Golden rim
                GoldPrimary.copy(alpha = 0.20f),// Soft mid
                Color(0x1A000000)               // Under-shadow
            )
        } else {
            listOf(
                Color(0xEEFFFFFF),              // Pure white light highlight
                GoldPrimary.copy(alpha = 0.70f),// Gold accent rim
                EmeraldDarkPrimary.copy(alpha = 0.25f),
                Color(0x22000000)
            )
        }
    )

    // Ambient caustics glow beneath the bar
    val ambientCausticsColor = if (isAmbientActive && ambientVolume > 0f) {
        ambientAccent.copy(alpha = if (isDark) 0.28f else 0.18f)
    } else {
        GoldPrimary.copy(alpha = if (isDark) 0.20f else 0.12f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .navigationBarsPadding()
            .scale(animatedScale)
            .testTag("crystal_glass_play_bar")
    ) {
        // Layer 0: Multi-layer tinted atmospheric drop shadow & caustics
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val cornerRadiusPx = 24.dp.toPx()

                    // Ambient soft caustics bloom
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ambientCausticsColor,
                                Color.Transparent
                            ),
                            center = Offset(w * 0.5f, h * 0.9f),
                            radius = w * 0.55f
                        ),
                        size = Size(w, h + 10.dp.toPx()),
                        topLeft = Offset(0f, 2.dp.toPx()),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )

                    // Deep directional drop shadow
                    drawRoundRect(
                        color = if (isDark) Color(0x66010A07) else Color(0x280A3024),
                        topLeft = Offset(0f, 6.dp.toPx()),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                }
        )

        // Main Frosted Glass Surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(glassBgColors))
                .border(1.25.dp, borderBrush, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        haptic.tap()
                        onOpenPlayer(miniArtworkBounds)
                    }
                )
        ) {
            // Layer 1: Top Edge Micro Progress Track
            if (durationMs > 0L) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter)
                ) {
                    val w = size.width
                    val h = size.height

                    // Background track groove
                    drawRect(
                        color = if (isDark) Color(0x22FFFFFF) else Color(0x1F0A5C44),
                        size = Size(w, h)
                    )

                    // Active progress fill with emerald-to-gold shimmer
                    if (progress > 0f) {
                        val activeWidth = w * progress
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    EmeraldMint,
                                    GoldPrimary,
                                    GoldLight
                                )
                            ),
                            size = Size(activeWidth, h)
                        )

                        // Glowing bead at head
                        drawCircle(
                            color = GoldLight,
                            radius = 2.5.dp.toPx(),
                            center = Offset(activeWidth, h / 2f)
                        )
                    }
                }
            }

            // Layer 2: Core Interactive Elements (Mobile Ergonomics)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Album / Surah Art Squircle with Pulsing Aura & ID3 Cover Art
                SurahArtworkSquircle(
                    track = track,
                    isPlaying = isPlaying,
                    selectedAmbient = selectedAmbient,
                    isAmbientActive = isAmbientActive,
                    isPlayerOpen = isPlayerOpen,
                    onPositioned = { miniArtworkBounds = it },
                    onClick = onOpenPlayer
                )

                Spacer(modifier = Modifier.width(10.dp))

                // 2. Center Info Column (Title + Reciter + Output + Ambient Pill)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Title
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Subtitle Metadata Row (Reciter + Output + Ambient Badge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reciter Name with safe weight so it flexes and truncates smoothly
                        Text(
                            text = track.reciter,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Smart Hardware Audio Output Icon
                        SmartAudioOutputIcon(
                            device = audioOutputDevice,
                            iconSize = 12.dp,
                            tint = if (audioOutputDevice.isWireless) EmeraldMint else GoldPrimary
                        )

                        // Ambient Status Capsule Pill (Guaranteed NO letter wrapping)
                        if (isAmbientActive && ambientVolume > 0f) {
                            Spacer(modifier = Modifier.width(6.dp))
                            AmbientStatusCapsule(
                                ambient = selectedAmbient,
                                volume = ambientVolume,
                                onClick = {
                                    if (onOpenAtmosphere != null) {
                                        onOpenAtmosphere()
                                    } else {
                                        onOpenPlayer(miniArtworkBounds)
                                    }
                                }
                            )
                        }
                    }
                }

                // 3. Right Action Controls: 3D Crystal Play/Pause Orb + Quick Expand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ColorOS 17 Frosted Crystal Glass Play/Pause Button
                    GlassPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = onTogglePlay,
                        size = 46.dp,
                        iconSize = 24.dp,
                        testTag = "mini_player_toggle_play"
                    )

                    // Atmosphere / Full Player Quick Action Button
                    IconButton(
                        onClick = {
                            haptic.tap()
                            if (onOpenAtmosphere != null) {
                                onOpenAtmosphere()
                            } else {
                                onOpenPlayer(miniArtworkBounds)
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("mini_player_quick_action")
                    ) {
                        Icon(
                            imageVector = if (onOpenAtmosphere != null) Icons.Default.Tune else Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Expand Soundscape",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-fidelity 48x48dp Surah Artwork Squircle with breathing ambient pulse and ID3 artwork rendering.
 */
@Composable
private fun SurahArtworkSquircle(
    track: AudioTrack? = null,
    isPlaying: Boolean,
    selectedAmbient: AmbientType,
    isAmbientActive: Boolean,
    isPlayerOpen: Boolean = false,
    onPositioned: (ArtworkBounds) -> Unit,
    onClick: (ArtworkBounds?) -> Unit
) {
    val isDark = true
    val artworkData by rememberHighResArtworkModel(track)

    val infiniteTransition = if (isPlaying && !isPlayerOpen) rememberInfiniteTransition(label = "art_pulse") else null
    val pulseScale by if (infiniteTransition != null) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "art_scale"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    var bounds by remember { mutableStateOf<ArtworkBounds?>(null) }
    val auraGlowColor = if (isAmbientActive) selectedAmbient.accentColor else GoldPrimary

    Box(
        modifier = Modifier
            .size(46.dp)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val measured = ArtworkBounds(
                    left = pos.x,
                    top = pos.y,
                    width = coords.size.width.toFloat(),
                    height = coords.size.height.toFloat()
                )
                bounds = measured
                onPositioned(measured)
            }
            .scale(if (isPlaying && !isPlayerOpen) pulseScale else 1f)
            .drawBehind {
                if (isPlaying && !isPlayerOpen) {
                    // Soft outer glow behind thumbnail
                    drawCircle(
                        color = auraGlowColor.copy(alpha = 0.35f),
                        radius = size.width * 0.58f
                    )
                }
            }
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0F3228),
                        Color(0xFF071B14)
                    )
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        GoldPrimary.copy(alpha = 0.65f),
                        EmeraldDarkPrimary.copy(alpha = 0.3f)
                    )
                ),
                RoundedCornerShape(13.dp)
            )
            .clickable { onClick(bounds) },
        contentAlignment = Alignment.Center
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
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(13.dp))
                    .graphicsLayer {
                        alpha = if (isPlayerOpen) 0f else 1f
                    }
            )
        } else {
            // Base Icon
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Mini Audio Wave Equalizer Overlay at bottom of album art
        if (isPlaying) {
            MiniWaveBarsOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 20.dp, height = 8.dp),
                color = GoldLight
            )
        }
    }
}

/**
 * Compact Ambient Status Capsule Pill.
 * Guaranteed fixed layout with no multi-line wrapping under tight constraints.
 */
@Composable
private fun AmbientStatusCapsule(
    ambient: AmbientType,
    volume: Float,
    onClick: () -> Unit
) {
    val haptic = rememberHapticController()
    val volPercent = (volume * 100).toInt()

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ambient.accentColor.copy(alpha = 0.18f))
            .border(0.75.dp, ambient.accentColor.copy(alpha = 0.45f), CircleShape)
            .clickable {
                haptic.tap()
                onClick()
            }
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Small pulsing dot
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .background(ambient.accentColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${ambient.displayName.split(" ").first()} $volPercent%",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = ambient.accentColor,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}



/**
 * Animated micro equalizer bars overlay for the artwork squircle.
 */
@Composable
private fun MiniWaveBarsOverlay(
    modifier: Modifier = Modifier,
    color: Color = GoldLight
) {
    val transition = rememberInfiniteTransition(label = "mini_wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mini_wave_phase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barCount = 3
        val barWidth = w / 5f
        val gap = (w - (barWidth * barCount)) / (barCount - 1)

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap)
            val factor = sin(phase + i * 1.2f) * 0.5f + 0.5f
            val barH = (h * (0.35f + 0.65f * factor)).coerceAtLeast(2.dp.toPx())
            val top = h - barH

            drawRoundRect(
                color = color.copy(alpha = 0.9f),
                topLeft = Offset(x, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
