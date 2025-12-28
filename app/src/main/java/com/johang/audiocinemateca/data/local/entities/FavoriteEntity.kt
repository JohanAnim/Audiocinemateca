package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val contentType: String, // "movie", "serie", "documentary", "shortfilm"
    val addedAt: Long = System.currentTimeMillis()
)