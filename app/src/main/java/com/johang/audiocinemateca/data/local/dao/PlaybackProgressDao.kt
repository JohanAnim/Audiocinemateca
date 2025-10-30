package com.johang.audiocinemateca.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity

@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackProgress(progress: PlaybackProgressEntity)

    @Transaction
    suspend fun updateSeriesProgress(progress: PlaybackProgressEntity) {
        deleteAllPlaybackProgressForContent(progress.contentId)
        insertPlaybackProgress(progress)
    }

    @Query("SELECT * FROM playback_progress WHERE contentId = :contentId AND partIndex = :partIndex AND episodeIndex = :episodeIndex")
    suspend fun getPlaybackProgress(contentId: String, partIndex: Int, episodeIndex: Int): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE contentId = :contentId")
    suspend fun getPlaybackProgressForContent(contentId: String): List<PlaybackProgressEntity>

    @Query("DELETE FROM playback_progress WHERE contentId = :contentId AND partIndex = :partIndex AND episodeIndex = :episodeIndex")
    suspend fun deletePlaybackProgress(contentId: String, partIndex: Int, episodeIndex: Int)

    @Query("DELETE FROM playback_progress WHERE contentId = :contentId")
    suspend fun deleteAllPlaybackProgressForContent(contentId: String)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAllPlaybackProgress()

    @Query("SELECT * FROM playback_progress ORDER BY lastPlayedTimestamp DESC")
    fun getAllPlaybackProgress(): kotlinx.coroutines.flow.Flow<List<PlaybackProgressEntity>>
}