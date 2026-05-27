package com.johang.audiocinemateca.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.johang.audiocinemateca.data.model.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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

    suspend fun deleteMessage(messageId: String) {
        db.collection("global_chat").document(messageId).delete().await()
    }

    suspend fun updateMessageReactions(messageId: String, reactions: Map<String, List<String>>) {
        db.collection("global_chat").document(messageId)
            .update("reactions", reactions).await()
    }

    fun setUserPresence(userId: String, isOnline: Boolean) {
        val presenceRef = db.collection("presence").document(userId)
        presenceRef.set(mapOf(
            "online" to isOnline,
            "lastActive" to com.google.firebase.Timestamp.now()
        ))
    }

    fun getOnlineCount(): Flow<Int> = callbackFlow {
        val subscription = db.collection("presence")
            .whereEqualTo("online", true)
            .addSnapshotListener { snapshot, _ ->
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { subscription.remove() }
    }
}
