package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class Announcement(
    val id: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val reactions: Map<String, String> = emptyMap() // userId -> emoji/tipo
)
