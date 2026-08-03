package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackProgressRepository @Inject constructor(
    private val playbackProgressDao: PlaybackProgressDao,
    private val cloudRepository: CloudRepository
) {

    suspend fun savePlaybackProgress(progress: PlaybackProgressEntity, syncToCloud: Boolean = true) {
        // Siempre guardamos en local
        playbackProgressDao.insertPlaybackProgress(progress)

        if (syncToCloud) {
            try {
                // Solo sincronizamos con la nube si se solicita expresamente (Pausa, Stop, Cambio)
                cloudRepository.uploadHistory(progress)
            } catch (e: Exception) {
                android.util.Log.e("SyncHistory", "Error al subir progreso: ${e.message}")
            }
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
        try {
            cloudRepository.deleteHistoryItem(contentId, partIndex, episodeIndex)
        } catch (e: Exception) {
            android.util.Log.e("SyncHistory", "Error al borrar en nube")
        }
    }

    suspend fun deleteAllPlaybackProgressForContent(contentId: String) {
        playbackProgressDao.deleteAllPlaybackProgressForContent(contentId)
        try {
            cloudRepository.deleteHistoryItem(contentId, 0, 0)
        } catch (e: Exception) {
            android.util.Log.e("SyncHistory", "Error al borrar todo para contenido en nube")
        }
    }

    suspend fun deleteAllPlaybackProgress() {
        playbackProgressDao.deleteAllPlaybackProgress()
        try {
            cloudRepository.deleteAllCloudHistory()
        } catch (e: Exception) {
            android.util.Log.e("SyncHistory", "Error al borrar todo en nube")
        }
    }

    fun getAllPlaybackProgress(): kotlinx.coroutines.flow.Flow<List<PlaybackProgressEntity>> {
        return playbackProgressDao.getAllPlaybackProgress()
    }
}