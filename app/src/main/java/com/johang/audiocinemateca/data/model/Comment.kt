package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class Comment(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val commentText: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val likes: Map<String, Boolean> = emptyMap() // userId -> true
)