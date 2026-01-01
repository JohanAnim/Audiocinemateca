package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress", primaryKeys = ["contentId", "partIndex", "episodeIndex"])
data class PlaybackProgressEntity(
    val contentId: String,
    val contentType: String,
    val currentPositionMs: Long,
    val totalDurationMs: Long,
    val partIndex: Int,
    val episodeIndex: Int,
    val lastPlayedTimestamp: Long,
    val isFinished: Boolean = false
)