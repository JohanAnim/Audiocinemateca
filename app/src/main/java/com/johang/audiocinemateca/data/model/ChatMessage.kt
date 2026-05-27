package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp
import com.johang.audiocinemateca.presentation.aichat.LinkedContent

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val mentions: List<String> = emptyList(),
    val replyToMessageId: String? = null,
    val replyToSenderName: String? = null,
    val replyToSenderId: String? = null,
    val replyToText: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val edited: Boolean = false,
    val linkedItems: List<LinkedContent> = emptyList()
)
