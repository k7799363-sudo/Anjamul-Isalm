package com.example.data.ambient

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AmbientDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    DOWNLOAD_FAILED,
    FILE_MISSING
}

@Entity(tableName = "downloaded_ambients")
data class DownloadedAmbientEntity(
    @PrimaryKey val id: String,
    val provider: String = "Freesound",
    val providerSoundId: String,
    val title: String,
    val creator: String,
    val durationMs: Long,
    val localFilePath: String,
    val previewUrl: String,
    val sourceUrl: String,
    val license: String,
    val licenseUrl: String? = null,
    val downloadedAtMs: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPlayedAtMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val categoryTag: String = "Ambient"
)

data class OnlineAmbientSound(
    val soundId: String,
    val provider: String = "Freesound",
    val title: String,
    val creator: String,
    val durationSec: Int,
    val previewUrl: String,
    val downloadUrl: String?,
    val license: String,
    val licenseUrl: String? = null,
    val sourceUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val downloadState: AmbientDownloadState = AmbientDownloadState.NOT_DOWNLOADED,
    val downloadProgress: Int = 0
)

data class AmbientSearchResultPage(
    val query: String,
    val page: Int,
    val totalResults: Int,
    val sounds: List<OnlineAmbientSound>
)

data class ActiveAmbientTrack(
    val id: String,
    val title: String,
    val creator: String,
    val uriOrPath: String,
    val isLocal: Boolean,
    val volume: Float = 0.5f,
    val isPlaying: Boolean = true
)
