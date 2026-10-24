package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_play_records")
data class TrackPlayRecordEntity(
    @PrimaryKey
    val trackUri: String,
    val trackId: String,
    val playCount: Int = 1,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val customCoverPath: String? = null,
    val isFavorite: Boolean = false
)
