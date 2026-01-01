package com.johang.audiocinemateca.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RatingStats(
    val averageRating: Double = 0.0,
    val totalRatings: Long = 0,
    val userRating: Int = 0 // 0 significa que no ha calificado
)

@Singleton
class RatingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    
    private val ratingsCollection = firestore.collection("content_ratings")

    fun getRatingStats(contentId: String): Flow<RatingStats> = callbackFlow {
        val docRef = ratingsCollection.document(contentId)
        val userId = auth.currentUser?.uid
        
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val avg = snapshot?.getDouble("averageRating") ?: 0.0
            val total = snapshot?.getLong("totalRatings") ?: 0L
            
            if (userId != null) {
                 docRef.collection("user_ratings").document(userId).get()
                     .addOnSuccessListener { userSnap ->
                         val myRating = userSnap.getLong("rating")?.toInt() ?: 0
                         trySend(RatingStats(avg, total, myRating))
                     }
                     .addOnFailureListener {
                         trySend(RatingStats(avg, total, 0))
                     }
            } else {
                trySend(RatingStats(avg, total, 0))
            }
        }

        awaitClose { listener.remove() }
    }

    suspend fun setRating(contentId: String, newRating: Int) {
        val userId = auth.currentUser?.uid ?: return
        val docRef = ratingsCollection.document(contentId)
        val userRatingRef = docRef.collection("user_ratings").document(userId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val userSnap = transaction.get(userRatingRef)
            
            var currentAvg = snapshot.getDouble("averageRating") ?: 0.0
            var currentTotal = snapshot.getLong("totalRatings") ?: 0L
            val oldRating = userSnap.getLong("rating")?.toInt() ?: 0

            // Si es la misma calificación, no hacemos nada
            if (oldRating == newRating) return@runTransaction

            // Calcular nuevo promedio
            // Primero, revertimos el promedio anterior (si existía)
            var sum = currentAvg * currentTotal
            
            if (oldRating > 0) {
                sum -= oldRating
                currentTotal--
            }

            // Ahora sumamos el nuevo
            sum += newRating
            currentTotal++

            val newAvg = sum / currentTotal

            // Guardar
            transaction.set(docRef, mapOf(
                "averageRating" to newAvg,
                "totalRatings" to currentTotal
            ), SetOptions.merge())

            transaction.set(userRatingRef, mapOf("rating" to newRating), SetOptions.merge())
        }.await()
    }
}
