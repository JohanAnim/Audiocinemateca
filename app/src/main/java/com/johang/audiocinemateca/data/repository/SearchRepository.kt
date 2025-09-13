package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository @Inject constructor(
    private val catalogRepository: CatalogRepository
) {

    suspend fun searchCatalog(query: String): List<CatalogItem> {
        if (query.isBlank()) {
            return emptyList()
        }

        val lowerCaseQuery = query.lowercase()
        val allItems = mutableListOf<CatalogItem>()
        catalogRepository.getCatalog()?.let {
            it.movies?.let { movies -> allItems.addAll(movies) }
            it.series?.let { series -> allItems.addAll(series) }
            it.documentaries?.let { docs -> allItems.addAll(docs) }
            it.shortFilms?.let { shorts -> allItems.addAll(shorts) }
        }

        val scoredItems = allItems.mapNotNull { item ->
            val score = when {
                item.title.equals(query, ignoreCase = true) -> 3 // Coincidencia exacta del título
                item.title.startsWith(query, ignoreCase = true) -> 2 // El título comienza con la búsqueda (para secuelas)
                item.title.lowercase().contains(lowerCaseQuery) ||
                item.director.lowercase().contains(lowerCaseQuery) ||
                item.reparto.lowercase().contains(lowerCaseQuery) ||
                item.sinopsis.lowercase().contains(lowerCaseQuery) -> 1 // Coincidencia en otros campos
                else -> 0
            }

            if (score > 0) item to score else null
        }

        // Ordenar por puntuación (descendente) y luego por año (ascendente)
        val sortedItems = scoredItems.sortedWith(
            compareByDescending<Pair<CatalogItem, Int>> { it.second } // Primero por puntuación
                .thenBy { it.first.anio } // Luego por año
        ).map { it.first } // Extraer solo los elementos del catálogo

        return sortedItems
    }

    suspend fun getRandomCatalogItem(): CatalogItem? {
        val allItems = mutableListOf<CatalogItem>()
        catalogRepository.getCatalog()?.let {
            it.movies?.let { movies -> allItems.addAll(movies) }
            it.series?.let { series -> allItems.addAll(series) }
            it.documentaries?.let { docs -> allItems.addAll(docs) }
            it.shortFilms?.let { shorts -> allItems.addAll(shorts) }
        }
        return allItems.randomOrNull()
    }

    suspend fun getCatalogItemByIdAndType(itemId: String, itemType: String): CatalogItem? {
        val catalog = catalogRepository.getCatalog()
        val pluralItemType = when (itemType) {
            "pelicula" -> "peliculas"
            "serie" -> "series"
            "documental" -> "documentales"
            "cortometraje" -> "cortometrajes"
            else -> itemType // Fallback for other types or if already plural
        }
        return when (pluralItemType) {
            "peliculas" -> catalog?.movies?.find { it.id == itemId }
            "series" -> catalog?.series?.find { it.id == itemId }
            "documentales" -> catalog?.documentaries?.find { it.id == itemId }
            "cortometrajes" -> catalog?.shortFilms?.find { it.id == itemId }
            else -> null
        }
    }
}