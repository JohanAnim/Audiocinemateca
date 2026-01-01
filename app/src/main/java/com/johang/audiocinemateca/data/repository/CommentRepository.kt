package com.johang.audiocinemateca.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.johang.audiocinemateca.data.model.Comment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val collection = firestore.collection("content_comments")

    private fun getDocId(contentId: String, partIndex: Int, episodeIndex: Int): String {
        return if (episodeIndex != -1) "${contentId}_S${partIndex}_E${episodeIndex}" else contentId
    }

    /**
     * Obtiene el total de comentarios y el más reciente para la vista previa.
     */
    fun getCommentsPreview(contentId: String, partIndex: Int, episodeIndex: Int): Flow<Pair<Long, Comment?>> = callbackFlow {
        val docId = getDocId(contentId, partIndex, episodeIndex)
        val commentsRef = collection.document(docId).collection("comments")
        
        val listener = commentsRef.orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val count = snapshot?.size()?.toLong() ?: 0L
                val doc = snapshot?.documents?.firstOrNull()
                val latestComment = doc?.toObject(Comment::class.java)?.copy(id = doc.id)
                
                trySend(Pair(count, latestComment))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Envía un nuevo comentario.
     */
    suspend fun addComment(contentId: String, partIndex: Int, episodeIndex: Int, text: String) {
        val user = auth.currentUser ?: return
        val docId = getDocId(contentId, partIndex, episodeIndex)
        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Usuario anónimo",
            commentText = text
        )
        collection.document(docId).collection("comments").add(comment).await()
    }
    
    /**
     * Alterna el "Me gusta" en un comentario específico.
     */
    suspend fun toggleCommentLike(contentId: String, partIndex: Int, episodeIndex: Int, commentId: String) {
        val userId = auth.currentUser?.uid ?: return
        val docId = getDocId(contentId, partIndex, episodeIndex)
        val commentRef = collection.document(docId).collection("comments").document(commentId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(commentRef)
            val data = snapshot.data
            val currentLikes = data?.get("likes") as? Map<*, *> ?: emptyMap<String, Boolean>()
            
            val newLikes = currentLikes.toMutableMap()
            if (newLikes.containsKey(userId)) {
                newLikes.remove(userId)
            } else {
                newLikes[userId] = true
            }
            
            transaction.update(commentRef, "likes", newLikes)
        }.await()
    }

    /**
     * Elimina un comentario específico.
     */
    suspend fun deleteComment(contentId: String, partIndex: Int, episodeIndex: Int, commentId: String) {
        val docId = getDocId(contentId, partIndex, episodeIndex)
        collection.document(docId).collection("comments").document(commentId).delete().await()
    }

    /**
     * Reporta un comentario (No lo elimina, solo crea una alerta para admin).
     */
    suspend fun reportComment(contentId: String, partIndex: Int, episodeIndex: Int, comment: Comment) {
        val userId = auth.currentUser?.uid ?: return
        val report = hashMapOf(
            "reportedBy" to userId,
            "commentId" to comment.id,
            "commentAuthorId" to comment.userId,
            "commentText" to comment.commentText,
            "contentId" to contentId,
            "partIndex" to partIndex,
            "episodeIndex" to episodeIndex,
            "timestamp" to com.google.firebase.Timestamp.now(),
            "status" to "pending" // pending, reviewed, dismissed, deleted
        )
        firestore.collection("comment_reports").add(report).await()
    }

    /**
     * Obtiene todos los comentarios.
     */
    fun getAllComments(contentId: String, partIndex: Int, episodeIndex: Int): Flow<List<Comment>> = callbackFlow {
        val docId = getDocId(contentId, partIndex, episodeIndex)
        val listener = collection.document(docId).collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val comments = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Comment::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(comments)
            }
        awaitClose { listener.remove() }
    }
}
