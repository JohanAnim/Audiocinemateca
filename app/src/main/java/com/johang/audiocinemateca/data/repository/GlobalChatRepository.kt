package com.johang.audiocinemateca.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.data.model.OnlineUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalChatRepository @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()

    companion object {
        private const val PRESENCE_BASE_URL = "http://207.231.110.156/audiocinemateca-server/api/presence"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    fun getMessages(): Flow<List<ChatMessage>> = callbackFlow {
        val subscription = db.collection("global_chat")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc: DocumentSnapshot ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(messages.reversed())
            }
        awaitClose { subscription.remove() }
    }

    fun getChatStatus(): Flow<Boolean> = callbackFlow {
        val subscription = db.collection("config").document("chat_global")
            .addSnapshotListener { snapshot: DocumentSnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val isOpen = snapshot?.getBoolean("isOpen") ?: true
                trySend(isOpen)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun sendMessage(message: ChatMessage) {
        db.collection("global_chat").add(message).await()
    }

    suspend fun updateMessage(messageId: String, newText: String) {
        db.collection("global_chat").document(messageId)
            .update(
                "text", newText,
                "edited", true
            ).await()
    }

    suspend fun deleteMessage(messageId: String, isByAdmin: Boolean = true) {
        if (isByAdmin) {
            db.collection("global_chat").document(messageId)
                .update(
                    "text", "Un admin eliminó este mensaje",
                    "isDeleted", true
                ).await()
        } else {
            db.collection("global_chat").document(messageId).delete().await()
        }
    }

    suspend fun updateMessageReactions(messageId: String, reactions: Map<String, List<String>>) {
        db.collection("global_chat").document(messageId)
            .update("reactions", reactions).await()
    }

    private val _refreshPresenceTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun triggerPresenceRefresh() {
        _refreshPresenceTrigger.tryEmit(Unit)
    }

    fun setUserPresence(userId: String, displayName: String? = null, email: String? = null, isOnline: Boolean = true) {
        if (userId.isBlank()) return

        val cleanEmail = email ?: ""
        val resolvedName = when {
            !displayName.isNullOrBlank() -> displayName
            cleanEmail.isNotBlank() -> cleanEmail.substringBefore("@")
            else -> "Usuario #${userId.takeLast(4)}"
        }

        val deviceInfo = "Android ${android.os.Build.VERSION.RELEASE}"

        // Sincronización de respaldo transparente en Firestore
        try {
            val firestoreData = hashMapOf<String, Any>(
                "userId" to userId,
                "displayName" to resolvedName,
                "email" to cleanEmail,
                "online" to isOnline,
                "deviceInfo" to deviceInfo,
                "lastActive" to FieldValue.serverTimestamp()
            )
            db.collection("presence").document(userId).set(firestoreData, SetOptions.merge())
        } catch (e: Exception) {}

        if (!isOnline) {
            val bodyMap = mapOf("userId" to userId)
            val jsonPayload = gson.toJson(bodyMap)
            val request = Request.Builder()
                .url("$PRESENCE_BASE_URL/offline")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {}
                override fun onResponse(call: Call, response: Response) { 
                    response.close()
                    _refreshPresenceTrigger.tryEmit(Unit)
                }
            })
            return
        }

        fun sendHttpHeartbeat(token: String?) {
            val bodyMap = mutableMapOf<String, Any>(
                "userId" to userId,
                "displayName" to resolvedName,
                "email" to cleanEmail,
                "deviceInfo" to deviceInfo
            )
            if (!token.isNullOrEmpty()) {
                bodyMap["fcmToken"] = token
            }

            val jsonPayload = gson.toJson(bodyMap)
            val request = Request.Builder()
                .url("$PRESENCE_BASE_URL/heartbeat")
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            okHttpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {}
                override fun onResponse(call: Call, response: Response) { 
                    response.close()
                    _refreshPresenceTrigger.tryEmit(Unit)
                }
            })
        }

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    sendHttpHeartbeat(token)
                }
        } catch (e: Exception) {
            sendHttpHeartbeat(null)
        }
    }

    suspend fun fetchOnlineUsers(): List<OnlineUser> = withContext(Dispatchers.IO) {
        val result = mutableListOf<OnlineUser>()
        try {
            val request = Request.Builder()
                .url("$PRESENCE_BASE_URL/list")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyString = response.body.string()
                response.close()

                val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
                if (jsonObject != null && jsonObject.has("users")) {
                    val usersArray = jsonObject.getAsJsonArray("users")
                    for (element in usersArray) {
                        val uObj = element.asJsonObject
                        val uid = if (uObj.has("userId") && !uObj.get("userId").isJsonNull) uObj.get("userId").asString else ""
                        val name = if (uObj.has("displayName") && !uObj.get("displayName").isJsonNull) uObj.get("displayName").asString else ""
                        val mail = if (uObj.has("email") && !uObj.get("email").isJsonNull) uObj.get("email").asString else ""
                        val online = if (uObj.has("online") && !uObj.get("online").isJsonNull) uObj.get("online").asBoolean else true
                        val token = if (uObj.has("fcmToken") && !uObj.get("fcmToken").isJsonNull) uObj.get("fcmToken").asString else null
                        val devInfo = if (uObj.has("deviceInfo") && !uObj.get("deviceInfo").isJsonNull) uObj.get("deviceInfo").asString else null
                        val lastActiveMs = if (uObj.has("lastActive") && !uObj.get("lastActive").isJsonNull) uObj.get("lastActive").asLong else System.currentTimeMillis()

                        val resolvedName = when {
                            name.isNotBlank() -> name
                            mail.isNotBlank() -> mail.substringBefore("@")
                            else -> "Usuario #${uid.takeLast(4)}"
                        }

                        result.add(
                            OnlineUser(
                                userId = uid,
                                displayName = resolvedName,
                                email = mail,
                                online = online,
                                lastActive = Timestamp(lastActiveMs / 1000, ((lastActiveMs % 1000) * 1_000_000).toInt()),
                                fcmToken = token,
                                deviceInfo = devInfo
                            )
                        )
                    }
                }
            } else {
                response.close()
            }
        } catch (e: Exception) {}

        // Capa de respaldo Firestore si Node.js no responde o está vacío
        if (result.isEmpty()) {
            try {
                val nowSec = Timestamp.now().seconds
                val snap = db.collection("presence").limit(50).get().await()

                for (doc in snap.documents) {
                    val isOnline = doc.getBoolean("online") ?: false
                    val ts = doc.getTimestamp("lastActive")
                    val lastActiveSec = ts?.seconds ?: 0L
                    
                    // Filtrar en memoria para no requerir índices compuestos en Firestore
                    if (isOnline && (nowSec - lastActiveSec) < 180L) {
                        val uid = doc.getString("userId") ?: doc.id
                        val name = doc.getString("displayName") ?: ""
                        val mail = doc.getString("email") ?: ""
                        val devInfo = doc.getString("deviceInfo") ?: "Android"
                        val token = doc.getString("fcmToken")

                        val resolvedName = when {
                            name.isNotBlank() -> name
                            mail.isNotBlank() -> mail.substringBefore("@")
                            else -> "Usuario #${uid.takeLast(4)}"
                        }

                        result.add(
                            OnlineUser(
                                userId = uid,
                                displayName = resolvedName,
                                email = mail,
                                online = true,
                                lastActive = ts,
                                fcmToken = token,
                                deviceInfo = devInfo
                            )
                        )
                    }
                }
            } catch (e: Exception) {}
        }

        // Garantizar que el usuario activo actual siempre se muestre conectado
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val alreadyPresent = result.any { it.userId == currentUser.uid }
                if (!alreadyPresent) {
                    val resolvedName = when {
                        !currentUser.displayName.isNullOrBlank() -> currentUser.displayName!!
                        !currentUser.email.isNullOrBlank() -> currentUser.email!!.substringBefore("@")
                        else -> "Usuario #${currentUser.uid.takeLast(4)}"
                    }
                    result.add(0, OnlineUser(
                        userId = currentUser.uid,
                        displayName = resolvedName,
                        email = currentUser.email ?: "",
                        online = true,
                        lastActive = Timestamp.now(),
                        deviceInfo = "Android ${android.os.Build.VERSION.RELEASE}"
                    ))
                }
            }
        } catch (e: Exception) {}

        result
    }

    fun getOnlineUsersFlow(): Flow<List<OnlineUser>> = callbackFlow {
        val subscription = db.collection("presence")
            .addSnapshotListener { snapshot: QuerySnapshot?, error: FirebaseFirestoreException? ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val nowSec = Timestamp.now().seconds
                val onlineList = mutableListOf<OnlineUser>()

                snapshot?.documents?.forEach { doc ->
                    val isOnline = doc.getBoolean("online") ?: false
                    val ts = doc.getTimestamp("lastActive")
                    val lastActiveSec = ts?.seconds ?: 0L

                    // Si está marcado online y tuvo actividad en los últimos 2 minutos
                    if (isOnline && (nowSec - lastActiveSec) < 120L) {
                        val uid = doc.getString("userId") ?: doc.id
                        val name = doc.getString("displayName") ?: ""
                        val mail = doc.getString("email") ?: ""
                        val devInfo = doc.getString("deviceInfo") ?: "Android"
                        val token = doc.getString("fcmToken")

                        val resolvedName = when {
                            name.isNotBlank() -> name
                            mail.isNotBlank() -> mail.substringBefore("@")
                            else -> "Usuario #${uid.takeLast(4)}"
                        }

                        onlineList.add(
                            OnlineUser(
                                userId = uid,
                                displayName = resolvedName,
                                email = mail,
                                online = true,
                                lastActive = ts,
                                fcmToken = token,
                                deviceInfo = devInfo
                            )
                        )
                    }
                }

                // Garantizar que el usuario activo actual en este dispositivo siempre se muestre conectado
                try {
                    val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                    if (currentUser != null && onlineList.none { it.userId == currentUser.uid }) {
                        val resolvedName = when {
                            !currentUser.displayName.isNullOrBlank() -> currentUser.displayName!!
                            !currentUser.email.isNullOrBlank() -> currentUser.email!!.substringBefore("@")
                            else -> "Usuario #${currentUser.uid.takeLast(4)}"
                        }
                        onlineList.add(0, OnlineUser(
                            userId = currentUser.uid,
                            displayName = resolvedName,
                            email = currentUser.email ?: "",
                            online = true,
                            lastActive = Timestamp.now(),
                            deviceInfo = "Android ${android.os.Build.VERSION.RELEASE}"
                        ))
                    }
                } catch (e: Exception) {}

                trySend(onlineList)
            }

        awaitClose { subscription.remove() }
    }

    fun getOnlineCount(): Flow<Int> = getOnlineUsersFlow().map { it.size }

    suspend fun isUserBanned(userId: String): Pair<Boolean, String?> {
        try {
            val doc = db.collection("banned_users").document(userId).get().await()
            if (doc.exists()) {
                val bannedUser = doc.toObject(com.johang.audiocinemateca.data.model.BannedUser::class.java)
                val until = bannedUser?.bannedUntil
                if (until != null) {
                    val nowSec = Timestamp.now().seconds
                    if (nowSec > until.seconds) {
                        unbanUser(userId)
                        return Pair(false, null)
                    }
                }
                return Pair(true, bannedUser?.reason ?: "Infracción de las reglas de la comunidad")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(false, null)
    }

    suspend fun banUser(bannedUser: com.johang.audiocinemateca.data.model.BannedUser) {
        db.collection("banned_users").document(bannedUser.userId)
            .set(bannedUser).await()
    }

    suspend fun unbanUser(userId: String) {
        db.collection("banned_users").document(userId).delete().await()
    }

    fun getBannedUsersFlow(): Flow<List<com.johang.audiocinemateca.data.model.BannedUser>> = callbackFlow {
        val subscription = db.collection("banned_users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(com.johang.audiocinemateca.data.model.BannedUser::class.java)?.copy(userId = doc.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }
}
