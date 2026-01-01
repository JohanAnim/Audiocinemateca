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
        return when (contentType) {
            "peliculas" -> catalogToUse?.movies?.firstOrNull { it.id == contentId }
            "series" -> catalogToUse?.series?.firstOrNull { it.id == contentId }
            "cortometrajes" -> catalogToUse?.shortFilms?.firstOrNull { it.id == contentId }
            "documentales" -> catalogToUse?.documentaries?.firstOrNull { it.id == contentId }
            else -> null
        }
    }
}