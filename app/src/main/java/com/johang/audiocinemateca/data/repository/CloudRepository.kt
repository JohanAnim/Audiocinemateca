package com.johang.audiocinemateca.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.remote.model.CloudFavorite
import com.johang.audiocinemateca.data.remote.model.CloudHistory
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val catalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository
) {

    private fun getUserDoc() = auth.currentUser?.uid?.let { uid ->
        firestore.collection("users").document(uid)
    }

    suspend fun getUserRole(): String {
        return try {
            val doc = getUserDoc()?.get()?.await()
            doc?.getString("role") ?: "user"
        } catch (e: Exception) {
            "user"
        }
    }

    suspend fun syncUserProfile() {
        val user = auth.currentUser ?: return
        val userDoc = getUserDoc() ?: return
        
        try {
            val snapshot = userDoc.get().await()
            val fcmToken = try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) { null }

            val updateData = mutableMapOf<String, Any>(
                "email" to (user.email ?: ""),
                "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Usuario"),
                "lastActive" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            if (!fcmToken.isNullOrEmpty()) {
                updateData["fcmToken"] = fcmToken
            }

            if (!snapshot.exists()) {
                updateData["name"] = user.displayName ?: ""
                updateData["role"] = "user"
                updateData["createdAt"] = System.currentTimeMillis()
                userDoc.set(updateData).await()
            } else {
                userDoc.set(updateData, SetOptions.merge()).await()
            }

            if (!fcmToken.isNullOrEmpty()) {
                firestore.collection("presence").document(user.uid)
                    .set(mapOf(
                        "userId" to user.uid,
                        "displayName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Usuario"),
                        "email" to (user.email ?: ""),
                        "fcmToken" to fcmToken
                    ), SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error syncing profile: ${e.message}")
        }
    }

    // --- LISTENERS EN TIEMPO REAL ---

    fun getFavoritesRealtimeFlow(): kotlinx.coroutines.flow.Flow<List<CloudFavorite>> = callbackFlow {
        val userDoc = getUserDoc()
        if (userDoc == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val subscription = userDoc.collection("favorites").addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CloudRepo", "Error en listener de favoritos: ${error.message}")
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val contentId = doc.getString("contentId") ?: doc.id
                    val title = doc.getString("title") ?: ""
                    val type = doc.getString("contentType") ?: "movie"
                    val addedAt = doc.getLong("addedAt") ?: 0L
                    CloudFavorite(contentId, title, type, addedAt)
                } catch (e: Exception) { null }
            } ?: emptyList()
            
            trySend(list)
        }
        awaitClose { subscription.remove() }
    }

    private fun parseContentIdAndIndices(doc: com.google.firebase.firestore.DocumentSnapshot): Triple<String, Int, Int> {
        val rawContentId = doc.getString("contentId")
        val rawPartIndex = (doc.get("partIndex") as? Number)?.toInt()
        val rawEpisodeIndex = (doc.get("episodeIndex") as? Number)?.toInt()

        val docId = doc.id
        val parts = docId.split("_")

        if (!rawContentId.isNullOrEmpty()) {
            val p = rawPartIndex ?: if (parts.size >= 3) parts[parts.size - 2].toIntOrNull() ?: 0 else 0
            val e = rawEpisodeIndex ?: if (parts.size >= 3) parts.last().toIntOrNull() ?: 0 else 0
            return Triple(rawContentId, p.coerceAtLeast(0), e.coerceAtLeast(0))
        }

        if (parts.size >= 3 && parts[parts.size - 1].toIntOrNull() != null && parts[parts.size - 2].toIntOrNull() != null) {
            val calculatedId = docId.substringBeforeLast('_').substringBeforeLast('_')
            val calculatedPart = parts[parts.size - 2].toIntOrNull() ?: 0
            val calculatedEp = parts[parts.size - 1].toIntOrNull() ?: 0
            return Triple(calculatedId, calculatedPart.coerceAtLeast(0), calculatedEp.coerceAtLeast(0))
        }

        val p = rawPartIndex ?: 0
        val e = rawEpisodeIndex ?: 0
        return Triple(docId, p.coerceAtLeast(0), e.coerceAtLeast(0))
    }

    fun getHistoryRealtimeFlow(): kotlinx.coroutines.flow.Flow<List<CloudHistory>> = callbackFlow {
        val userDoc = getUserDoc()
        if (userDoc == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val subscription = userDoc.collection("history").addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("CloudRepo", "Error en listener de historial: ${error.message}")
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                try {
                    val (contentId, pIndex, eIndex) = parseContentIdAndIndices(doc)
                    val title = doc.getString("title") ?: ""
                    val type = doc.getString("contentType") ?: "movie"
                    val currentPos = doc.getLong("currentPositionMs") ?: 0L
                    val totalDur = doc.getLong("totalDurationMs") ?: 0L
                    val timestamp = doc.getLong("lastPlayedTimestamp") ?: 0L
                    val finished = doc.getBoolean("isFinished") ?: false

                    if (contentId.isNotEmpty() && contentId !in listOf("pelicula", "serie", "cortometraje", "documental")) {
                        CloudHistory(contentId, title, type, currentPos, totalDur, pIndex, eIndex, timestamp, finished)
                    } else null
                } catch (e: Exception) { null }
            } ?: emptyList()

            trySend(list)
        }
        awaitClose { subscription.remove() }
    }

    // --- FAVORITOS ---

    suspend fun uploadFavorite(favorite: FavoriteEntity) {
        val fav = CloudFavorite(
            contentId = favorite.contentId,
            title = favorite.title,
            contentType = favorite.contentType,
            addedAt = favorite.addedAt
        )
        getUserDoc()?.collection("favorites")?.document(favorite.contentId)?.set(fav)?.await()
    }

    suspend fun deleteFavorite(contentId: String) {
        getUserDoc()?.collection("favorites")?.document(contentId)?.delete()?.await()
    }

    suspend fun getAllCloudFavorites(): List<CloudFavorite> {
        return try {
            val snapshot = getUserDoc()?.collection("favorites")?.get()?.await()
            val list = mutableListOf<CloudFavorite>()
            
            val catalog = catalogRepository.getCatalog()
            val allItems = mutableListOf<CatalogItem>()
            catalog?.movies?.let { allItems.addAll(it) }
            catalog?.series?.let { allItems.addAll(it) }
            catalog?.documentaries?.let { allItems.addAll(it) }
            catalog?.shortFilms?.let { allItems.addAll(it) }

            snapshot?.documents?.forEach { doc ->
                try {
                    val contentId = doc.getString("contentId") ?: doc.id
                    var finalTitle = doc.getString("title") ?: ""
                    var rawType = doc.getString("contentType")?.lowercase() ?: "movie"
                    var addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()

                    val finalType = when (rawType) {
                        "series", "serie" -> "serie"
                        "movie", "peliculas", "pelicula" -> "movie"
                        "documentary", "documentales", "documental" -> "documentary"
                        "short", "shortfilm", "cortometrajes", "cortometraje" -> "shortfilm"
                        else -> rawType
                    }

                    if (addedAt > 200000000000L && addedAt < 210000000000L) {
                        addedAt = 1704067200000L 
                    } else if (addedAt in 1L..9999999999L) {
                        addedAt *= 1000
                    }

                    if (finalTitle.isEmpty()) {
                        val localItem = allItems.find { it.id == contentId }
                        if (localItem != null) finalTitle = localItem.title
                    }

                    list.add(CloudFavorite(contentId, finalTitle, finalType, addedAt))
                } catch (e: Exception) {
                    android.util.Log.e("CloudRepo", "Error parseando favorito: ${e.message}")
                }
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error obteniendo favoritos: ${e.message}")
            emptyList()
        }
    }

    // --- HISTORIAL ---

    suspend fun uploadHistory(progress: PlaybackProgressEntity) {
        var hTitle = ""
        try {
            val catalog = catalogRepository.getCatalog()
            val allItems = mutableListOf<CatalogItem>()
            catalog?.movies?.let { allItems.addAll(it) }
            catalog?.series?.let { allItems.addAll(it) }
            catalog?.documentaries?.let { allItems.addAll(it) }
            catalog?.shortFilms?.let { allItems.addAll(it) }

            val item = allItems.find { it.id == progress.contentId }
            if (item != null) hTitle = item.title
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error buscando título: ${e.message}")
        }

        val history = CloudHistory(
            contentId = progress.contentId,
            title = hTitle,
            contentType = progress.contentType,
            currentPositionMs = progress.currentPositionMs,
            totalDurationMs = progress.totalDurationMs,
            partIndex = progress.partIndex,
            episodeIndex = progress.episodeIndex,
            lastPlayedTimestamp = progress.lastPlayedTimestamp,
            isFinished = progress.isFinished
        )
        val docId = "${progress.contentId}_${progress.partIndex}_${progress.episodeIndex}"
        getUserDoc()?.collection("history")?.document(docId)?.set(history, SetOptions.merge())?.await()
    }

    suspend fun getAllCloudHistory(): List<CloudHistory> {
        return try {
            val snapshot = getUserDoc()?.collection("history")?.get()?.await()
            val list = mutableListOf<CloudHistory>()
            
            val catalog = catalogRepository.getCatalog()
            val allItems = mutableListOf<CatalogItem>()
            catalog?.movies?.let { allItems.addAll(it) }
            catalog?.series?.let { allItems.addAll(it) }
            catalog?.documentaries?.let { allItems.addAll(it) }
            catalog?.shortFilms?.let { allItems.addAll(it) }

            snapshot?.documents?.forEach { doc ->
                try {
                    val (contentId, pIndex, eIndex) = parseContentIdAndIndices(doc)
                    var hTitle = doc.getString("title") ?: ""
                    var hType = doc.getString("contentType") ?: "movie"
                    
                    if (hTitle.isEmpty()) {
                        val localItem = allItems.find { it.id == contentId }
                        if (localItem != null) {
                            hTitle = localItem.title
                            if (hType == "movie") {
                                hType = when(localItem) {
                                    is Movie -> "movie"
                                    is Serie -> "serie"
                                    is Documentary -> "documentary"
                                    is ShortFilm -> "shortfilm"
                                    else -> hType
                                }
                            }
                        }
                    }

                    val currentPos = doc.getLong("currentPositionMs") ?: 0L
                    val totalDur = doc.getLong("totalDurationMs") ?: 0L
                    val timestamp = doc.getLong("lastPlayedTimestamp") ?: System.currentTimeMillis()
                    val finished = doc.getBoolean("isFinished") ?: false

                    if (contentId.isNotEmpty() && contentId !in listOf("pelicula", "serie", "cortometraje", "documental")) {
                        list.add(
                            CloudHistory(
                                contentId = contentId,
                                title = hTitle,
                                contentType = hType,
                                currentPositionMs = currentPos,
                                totalDurationMs = totalDur,
                                partIndex = pIndex,
                                episodeIndex = eIndex,
                                lastPlayedTimestamp = timestamp,
                                isFinished = finished
                            )
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CloudRepo", "Error parseando documento ${doc.id}: ${e.message}")
                }
            }
            list
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error obteniendo historial: ${e.message}")
            emptyList()
        }
    }

    suspend fun deleteHistoryItem(contentId: String, partIndex: Int, episodeIndex: Int) {
        val docId = "${contentId}_${partIndex}_${episodeIndex}"
        getUserDoc()?.collection("history")?.document(docId)?.delete()?.await()
    }

    suspend fun deleteAllCloudHistory() {
        try {
            val snapshot = getUserDoc()?.collection("history")?.get()?.await() ?: return
            val batch = firestore.batch()
            
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            
            batch.commit().await()
            android.util.Log.d("CloudRepo", "Historial en la nube eliminado (${snapshot.size()} elementos)")
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error borrando historial en lote: ${e.message}")
            throw e
        }
    }

    // --- PERFIL DE GUSTOS Y RECOMENDACIONES (TASTE PROFILE) ---

    suspend fun saveUserTasteProfile(profile: UserTasteProfile) {
        val userDoc = getUserDoc() ?: return
        try {
            val data = mapOf(
                "preferredDubbing" to profile.preferredDubbing,
                "topGenres" to profile.topGenres,
                "topDirectors" to profile.topDirectors,
                "topNarrators" to profile.topNarrators,
                "lastUpdated" to profile.lastUpdated
            )
            userDoc.collection("preferences").document("taste_profile")
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error al guardar perfil de gustos en Firestore: ${e.message}")
        }
    }

    suspend fun getUserTasteProfile(): UserTasteProfile? {
        val userDoc = getUserDoc() ?: return null
        return try {
            val doc = userDoc.collection("preferences").document("taste_profile").get().await()
            if (!doc.exists()) return null

            val preferredDubbing = doc.getString("preferredDubbing") ?: "latino"

            @Suppress("UNCHECKED_CAST")
            val rawGenres = doc.get("topGenres") as? Map<String, Any>
            val topGenres = rawGenres?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 } ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val rawDirectors = doc.get("topDirectors") as? Map<String, Any>
            val topDirectors = rawDirectors?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 } ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val rawNarrators = doc.get("topNarrators") as? Map<String, Any>
            val topNarrators = rawNarrators?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 } ?: emptyMap()

            val lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()

            UserTasteProfile(
                preferredDubbing = preferredDubbing,
                topGenres = topGenres,
                topDirectors = topDirectors,
                topNarrators = topNarrators,
                lastUpdated = lastUpdated
            )
        } catch (e: Exception) {
            android.util.Log.e("CloudRepo", "Error al leer perfil de gustos de Firestore: ${e.message}")
            null
        }
    }
}

data class UserTasteProfile(
    val preferredDubbing: String = "latino",
    val topGenres: Map<String, Double> = emptyMap(),
    val topDirectors: Map<String, Double> = emptyMap(),
    val topNarrators: Map<String, Double> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)
