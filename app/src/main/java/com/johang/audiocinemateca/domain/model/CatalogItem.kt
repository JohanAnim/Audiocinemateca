package com.johang.audiocinemateca.domain.model

import android.os.Parcelable

interface CatalogItem : Parcelable {
    val id: String
    val title: String
    val anio: String
    val genero: String
    val pais: String
    val director: String
    val guion: String
    val musica: String
    val fotografia: String
    val reparto: String
    val productora: String
    val narracion: String
    val duracion: String
    val idioma: String
    val filmaffinity: String
    val sinopsis: String
}