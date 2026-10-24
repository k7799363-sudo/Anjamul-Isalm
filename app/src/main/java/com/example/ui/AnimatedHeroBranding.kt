package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.EmeraldMintSubtle
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary

/**
 * Custom Animated Typography Hero Branding Element:
 * "Developed by Anjamul 💖 ↔ أنجم"
 *
 * Implements a native text gradient shader directly on the typography paint layer
 * using Jetpack Compose [TextStyle.brush] with a continuous [rememberInfiniteTransition],
 * completely eliminating bounding-box artifacts, clipping, or shimmer container glitches.
 */
@Composable
fun AnimatedHeroBranding(
    modifier: Modifier = Modifier,
    onBrandingClick: (() -> Unit)? = null
) {
    val isDark = true // Locked glassmorphic theme

    val infiniteTransition = rememberInfiniteTransition(label = "HeroBrandingAnimation")

    // Continuous serene linear gradient wave flowing through the typography letters
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerPhase"
    )

    // Breathing glow luminescence for ambient background depth
    val breathingGlow by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingGlow"
    )

    // Gentle heart pulse scale
    val heartPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HeartPulse"
    )

    // Bi-directional arrow gentle translation
    val arrowTranslation by infiniteTransition.animateFloat(
        initialValue = -2.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ArrowTranslation"
    )

    // Star sparkle pulsation
    val sparkleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SparkleAlpha"
    )

    // Native text gradient brushes applied directly to the font paint layer
    val textGradientWidth = 320f
    val shimmerOffset = shimmerPhase * textGradientWidth

    val anjamulTextBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                GoldPrimary,
                GoldLight,
                EmeraldMintSubtle,
                Color.White,
                EmeraldMint,
                GoldLight,
                GoldPrimary
            )
        } else {
            listOf(
                Color(0xFF9A7B1C),
                GoldPrimary,
                Color(0xFF0F3228),
                GoldLight,
                GoldPrimary,
                Color(0xFF9A7B1C)
            )
        },
        start = Offset(shimmerOffset - textGradientWidth * 0.5f, 0f),
        end = Offset(shimmerOffset + textGradientWidth * 0.5f, 50f)
    )

    val arabicTextBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                GoldLight,
                EmeraldMint,
                GoldPrimary,
                Color.White,
                GoldLight
            )
        } else {
            listOf(
                GoldPrimary,
                Color(0xFF0F3228),
                GoldLight,
                GoldPrimary
            )
        },
        start = Offset((1f - shimmerPhase) * textGradientWidth - textGradientWidth * 0.5f, 0f),
        end = Offset((1f - shimmerPhase) * textGradientWidth + textGradientWidth * 0.5f, 50f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_branding_header")
            .then(
                if (onBrandingClick != null) {
                    Modifier.tactilePress(
                        accentGlow = GoldLight,
                        onClick = onBrandingClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient soft bloom aura behind the hero capsule
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(58.dp)
                .graphicsLayer {
                    alpha = breathingGlow * 0.40f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = if (isDark) 0.28f else 0.16f),
                            EmeraldMint.copy(alpha = if (isDark) 0.14f else 0.08f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        )

        // Crystal Glass Branding Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = if (isDark) {
                            listOf(
                                Color(0x380F3228),
                                Color(0x400A231C),
                                Color(0x30144537)
                            )
                        } else {
                            listOf(
                                Color(0xE8FFFFFF),
                                Color(0xD8F4F7F5),
                                Color(0xE8FFFFFF)
                            )
                        }
                    )
                )
                .border(
                    width = 1.25.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GoldLight.copy(alpha = 0.70f * breathingGlow),
                            EmeraldMint.copy(alpha = 0.45f),
                            GoldPrimary.copy(alpha = 0.60f * breathingGlow),
                            Color(0x33FFFFFF)
                        ),
                        start = Offset(shimmerPhase * 500f, 0f),
                        end = Offset((shimmerPhase + 0.6f) * 500f, 150f)
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Main typography row: "✨ Developed by Anjamul 💖 ↔ أنجم ✨"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Celestial Noor Star icon
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GoldLight.copy(alpha = sparkleAlpha),
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer {
                                scaleX = sparkleAlpha
                                scaleY = sparkleAlpha
                            }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // "Developed by" text
                    Text(
                        text = "Developed by",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.4.sp,
                            color = if (isDark) Color(0xCCFFFFFF) else Color(0x990F261E)
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // "Anjamul" with continuous paint-layer linear gradient brush (Zero Bounding Box)
                    Text(
                        text = "Anjamul",
                        style = TextStyle(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            brush = anjamulTextBrush
                        )
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    // Animated 💖 Heart icon with warm harmonic pulse
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Love",
                        tint = Color(0xFFFF4D6D),
                        modifier = Modifier
                            .size(13.5.dp)
                            .scale(heartPulse)
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    // Bi-directional connector: "↔"
                    Text(
                        text = "↔",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldLight.copy(alpha = 0.95f)
                        ),
                        modifier = Modifier.graphicsLayer {
                            translationX = arrowTranslation
                        }
                    )

                    Spacer(modifier = Modifier.width(5.dp))

                    // Arabic Calligraphy: "أنجم" with native paint-layer text gradient shader
                    Text(
                        text = "أنجم",
                        style = TextStyle(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 0.6.sp,
                            brush = arabicTextBrush
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Trailing star sparkle
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldMint.copy(alpha = sparkleAlpha),
                        modifier = Modifier
                            .size(13.dp)
                            .graphicsLayer {
                                scaleX = sparkleAlpha
                                scaleY = sparkleAlpha
                            }
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Serene Islamic Sub-caption with subtle Noor dot
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "نُور أَنْجَام",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GoldPrimary.copy(alpha = 0.85f),
                            letterSpacing = 0.8.sp
                        )
                    )
                    Text(
                        text = " • ",
                        fontSize = 10.sp,
                        color = if (isDark) Color(0x66FFFFFF) else Color(0x660F261E)
                    )
                    Text(
                        text = "Sacred Audio Sanctuary",
                        style = TextStyle(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isDark) Color(0x99FFFFFF) else Color(0x880F261E),
                            letterSpacing = 0.4.sp
                        )
                    )
                }
            }
        }
    }
}
