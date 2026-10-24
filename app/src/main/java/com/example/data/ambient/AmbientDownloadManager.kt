package com.example.data.ambient

import android.content.Context
import android.util.Log
import com.example.data.db.NurAnjamDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class AmbientDownloadManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AmbientDownloadManager"

        @Volatile
        private var INSTANCE: AmbientDownloadManager? = null

        fun getInstance(context: Context): AmbientDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AmbientDownloadManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val db = NurAnjamDatabase.getInstance(context)
    private val dao: DownloadedAmbientDao = db.downloadedAmbientDao()

    // Download progress map: soundId -> StateFlow<Int> (0 to 100)
    private val _downloadProgressMap = ConcurrentHashMap<String, MutableStateFlow<Int>>()

    // Active downloading states: soundId -> AmbientDownloadState
    private val _downloadStatesMap = ConcurrentHashMap<String, MutableStateFlow<AmbientDownloadState>>()

    fun getDownloadProgressFlow(soundId: String): StateFlow<Int> {
        return _downloadProgressMap.getOrPut(soundId) { MutableStateFlow(0) }.asStateFlow()
    }

    fun getDownloadStateFlow(soundId: String): StateFlow<AmbientDownloadState> {
        return _downloadStatesMap.getOrPut(soundId) { MutableStateFlow(AmbientDownloadState.NOT_DOWNLOADED) }.asStateFlow()
    }

    private fun updateProgress(soundId: String, progress: Int) {
        _downloadProgressMap.getOrPut(soundId) { MutableStateFlow(0) }.update { progress }
    }

    private fun updateState(soundId: String, state: AmbientDownloadState) {
        _downloadStatesMap.getOrPut(soundId) { MutableStateFlow(AmbientDownloadState.NOT_DOWNLOADED) }.update { state }
    }

    suspend fun downloadAmbientSound(sound: OnlineAmbientSound): Result<DownloadedAmbientEntity> = withContext(Dispatchers.IO) {
        val soundId = sound.soundId
        updateState(soundId, AmbientDownloadState.DOWNLOADING)
        updateProgress(soundId, 0)

        try {
            // 1. Check if already downloaded
            val existing = dao.getById(soundId)
            if (existing != null && File(existing.localFilePath).exists()) {
                updateState(soundId, AmbientDownloadState.DOWNLOADED)
                updateProgress(soundId, 100)
                return@withContext Result.success(existing)
            }

            // 2. Storage safety check
            val targetFolder = File(context.filesDir, "ambient_sounds")
            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }

            val requiredBytes = sound.fileSizeBytes ?: (3 * 1024 * 1024L)
            val availableSpace = targetFolder.usableSpace
            if (availableSpace < requiredBytes + (10 * 1024 * 1024L)) { // 10MB safety buffer
                updateState(soundId, AmbientDownloadState.DOWNLOAD_FAILED)
                return@withContext Result.failure(Exception("Insufficient storage space on device."))
            }

            // 3. Perform network download
            val downloadUrlStr = sound.downloadUrl ?: sound.previewUrl
            val extension = if (downloadUrlStr.contains(".ogg")) ".ogg" else ".mp3"
            val outputFile = File(targetFolder, "${soundId}$extension")

            val url = URL(downloadUrlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "NurAnjamMusicPlayer/2.0")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                updateState(soundId, AmbientDownloadState.DOWNLOAD_FAILED)
                return@withContext Result.failure(Exception("Download HTTP error: $responseCode"))
            }

            val totalLength = connection.contentLengthLong.let { if (it > 0) it else requiredBytes }
            var downloadedBytes = 0L

            val inputStream: InputStream = connection.inputStream
            val outputStream = FileOutputStream(outputFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                val progressPercent = ((downloadedBytes * 100) / totalLength).toInt().coerceIn(0, 99)
                updateProgress(soundId, progressPercent)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            // 4. Verify downloaded file
            if (!outputFile.exists() || outputFile.length() <= 0) {
                outputFile.delete()
                updateState(soundId, AmbientDownloadState.DOWNLOAD_FAILED)
                return@withContext Result.failure(Exception("Downloaded file was empty or missing."))
            }

            // 5. Store metadata in Room Database
            val entity = DownloadedAmbientEntity(
                id = soundId,
                provider = sound.provider,
                providerSoundId = soundId.removePrefix("fs_"),
                title = sound.title,
                creator = sound.creator,
                durationMs = sound.durationSec * 1000L,
                localFilePath = outputFile.absolutePath,
                previewUrl = sound.previewUrl,
                sourceUrl = sound.sourceUrl ?: "",
                license = sound.license,
                licenseUrl = sound.licenseUrl,
                downloadedAtMs = System.currentTimeMillis(),
                isFavorite = sound.isFavorite,
                fileSizeBytes = outputFile.length(),
                categoryTag = sound.tags.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Ambient"
            )

            dao.insert(entity)
            updateProgress(soundId, 100)
            updateState(soundId, AmbientDownloadState.DOWNLOADED)

            Log.i(TAG, "Successfully downloaded ambient sound: ${sound.title} -> ${outputFile.absolutePath}")
            Result.success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading sound ${sound.title}: ${e.message}", e)
            updateState(soundId, AmbientDownloadState.DOWNLOAD_FAILED)
            Result.failure(e)
        }
    }

    /**
     * Copies a user-selected local audio URI directly into `context.filesDir`
     * (e.g. `custom_atmosphere_<timestamp>.mp3`) so playback never relies on external URI permissions
     * that expire after reboot, and persists the local file path and user-defined name into Room.
     */
    suspend fun importCustomAtmosphereFromUri(
        uri: android.net.Uri,
        userDefinedName: String
    ): Result<DownloadedAmbientEntity> = withContext(Dispatchers.IO) {
        try {
            val timestamp = System.currentTimeMillis()
            val soundId = "custom_atmosphere_$timestamp"

            val mimeType = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val extension = when {
                mimeType.contains("wav") -> ".wav"
                mimeType.contains("ogg") -> ".ogg"
                mimeType.contains("flac") -> ".flac"
                mimeType.contains("m4a") || mimeType.contains("mp4") -> ".m4a"
                else -> ".mp3"
            }

            // Copy directly into context.filesDir (e.g., custom_atmosphere_1.mp3)
            val destFile = File(context.filesDir, "${soundId}$extension")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Unable to open selected audio stream."))

            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            if (!destFile.exists() || destFile.length() <= 0L) {
                destFile.delete()
                return@withContext Result.failure(Exception("Copied audio file was empty."))
            }

            // Extract duration metadata from the saved internal file
            var durationMs = 0L
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(destFile.absolutePath)
                durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            val cleanTitle = userDefinedName.trim().ifBlank { "Custom Atmosphere" }

            val entity = DownloadedAmbientEntity(
                id = soundId,
                provider = "Local Storage",
                providerSoundId = soundId,
                title = cleanTitle,
                creator = "Custom Upload",
                durationMs = durationMs,
                localFilePath = destFile.absolutePath,
                previewUrl = destFile.absolutePath,
                sourceUrl = destFile.absolutePath,
                license = "Personal Local Audio",
                licenseUrl = null,
                downloadedAtMs = timestamp,
                isFavorite = false,
                lastPlayedAtMs = timestamp,
                fileSizeBytes = destFile.length(),
                categoryTag = "Custom"
            )

            dao.insert(entity)
            updateProgress(soundId, 100)
            updateState(soundId, AmbientDownloadState.DOWNLOADED)

            Log.i(TAG, "Saved custom atmosphere '$cleanTitle' to internal filesDir: ${destFile.absolutePath}")
            Result.success(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Failed importing custom atmosphere audio: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Removes any legacy hardcoded ambient items (fs_1001..fs_1008) from Room DB.
     */
    suspend fun purgeLegacyHardcodedAmbients() = withContext(Dispatchers.IO) {
        try {
            val legacyIds = listOf("fs_1001", "fs_1002", "fs_1003", "fs_1004", "fs_1005", "fs_1006", "fs_1007", "fs_1008")
            for (legacyId in legacyIds) {
                val existing = dao.getById(legacyId)
                if (existing != null) {
                    File(existing.localFilePath).takeIf { it.exists() }?.delete()
                    dao.deleteById(legacyId)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice purging legacy ambients: ${e.message}")
        }
    }

    suspend fun deleteDownloadedSound(soundId: String) = withContext(Dispatchers.IO) {
        try {
            val existing = dao.getById(soundId)
            if (existing != null) {
                val file = File(existing.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                dao.deleteById(soundId)
            }
            updateState(soundId, AmbientDownloadState.NOT_DOWNLOADED)
            updateProgress(soundId, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting sound $soundId: ${e.message}", e)
        }
    }
}
