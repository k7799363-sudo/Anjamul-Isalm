package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPlayDao {

    @Query("SELECT * FROM track_play_records")
    fun getAllRecords(): Flow<List<TrackPlayRecordEntity>>

    @Query("SELECT playCount FROM track_play_records WHERE trackUri = :uri LIMIT 1")
    suspend fun getPlayCountByUri(uri: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: TrackPlayRecordEntity)

    @Query("SELECT customCoverPath FROM track_play_records WHERE trackId = :trackId OR trackUri = :trackId LIMIT 1")
    suspend fun getCustomCoverPath(trackId: String): String?

    @Query("UPDATE track_play_records SET customCoverPath = :coverPath WHERE trackId = :trackId OR trackUri = :trackId")
    suspend fun updateCustomCoverPath(trackId: String, coverPath: String): Int

    @Query("SELECT * FROM track_play_records WHERE trackId = :trackId OR trackUri = :trackId LIMIT 1")
    suspend fun getRecordByTrackId(trackId: String): TrackPlayRecordEntity?

    @Query("UPDATE track_play_records SET isFavorite = :isFavorite WHERE trackId = :trackId OR trackUri = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean): Int

    @Query("SELECT isFavorite FROM track_play_records WHERE trackId = :trackId OR trackUri = :trackId LIMIT 1")
    fun observeIsFavorite(trackId: String): Flow<Boolean?>

    @Query("SELECT trackId FROM track_play_records WHERE isFavorite = 1")
    fun observeFavoriteTrackIds(): Flow<List<String>>
}
