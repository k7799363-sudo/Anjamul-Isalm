package com.example.ui

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import kotlin.math.roundToInt

/**
 * Low-frequency easing function that quantizes animation progress into discrete intervals
 * (~120ms update interval over a 3600ms cycle) so snapshot state only emits distinct values
 * at a low frequency, preventing excessive frame invalidations and conserving battery.
 */
private val LowFrequencyLedPulseEasing = Easing { fraction ->
    val steps = 30f // 3600ms / 30 steps = 120ms low-frequency update interval
    val quantized = (fraction * steps).roundToInt() / steps
    FastOutSlowInEasing.transform(quantized.coerceIn(0f, 1f))
}

private val CyanLed = Color(0xFF00E5FF)
private val TurquoiseLed = Color(0xFF00B8D4)
private val MintGlow = Color(0xFF1DE9B6)
private val BadgeBgTop = Color(0xFF0D2821)
private val BadgeBgMid = Color(0xFF051410)
private val BadgeBgBottom = Color(0xFF020A07)

/**
 * Official NurAnjam Main App Logo with Battery-Efficient Animated Cyan & Turquoise LED Glow Effect.
 *
 * Performance & Battery Architecture:
 * - Uses [rememberInfiniteTransition] with [LowFrequencyLedPulseEasing] (3600ms cycle quantized
 *   to low-frequency intervals) so the pulsing effect updates at a low frequency.
 * - Defers all [State.value] reads strictly to the draw phase inside [Modifier.drawWithCache],
 *   ensuring zero recompositions of this composable or its parent hierarchy.
 * - Pre-computes and caches all [Path] and [Brush] objects in [Modifier.drawWithCache] so zero
 *   allocations occur during animation frames.
 * - Pauses automatically when the activity leaves [Lifecycle.State.RESUMED] or when system
 *   reduced-motion is enabled.
 */
@Composable
fun NurAnjamLogoWithLedGlow(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    isAnimated: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Respect system reduced-motion accessibility setting
    val reduceMotion = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Pause animation when app is in background to save battery
    var isResumed by remember {
        mutableStateOf(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val shouldAnimate = isAnimated && isResumed && !reduceMotion

    // Hold State<Float> without reading .value in composition scope -> 0 recompositions
    val pulseGlowState: State<Float>
    val haloScaleState: State<Float>

    if (shouldAnimate) {
        val infiniteTransition = rememberInfiniteTransition(label = "LedGlowTransition")

        pulseGlowState = infiniteTransition.animateFloat(
            initialValue = 0.52f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 3600,
                    easing = LowFrequencyLedPulseEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LedPulseLowFreq"
        )

        haloScaleState = infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 3600,
                    easing = LowFrequencyLedPulseEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "LedHaloLowFreq"
        )
    } else {
        pulseGlowState = remember { mutableFloatStateOf(0.8f) }
        haloScaleState = remember { mutableFloatStateOf(1.0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val minDim = minOf(w, h)
                val cx = w / 2f
                val cy = h / 2f
                val baseHaloRadius = (minDim / 2f) * 1.32f
                val outerCorner = minDim * 0.28f
                val outerStrokeWidth = (minDim * 0.035f).coerceAtLeast(1.5f)
                val outerInset = outerStrokeWidth * 0.8f

                onDrawBehind {
                    // Read animated state strictly inside DrawScope -> zero recompositions
                    val effectiveGlow = pulseGlowState.value
                    val haloScale = haloScaleState.value
                    val currentHaloRadius = (baseHaloRadius * haloScale).coerceAtLeast(1f)

                    // 1. Surrounding Cyan & Turquoise LED light halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CyanLed.copy(alpha = 0.36f * effectiveGlow),
                                TurquoiseLed.copy(alpha = 0.22f * effectiveGlow),
                                MintGlow.copy(alpha = 0.09f * effectiveGlow),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = currentHaloRadius
                        ),
                        radius = currentHaloRadius,
                        center = Offset(cx, cy)
                    )

                    // 2. Soft outer neon edge glow ring
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                CyanLed.copy(alpha = 0.82f * effectiveGlow),
                                TurquoiseLed.copy(alpha = 0.62f * effectiveGlow),
                                MintGlow.copy(alpha = 0.74f * effectiveGlow)
                            )
                        ),
                        topLeft = Offset(-outerInset, -outerInset),
                        size = Size(w + outerInset * 2f, h + outerInset * 2f),
                        cornerRadius = CornerRadius(outerCorner, outerCorner),
                        style = Stroke(width = outerStrokeWidth)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Static Logo Artwork Layer: cached paths & gradients, never re-allocated on LED ticks
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val minDim = minOf(w, h)
                    val cornerR = minDim * 0.25f
                    val innerCornerR = minDim * 0.24f
                    val borderStroke = (minDim * 0.014f).coerceAtLeast(1f)

                    val clipRoundedPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(0f, 0f, w, h),
                                cornerRadius = CornerRadius(cornerR, cornerR)
                            )
                        )
                    }

                    val bgBrush = Brush.verticalGradient(
                        colors = listOf(BadgeBgTop, BadgeBgMid, BadgeBgBottom)
                    )
                    val glossBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.24f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = h * 0.36f
                    )
                    val rimBrush = Brush.linearGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.7f),
                            CyanLed.copy(alpha = 0.5f),
                            GoldLight.copy(alpha = 0.4f)
                        )
                    )

                    val pad = minDim * 0.13f
                    val innerW = w - pad * 2f
                    val innerH = h - pad * 2f
                    val cx = w / 2f
                    val cy = h / 2f
                    val radius = minOf(innerW, innerH) * 0.44f

                    // 1. Headphones Arch
                    val headphonePath = Path().apply {
                        addArc(
                            oval = Rect(
                                cx - radius * 0.72f,
                                cy - radius * 0.88f,
                                cx + radius * 0.72f,
                                cy + radius * 0.12f
                            ),
                            startAngleDegrees = 190f,
                            sweepAngleDegrees = 160f
                        )
                    }
                    val headphoneStrokeWidth = (minDim * 0.032f).coerceAtLeast(1.5f)
                    val goldBrush = Brush.linearGradient(listOf(GoldLight, GoldPrimary))
                    val ledVerticalBrush = Brush.verticalGradient(listOf(CyanLed, TurquoiseLed))
                    val ledLinearBrush = Brush.linearGradient(listOf(CyanLed, TurquoiseLed))
                    val playBrush = Brush.linearGradient(listOf(Color.White, MintGlow))

                    val earcupW = minDim * 0.085f
                    val earcupH = minDim * 0.17f
                    val earcupCorner = CornerRadius(earcupW / 2f, earcupW / 2f)
                    val leftEarcupOffset = Offset(cx - radius * 0.78f, cy - radius * 0.08f)
                    val rightEarcupOffset = Offset(cx + radius * 0.78f - earcupW, cy - radius * 0.08f)

                    // 2. Sacred Crescent Moon
                    val crescentPath = Path().apply {
                        moveTo(cx + radius * 0.15f, cy - radius * 0.6f)
                        cubicTo(
                            cx - radius * 0.5f, cy - radius * 0.6f,
                            cx - radius * 0.6f, cy + radius * 0.6f,
                            cx + radius * 0.15f, cy + radius * 0.7f
                        )
                        cubicTo(
                            cx - radius * 0.25f, cy + radius * 0.4f,
                            cx - radius * 0.3f, cy - radius * 0.3f,
                            cx + radius * 0.15f, cy - radius * 0.6f
                        )
                        close()
                    }

                    // 3. Audio Equalizer Bars
                    val barWidth = (minDim * 0.026f).coerceAtLeast(1.2f)
                    val barSpacing = (minDim * 0.034f).coerceAtLeast(1.5f)
                    val barRatios = floatArrayOf(0.32f, 0.62f, 0.86f, 0.52f)
                    val totalBarsWidth = barRatios.size * barWidth + (barRatios.size - 1) * barSpacing
                    val barsStartX = cx - totalBarsWidth / 2f
                    val barCorner = CornerRadius(barWidth / 2f, barWidth / 2f)

                    // 4. Play Symbol
                    val playPath = Path().apply {
                        val pLeft = cx - radius * 0.08f
                        val pTop = cy - radius * 0.18f
                        val pBottom = cy + radius * 0.18f
                        val pRight = cx + radius * 0.24f
                        moveTo(pLeft, pTop)
                        lineTo(pRight, cy)
                        lineTo(pLeft, pBottom)
                        close()
                    }

                    onDrawBehind {
                        clipPath(clipRoundedPath) {
                            // Glossy 3D dark emerald badge background
                            drawRect(brush = bgBrush)
                            // Specular top highlight
                            drawRect(brush = glossBrush)
                        }

                        // Inner rim gold/cyan border
                        drawRoundRect(
                            brush = rimBrush,
                            topLeft = Offset(borderStroke / 2f, borderStroke / 2f),
                            size = Size(w - borderStroke, h - borderStroke),
                            cornerRadius = CornerRadius(innerCornerR, innerCornerR),
                            style = Stroke(width = borderStroke)
                        )

                        // Headphones arch & earcups
                        drawPath(
                            path = headphonePath,
                            brush = goldBrush,
                            style = Stroke(width = headphoneStrokeWidth, cap = StrokeCap.Round)
                        )
                        drawRoundRect(
                            brush = ledLinearBrush,
                            topLeft = leftEarcupOffset,
                            size = Size(earcupW, earcupH),
                            cornerRadius = earcupCorner
                        )
                        drawRoundRect(
                            brush = ledLinearBrush,
                            topLeft = rightEarcupOffset,
                            size = Size(earcupW, earcupH),
                            cornerRadius = earcupCorner
                        )

                        // Crescent moon
                        drawPath(
                            path = crescentPath,
                            brush = goldBrush
                        )

                        // Audio equalizer bars
                        for (i in barRatios.indices) {
                            val bh = radius * 0.58f * barRatios[i]
                            val bx = barsStartX + i * (barWidth + barSpacing)
                            val by = cy + radius * 0.22f - bh
                            drawRoundRect(
                                brush = ledVerticalBrush,
                                topLeft = Offset(bx, by),
                                size = Size(barWidth, bh),
                                cornerRadius = barCorner
                            )
                        }

                        // Play symbol
                        drawPath(
                            path = playPath,
                            brush = playBrush
                        )
                    }
                }
        )
    }
}
