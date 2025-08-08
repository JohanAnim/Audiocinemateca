package com.johang.audiocinemateca.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Movie(
    override val id: String,
    @SerializedName("Titulo") override val title: String,
    override val anio: String,
    override val genero: String,
    override val pais: String,
    override val director: String,
    override val guion: String,
    override val musica: String,
    override val fotografia: String,
    override val reparto: String,
    override val productora: String,
    override val narracion: String,
    override val duracion: String,
    override val idioma: String,
    val partes: String,
    override val filmaffinity: String,
    override val sinopsis: String,
    val enlaces: List<String>
) : com.johang.audiocinemateca.domain.model.CatalogItem, Parcelable
