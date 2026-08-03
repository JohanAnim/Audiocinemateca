package com.johang.audiocinemateca.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.johang.audiocinemateca.data.model.LiveJamSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class JamRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @Named("AudiocinematecaClient") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val COLLECTION_JAMS = "global_chat_jams"
        private const val DOC_CURRENT_JAM = "current_jam"
        private const val NODE_BASE_URL = "http://207.231.110.156/audiocinemateca-server/api/jam"
        private const val NODE_SSL_BASE_URL = "https://207.231.110.156/audiocinemateca-server/api/jam"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()

    /**
     * Escucha en TIEMPO REAL la sala de Jam activa (Prioriza Node.js SSE, con respaldo en Firestore).
     */
    fun observeCurrentJam(): Flow<LiveJamSession?> = callbackFlow {
        val request = Request.Builder()
            .url("$NODE_BASE_URL/stream")
            .header("Accept", "text/event-stream")
            .build()

        var call: Call? = null
        var isNodeConnected = false

        try {
            call = okHttpClient.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    android.util.Log.e("JamRepository", "⚠️ No se pudo conectar a Node.js SSE (puerto 4892) (${e.message}). Intentando fallback...")
                    if (!isNodeConnected) {
                        trySslNodeStream(this@callbackFlow)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        android.util.Log.e("JamRepository", "⚠️ Respuesta no exitosa de Node.js SSE (${response.code}). Intentando fallback...")
                        trySslNodeStream(this@callbackFlow)
                        return
                    }
                    isNodeConnected = true
                    android.util.Log.d("JamRepository", "🌐 Conectado exitosamente al stream de Jam en Node.js (puerto 4892)")
                    try {
                        val source = response.body?.source() ?: return
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6).trim()
                                if (data == "null" || data.isBlank()) {
                                    trySend(null)
                                } else {
                                    val jam = gson.fromJson(data, LiveJamSession::class.java)
                                    if (jam != null && jam.jamId.isNotBlank() && jam.contentId.isNotBlank()) {
                                        trySend(jam)
                                    } else {
                                        trySend(null)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        attachFirestoreListener(this@callbackFlow)
                    }
                }
            })
        } catch (e: Exception) {
            attachFirestoreListener(this)
        }

        awaitClose {
            try { call?.cancel() } catch (e: Exception) {}
        }
    }

    private fun trySslNodeStream(scope: kotlinx.coroutines.channels.ProducerScope<LiveJamSession?>) {
        val request = Request.Builder()
            .url("$NODE_SSL_BASE_URL/stream")
            .header("Accept", "text/event-stream")
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                android.util.Log.e("JamRepository", "⚠️ Falló SSL SSE (${e.message}). Recurriendo a Firestore...")
                attachFirestoreListener(scope)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    android.util.Log.e("JamRepository", "⚠️ Respuesta SSL no exitosa (${response.code}). Recurriendo a Firestore...")
                    attachFirestoreListener(scope)
                    return
                }
                android.util.Log.d("JamRepository", "🌐 Conectado exitosamente al stream de Jam en Node.js (SSL)")
                try {
                    val source = response.body?.source() ?: return
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val data = line.substring(6).trim()
                            if (data == "null" || data.isBlank()) {
                                scope.trySend(null)
                            } else {
                                val jam = gson.fromJson(data, LiveJamSession::class.java)
                                if (jam != null && jam.jamId.isNotBlank() && jam.contentId.isNotBlank()) {
                                    scope.trySend(jam)
                                } else {
                                    scope.trySend(null)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    attachFirestoreListener(scope)
                }
            }
        })
    }

    private fun attachFirestoreListener(scope: kotlinx.coroutines.channels.ProducerScope<LiveJamSession?>) {
        val docRef = firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                scope.trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val jam = snapshot.toObject(LiveJamSession::class.java)
                if (jam != null && jam.jamId.isNotBlank() && jam.contentId.isNotBlank()) {
                    scope.trySend(jam)
                } else {
                    scope.trySend(null)
                }
            } else {
                scope.trySend(null)
            }
        }
    }

    /**
     * Inicia una nueva sesión de Jam (Exclusivo Administrador).
     */
    suspend fun startJam(
        hostUserId: String,
        hostName: String,
        contentId: String,
        title: String,
        contentType: String,
        partIndex: Int = 0,
        episodeIndex: Int = 0,
        episodeTitle: String = "",
        pinCode: String = ""
    ): Boolean {
        val now = System.currentTimeMillis()
        val jamId = "jam_${now}"
        val newJam = LiveJamSession(
            jamId = jamId,
            hostUserId = hostUserId,
            hostName = hostName,
            contentId = contentId,
            title = title,
            contentType = contentType,
            partIndex = partIndex,
            episodeIndex = episodeIndex,
            episodeTitle = episodeTitle,
            isPlaying = true,
            positionMs = 0L,
            lastUpdatedTimestamp = now,
            activeListenersCount = 1,
            pinCode = pinCode
        )

        // 1. Enviar a Servidor Linux Node.js
        try {
            val jsonPayload = gson.toJson(newJam)
            val req = Request.Builder()
                .url("$NODE_BASE_URL/start")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            resp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Backup opcional a Firestore
        try {
            firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM).set(newJam).await()
            if (pinCode.isBlank()) {
                val announcementText = "🔴 ¡$hostName ha iniciado una transmisión en vivo de '$title'! Únete desde la cabecera del chat."
                val announcementMsg = com.johang.audiocinemateca.data.model.ChatMessage(
                    senderId = "system_announcement",
                    senderName = "🎙️ Jam en Vivo",
                    text = announcementText,
                    timestamp = com.google.firebase.Timestamp.now()
                )
                firestore.collection("global_chat").add(announcementMsg).await()
            }
        } catch (e: Exception) {}

        return true
    }

    /**
     * Sincroniza la posición y estado de reproducción del Jam (Anfitrión).
     */
    suspend fun updateJamProgress(positionMs: Long, isPlaying: Boolean) {
        val now = System.currentTimeMillis()
        // 1. Enviar a Servidor Linux Node.js
        try {
            val bodyMap = mapOf("positionMs" to positionMs, "isPlaying" to isPlaying)
            val jsonPayload = gson.toJson(bodyMap)
            val req = Request.Builder()
                .url("$NODE_BASE_URL/update")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val resp = okHttpClient.newCall(req).execute()
            resp.close()
        } catch (e: Exception) {}

        // 2. Backup a Firestore
        try {
            firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM)
                .update(mapOf("positionMs" to positionMs, "isPlaying" to isPlaying, "lastUpdatedTimestamp" to now))
                .await()
        } catch (e: Exception) {}
    }

    /**
     * Finaliza la sesión de Jam activa.
     */
    suspend fun endJam(): Boolean {
        // 1. Enviar a Servidor Linux Node.js
        try {
            val req = Request.Builder().url("$NODE_BASE_URL/end").post("{}".toRequestBody(JSON_MEDIA_TYPE)).build()
            val resp = okHttpClient.newCall(req).execute()
            resp.close()
        } catch (e: Exception) {}

        // 2. Backup a Firestore
        try {
            firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM).delete().await()
        } catch (e: Exception) {}

        return true
    }

    /**
     * Incrementa el contador de oyentes en vivo.
     */
    suspend fun joinJam() {
        try {
            val req = Request.Builder().url("$NODE_BASE_URL/join").post("{}".toRequestBody(JSON_MEDIA_TYPE)).build()
            val resp = okHttpClient.newCall(req).execute()
            resp.close()
        } catch (e: Exception) {}

        try {
            firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM)
                .update("activeListenersCount", FieldValue.increment(1)).await()
        } catch (e: Exception) {}
    }

    /**
     * Decrementa el contador de oyentes en vivo.
     */
    suspend fun leaveJam() {
        try {
            val req = Request.Builder().url("$NODE_BASE_URL/leave").post("{}".toRequestBody(JSON_MEDIA_TYPE)).build()
            val resp = okHttpClient.newCall(req).execute()
            resp.close()
        } catch (e: Exception) {}

        try {
            firestore.collection(COLLECTION_JAMS).document(DOC_CURRENT_JAM)
                .update("activeListenersCount", FieldValue.increment(-1)).await()
        } catch (e: Exception) {}
    }
}
