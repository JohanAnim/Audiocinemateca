package com.johang.audiocinemateca.data.model

data class FeaturedBanner(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val rating: Double = 5.0,
    val maxRating: Int = 5,
    val genres: List<String> = emptyList(),
    val linkUrl: String = "",
    val itemId: String = "",
    val itemType: String = "pelicula",
    val hasStartedListening: Boolean = false,
    val isFavorite: Boolean = false
)
