package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.ContentStatsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContentStatsUseCase @Inject constructor(
    private val repository: ContentStatsRepository
) {
    operator fun invoke(contentId: String): Flow<Long> {
        return repository.getViewCount(contentId)
    }
}
