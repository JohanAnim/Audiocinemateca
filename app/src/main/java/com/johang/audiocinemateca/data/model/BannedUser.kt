package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class BannedUser(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val reason: String = "Infracción de las reglas de la comunidad",
    val bannedBy: String = "",
    val bannedUntil: Timestamp? = null,
    val createdAt: Timestamp = Timestamp.now()
)
