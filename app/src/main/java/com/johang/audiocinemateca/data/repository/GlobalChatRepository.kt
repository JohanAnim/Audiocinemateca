package com.johang.audiocinemateca.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.data.model.OnlineUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalChatRepository @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()

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

    fun setUserPresence(userId: String, displayName: String? = null, email: String? = null, isOnline: Boolean = true) {
        val presenceRef = db.collection("presence").document(userId)
        val userRef = db.collection("users").document(userId)
        val cleanEmail = email ?: ""
        val resolvedName = when {
            !displayName.isNullOrBlank() -> displayName
            cleanEmail.isNotBlank() -> cleanEmail.substringBefore("@")
            else -> "Usuario #${userId.takeLast(4)}"
        }

        val data = mutableMapOf<String, Any>(
            "userId" to userId,
            "online" to isOnline,
            "displayName" to resolvedName,
            "email" to cleanEmail,
            "lastActive" to Timestamp.now()
        )

        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result else null
                if (!token.isNullOrEmpty()) {
                    data["fcmToken"] = token
                    userRef.set(mapOf("fcmToken" to token, "displayName" to resolvedName, "email" to cleanEmail), com.google.firebase.firestore.SetOptions.merge())
                }
                presenceRef.set(data, com.google.firebase.firestore.SetOptions.merge())
            }
    }

    fun getOnlineUsersFlow(): Flow<List<OnlineUser>> = callbackFlow {
        val subscription = db.collection("presence")
            .whereEqualTo("online", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val nowSec = Timestamp.now().seconds
                val activeUsers = snapshot?.documents?.mapNotNull { doc ->
                    val user = doc.toObject(OnlineUser::class.java)?.copy(userId = doc.id)
                    user?.let {
                        val resolvedName = when {
                            it.displayName.isNotBlank() -> it.displayName
                            it.email.isNotBlank() -> it.email.substringBefore("@")
                            else -> "Usuario #${it.userId.takeLast(4)}"
                        }
                        it.copy(displayName = resolvedName)
                    }
                }?.filter { user ->
                    // Filtrar usuarios cuya presencia haya sido registrada en los últimos 90 segundos
                    val lastActiveSec = user.lastActive?.seconds ?: 0L
                    (nowSec - lastActiveSec) <= 90
                } ?: emptyList()

                trySend(activeUsers)
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
