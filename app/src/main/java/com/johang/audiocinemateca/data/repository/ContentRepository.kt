package com.johang.audiocinemateca.data.repository

import android.util.Log
import com.google.gson.Gson
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.model.CatalogResponse
import com.johang.audiocinemateca.domain.model.CatalogItem
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val gson: Gson
) {

    suspend fun getCatalogResponse() = catalogRepository.getCatalog()

    suspend fun getContentItem(contentId: String, contentType: String, loadedCatalog: CatalogResponse? = null): CatalogItem? {
        val cleanId = contentId.trim()
        val baseId = cleanId.substringBefore('_')

        val normalizedType = when (contentType.lowercase(Locale.ROOT)) {
            "peliculas", "pelicula", "movie" -> "peliculas"
            "series", "serie" -> "series"
            "cortometrajes", "cortometraje", "short", "shortfilm" -> "cortometrajes"
            "documentales", "documental", "documentary" -> "documentales"
            else -> contentType.lowercase(Locale.ROOT)
        }

        if (loadedCatalog != null) {
            val fromLoaded = when (normalizedType) {
                "peliculas" -> loadedCatalog.movies?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                "series" -> loadedCatalog.series?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                "cortometrajes" -> loadedCatalog.shortFilms?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                "documentales" -> loadedCatalog.documentaries?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                else -> null
            } ?: (loadedCatalog.movies?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                ?: loadedCatalog.series?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                ?: loadedCatalog.documentaries?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) }
                ?: loadedCatalog.shortFilms?.firstOrNull { it.id.equals(cleanId, ignoreCase = true) || it.id.equals(baseId, ignoreCase = true) })
            if (fromLoaded != null) return fromLoaded
        }

        // Búsqueda directa indexada en SQLite (<1ms)
        val item = catalogRepository.findItemByIdAndCategory(cleanId, normalizedType)
            ?: (if (baseId != cleanId) catalogRepository.findItemByIdAndCategory(baseId, normalizedType) else null)
            ?: catalogRepository.findItemById(cleanId, isSeriesHint = normalizedType == "series")
            ?: (if (baseId != cleanId) catalogRepository.findItemById(baseId, isSeriesHint = normalizedType == "series") else null)

        if (item == null) {
            Log.w("ContentRepo", "No se encontró el item con ID: $contentId (Tipo: $contentType)")
        }
        return item
    }
}
