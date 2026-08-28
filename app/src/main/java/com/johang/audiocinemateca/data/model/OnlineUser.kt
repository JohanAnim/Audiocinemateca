package com.johang.audiocinemateca.data.model

import com.google.firebase.Timestamp

data class OnlineUser(
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val online: Boolean = false,
    val lastActive: Timestamp? = null,
    val fcmToken: String? = null,
    val deviceInfo: String? = null
)
