package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_data")
data class CatalogDataEntity(
    @PrimaryKey
    val id: String,
    val data: String
)