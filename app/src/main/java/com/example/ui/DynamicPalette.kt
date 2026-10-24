package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.LruCache
import com.example.data.AudioArtworkExtractor
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.ColorUtils
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Complete Dynamic Theme generated for an individual track's album cover artwork.
 * Powered by the custom 5-Point Random Sampling & Solid Palette Snapping Algorithm.
 */
data class DynamicPlayerTheme(
    val primary: Color,             // Main dominant color derived from 5-point snapped solid color
    val secondary: Color,           // Complementary/sub-accent shade
    val accent: Color,              // Snapped solid color for glow, thumb, waveform & active states
    val background: Color,          // Deep dark base background color derived from snapped color
    val backgroundSecondary: Color, // Slightly elevated container background
    val surface: Color,             // Glassmorphic surface color with controlled opacity
    val surfaceLight: Color,        // Interactive glass surface
    val glow: Color,                // Atmospheric radial aura color for cover art halo & play button
    val textPrimary: Color,         // High contrast primary text color (guaranteed readable)
    val textSecondary: Color,       // Medium contrast secondary text color
    val border: Color,              // Glass card & active tab border color
    val progress: Color,            // Progress scrubber active track & thumb color
    val active: Color,              // Active control state highlight color
    val disabled: Color,            // Muted inactive state color
    val isDefaultTheme: Boolean = false
) {
    companion object {
        /**
         * Premium Default Theme when track has no artwork or artwork fails to decode.
         * Deep charcoal with soft cyan/silver/gold aura.
         */
        val DefaultTheme = DynamicPlayerTheme(
            primary = Color(0xFF1E293B),           // Slate Dark
            secondary = Color(0xFF0F172A),         // Charcoal
            accent = Color(0xFF3B82F6),            // Vibrant Solid Blue
            background = Color(0xFF090D16),        // Deep Obsidian
            backgroundSecondary = Color(0xFF131C2E),
            surface = Color(0x20FFFFFF),           // Frosted Glass
            surfaceLight = Color(0x35FFFFFF),
            glow = Color(0x603B82F6),              // Vibrant Blue Glow
            textPrimary = Color.White,
            textSecondary = Color(0xB3FFFFFF),
            border = Color(0x403B82F6),
            progress = Color(0xFF3B82F6),
            active = Color(0xFF3B82F6),
            disabled = Color(0x40FFFFFF),
            isDefaultTheme = true
        )
    }
}

/**
 * Hardcoded Predefined Solid Palette of 11 standard, vibrant Jetpack Compose Colors:
 * Red, Blue, Yellow, Green, Orange, Pink, Purple, Brown, Black, White, and Grey.
 */
object SolidColorPalette {
    val Red = Color(0xFFEF4444)
    val Blue = Color(0xFF3B82F6)
    val Yellow = Color(0xFFFACC15)
    val Green = Color(0xFF22C55E)
    val Orange = Color(0xFFF97316)
    val Pink = Color(0xFFEC4899)
    val Purple = Color(0xFFA855F7)
    val Brown = Color(0xFFA16207)
    val Black = Color(0xFF18181B)
    val White = Color(0xFFF8FAFC)
    val Grey = Color(0xFF94A3B8)

    val All: List<Color> = listOf(
        Red,
        Blue,
        Yellow,
        Green,
        Orange,
        Pink,
        Purple,
        Brown,
        Black,
        White,
        Grey
    )
}

/**
 * Predefined solid color list matching the exact 11 required colors:
 * Red, Blue, Yellow, Green, Orange, Pink, Purple, Brown, Black, White, and Grey.
 */
val PREDEFINED_SOLID_PALETTE: List<Color> = SolidColorPalette.All

/**
 * In-memory LRU Cache for extracted artwork themes to prevent color flickering & zero re-computation on renders.
 */
object ArtworkThemeCache {
    private val cache = LruCache<String, DynamicPlayerTheme>(40)

    fun get(trackId: String): DynamicPlayerTheme? = cache.get(trackId)
    fun put(trackId: String, theme: DynamicPlayerTheme) {
        cache.put(trackId, theme)
    }
}

/**
 * In-memory LRU Cache for generated blurred artwork background bitmaps.
 */
object BlurredArtworkCache {
    private val cache = LruCache<String, Bitmap>(30)

    fun get(trackId: String): Bitmap? = cache.get(trackId)
    fun put(trackId: String, bitmap: Bitmap) {
        cache.put(trackId, bitmap)
    }
}

/**
 * Preloads high-res artwork, theme colors, and blurred background in the background before animation opens.
 */
suspend fun preloadArtworkAndTheme(context: Context, track: AudioTrack) {
    withContext(Dispatchers.IO) {
        try {
            if (ArtworkThemeCache.get(track.id) != null && BlurredArtworkCache.get(track.id) != null) {
                return@withContext
            }
            // Warm raw uncompressed ByteArray cache for Coil Size.ORIGINAL rendering
            AudioArtworkExtractor.extractHighResArtworkModel(context, track)

            var bitmap = AudioArtworkExtractor.getCached(track.id)
                ?: AudioArtworkExtractor.getCached("${track.id}_2048x2048")
                ?: AudioArtworkExtractor.getCached("${track.id}_1080x1080")

            if (bitmap == null) {
                bitmap = AudioArtworkExtractor.getEmbeddedArtwork(context, track, 2048, 2048)
            }

            if (bitmap != null) {
                if (ArtworkThemeCache.get(track.id) == null) {
                    val theme = extractThemeFromBitmap(bitmap)
                    ArtworkThemeCache.put(track.id, theme)
                }
                if (BlurredArtworkCache.get(track.id) == null) {
                    val blurred = createBlurredArtworkBitmap(bitmap)
                    BlurredArtworkCache.put(track.id, blurred)
                }
            }
        } catch (_: Exception) {}
    }
}

/**
 * Generates a soft, heavily blurred downsampled thumbnail bitmap for fast background rendering across all API levels.
 */
fun createBlurredArtworkBitmap(source: Bitmap): Bitmap {
    val width = 48
    val height = 48
    val scaled = Bitmap.createScaledBitmap(source, width, height, true)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)

    val radius = 16
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0; var g = 0; var b = 0; var count = 0
            for (dx in -radius..radius) {
                val nx = (x + dx).coerceIn(0, width - 1)
                val color = pixels[y * width + nx]
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
                count++
            }
            pixels[y * width + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
    val finalPixels = IntArray(width * height)
    for (x in 0 until width) {
        for (y in 0 until height) {
            var r = 0; var g = 0; var b = 0; var count = 0
            for (dy in -radius..radius) {
                val ny = (y + dy).coerceIn(0, height - 1)
                val color = pixels[ny * width + x]
                r += (color shr 16) and 0xFF
                g += (color shr 8) and 0xFF
                b += color and 0xFF
                count++
            }
            finalPixels[y * width + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
        }
    }
    output.setPixels(finalPixels, 0, width, 0, 0, width, height)
    return output
}

/**
 * Composable hook to obtain a cached blurred background bitmap for [track] and [bitmap].
 */
@Composable
fun rememberBlurredArtwork(track: AudioTrack?, bitmap: Bitmap?): Bitmap? {
    if (track == null) return null
    var blurred by remember(track.id) {
        mutableStateOf(BlurredArtworkCache.get(track.id))
    }

    LaunchedEffect(track.id, bitmap) {
        val cached = BlurredArtworkCache.get(track.id)
        if (cached != null) {
            blurred = cached
            return@LaunchedEffect
        }
        val sourceBitmap = bitmap
            ?: AudioArtworkExtractor.getCached(track.id)
            ?: AudioArtworkExtractor.getCached("${track.id}_2048x2048")
        if (sourceBitmap == null) return@LaunchedEffect

        val created = withContext(Dispatchers.Default) {
            try {
                createBlurredArtworkBitmap(sourceBitmap)
            } catch (_: Exception) { null }
        }
        if (created != null) {
            BlurredArtworkCache.put(track.id, created)
            blurred = created
        }
    }
    return blurred
}

/**
 * Calculates the 3D Euclidean distance in RGB color space (0..255 per channel)
 * between an extracted ARGB integer pixel and a target Jetpack Compose [Color]:
 *
 * distance = sqrt((R1 - R2)^2 + (G1 - G2)^2 + (B1 - B2)^2)
 */
fun calculateRgbEuclideanDistance(argbPixel: Int, targetColor: Color): Double {
    val r1 = (argbPixel shr 16) and 0xFF
    val g1 = (argbPixel shr 8) and 0xFF
    val b1 = argbPixel and 0xFF

    val targetArgb = targetColor.toArgb()
    val r2 = (targetArgb shr 16) and 0xFF
    val g2 = (targetArgb shr 8) and 0xFF
    val b2 = targetArgb and 0xFF

    val dr = (r1 - r2).toDouble()
    val dg = (g1 - g2).toDouble()
    val db = (b1 - b2).toDouble()

    return sqrt(dr * dr + dg * dg + db * db)
}

/**
 * Snaps an extracted ARGB pixel color to the nearest solid color in [palette]
 * by minimizing Euclidean distance in RGB space.
 */
fun findNearestSolidPaletteColor(
    argbPixel: Int,
    palette: List<Color> = PREDEFINED_SOLID_PALETTE
): Color {
    return palette.minByOrNull { candidateColor ->
        calculateRgbEuclideanDistance(argbPixel, candidateColor)
    } ?: SolidColorPalette.Blue
}

/**
 * Cluster representing a group of visually similar colors grouped by Euclidean distance threshold.
 * Allows variations of a color family (e.g. light blue vs. dark blue) to coalesce into a single vote group.
 */
private class ColorGroup(initialPixel: Int) {
    val pixels: MutableList<Int> = mutableListOf(initialPixel)

    // Running sums to maintain accurate centroid in RGB space
    private var sumR: Long = ((initialPixel shr 16) and 0xFF).toLong()
    private var sumG: Long = ((initialPixel shr 8) and 0xFF).toLong()
    private var sumB: Long = (initialPixel and 0xFF).toLong()

    val count: Int get() = pixels.size
    val centroidR: Double get() = sumR.toDouble() / count
    val centroidG: Double get() = sumG.toDouble() / count
    val centroidB: Double get() = sumB.toDouble() / count

    fun add(pixel: Int) {
        pixels.add(pixel)
        sumR += (pixel shr 16) and 0xFF
        sumG += (pixel shr 8) and 0xFF
        sumB += pixel and 0xFF
    }

    /**
     * Calculates the 3D Euclidean distance between the candidate pixel and the current group centroid.
     */
    fun distanceTo(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val dr = r - centroidR
        val dg = g - centroidG
        val db = b - centroidB
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /**
     * Calculates the representative dominant color of this group (the centroid color).
     */
    fun representativeColor(): Color {
        val r = centroidR.roundToInt().coerceIn(0, 255)
        val g = centroidG.roundToInt().coerceIn(0, 255)
        val b = centroidB.roundToInt().coerceIn(0, 255)
        return Color(r, g, b)
    }
}

/**
 * Custom 10-Point Dominant Color Extraction Algorithm with Majority Vote (Mode Calculation).
 *
 * 1. Generates 10 random (x, y) coordinate points within the image boundaries.
 * 2. Extracts the pixel color at each of the 10 points using `bitmap.getPixel(x, y)`.
 * 3. Crucial Grouping Logic: Groups pixels using a 3D Euclidean distance threshold in RGB space (75.0)
 *    so variations within the same color family (e.g., light blue vs. dark blue) cluster into the same group.
 * 4. Frequency Count & Majority Vote: Computes the frequency (vote count) for each color cluster.
 * 5. Selects the winning color group that received the most votes (highest frequency) and returns its
 *    representative dominant centroid color.
 * 6. Executes safely on [Dispatchers.Default] so the 120Hz UI thread never drops a frame.
 */
suspend fun Bitmap.extract10PointDominantColor(
    random: Random = Random.Default
): Color = withContext(Dispatchers.Default) {
    extract10PointDominantColorSync(random)
}

/**
 * Synchronous worker for the 10-point dominant color extraction with majority vote grouping.
 * Must be invoked from a background dispatcher ([Dispatchers.Default]).
 */
fun Bitmap.extract10PointDominantColorSync(
    random: Random = Random.Default
): Color {
    if (width <= 0 || height <= 0) return SolidColorPalette.Blue

    // Hardware bitmaps on API 26+ do not support direct getPixel(x, y) reads; copy to software ARGB_8888 if needed
    val readableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false) ?: return SolidColorPalette.Blue
    } else {
        this
    }

    val w = readableBitmap.width
    val h = readableBitmap.height

    // Step 1 & 2: Sample 10 random (x, y) coordinates within boundaries and extract pixel colors
    val samplePixels = List(10) {
        val randomX = random.nextInt(w)
        val randomY = random.nextInt(h)
        readableBitmap.getPixel(randomX, randomY)
    }

    // Step 3: Crucial Grouping Logic using 3D Euclidean distance in RGB color space
    // Threshold of 75.0 groups slight tone/brightness variations within the same hue family together
    val groupingThreshold = 75.0
    val colorGroups = mutableListOf<ColorGroup>()

    for (pixel in samplePixels) {
        var closestGroup: ColorGroup? = null
        var minDistance = Double.MAX_VALUE

        for (group in colorGroups) {
            val dist = group.distanceTo(pixel)
            if (dist <= groupingThreshold && dist < minDistance) {
                minDistance = dist
                closestGroup = group
            }
        }

        if (closestGroup != null) {
            closestGroup.add(pixel)
        } else {
            colorGroups.add(ColorGroup(pixel))
        }
    }

    // Step 4 & 5: Majority Vote / Mode Calculation
    // Find the winning color group that received the most votes (highest frequency)
    val winningGroup = colorGroups.maxWithOrNull(
        compareBy<ColorGroup> { it.count }
            .thenBy { group ->
                // Tie-breaker: prefer higher saturation/vibrancy over dull neutral frames
                val r = group.centroidR / 255.0
                val g = group.centroidG / 255.0
                val b = group.centroidB / 255.0
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                if (max == 0.0) 0.0 else (max - min) / max
            }
    ) ?: return SolidColorPalette.Blue

    return winningGroup.representativeColor()
}

/**
 * Named alias for [extract10PointDominantColor].
 */
suspend fun Bitmap.extractTenPointDominantColor(
    random: Random = Random.Default
): Color = extract10PointDominantColor(random)

/**
 * Named alias for [extract10PointDominantColorSync].
 */
fun Bitmap.extractTenPointDominantColorSync(
    random: Random = Random.Default
): Color = extract10PointDominantColorSync(random)

/**
 * Custom 5-Point Random Sampling & Predefined Solid Palette Snapping Algorithm.
 * Maintained for backward compatibility.
 */
suspend fun Bitmap.extractFivePointRandomSnappedColor(
    random: Random = Random.Default
): Color = withContext(Dispatchers.Default) {
    extractFivePointRandomSnappedColorSync(random)
}

/**
 * Synchronous worker for 5-point random sampling and solid palette snapping.
 * Must be invoked from a background coroutine dispatcher ([Dispatchers.Default]).
 */
fun Bitmap.extractFivePointRandomSnappedColorSync(
    random: Random = Random.Default
): Color {
    if (width <= 0 || height <= 0) return SolidColorPalette.Blue

    // Hardware bitmaps on API 26+ do not support direct getPixel(x, y) reads; copy to software ARGB_8888 if needed
    val readableBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false) ?: return SolidColorPalette.Blue
    } else {
        this
    }

    val w = readableBitmap.width
    val h = readableBitmap.height

    // Sample 5 random (x, y) coordinates and snap each pixel to the nearest predefined solid color
    val snappedSolidColors = List(5) {
        val randomX = random.nextInt(w)
        val randomY = random.nextInt(h)
        val pixelArgb = readableBitmap.getPixel(randomX, randomY)
        findNearestSolidPaletteColor(pixelArgb, PREDEFINED_SOLID_PALETTE)
    }

    return snappedSolidColors.random(random)
}

/**
 * Backwards-compatible alias that invokes the 5-point random sampling & solid color snapping algorithm.
 */
fun Bitmap.extractFivePointBlendedColor(): Color = extractFivePointRandomSnappedColorSync()

/**
 * Extracts a complete [DynamicPlayerTheme] from [bitmap] asynchronously on [Dispatchers.Default]
 * using the 10-point dominant color majority vote algorithm.
 */
suspend fun extractThemeFromBitmap(bitmap: Bitmap): DynamicPlayerTheme {
    return withContext(Dispatchers.Default) {
        try {
            val dominantColor = bitmap.extract10PointDominantColor()
            val dominantArgb = dominantColor.toArgb()

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(dominantArgb, hsl)

            // Ensure vibrant glow & progress visibility even if dominant color is very dark
            val effectiveAccent = if (hsl[2] < 0.12f) {
                val elevatedHsl = floatArrayOf(hsl[0], hsl[1].coerceAtLeast(0.40f), 0.50f)
                Color(ColorUtils.HSLToColor(elevatedHsl))
            } else {
                dominantColor
            }

            // Derive cohesive shades from the dominant color for glassmorphic surfaces and deep backdrop
            val primaryHsl = floatArrayOf(
                hsl[0],
                hsl[1].coerceIn(0f, 0.90f),
                hsl[2].coerceIn(0.22f, 0.58f)
            )
            val secondaryHsl = floatArrayOf(
                hsl[0],
                hsl[1].coerceIn(0f, 0.85f),
                (hsl[2] * 0.65f).coerceIn(0.14f, 0.42f)
            )
            val bgHsl = floatArrayOf(
                hsl[0],
                (hsl[1] * 0.60f).coerceIn(0f, 0.55f),
                0.08f
            )
            val bgSecHsl = floatArrayOf(
                hsl[0],
                (hsl[1] * 0.65f).coerceIn(0f, 0.60f),
                0.14f
            )

            val primaryColor = Color(ColorUtils.HSLToColor(primaryHsl))
            val secondaryColor = Color(ColorUtils.HSLToColor(secondaryHsl))
            val backgroundColor = Color(ColorUtils.HSLToColor(bgHsl))
            val backgroundSecondaryColor = Color(ColorUtils.HSLToColor(bgSecHsl))

            DynamicPlayerTheme(
                primary = primaryColor,
                secondary = secondaryColor,
                accent = effectiveAccent,
                background = backgroundColor,
                backgroundSecondary = backgroundSecondaryColor,
                surface = dominantColor.copy(alpha = 0.20f),
                surfaceLight = effectiveAccent.copy(alpha = 0.28f),
                glow = effectiveAccent,
                textPrimary = Color.White,
                textSecondary = Color.White.copy(alpha = 0.75f),
                border = effectiveAccent.copy(alpha = 0.55f),
                progress = effectiveAccent,
                active = effectiveAccent,
                disabled = Color.White.copy(alpha = 0.30f),
                isDefaultTheme = false
            )
        } catch (e: Exception) {
            Log.w("DynamicTheme", "Failed 10-point dominant color extraction: ${e.message}")
            DynamicPlayerTheme.DefaultTheme
        }
    }
}

/**
 * Animates a [DynamicPlayerTheme] with smooth 700ms color morphing transitions on track changes.
 */
@Composable
fun rememberAnimatedPlayerTheme(rawTheme: DynamicPlayerTheme): DynamicPlayerTheme {
    val duration = 700
    val spec = tween<Color>(duration, easing = LinearOutSlowInEasing)

    val animPrimary by animateColorAsState(rawTheme.primary, spec, label = "anim_primary")
    val animSecondary by animateColorAsState(rawTheme.secondary, spec, label = "anim_sec")
    val animAccent by animateColorAsState(rawTheme.accent, spec, label = "anim_acc")
    val animBg by animateColorAsState(rawTheme.background, spec, label = "anim_bg")
    val animBgSec by animateColorAsState(rawTheme.backgroundSecondary, spec, label = "anim_bg_sec")
    val animSurface by animateColorAsState(rawTheme.surface, spec, label = "anim_surf")
    val animSurfaceLight by animateColorAsState(rawTheme.surfaceLight, spec, label = "anim_surf_light")
    val animGlow by animateColorAsState(rawTheme.glow, spec, label = "anim_glow")
    val animTextPrimary by animateColorAsState(rawTheme.textPrimary, spec, label = "anim_tp")
    val animTextSec by animateColorAsState(rawTheme.textSecondary, spec, label = "anim_ts")
    val animBorder by animateColorAsState(rawTheme.border, spec, label = "anim_bdr")
    val animProgress by animateColorAsState(rawTheme.progress, spec, label = "anim_prg")
    val animActive by animateColorAsState(rawTheme.active, spec, label = "anim_act")
    val animDisabled by animateColorAsState(rawTheme.disabled, spec, label = "anim_dis")

    return DynamicPlayerTheme(
        primary = animPrimary,
        secondary = animSecondary,
        accent = animAccent,
        background = animBg,
        backgroundSecondary = animBgSec,
        surface = animSurface,
        surfaceLight = animSurfaceLight,
        glow = animGlow,
        textPrimary = animTextPrimary,
        textSecondary = animTextSec,
        border = animBorder,
        progress = animProgress,
        active = animActive,
        disabled = animDisabled,
        isDefaultTheme = rawTheme.isDefaultTheme
    )
}

/**
 * Composable hook to obtain an animated [DynamicPlayerTheme] for the given [track] and [bitmap].
 * Uses LRU caching to eliminate color flicker and render overhead.
 */
@Composable
fun rememberDynamicPlayerTheme(track: AudioTrack, bitmap: Bitmap?): DynamicPlayerTheme {
    var rawTheme by remember(track.id) {
        val cached = ArtworkThemeCache.get(track.id)
        mutableStateOf(cached ?: DynamicPlayerTheme.DefaultTheme)
    }

    LaunchedEffect(track.id, bitmap) {
        val cached = ArtworkThemeCache.get(track.id)
        if (cached != null) {
            rawTheme = cached
            return@LaunchedEffect
        }

        val source = bitmap
            ?: AudioArtworkExtractor.getCached(track.id)
            ?: AudioArtworkExtractor.getCached("${track.id}_128x128")
        if (source == null) {
            rawTheme = DynamicPlayerTheme.DefaultTheme
            return@LaunchedEffect
        }

        val extracted = extractThemeFromBitmap(source)
        ArtworkThemeCache.put(track.id, extracted)
        rawTheme = extracted
    }

    return rememberAnimatedPlayerTheme(rawTheme)
}

/**
 * Linearly interpolates between two [DynamicPlayerTheme] instances by [fraction] (0f..1f)
 * for 120 FPS swipe-to-skip color blending inside a [HorizontalPager].
 */
fun lerpDynamicPlayerTheme(
    start: DynamicPlayerTheme,
    stop: DynamicPlayerTheme,
    fraction: Float
): DynamicPlayerTheme {
    val t = fraction.coerceIn(0f, 1f)
    if (t <= 0.001f) return start
    if (t >= 0.999f) return stop
    return DynamicPlayerTheme(
        primary = androidx.compose.ui.graphics.lerp(start.primary, stop.primary, t),
        secondary = androidx.compose.ui.graphics.lerp(start.secondary, stop.secondary, t),
        accent = androidx.compose.ui.graphics.lerp(start.accent, stop.accent, t),
        background = androidx.compose.ui.graphics.lerp(start.background, stop.background, t),
        backgroundSecondary = androidx.compose.ui.graphics.lerp(start.backgroundSecondary, stop.backgroundSecondary, t),
        surface = androidx.compose.ui.graphics.lerp(start.surface, stop.surface, t),
        surfaceLight = androidx.compose.ui.graphics.lerp(start.surfaceLight, stop.surfaceLight, t),
        glow = androidx.compose.ui.graphics.lerp(start.glow, stop.glow, t),
        textPrimary = androidx.compose.ui.graphics.lerp(start.textPrimary, stop.textPrimary, t),
        textSecondary = androidx.compose.ui.graphics.lerp(start.textSecondary, stop.textSecondary, t),
        border = androidx.compose.ui.graphics.lerp(start.border, stop.border, t),
        progress = androidx.compose.ui.graphics.lerp(start.progress, stop.progress, t),
        active = androidx.compose.ui.graphics.lerp(start.active, stop.active, t),
        disabled = androidx.compose.ui.graphics.lerp(start.disabled, stop.disabled, t),
        isDefaultTheme = start.isDefaultTheme && stop.isDefaultTheme
    )
}

/**
 * Dynamic full-screen blurred background container for "Now Playing" glassmorphic UI.
 * Renders blurred artwork (Layer 1 & 2), dark translucent overlay (Layer 3), artwork color tint (Layer 4),
 * central atmospheric radial aura glow (Layer 5), and existing player UI (Layer 6).
 * Smoothly crossfades between tracks during HorizontalPager swipe gestures without flashing or artifacts.
 */
@Composable
fun DynamicBlurredBackgroundContainer(
    bitmap: Bitmap?,
    theme: DynamicPlayerTheme,
    track: AudioTrack? = null,
    targetSwipeTrack: AudioTrack? = null,
    swipeFraction: Float = 0f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val blurredBitmap = rememberBlurredArtwork(track, bitmap) ?: bitmap
    val targetBlurredBitmap = rememberBlurredArtwork(targetSwipeTrack, null)
    val clampedSwipeFraction = swipeFraction.coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1 & 2: Full-screen enlarged, heavily blurred current artwork (Fast GPU texture stretch - 0ms shader cost)
        if (blurredBitmap != null) {
            Image(
                bitmap = blurredBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (targetBlurredBitmap != null) 1f - clampedSwipeFraction else 1f
                    }
            )
        } else {
            // Polished Default Background when no artwork
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                theme.accent.copy(alpha = 0.25f),
                                theme.backgroundSecondary,
                                theme.background
                            )
                        )
                    )
            )
        }

        // Real-time crossfade to the next/previous track's blurred artwork while swiping HorizontalPager
        if (targetBlurredBitmap != null && clampedSwipeFraction > 0.01f) {
            Image(
                bitmap = targetBlurredBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = clampedSwipeFraction
                    }
            )
        }

        // Layer 4: Artwork-derived color tint overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            theme.primary.copy(alpha = 0.35f),
                            theme.backgroundSecondary.copy(alpha = 0.50f),
                            theme.background.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // Layer 5: Central Atmospheric Radial Aura Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            theme.glow.copy(alpha = 0.45f),
                            theme.primary.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )

        // Layer 3: Dark Translucent Scrim for 100% Text & UI Contrast Readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.40f),
                            theme.background.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Layer 6: Existing Player UI Content
        content()
    }
}

