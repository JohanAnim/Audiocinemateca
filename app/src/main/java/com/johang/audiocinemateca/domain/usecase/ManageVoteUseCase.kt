package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.VoteRepository
import com.johang.audiocinemateca.data.repository.VoteStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageVoteUseCase @Inject constructor(
    private val repository: VoteRepository
) {
    
    fun getVoteStats(contentId: String, partIndex: Int, episodeIndex: Int): Flow<VoteStats> {
        return repository.getVoteStats(contentId, partIndex, episodeIndex)
    }

    suspend fun toggleVote(contentId: String, partIndex: Int, episodeIndex: Int, currentVote: Int, intentVote: Int) {
        // Si el usuario intenta votar lo mismo que ya tiene, significa que quiere quitar el voto (0)
        // Si es diferente, aplicamos el nuevo voto (intentVote)
        val finalVote = if (currentVote == intentVote) 0 else intentVote
        repository.performVote(contentId, partIndex, episodeIndex, finalVote)
    }
}
