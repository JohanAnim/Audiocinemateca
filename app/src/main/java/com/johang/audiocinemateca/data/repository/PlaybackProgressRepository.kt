package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackProgressRepository @Inject constructor(
    private val playbackProgressDao: PlaybackProgressDao
) {

    suspend fun savePlaybackProgress(progress: PlaybackProgressEntity) {
        if (progress.contentType == "series") {
            playbackProgressDao.updateSeriesProgress(progress)
        } else {
            playbackProgressDao.insertPlaybackProgress(progress)
        }
    }

    suspend fun getPlaybackProgress(contentId: String, partIndex: Int, episodeIndex: Int): PlaybackProgressEntity? {
        return playbackProgressDao.getPlaybackProgress(contentId, partIndex, episodeIndex)
    }

    suspend fun getPlaybackProgressForContent(contentId: String): List<PlaybackProgressEntity> {
        return playbackProgressDao.getPlaybackProgressForContent(contentId)
    }

    suspend fun deletePlaybackProgress(contentId: String, partIndex: Int, episodeIndex: Int) {
        playbackProgressDao.deletePlaybackProgress(contentId, partIndex, episodeIndex)
    }

    suspend fun deleteAllPlaybackProgressForContent(contentId: String) {
        playbackProgressDao.deleteAllPlaybackProgressForContent(contentId)
    }

    suspend fun deleteAllPlaybackProgress() {
        playbackProgressDao.deleteAllPlaybackProgress()
    }

    fun getAllPlaybackProgress(): kotlinx.coroutines.flow.Flow<List<PlaybackProgressEntity>> {
        return playbackProgressDao.getAllPlaybackProgress()
    }
}