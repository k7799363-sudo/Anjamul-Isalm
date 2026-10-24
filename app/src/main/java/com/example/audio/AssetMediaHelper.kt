package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object AssetMediaHelper {

    private const val TAG = "AssetMediaHelper"

    /**
     * Extracts an asset to internal storage if not already extracted,
     * and returns the local file reference for seamless MediaPlayer playback.
     */
    fun getOrExtractAssetFile(context: Context, assetPath: String): File {
        val cleanPath = assetPath.removePrefix("asset:///").removePrefix("/")
        val targetFile = File(context.filesDir, cleanPath.replace('/', '_'))
        if (!targetFile.exists() || targetFile.length() < 100L) {
            try {
                context.assets.open(cleanPath).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Successfully extracted asset $cleanPath to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed extracting asset $cleanPath: ${e.message}", e)
            }
        }
        return targetFile
    }

    /**
     * Sets the data source for a MediaPlayer using the reliable extracted file path,
     * avoiding Android's premature file descriptor closure bugs.
     */
    fun setDataSourceFromAsset(mediaPlayer: MediaPlayer, context: Context, assetPath: String) {
        val targetFile = getOrExtractAssetFile(context, assetPath)
        if (targetFile.exists() && targetFile.length() > 0) {
            mediaPlayer.setDataSource(targetFile.absolutePath)
        } else {
            // Fallback to openFd with duplicated descriptor
            val afd = context.assets.openFd(assetPath)
            mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        }
    }
}
