package com.johang.audiocinemateca.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class VoteStats(
    val likeCount: Long = 0,
    val dislikeCount: Long = 0,
    val userVote: Int = 0 // 0: Neutro, 1: Like, -1: Dislike
)

@Singleton
class VoteRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    // Colección principal de votos por contenido
    private val votesCollection = firestore.collection("content_votes")

    private fun getVoteDocId(contentId: String, partIndex: Int, episodeIndex: Int): String {
        return if (episodeIndex != -1) {
            // Es un episodio de serie: ID_Temp_Ep
            "${contentId}_S${partIndex}_E${episodeIndex}"
        } else {
            // Es película o contenido único
            contentId
        }
    }

    /**
     * Obtiene los contadores en tiempo real y el voto del usuario actual.
     */
    fun getVoteStats(contentId: String, partIndex: Int, episodeIndex: Int): Flow<VoteStats> = callbackFlow {
        val docId = getVoteDocId(contentId, partIndex, episodeIndex)
        val userId = auth.currentUser?.uid
        
        val docRef = votesCollection.document(docId)
        
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val likeCount = snapshot?.getLong("likeCount") ?: 0L
            val dislikeCount = snapshot?.getLong("dislikeCount") ?: 0L
            
            // Ahora verificamos si el usuario votó (esto podría optimizarse localmente, 
            // pero por ahora lo leemos de la subcolección de usuarios dentro del documento)
            var userVote = 0
            
            if (userId != null) {
                 docRef.collection("user_votes").document(userId).get()
                     .addOnSuccessListener { userSnap ->
                         userVote = userSnap.getLong("voteType")?.toInt() ?: 0
                         trySend(VoteStats(likeCount, dislikeCount, userVote))
                     }
                     .addOnFailureListener {
                         trySend(VoteStats(likeCount, dislikeCount, 0))
                     }
            } else {
                trySend(VoteStats(likeCount, dislikeCount, 0))
            }
        }

        awaitClose { listener.remove() }
    }

    /**
     * Realiza el voto.
     * voteType: 1 (Like), -1 (Dislike), 0 (Remover voto)
     */
    suspend fun performVote(contentId: String, partIndex: Int, episodeIndex: Int, newVoteType: Int) {
        val userId = auth.currentUser?.uid ?: return
        val docId = getVoteDocId(contentId, partIndex, episodeIndex)
        val docRef = votesCollection.document(docId)
        val userVoteRef = docRef.collection("user_votes").document(userId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val userVoteSnapshot = transaction.get(userVoteRef)
            
            val currentLikes = snapshot.getLong("likeCount") ?: 0L
            val currentDislikes = snapshot.getLong("dislikeCount") ?: 0L
            val oldVoteType = userVoteSnapshot.getLong("voteType")?.toInt() ?: 0

            // Si el voto es el mismo, no hacemos nada (o lo quitamos si es toggle, eso lo maneja el ViewModel)
            if (oldVoteType == newVoteType) return@runTransaction

            // Calcular nuevos contadores
            var newLikes = currentLikes
            var newDislikes = currentDislikes

            // 1. Restar el voto anterior
            when (oldVoteType) {
                1 -> newLikes = (newLikes - 1).coerceAtLeast(0)
                -1 -> newDislikes = (newDislikes - 1).coerceAtLeast(0)
            }

            // 2. Sumar el voto nuevo
            when (newVoteType) {
                1 -> newLikes++
                -1 -> newDislikes++
            }

            // Actualizar documento principal
            transaction.set(docRef, mapOf(
                "likeCount" to newLikes,
                "dislikeCount" to newDislikes
            ), SetOptions.merge())

            // Actualizar voto del usuario
            if (newVoteType == 0) {
                transaction.delete(userVoteRef)
            } else {
                transaction.set(userVoteRef, mapOf("voteType" to newVoteType))
            }
        }.await()
    }
}
