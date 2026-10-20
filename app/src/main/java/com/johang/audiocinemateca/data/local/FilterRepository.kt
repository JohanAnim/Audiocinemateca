package com.johang.audiocinemateca.data.local

import com.johang.audiocinemateca.domain.model.FilterOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class FilterRepository @Inject constructor(private val sharedPreferencesManager: SharedPreferencesManager) {

    private fun getDefaultFilterOptions(): FilterOptions {
        val defaultFilterPref = sharedPreferencesManager.getString("default_filter", "date_desc")
        return when (defaultFilterPref) {
            "alpha_asc" -> FilterOptions(sortType = "Alfabéticamente", sortValue = "A-Z")
            "alpha_desc" -> FilterOptions(sortType = "Alfabéticamente", sortValue = "Z-A")
            "date_desc" -> FilterOptions(sortType = "Fecha", sortValue = "Más nuevo")
            "date_asc" -> FilterOptions(sortType = "Fecha", sortValue = "Más antiguo")
            else -> FilterOptions(sortType = "Fecha", sortValue = "Más nuevo")
        }
    }

    private val _categoryFilters = MutableStateFlow(
        mapOf(
            "peliculas" to getDefaultFilterOptions(),
            "cortometrajes" to getDefaultFilterOptions(),
            "documentales" to getDefaultFilterOptions(),
            "series" to getDefaultFilterOptions()
        )
    )

    fun getFilterOptionsFlow(categoryName: String): Flow<FilterOptions> {
        return _categoryFilters.map { map ->
            map[categoryName] ?: getDefaultFilterOptions()
        }
    }

    fun updateSort(categoryName: String, sortType: String, sortValue: String) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: getDefaultFilterOptions()
            this[categoryName] = currentOptions.copy(sortType = sortType, sortValue = sortValue)
        }
    }

    fun updateGenre(categoryName: String, genre: String) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: getDefaultFilterOptions()
            this[categoryName] = currentOptions.copy(genre = genre)
        }
    }

    fun updateLanguage(categoryName: String, language: String) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: getDefaultFilterOptions()
            this[categoryName] = currentOptions.copy(language = language)
        }
    }

    fun updateCountry(categoryName: String, country: String) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: getDefaultFilterOptions()
            this[categoryName] = currentOptions.copy(country = country)
        }
    }

    fun updateFilter(categoryName: String, filterType: String, filterValue: String) {
        when (filterType) {
            "Alfabéticamente", "Fecha", "Año", "Año de estreno" -> updateSort(categoryName, filterType, filterValue)
            "Género" -> updateGenre(categoryName, filterValue)
            "Idiomas" -> updateLanguage(categoryName, filterValue)
            "Países" -> updateCountry(categoryName, filterValue)
            else -> updateSort(categoryName, filterType, filterValue)
        }
    }

    fun resetFilters(categoryName: String) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            this[categoryName] = getDefaultFilterOptions()
        }
    }

    fun updateSearchQuery(categoryName: String, searchQuery: String?) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: getDefaultFilterOptions()
            this[categoryName] = currentOptions.copy(searchQuery = searchQuery)
        }
    }
}
