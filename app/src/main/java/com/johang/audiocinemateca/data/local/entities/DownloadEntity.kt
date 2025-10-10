package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity

@Entity(tableName = "downloads", primaryKeys = ["contentId", "partIndex", "episodeIndex"])
data class DownloadEntity(
    val contentId: String,
    val contentType: String, // "movie", "serie", "documentary", "shortfilm"
    val title: String,
    val partIndex: Int, // Para películas/documentales con partes, o temporada para series
    val episodeIndex: Int, // Para series
    val downloadStatus: String, // QUEUED, DOWNLOADING, COMPLETE, FAILED
    val filePath: String?, // Ruta al archivo descargado
    val downloadedAt: Long, // Timestamp de la descarga
    val totalSizeMb: Double,
    val durationMs: Long = 0,
    val errorMessage: String? = null
)
