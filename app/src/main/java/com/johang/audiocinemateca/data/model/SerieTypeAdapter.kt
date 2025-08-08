package com.johang.audiocinemateca.data.model

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class SerieTypeAdapter : TypeAdapter<Serie>() {
    private val gson = Gson()

    override fun write(out: JsonWriter, value: Serie?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("id").value(value.id)
        out.name("titulo").value(value.title)
        out.name("anio").value(value.anio)
        out.name("duracion").value(value.duracion)
        out.name("pais").value(value.pais)
        out.name("director").value(value.director)
        out.name("guion").value(value.guion)
        out.name("musica").value(value.musica)
        out.name("fotografia").value(value.fotografia)
        out.name("reparto").value(value.reparto)
        out.name("genero").value(value.genero)
        out.name("temporadas").value(value.temporadas)
        out.name("idioma").value(value.idioma)
        out.name("narracion").value(value.narracion)
        out.name("filmaffinity").value(value.filmaffinity)
        out.name("sinopsis").value(value.sinopsis)
        out.name("productora").value(value.productora)
        out.name("capitulos")
        gson.toJson(value.capitulos, object : TypeToken<Map<String, List<Episode>>>() {}.type, out)
        out.endObject()
    }

    override fun read(reader: JsonReader): Serie? {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull()
            return null
        }

        var id: String = ""
        var title: String = ""
        var anio: String = ""
        var duracion: String = ""
        var pais: String = ""
        var director: String = ""
        var guion: String = ""
        var musica: String = ""
        var fotografia: String = ""
        var reparto: String = ""
        var genero: String = ""
        var temporadas: String = ""
        var idioma: String = ""
        var narracion: String = ""
        var filmaffinity: String = ""
        var sinopsis: String = ""
        var productora: String = ""
        var capitulos: Map<String, List<Episode>> = emptyMap()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "titulo" -> title = reader.nextString()
                "anio" -> anio = reader.nextString()
                "duracion" -> duracion = reader.nextString()
                "pais" -> pais = reader.nextString()
                "director" -> director = reader.nextString()
                "guion" -> guion = reader.nextString()
                "musica" -> musica = reader.nextString()
                "fotografia" -> fotografia = reader.nextString()
                "reparto" -> reparto = reader.nextString()
                "genero" -> genero = reader.nextString()
                "temporadas" -> temporadas = reader.nextString()
                "idioma" -> idioma = reader.nextString()
                "narracion" -> narracion = reader.nextString()
                "filmaffinity" -> filmaffinity = reader.nextString()
                "sinopsis" -> sinopsis = reader.nextString()
                "productora" -> productora = reader.nextString()
                "capitulos" -> {
                    // Read the nested ChaptersWrapper
                    val type = object : TypeToken<Map<String, List<Episode>>>() {}.type
                                capitulos = gson.fromJson(reader, type)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return Serie(
            id = id,
            title = title,
            anio = anio,
            duracion = duracion,
            pais = pais,
            director = director,
            guion = guion,
            musica = musica,
            fotografia = fotografia,
            reparto = reparto,
            genero = genero,
            temporadas = temporadas,
            idioma = idioma,
            narracion = narracion,
            filmaffinity = filmaffinity,
            sinopsis = sinopsis,
            productora = productora,
            capitulos = capitulos
        )
    }
}