package com.example.data.ambient

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedAmbientDao {

    @Query("SELECT * FROM downloaded_ambients ORDER BY downloadedAtMs DESC")
    fun getAllDownloaded(): Flow<List<DownloadedAmbientEntity>>

    @Query("SELECT * FROM downloaded_ambients WHERE isFavorite = 1 ORDER BY downloadedAtMs DESC")
    fun getFavorites(): Flow<List<DownloadedAmbientEntity>>

    @Query("SELECT * FROM downloaded_ambients ORDER BY lastPlayedAtMs DESC LIMIT 15")
    fun getRecent(): Flow<List<DownloadedAmbientEntity>>

    @Query("SELECT * FROM downloaded_ambients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadedAmbientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadedAmbientEntity)

    @Query("DELETE FROM downloaded_ambients WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE downloaded_ambients SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE downloaded_ambients SET lastPlayedAtMs = :playedAtMs WHERE id = :id")
    suspend fun updateLastPlayed(id: String, playedAtMs: Long = System.currentTimeMillis())

    @Query("SELECT SUM(fileSizeBytes) FROM downloaded_ambients")
    fun getTotalStorageBytes(): Flow<Long?>
}
