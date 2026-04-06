package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.CommentRepository
import javax.inject.Inject

class AddCommentUseCase @Inject constructor(private val repository: CommentRepository) {
    suspend operator fun invoke(
        contentId: String,
        contentTitle: String,
        contentType: String,
        partIndex: Int,
        episodeIndex: Int,
        text: String
    ) {
        repository.addComment(contentId, contentTitle, contentType, partIndex, episodeIndex, text)
    }
}