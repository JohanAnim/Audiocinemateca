package com.johang.audiocinemateca.data.model

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class MovieTypeAdapter : TypeAdapter<Movie>() {
    private val gson = Gson()

    override fun write(out: JsonWriter, value: Movie?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("id").value(value.id)
        out.name("Titulo").value(value.title)
        out.name("anio").value(value.anio)
        out.name("genero").value(value.genero)
        out.name("pais").value(value.pais)
        out.name("director").value(value.director)
        out.name("guion").value(value.guion)
        out.name("musica").value(value.musica)
        out.name("fotografia").value(value.fotografia)
        out.name("reparto").value(value.reparto)
        out.name("productora").value(value.productora)
        out.name("narracion").value(value.narracion)
        out.name("duracion").value(value.duracion)
        out.name("idioma").value(value.idioma)
        out.name("partes").value(value.partes)
        out.name("filmaffinity").value(value.filmaffinity)
        out.name("sinopsis").value(value.sinopsis)
        out.name("enlaces")
        gson.toJson(value.enlaces, object : TypeToken<List<String>>() {}.type, out)
        out.endObject()
    }

    override fun read(reader: JsonReader): Movie? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        var id: String = ""
        var title: String = ""
        var anio: String = ""
        var genero: String = ""
        var pais: String = ""
        var director: String = ""
        var guion: String = ""
        var musica: String = ""
        var fotografia: String = ""
        var reparto: String = ""
        var productora: String = ""
        var narracion: String = ""
        var duracion: String = ""
        var idioma: String = ""
        var partes: String = ""
        var filmaffinity: String = ""
        var sinopsis: String = ""
        var enlaces: List<String> = emptyList()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "Titulo" -> title = reader.nextString()
                "anio" -> anio = reader.nextString()
                "genero" -> genero = reader.nextString()
                "pais" -> pais = reader.nextString()
                "director" -> director = reader.nextString()
                "guion" -> guion = reader.nextString()
                "musica" -> musica = reader.nextString()
                "fotografia" -> fotografia = reader.nextString()
                "reparto" -> reparto = reader.nextString()
                "productora" -> productora = reader.nextString()
                "narracion" -> narracion = reader.nextString()
                "duracion" -> duracion = reader.nextString()
                "idioma" -> idioma = reader.nextString()
                "partes" -> partes = reader.nextString()
                "filmaffinity" -> filmaffinity = reader.nextString()
                "sinopsis" -> sinopsis = reader.nextString()
                "enlaces" -> enlaces = gson.fromJson(reader, object : TypeToken<List<String>>() {}.type)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Movie(
            id = id,
            title = title,
            anio = anio,
            genero = genero,
            pais = pais,
            director = director,
            guion = guion,
            musica = musica,
            fotografia = fotografia,
            reparto = reparto,
            productora = productora,
            narracion = narracion,
            duracion = duracion,
            idioma = idioma,
            partes = partes,
            filmaffinity = filmaffinity,
            sinopsis = sinopsis,
            enlaces = enlaces
        )
    }
}