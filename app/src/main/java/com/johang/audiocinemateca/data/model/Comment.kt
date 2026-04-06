package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class Comment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val commentText: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likes: Map<String, Boolean> = emptyMap(), // userId -> true
    val isPending: Boolean = false,
    // Nuevos campos para la sección de Comunidad
    val contentId: String = "",
    val contentTitle: String = "",
    val contentType: String = "",
    val partIndex: Int = -1,
    val episodeIndex: Int = -1
)