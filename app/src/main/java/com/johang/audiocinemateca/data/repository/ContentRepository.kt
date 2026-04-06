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
        val catalogToUse = loadedCatalog ?: getCatalogResponse()
        val normalizedType = when (contentType.lowercase()) {
            "peliculas", "movie" -> "peliculas"
            "series", "series" -> "series"
            "cortometrajes", "short" -> "cortometrajes"
            "documentales", "documentary" -> "documentales"
            else -> contentType.lowercase()
        }
        return when (normalizedType) {
            "peliculas" -> catalogToUse?.movies?.firstOrNull { it.id == contentId }
            "series" -> catalogToUse?.series?.firstOrNull { it.id == contentId }
            "cortometrajes" -> catalogToUse?.shortFilms?.firstOrNull { it.id == contentId }
            "documentales" -> catalogToUse?.documentaries?.firstOrNull { it.id == contentId }
            else -> {
                // Si aún así no se encuentra, buscar en todas las categorías como último recurso
                val item = catalogToUse?.movies?.firstOrNull { it.id == contentId }
                    ?: catalogToUse?.series?.firstOrNull { it.id == contentId }
                    ?: catalogToUse?.documentaries?.firstOrNull { it.id == contentId }
                    ?: catalogToUse?.shortFilms?.firstOrNull { it.id == contentId }
                
                if (item == null) {
                    android.util.Log.w("ContentRepo", "No se encontró el item con ID: $contentId (Tipo: $contentType)")
                }
                item
            }
        }
    }
}