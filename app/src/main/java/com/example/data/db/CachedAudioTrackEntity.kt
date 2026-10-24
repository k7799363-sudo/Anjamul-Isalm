package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AudioTrack

@Entity(tableName = "tracks")
data class CachedAudioTrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val reciter: String,
    val durationMs: Long,
    val dataUri: String,
    val album: String,
    val folderName: String,
    val sizeBytes: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val isFavorite: Boolean,
    val dateAdded: Long,
    var customSortTimestamp: Long = dateAdded,
    val playCount: Int,
    var lastPlayedTimestamp: Long = 0L
) {
    fun toDomainModel(): AudioTrack {
        val resolvedSortTimestamp = if (customSortTimestamp > 0L) customSortTimestamp else dateAdded
        return AudioTrack(
            id = id,
            title = title,
            reciter = reciter,
            durationMs = durationMs,
            dataUri = dataUri,
            album = album,
            folderName = folderName,
            sizeBytes = sizeBytes,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            isFavorite = isFavorite,
            dateAdded = dateAdded,
            dateAddedMs = dateAdded,
            customSortTimestamp = resolvedSortTimestamp,
            playCount = playCount,
            lastPlayedTimestamp = lastPlayedTimestamp
        )
    }

    companion object {
        fun fromDomainModel(track: AudioTrack): CachedAudioTrackEntity {
            val resolvedDateAdded = if (track.dateAdded > 0L) track.dateAdded else track.dateAddedMs
            val resolvedSortTimestamp = if (track.customSortTimestamp > 0L) {
                track.customSortTimestamp
            } else {
                resolvedDateAdded
            }
            return CachedAudioTrackEntity(
                id = track.id,
                title = track.title,
                reciter = track.reciter,
                durationMs = track.durationMs,
                dataUri = track.dataUri,
                album = track.album,
                folderName = track.folderName,
                sizeBytes = track.sizeBytes,
                bitrateKbps = track.bitrateKbps,
                sampleRateHz = track.sampleRateHz,
                isFavorite = track.isFavorite,
                dateAdded = resolvedDateAdded,
                customSortTimestamp = resolvedSortTimestamp,
                playCount = track.playCount,
                lastPlayedTimestamp = track.lastPlayedTimestamp
            )
        }
    }
}
