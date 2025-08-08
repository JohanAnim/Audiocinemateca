package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress", primaryKeys = ["contentId", "partIndex", "episodeIndex"])
data class PlaybackProgressEntity(
    val contentId: String,
    val contentType: String, // "movie", "serie", "documentary", "shortfilm"
    val currentPositionMs: Long,
    val totalDurationMs: Long,
    val partIndex: Int, // Para películas/documentales con partes, o temporada para series
    val episodeIndex: Int, // Para series
    val lastPlayedTimestamp: Long
)