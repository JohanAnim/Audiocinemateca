package com.johang.audiocinemateca.data.local.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "catalog_items",
    primaryKeys = ["category", "id"],
    indices = [
        Index(value = ["category"]),
        Index(value = ["id"]),
        Index(value = ["category", "titleNorm"]),
        Index(value = ["category", "sortOrder"])
    ]
)
data class CatalogItemEntity(
    val id: String,
    val category: String,
    val title: String,
    val titleNorm: String,
    val anio: String,
    val genero: String,
    val pais: String,
    val director: String,
    val guion: String,
    val musica: String,
    val fotografia: String,
    val reparto: String,
    val productora: String,
    val narracion: String,
    val duracion: String,
    val idioma: String,
    val partes: String,
    val filmaffinity: String,
    val sinopsis: String,
    val mediaJson: String,
    val searchTextNorm: String,
    val sortOrder: Int
)
