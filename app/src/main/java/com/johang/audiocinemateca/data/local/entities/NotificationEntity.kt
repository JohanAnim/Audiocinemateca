package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val remoteId: String? = null, // ID de Firestore o FCM para evitar duplicados
    val title: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val linkUrl: String? = null, // Para guardar enlaces de donación o recomendaciones
    val destination: String? = null // Para guardar el destino interno (p. ej. "announcements")
)
