package com.johang.audiocinemateca.data.model

import com.google.gson.annotations.SerializedName

data class CatalogResponse(
    @SerializedName("peliculas") val movies: List<Movie>?,
    @SerializedName("series") val series: List<Serie>?,
    @SerializedName("documentales") val documentaries: List<Documentary>?,
    @SerializedName("cortometrajes") val shortFilms: List<ShortFilm>?
)