package com.johang.audiocinemateca.domain.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogFilter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun <T : CatalogItem> applyFilter(items: List<T>, options: FilterOptions): List<T> {
        var currentFilteredItems: List<T> = items

        // 1. Apply search filter (case-insensitive)
        if (!options.searchQuery.isNullOrBlank()) {
            currentFilteredItems = currentFilteredItems.filter { item ->
                item.title.lowercase().contains(options.searchQuery.lowercase())
            }
        }

        // 2. Apply the main filter based on filterType and filterValue
        when (options.filterType) {
            "Alfabéticamente" -> {
                currentFilteredItems = when (options.filterValue) {
                    "A-Z" -> currentFilteredItems.sortedBy { it.title.lowercase() }
                    "Z-A" -> currentFilteredItems.sortedByDescending { it.title.lowercase() }
                    else -> currentFilteredItems
                }
            }
            "Fecha" -> {
                currentFilteredItems = when (options.filterValue) {
                    "Más nuevo" -> currentFilteredItems.reversed()
                    "Más antiguo" -> currentFilteredItems
                    "Con fecha más reciente" -> currentFilteredItems.sortedByDescending { it.anio.toIntOrNull() ?: 0 }
                    "Con fecha más antigua" -> currentFilteredItems.sortedBy { it.anio.toIntOrNull() ?: 0 }
                    else -> currentFilteredItems
                }
            }
            "Género" -> {
                if (options.filterValue != "Todos") {
                    currentFilteredItems = currentFilteredItems.filter { item ->
                        val itemGenreString = item.genero.lowercase()
                        // Split the genre string by common delimiters and check if any part contains the filter value
                        val genresInItem = itemGenreString.split(".", "|", ",").map { it.trim() }
                        genresInItem.any { it.contains(options.filterValue.lowercase()) }
                    }
                } else {
                    // If "Todos" is selected, apply default alphabetical sorting (A-Z)
                    currentFilteredItems = currentFilteredItems.sortedBy { it.title.lowercase() }
                }
            }
            "Idiomas" -> {
                if (options.filterValue != "Todos") {
                    currentFilteredItems = currentFilteredItems.filter { item ->
                        val itemLanguageCode = item.idioma
                        when (options.filterValue) {
                            "Español de España" -> itemLanguageCode == "1"
                            "Español Latino" -> itemLanguageCode != "1"
                            else -> false // Should not happen if filterValue is from the spinner
                        }
                    }
                }
            }
            "Países" -> {
                if (options.filterValue != "Todos") {
                    currentFilteredItems = currentFilteredItems.filter { item ->
                        val countriesInItem = item.pais.split(",").map { it.trim() }
                        countriesInItem.any { it.equals(options.filterValue, ignoreCase = true) }
                    }
                }
            }
        }
        return currentFilteredItems
    }
}
