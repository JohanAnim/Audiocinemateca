package com.johang.audiocinemateca.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE contentId = :contentId AND partIndex = :partIndex AND episodeIndex = :episodeIndex")
    suspend fun getDownload(contentId: String, partIndex: Int, episodeIndex: Int): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE contentId = :contentId ORDER BY partIndex, episodeIndex")
    fun getDownloadsForContent(contentId: String): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE contentId = :contentId AND partIndex = :partIndex AND episodeIndex = :episodeIndex")
    suspend fun deleteDownload(contentId: String, partIndex: Int, episodeIndex: Int)

    @Query("DELETE FROM downloads")
    suspend fun deleteAllDownloads()
}
