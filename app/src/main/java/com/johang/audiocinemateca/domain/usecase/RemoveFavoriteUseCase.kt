package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import javax.inject.Inject

class RemoveFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    suspend operator fun invoke(contentId: String) {
        favoritesRepository.removeFavorite(contentId)
    }
}
