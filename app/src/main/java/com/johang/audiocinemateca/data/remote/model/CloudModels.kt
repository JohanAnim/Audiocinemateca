package com.johang.audiocinemateca.data.remote.model

// Modelo compatible con Firestore para Favoritos
data class CloudFavorite(
    val contentId: String = "",
    val title: String = "",
    val contentType: String = "",
    val addedAt: Long = 0L
)

// Modelo compatible con Firestore para Historial
data class CloudHistory(
    val contentId: String = "",
    val contentType: String = "",
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val partIndex: Int = -1,
    val episodeIndex: Int = -1,
    val lastPlayedTimestamp: Long = 0L,
    val isFinished: Boolean = false
)
