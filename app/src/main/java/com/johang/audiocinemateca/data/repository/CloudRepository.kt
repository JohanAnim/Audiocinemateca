package com.johang.audiocinemateca.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.remote.model.CloudFavorite
import com.johang.audiocinemateca.data.remote.model.CloudHistory
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRepository @Inject constructor() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getUserDoc() = auth.currentUser?.uid?.let { 
        firestore.collection("users").document(it) 
    }

    // --- PERFIL Y ROLES ---

    suspend fun syncUserProfile(preferredName: String? = null) {
        val user = auth.currentUser ?: return
        val docRef = firestore.collection("users").document(user.uid)
        
        val snapshot = docRef.get().await()
        if (!snapshot.exists()) {
            val nameToSave = preferredName ?: user.displayName ?: "Usuario de Audiocinemateca"
            
            val userData = hashMapOf(
                "uid" to user.uid,
                "email" to user.email,
                "name" to nameToSave,
                "role" to "client", // Todos empiezan como cliente
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            docRef.set(userData).await()
        }
    }

    suspend fun getUserRole(): String {
        val snapshot = getUserDoc()?.get()?.await()
        return snapshot?.getString("role") ?: "client"
    }

    // --- FAVORITOS ---

    suspend fun uploadFavorite(favorite: FavoriteEntity) {
        val cloudFav = CloudFavorite(
            contentId = favorite.contentId,
            title = favorite.title,
            contentType = favorite.contentType,
            addedAt = favorite.addedAt
        )
        getUserDoc()?.collection("favorites")?.document(cloudFav.contentId)
            ?.set(cloudFav, SetOptions.merge())?.await()
    }

    suspend fun deleteFavorite(contentId: String) {
        getUserDoc()?.collection("favorites")?.document(contentId)?.delete()?.await()
    }

    suspend fun getAllCloudFavorites(): List<CloudFavorite> {
        val snapshot = getUserDoc()?.collection("favorites")?.get()?.await()
        return snapshot?.toObjects(CloudFavorite::class.java) ?: emptyList()
    }

    // --- HISTORIAL ---

    suspend fun uploadHistory(history: PlaybackProgressEntity) {
        val cloudHist = com.johang.audiocinemateca.data.remote.model.CloudHistory(
            contentId = history.contentId,
            contentType = history.contentType,
            currentPositionMs = history.currentPositionMs,
            totalDurationMs = history.totalDurationMs,
            partIndex = history.partIndex,
            episodeIndex = history.episodeIndex,
            lastPlayedTimestamp = history.lastPlayedTimestamp,
            isFinished = history.isFinished
        )
        // Usamos una clave compuesta para el documento (Netflix style)
        val docId = "${cloudHist.contentId}_${cloudHist.partIndex}_${cloudHist.episodeIndex}"
        getUserDoc()?.collection("history")?.document(docId)
            ?.set(cloudHist, com.google.firebase.firestore.SetOptions.merge())?.await()
    }

    suspend fun getAllCloudHistory(): List<com.johang.audiocinemateca.data.remote.model.CloudHistory> {
        val snapshot = getUserDoc()?.collection("history")?.get()?.await()
        return snapshot?.toObjects(com.johang.audiocinemateca.data.remote.model.CloudHistory::class.java) ?: emptyList()
    }

    suspend fun deleteHistoryItem(contentId: String, partIndex: Int, episodeIndex: Int) {
        val docId = "${contentId}_${partIndex}_${episodeIndex}"
        getUserDoc()?.collection("history")?.document(docId)?.delete()?.await()
    }

    suspend fun deleteAllCloudHistory() {
        val snapshot = getUserDoc()?.collection("history")?.get()?.await()
        snapshot?.documents?.forEach { doc ->
            doc.reference.delete().await()
        }
    }
}