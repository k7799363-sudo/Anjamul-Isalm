package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Ultra-High-Resolution ("4K" Original Quality) Audio Artwork Pipeline.
 *
 * - Step 1: Eliminates low-res MediaStore ALBUM_ART thumbnails and aggressive downsampling.
 * - Step 2: Extracts raw, uncompressed embedded picture bytes (`ByteArray`) directly from the audio
 *   file via `MediaMetadataRetriever.embeddedPicture`.
 * - Step 3: Provides raw `ByteArray` / high-res `Bitmap` models for Coil `AsyncImage` configured with
 *   `Size.ORIGINAL`, `allowHardware(true)`, and `crossfade(500)`.
 * - Step 4: Falls back to `ContentResolver.loadThumbnail(uri, Size(2048, 2048), null)` on Android 10+ (API 29+)
 *   when `MediaMetadataRetriever.embeddedPicture` returns null.
 */
object AudioArtworkExtractor {

    // 32MB LRU Cache for high-resolution decoded bitmaps (used for 5-point color sampling & blur)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val bitmapCacheSize = (maxMemory / 6).coerceIn(8192, 32768)

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(bitmapCacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    // 24MB LRU Cache for raw uncompressed embedded picture ByteArrays (for Coil Size.ORIGINAL rendering)
    private val rawBytesCacheSize = (maxMemory / 8).coerceIn(4096, 24576)
    private val rawBytesCache: LruCache<String, ByteArray> = object : LruCache<String, ByteArray>(rawBytesCacheSize) {
        override fun sizeOf(key: String, value: ByteArray): Int {
            return (value.size / 1024).coerceAtLeast(1)
        }
    }

    fun getCached(trackId: String?): Bitmap? {
        if (trackId == null) return null
        return memoryCache.get(trackId)
    }

    fun getCachedBytes(trackId: String?): ByteArray? {
        if (trackId == null) return null
        return rawBytesCache.get(trackId)
    }

    fun getCachedModel(trackId: String?): Any? {
        if (trackId == null) return null
        return rawBytesCache.get(trackId) ?: memoryCache.get(trackId)
    }

    fun evictCache(trackId: String) {
        rawBytesCache.remove(trackId)
        memoryCache.remove(trackId)
        memoryCache.remove("${trackId}_2048x2048")
        memoryCache.remove("${trackId}_1080x1080")
        memoryCache.remove("${trackId}_512x512")
    }

    fun hasCustomCover(context: Context, trackId: String): Boolean {
        val customFile = File(context.filesDir, "custom_covers/cover_${trackId}.jpg")
        return customFile.exists() && customFile.length() > 0
    }

    fun removeCustomCover(context: Context, trackId: String) {
        try {
            evictCache(trackId)
            val customFile = File(context.filesDir, "custom_covers/cover_${trackId}.jpg")
            if (customFile.exists()) {
                customFile.delete()
            }
        } catch (_: Exception) {}
    }

    suspend fun saveCustomCover(
        context: Context,
        trackId: String,
        imageUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, "custom_covers").apply { if (!exists()) mkdirs() }
            val destFile = File(dir, "cover_${trackId}.jpg")

            context.contentResolver.openInputStream(imageUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@withContext null
                java.io.FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                try {
                    val db = com.example.data.db.NurAnjamDatabase.getInstance(context)
                    db.trackPlayDao().updateCustomCoverPath(trackId, destFile.absolutePath)
                } catch (_: Exception) {}

                evictCache(trackId)
                return@withContext destFile.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioArtworkExtractor", "Error saving custom cover: ${e.message}", e)
        }
        null
    }

    /**
     * Step 2 & Step 4 Helper:
     * Extracts the raw, uncompressed embedded cover art (`ByteArray`) directly from an audio [Uri]
     * using [MediaMetadataRetriever], or falls back to a 2048x2048 high-resolution system thumbnail
     * on Android 10+ (API 29+) if `embeddedPicture` is null.
     */
    suspend fun extractHighResArtworkFromUri(
        context: Context,
        audioUri: Uri
    ): Any? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, audioUri)
            val rawBytes: ByteArray? = retriever.embeddedPicture
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                return@withContext rawBytes
            }
        } catch (_: Exception) {
            // Fall through to Android 10+ high-res thumbnail fallback
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Step 4: Fallback for Android 10+ (API 29+) using 2048x2048 high-res thumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return@withContext context.contentResolver.loadThumbnail(
                    audioUri,
                    Size(2048, 2048),
                    null
                )
            } catch (_: Exception) {}
        }

        null
    }

    /**
     * Extracts the raw uncompressed `ByteArray` (or Android 10+ 2048x2048 `Bitmap` fallback)
     * for an [AudioTrack], supporting content:// URIs, local file paths, custom covers, and bundled assets.
     */
    suspend fun extractHighResArtworkModel(
        context: Context,
        track: AudioTrack
    ): Any? = withContext(Dispatchers.IO) {
        rawBytesCache.get(track.id)?.let { return@withContext it }
        memoryCache.get(track.id)?.let { return@withContext it }

        if (track.dataUri.isBlank()) return@withContext null

        // 1. Check user-selected custom cover file first (100% uncompressed quality)
        val customFile = File(context.filesDir, "custom_covers/cover_${track.id}.jpg")
        if (customFile.exists() && customFile.length() > 0) {
            try {
                val bytes = customFile.readBytes()
                if (bytes.isNotEmpty()) {
                    rawBytesCache.put(track.id, bytes)
                    return@withContext bytes
                }
            } catch (_: Exception) {}
        }

        // 2. Step 2: Extract raw uncompressed embedded picture bytes via MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            configureRetrieverDataSource(context, retriever, track.dataUri)
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                rawBytesCache.put(track.id, pictureBytes)
                return@withContext pictureBytes
            }
        } catch (_: Exception) {
            // Fall through to Android 10+ fallback
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // 3. Step 4: Fallback for Android 10+ (API 29+) -> 2048x2048 ContentResolver.loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.dataUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(track.dataUri)
                val fallbackBitmap = context.contentResolver.loadThumbnail(
                    uri,
                    Size(2048, 2048),
                    null
                )
                val safeBitmap = ensureSoftwareBitmap(fallbackBitmap)
                memoryCache.put(track.id, safeBitmap)
                memoryCache.put("${track.id}_2048x2048", safeBitmap)

                // Also cache high-quality PNG/JPEG byte array for Coil Size.ORIGINAL pipeline
                val stream = ByteArrayOutputStream()
                safeBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val fallbackBytes = stream.toByteArray()
                if (fallbackBytes.isNotEmpty()) {
                    rawBytesCache.put(track.id, fallbackBytes)
                    return@withContext fallbackBytes
                }
                return@withContext safeBitmap
            } catch (_: Exception) {}
        }

        null
    }

    /**
     * Decodes a high-resolution software [Bitmap] (`ARGB_8888`) for [track] (used by the
     * 5-point color sampler, dynamic blur background, and MediaSession notification metadata).
     * Never downsamples to low-res thumbnails.
     */
    suspend fun getEmbeddedArtwork(
        context: Context,
        track: AudioTrack,
        targetWidth: Int = 2048,
        targetHeight: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        val effectiveWidth = targetWidth.coerceAtLeast(1080)
        val effectiveHeight = targetHeight.coerceAtLeast(1080)
        val cacheKey = "${track.id}_${effectiveWidth}x$effectiveHeight"

        memoryCache.get(track.id)?.let { return@withContext it }
        memoryCache.get(cacheKey)?.let { return@withContext it }

        if (track.dataUri.isBlank()) return@withContext null

        val customFile = File(context.filesDir, "custom_covers/cover_${track.id}.jpg")
        if (customFile.exists() && customFile.length() > 0) {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val customBitmap = BitmapFactory.decodeFile(customFile.absolutePath, options)
            if (customBitmap != null) {
                try {
                    rawBytesCache.put(track.id, customFile.readBytes())
                } catch (_: Exception) {}
                memoryCache.put(cacheKey, customBitmap)
                memoryCache.put(track.id, customBitmap)
                return@withContext customBitmap
            }
        }

        // Check if raw bytes are already cached
        val cachedBytes = rawBytesCache.get(track.id)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            val decoded = decodeHighResBitmap(cachedBytes, effectiveWidth, effectiveHeight)
            if (decoded != null) {
                memoryCache.put(cacheKey, decoded)
                memoryCache.put(track.id, decoded)
                return@withContext decoded
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            configureRetrieverDataSource(context, retriever, track.dataUri)
            val pictureBytes = retriever.embeddedPicture
            if (pictureBytes != null && pictureBytes.isNotEmpty()) {
                rawBytesCache.put(track.id, pictureBytes)
                val bitmap = decodeHighResBitmap(pictureBytes, effectiveWidth, effectiveHeight)
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    memoryCache.put(track.id, bitmap)
                    return@withContext bitmap
                }
            }
        } catch (_: Exception) {
            // Silently ignore decode errors for corrupt/missing ID3 frames
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Step 4: Fallback for Android 10+ (API 29+) using ContentResolver.loadThumbnail(uri, Size(2048, 2048), null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.dataUri.startsWith("content://")) {
            try {
                val uri = Uri.parse(track.dataUri)
                val thumbnail = context.contentResolver.loadThumbnail(
                    uri,
                    Size(2048, 2048),
                    null
                )
                val safeBitmap = ensureSoftwareBitmap(thumbnail)
                memoryCache.put(cacheKey, safeBitmap)
                memoryCache.put(track.id, safeBitmap)
                return@withContext safeBitmap
            } catch (_: Exception) {}
        }

        null
    }

    private fun configureRetrieverDataSource(
        context: Context,
        retriever: MediaMetadataRetriever,
        dataUri: String
    ) {
        when {
            dataUri.startsWith("asset:") || dataUri.startsWith("assets:") || dataUri.startsWith("asset://") -> {
                val assetPath = dataUri
                    .removePrefix("assets:")
                    .removePrefix("asset:")
                    .removePrefix("asset:///")
                    .removePrefix("asset://")
                val file = com.example.audio.AssetMediaHelper.getOrExtractAssetFile(context, assetPath)
                if (file.exists() && file.length() > 0) {
                    retriever.setDataSource(file.absolutePath)
                } else {
                    val afd = context.assets.openFd(assetPath)
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
            }
            dataUri.startsWith("content://") -> {
                val uri = Uri.parse(dataUri)
                retriever.setDataSource(context, uri)
            }
            dataUri.startsWith("file://") -> {
                val uri = Uri.parse(dataUri)
                val path = uri.path
                if (path != null) {
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(context, uri)
                }
            }
            dataUri.isNotBlank() -> {
                val file = File(dataUri)
                if (file.exists()) {
                    retriever.setDataSource(file.absolutePath)
                } else {
                    retriever.setDataSource(dataUri)
                }
            }
        }
    }

    private fun decodeHighResBitmap(
        pictureBytes: ByteArray,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, options)

            var inSampleSize = 1
            if (options.outHeight > targetHeight || options.outWidth > targetWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= targetHeight && (halfWidth / inSampleSize) >= targetWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(pictureBytes, 0, pictureBytes.size, decodeOptions)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureSoftwareBitmap(bitmap: Bitmap): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
    }
}

/**
 * Composable helper that provides the raw uncompressed `ByteArray` (or Android 10+ 2048x2048 `Bitmap` fallback)
 * for Coil's `AsyncImage` configured with `Size.ORIGINAL` and `allowHardware(true)`.
 */
@Composable
fun rememberHighResArtworkModel(track: AudioTrack?): State<Any?> {
    val context = LocalContext.current
    val initialCached = remember(track?.id) {
        AudioArtworkExtractor.getCachedModel(track?.id)
    }
    val modelState = remember(track?.id) { mutableStateOf<Any?>(initialCached) }

    LaunchedEffect(track?.id, track?.dataUri) {
        if (track != null) {
            val cached = AudioArtworkExtractor.getCachedModel(track.id)
            if (cached != null) {
                if (modelState.value !== cached) {
                    modelState.value = cached
                }
                return@LaunchedEffect
            }
            val extracted = AudioArtworkExtractor.extractHighResArtworkModel(context, track)
            if (extracted != null) {
                modelState.value = extracted
            }
        } else {
            modelState.value = null
        }
    }

    return modelState
}

/**
 * Composable helper returning a high-resolution software [Bitmap] (`ARGB_8888`) for dynamic color extraction,
 * blurred backgrounds, and shared element transitions.
 */
@Composable
fun rememberTrackArtwork(
    track: AudioTrack?,
    targetWidth: Int = 2048,
    targetHeight: Int = 2048
): State<Bitmap?> {
    val context = LocalContext.current
    val effectiveWidth = targetWidth.coerceAtLeast(1080)
    val effectiveHeight = targetHeight.coerceAtLeast(1080)
    val cacheKey = track?.let { "${it.id}_${effectiveWidth}x$effectiveHeight" }
    val cachedBitmap = remember(track?.id) {
        AudioArtworkExtractor.getCached(track?.id)
            ?: AudioArtworkExtractor.getCached(cacheKey)
            ?: AudioArtworkExtractor.getCached("${track?.id}_2048x2048")
            ?: AudioArtworkExtractor.getCached("${track?.id}_1080x1080")
    }
    val artworkState = remember(track?.id) { mutableStateOf<Bitmap?>(cachedBitmap) }

    LaunchedEffect(track?.id, track?.dataUri) {
        if (track != null) {
            val inMemoryTarget = AudioArtworkExtractor.getCached(track.id)
                ?: AudioArtworkExtractor.getCached(cacheKey)
                ?: AudioArtworkExtractor.getCached("${track.id}_2048x2048")
                ?: AudioArtworkExtractor.getCached("${track.id}_1080x1080")
            if (inMemoryTarget != null) {
                if (artworkState.value != inMemoryTarget) {
                    artworkState.value = inMemoryTarget
                }
                return@LaunchedEffect
            }
            if (artworkState.value == null) {
                val loaded = AudioArtworkExtractor.getEmbeddedArtwork(context, track, effectiveWidth, effectiveHeight)
                if (loaded != null) {
                    artworkState.value = loaded
                }
            }
        } else {
            artworkState.value = null
        }
    }

    return artworkState
}
