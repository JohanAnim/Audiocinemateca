package com.johang.audiocinemateca.presentation.aichat

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LinkedContent(
    val id: String,
    val title: String,
    val type: String
) : Parcelable

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val isSilent: Boolean = false,
    val linkedItems: List<LinkedContent> = emptyList()
)