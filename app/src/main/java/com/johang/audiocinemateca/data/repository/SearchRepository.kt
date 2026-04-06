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
        val queryWords = lowerCaseQuery.split(" ").filter { it.length > 2 }
        val allItems = mutableListOf<CatalogItem>()
        catalogRepository.getCatalog()?.let {
            it.movies?.let { movies -> allItems.addAll(movies) }
            it.series?.let { series -> allItems.addAll(series) }
            it.documentaries?.let { docs -> allItems.addAll(docs) }
            it.shortFilms?.let { shorts -> allItems.addAll(shorts) }
        }

        val scoredItems = allItems.mapNotNull { item ->
            val titleLower = item.title.lowercase()
            val directorLower = item.director.lowercase()
            val repartoLower = item.reparto.lowercase()
            val sinopsisLower = item.sinopsis.lowercase()
            val generoLower = item.genero.lowercase()

            var score = 0
            if (titleLower == lowerCaseQuery) score += 10
            if (titleLower.startsWith(lowerCaseQuery)) score += 5
            
            // Búsqueda por palabras clave en sinopsis y otros campos
            for (word in queryWords) {
                if (titleLower.contains(word)) score += 3
                if (generoLower.contains(word)) score += 3
                if (sinopsisLower.contains(word)) score += 2
                if (directorLower.contains(word)) score += 1
                if (repartoLower.contains(word)) score += 1
            }

            // Si la consulta original (frase completa) está en la sinopsis, bono extra
            if (sinopsisLower.contains(lowerCaseQuery)) score += 4

            if (score > 0) item to score else null
        }

        // Ordenar por puntuación (descendente) y luego por año (ascendente)
        val sortedItems = scoredItems.sortedWith(
            compareByDescending<Pair<CatalogItem, Int>> { it.second } // Primero por puntuación
                .thenBy { it.first.anio } // Luego por año
        ).map { it.first } // Extraer solo los elementos del catálogo

        return sortedItems
    }

    suspend fun getRandomCatalogItem(categoryName: String? = null): CatalogItem? {
        val catalog = catalogRepository.getCatalog() ?: return null
        val items = when (categoryName) {
            "peliculas" -> catalog.movies
            "series" -> catalog.series
            "documentales" -> catalog.documentaries
            "cortometrajes" -> catalog.shortFilms
            else -> {
                val allItems = mutableListOf<CatalogItem>()
                catalog.movies?.let { allItems.addAll(it) }
                catalog.series?.let { allItems.addAll(it) }
                catalog.documentaries?.let { allItems.addAll(it) }
                catalog.shortFilms?.let { allItems.addAll(it) }
                allItems
            }
        }
        return items?.randomOrNull()
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

    suspend fun findCatalogItemById(itemId: String, isSeriesHint: Boolean = false): CatalogItem? {
        val catalog = catalogRepository.getCatalog() ?: return null
        
        if (isSeriesHint) {
            // Si sabemos que es una serie (por los índices en la ruta), buscamos primero en series
            return catalog.series?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.movies?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.documentaries?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.shortFilms?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
        } else {
            // Si NO tiene índices, buscamos primero en categorías de contenido único (Películas, Docs, Cortos)
            return catalog.movies?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.documentaries?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.shortFilms?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
                ?: catalog.series?.firstOrNull { it.id.equals(itemId, ignoreCase = true) }
        }
    }
}