package com.johang.audiocinemateca.presentation.aichat

import android.os.Parcelable
import com.johang.audiocinemateca.data.remote.ai.Content
import kotlinx.parcelize.Parcelize

@Parcelize
data class LinkedContent(
    val id: String = "",
    val title: String = "",
    val type: String = ""
) : Parcelable

data class ChatMessage(
    val id: String = "",
    val text: String = "",
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isSilent: Boolean = false,
    val linkedItems: List<LinkedContent> = emptyList(),
    val expressiveSpeechText: String? = null,
    val expressiveStyleHint: String? = null
)

data class AuraChatSession(
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val aiHistory: List<Content> = emptyList()
)
