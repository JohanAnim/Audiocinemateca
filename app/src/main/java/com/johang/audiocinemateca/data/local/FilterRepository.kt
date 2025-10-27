package com.johang.audiocinemateca.data.local

import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.model.FilterOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class FilterRepository @Inject constructor(private val sharedPreferencesManager: SharedPreferencesManager) {

    private fun getDefaultFilterOptions(): FilterOptions {
        val defaultFilterPref = sharedPreferencesManager.getString("default_filter_type", "alpha_asc")
        return when (defaultFilterPref) {
            "alpha_asc" -> FilterOptions("Alfabéticamente", "A-Z")
            "alpha_desc" -> FilterOptions("Alfabéticamente", "Z-A")
            "date_desc" -> FilterOptions("Fecha", "Más nuevo")
            "date_asc" -> FilterOptions("Fecha", "Más antiguo")
            else -> FilterOptions()
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
            val options = map[categoryName] ?: FilterOptions()
            Log.d("FilterRepository", "getFilterOptionsFlow: Returning options for '$categoryName': $options")
            options
        }
    }

    fun updateFilter(categoryName: String, filterType: String, filterValue: String) {
        Log.d("FilterRepository", "updateFilter: Called for category: '$categoryName', type: '$filterType', value: '$filterValue'")
        val oldOptions = _categoryFilters.value[categoryName]
        Log.d("FilterRepository", "updateFilter: Old options for '$categoryName': $oldOptions")

        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: FilterOptions()
            val newOptions = currentOptions.copy(
                filterType = filterType,
                filterValue = filterValue
            )
            this[categoryName] = newOptions
            Log.d("FilterRepository", "updateFilter: New options to set for '$categoryName': $newOptions")
        }
        Log.d("FilterRepository", "updateFilter: _categoryFilters.value after update: ${_categoryFilters.value}")
    }

    fun updateSearchQuery(categoryName: String, searchQuery: String?) {
        _categoryFilters.value = _categoryFilters.value.toMutableMap().apply {
            val currentOptions = this[categoryName] ?: FilterOptions()
            this[categoryName] = currentOptions.copy(searchQuery = searchQuery)
        }
    }
}