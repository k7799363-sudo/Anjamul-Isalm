package com.example.data

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeletionResult {
    data class Success(val track: AudioTrack) : DeletionResult()
    data class RequiresIntentSender(val intentSender: IntentSender, val track: AudioTrack) : DeletionResult()
    data class Error(val track: AudioTrack, val message: String) : DeletionResult()
}

class NativeFileDeletionManager(private val context: Context) {

    companion object {
        private const val TAG = "FileDeletionManager"
    }

    private val contentResolver = context.contentResolver

    /**
     * Attempts to permanently delete the physical audio file from device storage.
     * On Android 11+ (API 30+), MediaStore files return [DeletionResult.RequiresIntentSender]
     * to prompt the user with the mandatory OS confirmation dialog.
     */
    suspend fun deleteAudioFile(track: AudioTrack): DeletionResult = withContext(Dispatchers.IO) {
        try {
            val dataUriStr = track.dataUri

            // 1. Curated Asset Tracks (read-only in APK) -> Handled via Library removal
            if (dataUriStr.startsWith("assets:")) {
                return@withContext DeletionResult.Success(track)
            }

            // 2. MediaStore Track (e.g. id "local_12345" or content://media/external/audio/media/...)
            val mediaStoreUri = resolveMediaStoreUri(track)
            if (mediaStoreUri != null) {
                // On Android 11+ (API 30+), MediaStore requires system consent via createDeleteRequest
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(
                            contentResolver,
                            listOf(mediaStoreUri)
                        )
                        return@withContext DeletionResult.RequiresIntentSender(
                            intentSender = pendingIntent.intentSender,
                            track = track
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "createDeleteRequest failed, falling back to direct delete: ${e.message}")
                    }
                }

                // Android 10 (API 29) & fallback for Android 11+
                try {
                    val deletedRows = contentResolver.delete(mediaStoreUri, null, null)
                    deleteDirectFileIfPresent(track.dataUri)
                    if (deletedRows > 0) {
                        return@withContext DeletionResult.Success(track)
                    }
                } catch (securityEx: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityEx is RecoverableSecurityException) {
                        return@withContext DeletionResult.RequiresIntentSender(
                            intentSender = securityEx.userAction.actionIntent.intentSender,
                            track = track
                        )
                    }
                    Log.w(TAG, "SecurityException while deleting MediaStore row: ${securityEx.message}")
                }
            }

            // 3. Storage Access Framework (SAF / Document Uri)
            if (dataUriStr.startsWith("content://")) {
                val parsedUri = Uri.parse(dataUriStr)
                try {
                    val isDocDeleted = DocumentsContract.deleteDocument(contentResolver, parsedUri)
                    if (isDocDeleted) {
                        return@withContext DeletionResult.Success(track)
                    }
                } catch (_: Exception) {}

                try {
                    val rows = contentResolver.delete(parsedUri, null, null)
                    if (rows > 0) {
                        return@withContext DeletionResult.Success(track)
                    }
                } catch (_: Exception) {}
            }

            // 4. Direct Physical File path on device storage
            val directPath = if (dataUriStr.startsWith("file://")) {
                dataUriStr.removePrefix("file://")
            } else {
                dataUriStr
            }

            val file = File(directPath)
            if (file.exists()) {
                val isDeleted = file.delete()
                // Clean up MediaStore index if present
                try {
                    contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(file.absolutePath)
                    )
                } catch (_: Exception) {}

                if (isDeleted) {
                    return@withContext DeletionResult.Success(track)
                }
            }

            // If file was already gone or handled:
            DeletionResult.Success(track)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete track file: ${e.message}", e)
            DeletionResult.Error(track, e.message ?: "Unknown deletion error")
        }
    }

    /**
     * Resolves the MediaStore URI for a track if indexed.
     */
    private fun resolveMediaStoreUri(track: AudioTrack): Uri? {
        val dataUriStr = track.dataUri
        if (dataUriStr.startsWith("content://media/external/audio/media/")) {
            return Uri.parse(dataUriStr)
        }

        if (track.id.startsWith("local_")) {
            val rawId = track.id.removePrefix("local_").toLongOrNull()
            if (rawId != null) {
                return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, rawId)
            }
        }

        // Search MediaStore by exact file data path
        if (track.dataUri.isNotBlank() && !track.dataUri.startsWith("assets:")) {
            try {
                contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.DATA} = ?",
                    arrayOf(track.dataUri),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    private fun deleteDirectFileIfPresent(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    /**
     * Verifies that the physical file or MediaStore entry no longer exists.
     * Must be called before removing the track from the database/library.
     */
    fun verifyFileDeleted(track: AudioTrack): Boolean {
        val dataUriStr = track.dataUri

        if (dataUriStr.startsWith("assets:") || dataUriStr.startsWith("asset:")) {
            return true
        }

        val directPath = if (dataUriStr.startsWith("file://")) {
            dataUriStr.removePrefix("file://")
        } else {
            dataUriStr
        }

        if (!dataUriStr.startsWith("content://")) {
            val file = File(directPath)
            if (file.exists()) {
                Log.w(TAG, "Verification failed: Physical file still exists at $directPath")
                return false
            }
        }

        if (dataUriStr.startsWith("content://")) {
            try {
                contentResolver.query(
                    Uri.parse(dataUriStr),
                    arrayOf(MediaStore.Audio.Media._ID),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.count > 0) {
                        Log.w(TAG, "Verification failed: MediaStore record still exists")
                        return false
                    }
                }
            } catch (_: Exception) {}
        }

        return true
    }
}
