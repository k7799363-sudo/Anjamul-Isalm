package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedTrackDao {

    @Query("SELECT * FROM tracks ORDER BY customSortTimestamp DESC")
    fun observeTracksByNewest(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY customSortTimestamp ASC")
    fun observeTracksByOldest(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY LOWER(title) ASC")
    fun observeTracksByTitleAz(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY LOWER(title) DESC")
    fun observeTracksByTitleZa(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY playCount DESC, LOWER(title) ASC")
    fun observeTracksByMostPlayed(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY customSortTimestamp DESC")
    suspend fun getAllCachedTracksNewestFirst(): List<CachedAudioTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<CachedAudioTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: CachedAudioTrackEntity)

    @Query("UPDATE tracks SET customSortTimestamp = :newTime WHERE id = :trackId")
    suspend fun updateTrackDate(trackId: String, newTime: Long)

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun updateFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = :playCount WHERE id = :trackId")
    suspend fun updatePlayCount(trackId: String, playCount: Int)

    @Query("UPDATE tracks SET lastPlayedTimestamp = :currentTime WHERE id = :trackId")
    suspend fun updateLastPlayedTimestamp(trackId: String, currentTime: Long)

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 50")
    fun observeRecentTracks(): Flow<List<CachedAudioTrackEntity>>

    @Query("SELECT * FROM tracks WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 50")
    suspend fun getRecentTracks(): List<CachedAudioTrackEntity>

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM tracks WHERE id NOT IN (:validIds)")
    suspend fun pruneRemovedTracks(validIds: List<String>)
}
