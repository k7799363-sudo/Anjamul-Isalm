package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Interactive Radial Sleep Timer with strict "Hard Stop" logic.
 * Features a clock-style circular dial (radial slider) supporting 1 to 120 minutes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadialSleepTimerSheet(
    isActive: Boolean,
    remainingSeconds: Int?,
    onSetTimer: (minutes: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val isDark = true // Locked glassmorphic theme
    val haptic = rememberHapticController()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Current selected minute: defaults to remaining time if active, else 30 mins
    val initialMinutes = if (isActive && remainingSeconds != null && remainingSeconds > 0) {
        ((remainingSeconds + 59) / 60).coerceIn(1, 120)
    } else {
        30
    }

    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF04140F),
        scrimColor = Color.Black.copy(alpha = 0.75f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(GoldPrimary.copy(alpha = 0.25f), EmeraldMint.copy(alpha = 0.15f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = "Sleep Timer",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Radial Sleep Timer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Clock dial • Strict Hard Stop",
                            fontSize = 12.sp,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.tap()
                        onDismissRequest()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Clock-Style Circular Radial Dial Slider
            RadialDialSlider(
                selectedMinutes = selectedMinutes,
                onMinutesChanged = { newMins ->
                    if (newMins != selectedMinutes) {
                        haptic.tick()
                        selectedMinutes = newMins
                    }
                },
                isActive = isActive,
                remainingSeconds = remainingSeconds
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Step adjustment buttons: [- 5m] [- 1m] [+ 1m] [+ 5m]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepButton(
                    label = "-5m",
                    onClick = {
                        haptic.tap()
                        selectedMinutes = (selectedMinutes - 5).coerceAtLeast(1)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                StepButton(
                    label = "-1m",
                    onClick = {
                        haptic.tap()
                        selectedMinutes = (selectedMinutes - 1).coerceAtLeast(1)
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                StepButton(
                    label = "+1m",
                    onClick = {
                        haptic.tap()
                        selectedMinutes = (selectedMinutes + 1).coerceAtMost(120)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                StepButton(
                    label = "+5m",
                    onClick = {
                        haptic.tap()
                        selectedMinutes = (selectedMinutes + 5).coerceAtMost(120)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Preset Chips (15m, 30m, 45m, 60m, 90m)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(15, 30, 45, 60, 90).forEach { preset ->
                    val isPresetSelected = selectedMinutes == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPresetSelected) GoldPrimary.copy(alpha = 0.25f)
                                else if (isDark) Color(0x18FFFFFF) else Color(0x10000000)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPresetSelected) GoldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tactilePress(
                                scaleDown = 0.92f,
                                accentGlow = GoldLight,
                                onClick = {
                                    haptic.confirm()
                                    selectedMinutes = preset
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("preset_chip_${preset}m")
                    ) {
                        Text(
                            text = "${preset}m",
                            fontSize = 12.5.sp,
                            fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPresetSelected) {
                                if (isDark) GoldLight else GoldPrimary
                            } else {
                                MaterialTheme.colorScheme.onBackground
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Strict Hard Stop Notice Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) Color(0x22143B30) else Color(0xFFE8F5E9)
                    )
                    .border(
                        width = 1.dp,
                        color = EmeraldMint.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        tint = EmeraldMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instant Hard Stop: Playback immediately cuts off at 0:00 (no fade-out)",
                        fontSize = 11.5.sp,
                        color = if (isDark) Color(0xEEFFFFFF) else Color(0xFF0F3228),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Start Timer / Turn Off Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isActive) {
                    OutlinedButton(
                        onClick = {
                            haptic.confirm()
                            onCancelTimer()
                            onDismissRequest()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_sleep_timer_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF5350)
                        )
                    ) {
                        Text(
                            text = "Turn Off",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        haptic.confirm()
                        onSetTimer(selectedMinutes)
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .weight(if (isActive) 1.4f else 1f)
                        .height(48.dp)
                        .testTag("set_sleep_timer_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color(0xFF0C1F17)
                    )
                ) {
                    Text(
                        text = if (isActive) "Update ($selectedMinutes min)" else "Start ($selectedMinutes min)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    onClick: () -> Unit
) {
    val isDark = true
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0x20FFFFFF) else Color(0x14000000))
            .border(
                width = 1.dp,
                color = if (isDark) Color(0x30FFFFFF) else Color(0x20000000),
                shape = RoundedCornerShape(10.dp)
            )
            .tactilePress(
                scaleDown = 0.90f,
                accentGlow = GoldLight,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("step_button_$label")
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Custom Interactive Clock-Style Radial Dial Canvas Component
 */
@Composable
private fun RadialDialSlider(
    selectedMinutes: Int,
    onMinutesChanged: (Int) -> Unit,
    isActive: Boolean,
    remainingSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val isDark = true

    // Smooth animated sweep using Battery-Saver resistant spring physics
    val animatedMinutes by animateFloatAsState(
        targetValue = selectedMinutes.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "RadialSliderSweep"
    )

    // Calculate projected end time
    val stopTimeStr = remember(selectedMinutes) {
        val stopTimeMs = System.currentTimeMillis() + selectedMinutes * 60 * 1000L
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(stopTimeMs))
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("radial_dial_canvas_box"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .pointerInput(Unit) {
                    fun updateAngle(touchPos: Offset) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = touchPos.x - centerX
                        val dy = touchPos.y - centerY

                        // Angle in radians with 0 at 12 o'clock (-PI/2)
                        var angleRad = atan2(dy, dx) + (PI / 2).toFloat()
                        if (angleRad < 0) {
                            angleRad += (2 * PI).toFloat()
                        }

                        // Map angle 0..2*PI to 1..120 minutes
                        val fraction = (angleRad / (2 * PI)).toFloat().coerceIn(0f, 1f)
                        val minute = (fraction * 120f).roundToInt().coerceIn(1, 120)
                        onMinutesChanged(minute)
                    }

                    detectTapGestures { offset ->
                        updateAngle(offset)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f
                        val dx = change.position.x - centerX
                        val dy = change.position.y - centerY

                        var angleRad = atan2(dy, dx) + (PI / 2).toFloat()
                        if (angleRad < 0) {
                            angleRad += (2 * PI).toFloat()
                        }

                        val fraction = (angleRad / (2 * PI)).toFloat().coerceIn(0f, 1f)
                        val minute = (fraction * 120f).roundToInt().coerceIn(1, 120)
                        onMinutesChanged(minute)
                    }
                }
        ) {
            val canvasSize = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 14.dp.toPx()
            val radius = (canvasSize - strokeWidth) / 2f - 10.dp.toPx()

            // 1. Draw Background Track
            drawCircle(
                color = if (isDark) Color(0x1CFFFFFF) else Color(0x18000000),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 2. Draw Clock Ticks around dial
            val totalTicks = 24 // every 5 minutes
            for (i in 0 until totalTicks) {
                val tickAngleDeg = (i * 360f / totalTicks) - 90f
                val tickAngleRad = Math.toRadians(tickAngleDeg.toDouble())
                val isMajor = i % 2 == 0 // every 10 min
                val tickLen = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                val tickInnerRadius = radius - strokeWidth / 2f - 4.dp.toPx()
                val tickOuterRadius = tickInnerRadius - tickLen

                val start = Offset(
                    x = center.x + (tickInnerRadius * cos(tickAngleRad)).toFloat(),
                    y = center.y + (tickInnerRadius * sin(tickAngleRad)).toFloat()
                )
                val end = Offset(
                    x = center.x + (tickOuterRadius * cos(tickAngleRad)).toFloat(),
                    y = center.y + (tickOuterRadius * sin(tickAngleRad)).toFloat()
                )

                val tickPassed = (i * 120 / totalTicks) <= selectedMinutes
                drawLine(
                    color = if (tickPassed) GoldPrimary.copy(alpha = 0.8f) else if (isDark) Color(0x30FFFFFF) else Color(0x30000000),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. Draw Active Glowing Arc Sweep
            val sweepFraction = (animatedMinutes / 120f).coerceIn(0.008f, 1f)
            val sweepAngle = sweepFraction * 360f

            val arcBrush = Brush.sweepGradient(
                colors = listOf(
                    GoldPrimary,
                    EmeraldMint,
                    GoldLight,
                    GoldPrimary
                ),
                center = center
            )

            drawArc(
                brush = arcBrush,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 4. Draw Thumb Knob / Golden Bead
            val thumbAngleDeg = -90f + sweepAngle
            val thumbAngleRad = Math.toRadians(thumbAngleDeg.toDouble())
            val thumbX = center.x + (radius * cos(thumbAngleRad)).toFloat()
            val thumbY = center.y + (radius * sin(thumbAngleRad)).toFloat()
            val thumbPos = Offset(thumbX, thumbY)

            // Outer Bloom Halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GoldLight.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = thumbPos,
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = thumbPos
            )

            // Inner Thumb Core
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = thumbPos
            )
            drawCircle(
                color = GoldPrimary,
                radius = 6.dp.toPx(),
                center = thumbPos
            )
        }

        // Center Clock Readout Content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isActive && remainingSeconds != null && remainingSeconds > 0) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                val formattedRemaining = String.format("%02d:%02d", mins, secs)

                Text(
                    text = "REMAINING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldMint,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = formattedRemaining,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Hard stop at 0:00",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "$selectedMinutes",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = if (selectedMinutes == 1) "MINUTE" else "MINUTES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stops at $stopTimeStr",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
