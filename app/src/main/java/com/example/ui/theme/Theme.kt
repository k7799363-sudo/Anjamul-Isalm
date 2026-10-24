package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Locked ColorOS 17 Crystal-Glass Color Scheme for NurAnjam.
 *
 * - Strictly ignores `isSystemInDarkTheme()` so Android's automatic Dark/Light mode
 *   never turns translucent glass surfaces pitch-black or washed-out white.
 * - Strictly disables Material You Dynamic Colors (`dynamicColor = false`) so the
 *   system never pulls muddy wallpaper colors.
 */
private val NurAnjamLockedGlassColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = Color(0xFF030D0A),
    primaryContainer = Color(0x1A5DEBB3),
    onPrimaryContainer = EmeraldMintSubtle,
    secondary = GoldPrimary,
    onSecondary = Color(0xFF030D0A),
    secondaryContainer = Color(0x1FD4AF37),
    onSecondaryContainer = GoldLight,
    tertiary = AccentCyan,
    background = Color(0xFF030D0A),          // Deep crystal-emerald abyss (never pitch black #000000)
    onBackground = Color(0xFFF2F6F4),        // Crisp frosty white typography
    surface = Color(0x0DFFFFFF),             // 5% Frosted White translucent glass surface
    onSurface = Color(0xFFF2F6F4),           // Crisp frosty white
    surfaceVariant = Color(0x1AFFFFFF),      // 10% Frosted White elevated glass layer
    onSurfaceVariant = Color(0xFFB8C9C2),    // Soft frosted silver-mint secondary text
    outline = Color(0x33FFFFFF),             // 20% Specular white crystal border
    outlineVariant = Color(0x1AFFFFFF)
)

@Composable
fun NurAnjamTheme(
    // Forced false: prevents Android 12+ Material You from overriding glass palette with wallpaper colors
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Always lock to NurAnjamLockedGlassColorScheme regardless of system Dark/Light mode or dynamicColor flag
    val lockedColorScheme = if (dynamicColor) {
        NurAnjamLockedGlassColorScheme
    } else {
        NurAnjamLockedGlassColorScheme
    }

    MaterialTheme(
        colorScheme = lockedColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NurAnjamTheme(
        dynamicColor = false,
        content = content
    )
}
