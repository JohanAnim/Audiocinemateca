package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.data.local.dao.CatalogDao
import com.google.gson.Gson
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.model.CatalogResponse // Importar CatalogResponse
import com.johang.audiocinemateca.data.model.Episode // Importar Episode
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val gson: Gson
) {

    // Nuevo método para obtener el catálogo completo una sola vez
    suspend fun getCatalogResponse() = catalogRepository.getCatalog()

    // --- MÉTODOS DE CONSULTA ---

    suspend fun getContentItem(contentId: String, contentType: String, loadedCatalog: CatalogResponse? = null): CatalogItem? {
        val catalogToUse = loadedCatalog ?: getCatalogResponse() ?: return null
        val cleanId = contentId.trim()
        val baseId = cleanId.substringBefore('_')

        val normalizedType = when (contentType.lowercase(java.util.Locale.ROOT)) {
            "peliculas", "pelicula", "movie" -> "peliculas"
            "series", "serie" -> "series"
            "cortometrajes", "cortometraje", "short", "shortfilm" -> "cortometrajes"
            "documentales", "documental", "documentary" -> "documentales"
            else -> contentType.lowercase(java.util.Locale.ROOT)
        }
        val item = when (normalizedType) {
            "peliculas" -> catalogToUse.movies?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            "series" -> catalogToUse.series?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            "cortometrajes" -> catalogToUse.shortFilms?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            "documentales" -> catalogToUse.documentaries?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            else -> null
        } ?: (catalogToUse.movies?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            ?: catalogToUse.series?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            ?: catalogToUse.documentaries?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
            ?: catalogToUse.shortFilms?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) })

        if (item == null) {
            android.util.Log.w("ContentRepo", "No se encontró el item con ID: $contentId (Tipo: $contentType)")
        }
        return item
    }
}