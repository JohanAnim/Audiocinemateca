package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val cloudRepository: CloudRepository
) : FavoritesRepository {
    override fun getAllFavorites(): Flow<List<FavoriteEntity>> {
        return favoritesDao.getAllFavorites()
    }

    override suspend fun addFavorite(favorite: FavoriteEntity) {
        // 1. Local
        favoritesDao.insertFavorite(favorite)
        
        // 2. Nube
        try {
            cloudRepository.uploadFavorite(favorite)
        } catch (e: Exception) {
            android.util.Log.e("SyncFav", "Error al subir favorito: ${e.message}")
        }
    }

    override suspend fun removeFavorite(contentId: String) {
        // 1. Local
        favoritesDao.removeFavorite(contentId)
        
        // 2. Nube
        try {
            cloudRepository.deleteFavorite(contentId)
        } catch (e: Exception) {
            android.util.Log.e("SyncFav", "Error al eliminar favorito: ${e.message}")
        }
    }

    override fun isFavorite(contentId: String): Flow<Boolean> {
        return favoritesDao.isFavorite(contentId)
    }
}
