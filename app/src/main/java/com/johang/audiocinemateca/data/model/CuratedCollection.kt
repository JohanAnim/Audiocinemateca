package com.johang.audiocinemateca.data.model

data class CuratedCollection(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val items: List<CuratedItem> = emptyList()
)

data class CuratedItem(
    val id: String = "",
    val title: String = "",
    val type: String = "peliculas",
    val year: String = "",
    val genre: String = ""
)
