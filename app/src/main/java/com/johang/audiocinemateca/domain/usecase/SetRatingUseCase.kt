package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.RatingRepository
import javax.inject.Inject

class SetRatingUseCase @Inject constructor(
    private val repository: RatingRepository
) {
    suspend operator fun invoke(contentId: String, rating: Int) {
        repository.setRating(contentId, rating)
    }
}
