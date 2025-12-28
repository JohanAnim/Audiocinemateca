package com.johang.audiocinemateca.domain.repository

import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getAllFavorites(): Flow<List<FavoriteEntity>>
    suspend fun addFavorite(favorite: FavoriteEntity)
    suspend fun removeFavorite(contentId: String)
    fun isFavorite(contentId: String): Flow<Boolean>
}
