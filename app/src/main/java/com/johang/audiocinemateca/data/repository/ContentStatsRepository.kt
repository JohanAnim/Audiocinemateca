package com.johang.audiocinemateca.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentStatsRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val collection = firestore.collection("content_stats")

    /**
     * Incrementa el contador de vistas de un contenido de forma atómica.
     * Crea el documento si no existe.
     */
    suspend fun incrementViewCount(contentId: String) {
        try {
            val docRef = collection.document(contentId)
            // Usamos set con merge para crear el documento si no existe, 
            // y luego actualizamos el contador.
            // Nota: increment(1) funciona incluso si el campo no existe (lo crea como 1).
            docRef.set(mapOf("viewCount" to FieldValue.increment(1)), SetOptions.merge()).await()
        } catch (e: Exception) {
            // Manejamos el error silenciosamente o lo logueamos, 
            // no queremos romper la experiencia del usuario por una estadística.
            e.printStackTrace()
        }
    }

    /**
     * Obtiene un flujo en tiempo real del número de vistas.
     */
    fun getViewCount(contentId: String): Flow<Long> = callbackFlow {
        val docRef = collection.document(contentId)
        
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val views = snapshot.getLong("viewCount") ?: 0L
                trySend(views)
            } else {
                // Si no existe el documento, asumimos 0 vistas
                trySend(0L)
            }
        }

        awaitClose { listener.remove() }
    }
}
