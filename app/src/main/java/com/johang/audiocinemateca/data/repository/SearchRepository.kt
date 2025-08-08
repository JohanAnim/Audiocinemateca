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

        return allItems.filter { item ->
            when (item) {
                is Movie -> {
                    item.title.lowercase().contains(lowerCaseQuery) ||
                    item.director.lowercase().contains(lowerCaseQuery) ||
                    item.reparto.lowercase().contains(lowerCaseQuery) ||
                    item.sinopsis.lowercase().contains(lowerCaseQuery)
                }
                is Serie -> {
                    item.title.lowercase().contains(lowerCaseQuery) ||
                    item.director.lowercase().contains(lowerCaseQuery) ||
                    item.reparto.lowercase().contains(lowerCaseQuery) ||
                    item.sinopsis.lowercase().contains(lowerCaseQuery)
                }
                is Documentary -> {
                    item.title.lowercase().contains(lowerCaseQuery) ||
                    item.director.lowercase().contains(lowerCaseQuery) ||
                    item.reparto.lowercase().contains(lowerCaseQuery) ||
                    item.sinopsis.lowercase().contains(lowerCaseQuery)
                }
                is ShortFilm -> {
                    item.title.lowercase().contains(lowerCaseQuery) ||
                    item.director.lowercase().contains(lowerCaseQuery) ||
                    item.reparto.lowercase().contains(lowerCaseQuery) ||
                    item.sinopsis.lowercase().contains(lowerCaseQuery)
                }
                else -> false
            }
        }
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