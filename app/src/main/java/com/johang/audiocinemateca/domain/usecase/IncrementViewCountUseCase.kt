package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.ContentStatsRepository
import javax.inject.Inject

class IncrementViewCountUseCase @Inject constructor(
    private val repository: ContentStatsRepository
) {
    suspend operator fun invoke(contentId: String) {
        repository.incrementViewCount(contentId)
    }
}
