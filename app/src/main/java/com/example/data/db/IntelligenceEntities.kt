package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_events")
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val trackTitle: String,
    val reciter: String,
    val album: String,
    val folderName: String,
    val trackUri: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val durationPlayedMs: Long,
    val trackDurationMs: Long,
    val completionPercentage: Float,
    val wasSkipped: Boolean,
    val wasCompleted: Boolean,
    val wasReplay: Boolean,
    val hourOfDay: Int,
    val dayOfWeek: Int
)

@Entity(tableName = "daily_listening_aggregates")
data class DailyListeningAggregateEntity(
    @PrimaryKey
    val dateString: String, // e.g. "2026-10-01"
    val totalListeningTimeMs: Long = 0L,
    val totalSongsPlayed: Int = 0,
    val uniqueSongsCount: Int = 0,
    val topTrackId: String? = null,
    val topTrackTitle: String? = null,
    val mostActiveHour: Int = 12,
    val completionRate: Float = 0f
)
