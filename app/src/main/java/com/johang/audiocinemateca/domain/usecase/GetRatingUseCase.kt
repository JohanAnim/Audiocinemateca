package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.RatingRepository
import com.johang.audiocinemateca.data.repository.RatingStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRatingUseCase @Inject constructor(
    private val repository: RatingRepository
) {
    operator fun invoke(contentId: String): Flow<RatingStats> {
        return repository.getRatingStats(contentId)
    }
}
