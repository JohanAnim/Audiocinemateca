package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.model.Comment
import com.johang.audiocinemateca.data.repository.CommentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCommentsPreviewUseCase @Inject constructor(private val repository: CommentRepository) {
    operator fun invoke(contentId: String, partIndex: Int, episodeIndex: Int): Flow<Pair<Long, Comment?>> {
        return repository.getCommentsPreview(contentId, partIndex, episodeIndex)
    }
}
