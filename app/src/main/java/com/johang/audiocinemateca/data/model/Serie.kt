package com.johang.audiocinemateca.data.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Serie(
    override val id: String,
    @SerializedName("titulo") override val title: String,
    override val anio: String,
    override val duracion: String,
    override val pais: String,
    override val director: String,
    override val guion: String,
    override val musica: String,
    override val fotografia: String,
    override val reparto: String,
    override val genero: String,
    val temporadas: String,
    override val idioma: String,
    override val narracion: String,
    override val filmaffinity: String,
    override val sinopsis: String,
    override val productora: String,
    val capitulos: Map<String, List<Episode>>
) : com.johang.audiocinemateca.domain.model.CatalogItem, Parcelable

@Parcelize
data class Episode(
    val capitulo: String,
    val titulo: String,
    val enlace: String
) : Parcelable
