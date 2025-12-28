package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {
    override fun getAllFavorites(): Flow<List<FavoriteEntity>> {
        return favoritesDao.getAllFavorites()
    }

    override suspend fun addFavorite(favorite: FavoriteEntity) {
        favoritesDao.insertFavorite(favorite)
    }

    override suspend fun removeFavorite(contentId: String) {
        favoritesDao.removeFavorite(contentId)
    }

    override fun isFavorite(contentId: String): Flow<Boolean> {
        return favoritesDao.isFavorite(contentId)
    }
}
