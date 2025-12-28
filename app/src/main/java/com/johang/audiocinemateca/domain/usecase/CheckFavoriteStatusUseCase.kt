package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CheckFavoriteStatusUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    operator fun invoke(contentId: String): Flow<Boolean> {
        return favoritesRepository.isFavorite(contentId)
    }
}
