package com.example.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Controller for touch feedback.
 *
 * Manual Vibrator / VibrationEffect invocations have been removed to eliminate
 * aggressive vibrations during swipe gestures, dragging, or UI state transitions,
 * and to prevent double-vibrations with the Android OS native touch response.
 *
 * All standard UI controls (buttons, clickables) rely on native Android system
 * touch feedback settings rather than forced manual motor vibrations.
 */
class HapticController(private val context: Context) {

    /** Silenced to prevent double-vibrations with the system's native click response. */
    fun tap() {
        // No-op: standard Compose components (onClick, clickable) honor system haptics
    }

    /** Silenced: swipe gestures, scrubbing, and drags do not trigger manual vibrations. */
    fun tick() {
        // No-op: swipe and drag gestures must not vibrate
    }

    /** Silenced: UI state transitions do not force manual buzzes. */
    fun confirm() {
        // No-op
    }
}

@Composable
fun rememberHapticController(): HapticController {
    val context = LocalContext.current
    return remember(context) { HapticController(context) }
}
