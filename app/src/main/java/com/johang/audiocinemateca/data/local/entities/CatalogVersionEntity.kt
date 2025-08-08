package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_version")
data class CatalogVersionEntity(
    @PrimaryKey
    val id: String,
    val versionDate: String
)