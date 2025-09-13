package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity

@Entity(tableName = "search_history", primaryKeys = ["query"])
data class SearchHistoryEntity(
    val query: String,
    val timestamp: Long
)
