package com.johang.audiocinemateca.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.johang.audiocinemateca.data.model.Announcement
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Singleton
class AnnouncementRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("anuncios")
    private val client = OkHttpClient()

    // --- CONFIGURACIÓN DE NOTIFICACIONES ---
    // Reemplaza esto con tu clave real en la consola de Firebase
    private val FCM_SERVER_KEY = "TU_CLAVE_DE_SERVIDOR_AQUI" 
    private val FCM_URL = "https://fcm.googleapis.com/fcm/send"

    fun getAnnouncements(): Flow<List<Announcement>> = callbackFlow {
        val listener = collection.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val ads = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Announcement::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(ads)
            }
        awaitClose { listener.remove() }
    }

    suspend fun createAnnouncement(text: String) {
        val user = auth.currentUser ?: return
        val adminName = user.displayName ?: "Johan"
        val announcement = Announcement(
            adminId = user.uid,
            adminName = adminName,
            text = text
        )
        val docRef = collection.add(announcement).await()
        
        // Si el guardado fue exitoso, enviamos la notificación push a todos
        sendPushNotification(adminName, text)
    }

    private fun sendPushNotification(adminName: String, text: String) {
        // Solo intentamos enviar si la clave no es la de ejemplo
        if (FCM_SERVER_KEY == "TU_CLAVE_DE_SERVIDOR_AQUI") return

        val bodyText = if (text.length > 50) text.take(50) + "..." else text
        
        val json = JSONObject().apply {
            put("to", "/topics/anuncios")
            put("notification", JSONObject().apply {
                put("title", "El administrador $adminName ha publicado un anuncio:")
                put("body", bodyText)
                put("sound", "default")
            })
            put("data", JSONObject().apply {
                put("destination", "announcements")
            })
        }

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(FCM_URL)
            .addHeader("Authorization", "key=$FCM_SERVER_KEY")
            .post(requestBody)
            .build()

        // Lo enviamos en un hilo separado para no bloquear
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("AnnouncementRepo", "Fallo al enviar notificación: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d("AnnouncementRepo", "Notificación enviada con éxito: ${response.body?.string()}")
            }
        })
    }

    suspend fun deleteAnnouncement(id: String) {
        collection.document(id).delete().await()
    }

    suspend fun toggleReaction(announcementId: String, reactionType: String) {
        val userId = auth.currentUser?.uid ?: return
        val docRef = collection.document(announcementId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val reactions = snapshot.get("reactions") as? Map<String, String> ?: emptyMap()
            val newReactions = reactions.toMutableMap()

            if (newReactions[userId] == reactionType) {
                newReactions.remove(userId)
            } else {
                newReactions[userId] = reactionType
            }
            transaction.update(docRef, "reactions", newReactions)
        }.await()
    }
}
