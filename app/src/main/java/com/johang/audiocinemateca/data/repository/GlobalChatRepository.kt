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
        db.collection("global_chat").add(message)
    }
}
