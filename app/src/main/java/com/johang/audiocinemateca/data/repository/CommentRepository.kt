package com.johang.audiocinemateca.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
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

    data class GlobalCommentsResult(
        val comments: List<Comment>,
        val lastVisible: DocumentSnapshot?
    )

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
     * Envía un nuevo comentario con metadatos.
     */
    suspend fun addComment(
        contentId: String,
        contentTitle: String,
        contentType: String,
        partIndex: Int,
        episodeIndex: Int,
        text: String
    ) {
        val user = auth.currentUser ?: return
        val docId = getDocId(contentId, partIndex, episodeIndex)
        val comment = Comment(
            userId = user.uid,
            userName = user.displayName ?: "Usuario anónimo",
            commentText = text,
            contentId = contentId,
            contentTitle = contentTitle,
            contentType = contentType,
            partIndex = partIndex,
            episodeIndex = episodeIndex
        )
        collection.document(docId).collection("comments").add(comment).await()
    }

    /**
     * Obtiene comentarios globales con paginación.
     */
    fun getGlobalComments(filter: String, lastVisible: DocumentSnapshot? = null): Flow<GlobalCommentsResult> = callbackFlow {
        val user = auth.currentUser
        val queryGroup = firestore.collectionGroup("comments")
        
        var baseQuery: Query = when (filter) {
            "Más antiguos" -> queryGroup.orderBy("timestamp", Query.Direction.ASCENDING)
            "Mis comentarios" -> {
                if (user != null) queryGroup.whereEqualTo("userId", user.uid).orderBy("timestamp", Query.Direction.DESCENDING)
                else queryGroup.orderBy("timestamp", Query.Direction.DESCENDING)
            }
            else -> queryGroup.orderBy("timestamp", Query.Direction.DESCENDING)
        }

        if (lastVisible != null) {
            baseQuery = baseQuery.startAfter(lastVisible)
        }

        val listener = baseQuery.limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val message = error.message ?: ""
                    if (message.contains("https://console.firebase.google.com")) {
                        // Buscamos el inicio de la URL
                        val startIndex = message.indexOf("https://console.firebase.google.com")
                        // Buscamos el final (espacio, coma o fin de cadena)
                        val endIndex = message.indexOf(" ", startIndex).let { if (it == -1) message.length else it }
                        val url = message.substring(startIndex, endIndex).trim().replace("\"", "").replace(",", "")
                        
                        if (url.startsWith("http")) {
                            close(IndexMissingException(url))
                        } else {
                            close(error)
                        }
                    } else {
                        close(error)
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    trySend(GlobalCommentsResult(emptyList(), null))
                    return@addSnapshotListener
                }

                val lastDoc = snapshot.documents.lastOrNull()
                val comments = snapshot.documents.mapNotNull { doc ->
                    val comment = doc.toObject(Comment::class.java)?.copy(id = doc.id)
                    if (comment != null) {
                        val parentDocId = doc.reference.parent.parent?.id ?: ""
                        var realContentId = parentDocId
                        var pIndex = comment.partIndex
                        var eIndex = comment.episodeIndex
                        var detectedType = comment.contentType

                        if (parentDocId.contains("_S") && parentDocId.contains("_E")) {
                            try {
                                realContentId = parentDocId.substringBefore("_S")
                                pIndex = parentDocId.substringAfter("_S").substringBefore("_E").toInt()
                                eIndex = parentDocId.substringAfter("_E").toInt()
                                if (detectedType.isEmpty()) detectedType = "series"
                            } catch (e: Exception) { }
                        }

                        comment.copy(
                            contentId = realContentId,
                            partIndex = pIndex,
                            episodeIndex = eIndex,
                            contentType = detectedType
                        )
                    } else null
                }

                // Ordenamiento local para popularidad (ya que Firestore no lo soporta sin campo denormalizado)
                val finalComments = when (filter) {
                    "Más populares" -> comments.sortedByDescending { it.likes.size }
                    "Menos populares" -> comments.sortedBy { it.likes.size }
                    else -> comments
                }

                trySend(GlobalCommentsResult(finalComments, lastDoc))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Operaciones de Likes, Borrado y Reporte
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
            if (newLikes.containsKey(userId)) newLikes.remove(userId) else newLikes[userId] = true
            transaction.update(commentRef, "likes", newLikes)
        }.await()
    }

    suspend fun deleteComment(contentId: String, partIndex: Int, episodeIndex: Int, commentId: String) {
        val docId = getDocId(contentId, partIndex, episodeIndex)
        collection.document(docId).collection("comments").document(commentId).delete().await()
    }

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
            "status" to "pending"
        )
        firestore.collection("comment_reports").add(report).await()
    }

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

class IndexMissingException(val url: String) : Exception("Falta el índice de Firestore necesario.")