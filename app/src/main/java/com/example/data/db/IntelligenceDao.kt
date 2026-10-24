package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntelligenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningEvent(event: ListeningEventEntity): Long

    @Query("SELECT * FROM listening_events ORDER BY timestampMs DESC")
    fun getAllListeningEvents(): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM listening_events WHERE timestampMs >= :sinceTimestampMs ORDER BY timestampMs DESC")
    fun getListeningEventsSince(sinceTimestampMs: Long): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM listening_events WHERE timestampMs >= :startMs AND timestampMs <= :endMs")
    suspend fun getListeningEventsRange(startMs: Long, endMs: Long): List<ListeningEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyAggregate(aggregate: DailyListeningAggregateEntity)

    @Query("SELECT * FROM daily_listening_aggregates ORDER BY dateString DESC")
    fun getAllDailyAggregates(): Flow<List<DailyListeningAggregateEntity>>

    @Query("SELECT * FROM daily_listening_aggregates WHERE dateString = :dateString LIMIT 1")
    suspend fun getDailyAggregate(dateString: String): DailyListeningAggregateEntity?

    @Query("DELETE FROM listening_events")
    suspend fun clearAllEvents()

    @Query("DELETE FROM daily_listening_aggregates")
    suspend fun clearAllAggregates()
}
