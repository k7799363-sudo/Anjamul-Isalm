package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GoldPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Global Physics & Animation Specifications for NurAnjam.
 * Dialed in for 120Hz-ready buttery smooth fluidity and organic tactile feedback.
 */
object NurAnjamPhysics {
    // 1. Fluid Screen & Sheet Navigation Spring (stiffness: 380, damping: 0.82)
    val FluidNavigationSpring = spring<Float>(
        dampingRatio = 0.82f,
        stiffness = 380f
    )

    val FluidOffsetSpring = spring<IntOffset>(
        dampingRatio = 0.82f,
        stiffness = 380f
    )

    // 2. Tactile Button & Icon Press Spring (stiffness: 420, damping: 0.72)
    val TactilePressSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 420f
    )

    // 3. Elastic Card & Tile Snap Spring (stiffness: 340, damping: 0.76)
    val ElasticCardSpring = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 340f
    )

    // 4. Snappy Slider & Scrubber Scrub Spring (stiffness: 650, damping: 0.90)
    val SnappyScrubberSpring = spring<Float>(
        dampingRatio = 0.90f,
        stiffness = 650f
    )

    // 5. Gentle Ambient Glow & Waveform Pulse Spring (stiffness: 180, damping: 0.85)
    val GentleAuraSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 180f
    )

    // 6. ColorOS 17 Aquamorphic Water-Surface Bounce Spring (Battery-Saver Resistant)
    val AquamorphicBounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    // 7. Battery-Saver Resistant Glass Spring (dampingRatio: NoBouncy, stiffness: StiffnessLow)
    val BatterySafeGlassSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}

/**
 * 120Hz RenderThread-Optimized ColorOS 17 Aquamorphic Tactile Press Modifier.
 *
 * - Uses explicit spring physics (`spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)`)
 *   so animations maintain full depth and responsiveness even when Battery Saver is active.
 * - Zero hardware `shadowElevation` or `shadow()` modifiers: prevents Android from casting a muddy
 *   black drop-shadow halo behind translucent glass tiles.
 */
fun Modifier.tactilePress(
    scaleDown: Float = 0.95f,
    accentGlow: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    hapticFeedback: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    isGlassTile: Boolean = false
): Modifier = composed {
    val haptic = rememberHapticController()
    val scope = rememberCoroutineScope()

    val internalSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by internalSource.collectIsPressedAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val scaleAnim by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aquamorphicScale"
    )
    val translationYAnim by animateFloatAsState(
        targetValue = if (isPressed) 1.5f * density else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "aquamorphicTranslationY"
    )

    this
        .graphicsLayer {
            scaleX = scaleAnim
            scaleY = scaleAnim
            translationY = translationYAnim
        }
        .then(
            if (isGlassTile) {
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp)
                    )
            } else Modifier
        )
        .pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onPress = { offset ->
                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                    scope.launch { internalSource.emit(press) }
                    try {
                        tryAwaitRelease()
                        scope.launch { internalSource.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press)) }
                    } catch (c: Exception) {
                        scope.launch { internalSource.emit(androidx.compose.foundation.interaction.PressInteraction.Cancel(press)) }
                    }
                },
                onTap = {
                    if (hapticFeedback) {
                        haptic.tap()
                    }
                    onClick?.invoke()
                },
                onLongPress = {
                    if (hapticFeedback) {
                        haptic.confirm()
                    }
                    onLongClick?.invoke()
                }
            )
        }
}

/**
 * Advanced Physics-Driven Drag-To-Dismiss Interactive Container for Now Playing Screen.
 *
 * Tracks finger drag vertically in real-time without latency.
 * Applies progressive friction resistance upwards and momentum flinging downwards.
 */
@Composable
fun InteractiveDragToDismissContainer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissThresholdDp: Float = 140f,
    content: @Composable BoxScope.(dragProgress: Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticController()
    val offsetY = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val dragProgress = (offsetY.value / (600f * density)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
            .graphicsLayer {
                val progress = dragProgress
                scaleX = 1f - progress * 0.10f
                scaleY = 1f - progress * 0.10f
                alpha = 1f - progress * 0.35f
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        val current = offsetY.value
                        val newOffset = if (current + dragAmount < 0) {
                            // Elastic rubber-banding upward resistance
                            current + dragAmount * 0.22f
                        } else {
                            current + dragAmount
                        }

                        scope.launch {
                            offsetY.snapTo(newOffset)
                        }
                    },
                    onDragEnd = {
                        val velocity = velocityTracker.calculateVelocity().y
                        val shouldDismiss = offsetY.value > dismissThresholdDp * density || velocity > 1200f

                        scope.launch {
                            if (shouldDismiss) {
                                offsetY.animateTo(
                                    targetValue = 2400f,
                                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                                )
                                onDismiss()
                            } else {
                                offsetY.animateTo(
                                    targetValue = 0f,
                                    animationSpec = NurAnjamPhysics.FluidNavigationSpring
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            offsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = NurAnjamPhysics.FluidNavigationSpring
                            )
                        }
                    }
                )
            }
    ) {
        content(dragProgress)
    }
}
