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

        // 1. Filtro por búsqueda textual
        if (!options.searchQuery.isNullOrBlank()) {
            val q = options.searchQuery.lowercase()
            currentFilteredItems = currentFilteredItems.filter { item ->
                item.title.lowercase().contains(q)
            }
        }

        // 2. Filtro por Género
        if (options.genre != "Todos" && options.genre.isNotBlank()) {
            val genreQuery = options.genre.lowercase()
            currentFilteredItems = currentFilteredItems.filter { item ->
                val itemGenreString = item.genero.lowercase()
                val genresInItem = itemGenreString.split(".", "|", ",").map { it.trim() }
                genresInItem.any { it.contains(genreQuery) }
            }
        }

        // 3. Filtro por Idioma / Doblaje
        if (options.language != "Todos" && options.language.isNotBlank()) {
            currentFilteredItems = currentFilteredItems.filter { item ->
                val itemLanguageCode = item.idioma.trim()
                when (options.language) {
                    "Español de España", "Castellano" -> itemLanguageCode == "1" || itemLanguageCode.lowercase().contains("españa") || itemLanguageCode.lowercase().contains("castellano")
                    "Español Latino", "Latino" -> itemLanguageCode == "2" || itemLanguageCode != "1"
                    else -> true
                }
            }
        }

        // 4. Filtro por País
        if (options.country != "Todos" && options.country.isNotBlank()) {
            val countryQuery = options.country.lowercase()
            currentFilteredItems = currentFilteredItems.filter { item ->
                val countriesInItem = item.pais.split(",").map { it.trim().lowercase() }
                countriesInItem.any { it.contains(countryQuery) }
            }
        }

        // 5. Ordenamiento dinámico
        currentFilteredItems = when (options.sortType) {
            "Alfabéticamente" -> {
                when (options.sortValue) {
                    "Z-A" -> currentFilteredItems.sortedByDescending { it.title.lowercase() }
                    else -> currentFilteredItems.sortedBy { it.title.lowercase() }
                }
            }
            "Año", "Año de estreno" -> {
                when (options.sortValue) {
                    "Más antiguo" -> currentFilteredItems.sortedBy { it.anio.toIntOrNull() ?: 0 }
                    else -> currentFilteredItems.sortedByDescending { it.anio.toIntOrNull() ?: 0 }
                }
            }
            "Fecha", "Fecha de inclusión" -> {
                when (options.sortValue) {
                    "Más antiguo" -> currentFilteredItems
                    else -> currentFilteredItems.reversed()
                }
            }
            else -> currentFilteredItems
        }

        return currentFilteredItems
    }
}
