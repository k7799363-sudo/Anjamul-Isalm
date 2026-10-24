package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldDeepDark
import com.example.ui.theme.GoldPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ColorOS 17 "Aquamorphic" Glass Card Component (120 FPS Zero-Freeze Architecture).
 *
 * - For scrolling lists (`enableStaticBlur = false`): Uses high-performance translucent tint (`#1AFFFFFF`)
 *   and a thin specular border (`#33FFFFFF`) without per-frame RenderNode BlurEffect overhead.
 * - For static surfaces (`enableStaticBlur = true`): Applies hardware-accelerated `BlurEffect` inside
 *   `Modifier.graphicsLayer` on Android 12+ (API 31+).
 * - Replaces default Material ripples with ColorOS-style water-surface spring physics
 *   (`spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)`).
 */
@Composable
fun AquamorphicGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0x1AFFFFFF),
    borderColor: Color = Color(0x33FFFFFF),
    borderWidth: Dp = 1.dp,
    enableStaticBlur: Boolean = false,
    blurRadiusDp: Dp = 24.dp,
    scaleDown: Float = 0.96f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticController()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) scaleDown else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aquamorphic_card_scale"
    )

    val bgAlphaBoost by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.08f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aquamorphic_card_bg"
    )

    val bgBrush = remember(backgroundColor, bgAlphaBoost) {
        Brush.linearGradient(
            colors = listOf(
                backgroundColor.copy(alpha = (backgroundColor.alpha + bgAlphaBoost).coerceIn(0f, 1f)),
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.45f + bgAlphaBoost).coerceIn(0f, 1f))
            ),
            start = Offset.Zero,
            end = Offset.Infinite
        )
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scaleAnim
                scaleY = scaleAnim
                if (enableStaticBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val radiusPx = blurRadiusDp.toPx()
                    if (radiusPx > 0f) {
                        renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Decal)
                    }
                }
            }
            .clip(shape)
            .background(bgBrush)
            .border(borderWidth, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(
                            onPress = { offset ->
                                val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                                scope.launch { interactionSource.emit(press) }
                                try {
                                    tryAwaitRelease()
                                    scope.launch {
                                        interactionSource.emit(
                                            androidx.compose.foundation.interaction.PressInteraction.Release(press)
                                        )
                                    }
                                } catch (_: Exception) {
                                    scope.launch {
                                        interactionSource.emit(
                                            androidx.compose.foundation.interaction.PressInteraction.Cancel(press)
                                        )
                                    }
                                }
                            },
                            onTap = {
                                haptic.tap()
                                onClick()
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        content()
    }
}

/**
 * Reusable frosted glass card with translucent gradients and subtle gold/emerald borders.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = true // Locked glassmorphic theme
    val defaultBg = Color(0x0DFFFFFF) // 5% Frosted White
    val defaultBorder = Color(0x33FFFFFF) // 20% Specular White

    val bgBrush = Brush.linearGradient(
        colors = listOf(
            backgroundColor ?: defaultBg,
            (backgroundColor ?: defaultBg).copy(alpha = if (isDark) 0.06f else 0.55f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgBrush)
            .border(borderWidth, borderColor ?: defaultBorder, shape)
    ) {
        content()
    }
}

/**
 * Glassmorphic outlined pill button with ColorOS 17 water-surface spring animation.
 */
@Composable
fun GlassOutlinedPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderColor: Color = Color(0x33FFFFFF),
    backgroundColor: Color = Color(0x1AFFFFFF),
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pill_scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Aquamorphic Breathing Mesh Background.
 * Executes continuous mesh phase calculation on `Dispatchers.Default` to keep `Dispatchers.Main`
 * completely free for 120 FPS touch and scroll responsiveness.
 */
@Composable
fun IslamicSacredBackground(
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var meshPhase by remember { mutableFloatStateOf(0f) }

    // Run slow-moving breathing mesh phase computation off the main thread on Dispatchers.Default
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val startTime = System.nanoTime()
            while (isActive) {
                val elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000f
                // Slow 14-second breathing cycle
                meshPhase = (elapsedSec * (2f * PI.toFloat() / 14f)) % (2f * PI.toFloat())
                delay(64L) // ~15fps ambient mesh drift: imperceptibly smooth for radial gradients with minimal recomposition
            }
        }
    }

    val baseGradient = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF010605),
                Color(0xFF030D0A),
                Color(0xFF04140F),
                Color(0xFF010504)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF3F7F5),
                Color(0xFFE5EFEA),
                Color(0xFFDFEBE5),
                Color(0xFFEFF5F2)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseGradient)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val phase = meshPhase
            val driftX = cos(phase) * 0.08f
            val driftY = sin(phase) * 0.06f
            val pulseAlpha = 0.13f + 0.03f * sin(phase)

            // Multi-Point Breathing Mesh Point 1 (Warm Sacred Gold Aura)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isDark) GoldPrimary else Color(0xFFD4AF37)).copy(alpha = pulseAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.82f + driftX), h * (0.16f + driftY)),
                    radius = w * 0.72f
                )
            )

            // Multi-Point Breathing Mesh Point 2 (Aquamorphic Emerald Aura)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isDark) Color(0xFF5DEBB3) else Color(0xFF147A5A)).copy(alpha = pulseAlpha * 0.85f),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.18f - driftX), h * (0.78f - driftY)),
                    radius = w * 0.78f
                )
            )

            // Multi-Point Breathing Mesh Point 3 (Deep Teal Water Caustic Center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isDark) Color(0xFF0F4C3A) else Color(0xFF80CBC4)).copy(alpha = pulseAlpha * 0.70f),
                        Color.Transparent
                    ),
                    center = Offset(w * (0.50f + driftY), h * (0.48f - driftX)),
                    radius = w * 0.65f
                )
            )

            // Draw Rub el Hizb (Sacred 8-pointed star) subtle geometry
            val starCenter = Offset(w * 0.5f, h * 0.22f)
            val starRadius = w * 0.28f
            val starStroke = Stroke(width = 1.2f)
            val starColor = (if (isDark) GoldPrimary else Color(0xFF0A5C44)).copy(alpha = 0.09f)

            drawSacredOctagram(
                center = starCenter,
                radius = starRadius,
                rotationDegrees = 0f,
                color = starColor,
                stroke = starStroke
            )
        }

        content()
    }
}

/**
 * Draws an 8-pointed Islamic Star (Rub el Hizb)
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSacredOctagram(
    center: Offset,
    radius: Float,
    rotationDegrees: Float,
    color: Color,
    stroke: Stroke
) {
    val rotRad = (rotationDegrees * PI / 180.0).toFloat()

    // First Square
    val path1 = Path()
    for (i in 0..4) {
        val angle = rotRad + (i * PI / 2.0).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path1.moveTo(x, y) else path1.lineTo(x, y)
    }
    drawPath(path1, color = color, style = stroke)

    // Second Square rotated 45 degrees
    val path2 = Path()
    val offsetRad = (PI / 4.0).toFloat()
    for (i in 0..4) {
        val angle = rotRad + offsetRad + (i * PI / 2.0).toFloat()
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
    }
    drawPath(path2, color = color, style = stroke)

    // Inner sacred ring
    drawCircle(
        color = color.copy(alpha = color.alpha * 0.8f),
        radius = radius * 0.65f,
        center = center,
        style = stroke
    )
}

/**
 * Animated audio waveform visualizer bars
 */
@Composable
fun AudioVisualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    activeColor: Color = GoldPrimary
) {
    val transition = if (isPlaying) rememberInfiniteTransition(label = "waveform_anim") else null
    val phase by if (transition != null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val totalSpacing = w * 0.3f
        val barWidth = (w - totalSpacing) / barCount
        val gap = totalSpacing / (barCount - 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap)
            val wave = if (isPlaying) {
                val factor = sin(phase + i * 0.6f) * 0.5f + 0.5f
                0.2f + 0.8f * factor
            } else {
                0.15f
            }
            val barHeight = (h * wave).coerceAtLeast(3.dp.toPx())
            val top = (h - barHeight) / 2f

            drawRoundRect(
                color = activeColor.copy(alpha = if (isPlaying) 0.85f else 0.35f),
                topLeft = Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Highly tactile 3D "Crystal Glass" Icon Button.
 * Features:
 * 1. Deep polished crystal glassmorphism with refraction
 * 2. Delicate semi-transparent specular border simulating cut glass facets
 * 3. Soft internal refractive glow catching light inside the crystal
 * 4. Multi-layered tinted floating drop shadow (never harsh black)
 * 5. Diagonal specular light sweep across top-left edge
 * 6. Tactile interactive spring scale on press
 */
@Composable
fun CrystalGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
    shape: Shape = CircleShape,
    accentGlow: Color = GoldPrimary,
    tintColor: Color = Color.White,
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "crystal_scale"
    )

    // Tinted multi-layer shadow colors
    val shadowTint = if (isDark) {
        Color(0x66020C08) // Tinted deep emerald-black
    } else {
        Color(0x350A5C44) // Soft diffused emerald-slate
    }

    Box(
        modifier = modifier
            .scale(animatedScale)
            .size(buttonSize)
            .drawBehind {
                val w = size.width
                val h = size.height

                // Layer 1: Concentric soft ambient floating shadow
                drawCircle(
                    color = shadowTint.copy(alpha = 0.30f),
                    radius = w * 0.50f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )

                // Layer 2: Refractive colored caustics glow centered behind the button
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = w * 0.52f
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = w * 0.52f
                )
            }
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // 3D Crystal Body Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Crystal Substrate (Frosted glass refraction with translucent gradient)
            val substrateColors = if (isDark) {
                listOf(
                    Color(0x4DFFFFFF), // Crisp translucent top
                    Color(0x1AFFFFFF), // Middle body
                    Color(0x0A000000), // Deep crystal base
                    accentGlow.copy(alpha = 0.15f) // Subtle internal prism refraction
                )
            } else {
                listOf(
                    Color(0xEBFFFFFF),
                    Color(0xB3FFFFFF),
                    Color(0x80F0F7F4),
                    accentGlow.copy(alpha = 0.18f)
                )
            }

            drawCircle(
                brush = Brush.linearGradient(
                    colors = substrateColors,
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            )

            // 2. Soft Inner Refractive Glow (mimics internal light scattering)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accentGlow.copy(alpha = if (isDark) 0.35f else 0.25f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f, h * 0.65f),
                    radius = w * 0.45f
                )
            )

            // 3. Diagonal Specular Light Sweep (Glossy top-left reflective highlight)
            val highlightBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xE6FFFFFF), // Intense specular white rim
                    Color(0x66FFFFFF),
                    Color(0x10FFFFFF),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w * 0.70f, h * 0.70f)
            )

            // Subtle crescent-shaped top sheen
            drawCircle(
                brush = highlightBrush,
                radius = w * 0.47f,
                center = Offset(w * 0.46f, h * 0.44f),
                style = Stroke(width = 2.5f)
            )

            // 4. Polished Cut-Glass Beveled Border (Multi-stop reflective rim)
            val borderBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xF0FFFFFF), // 95% White at top-left specular point
                    Color(0x80FFFFFF),
                    accentGlow.copy(alpha = 0.65f), // Gold/Emerald reflection along curve
                    Color(0x30FFFFFF),
                    Color(0x15FFFFFF)  // Soft dark refraction at bottom-right
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )

            drawCircle(
                brush = borderBrush,
                radius = (w / 2f) - 1.2f,
                style = Stroke(width = 1.8f)
            )
        }

        // Inner Icon / Content
        content()
    }
}

/**
 * ColorOS 17 Frosted Crystal Glass Play/Pause Button.
 * Reusable across full-screen Now Playing and persistent bottom mini-player.
 *
 * Strict Architectural Principles:
 * 1. Zero solid grey / cheap elevation.
 * 2. Perfect circular shape: Modifier.clip(CircleShape).
 * 3. Frosted glass layer: Modifier.background(Color.White.copy(alpha = 0.15f)).
 * 4. Specular glass reflection edge: Modifier.border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape).
 * 5. Interactive spring physics via MutableInteractionSource:
 *    - Pressed: 0.85f, Released: 1.0f
 *    - spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)
 *    - Modifier.scale(scale)
 * 6. 3D embossed icon pop:
 *    - Crisp Color.White Play/Pause vector icons
 *    - Soft, tight shadow directly on the icon itself (graphicsLayer / shadow) inside the glass casing
 */
@Composable
fun GlassPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    iconSize: Dp = (size.value * 0.52f).dp,
    testTag: String? = null
) {
    val haptic = rememberHapticController()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "glass_play_pause_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.tap()
                    onClick()
                }
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
        val contentDesc = if (isPlaying) "Pause" else "Play"

        // 3D Embossed Soft Shadow Layer directly on the icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.40f),
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    translationX = 1f
                    translationY = 1.5f
                    alpha = 0.70f
                }
        )

        // Crisp White Play/Pause Vector Icon
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

