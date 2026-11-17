package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = CatalogItemEntity::class)
@Entity(tableName = "catalog_items_fts")
data class CatalogItemFtsEntity(
    val titleNorm: String,
    val searchTextNorm: String
)
