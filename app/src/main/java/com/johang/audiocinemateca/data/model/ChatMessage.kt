package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val mentions: List<String> = emptyList()
)
