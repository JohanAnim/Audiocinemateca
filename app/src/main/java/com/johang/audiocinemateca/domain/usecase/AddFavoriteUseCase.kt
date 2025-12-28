package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.repository.FavoritesRepository
import javax.inject.Inject

class AddFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {
    suspend operator fun invoke(contentId: String, title: String, contentType: String) {
        val entity = FavoriteEntity(
            contentId = contentId,
            title = title,
            contentType = contentType
        )
        favoritesRepository.addFavorite(entity)
    }
}