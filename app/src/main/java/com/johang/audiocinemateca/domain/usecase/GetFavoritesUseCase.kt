package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    operator fun invoke(): Flow<List<FavoriteEntity>> {
        return favoritesRepository.getAllFavorites()
    }
}
