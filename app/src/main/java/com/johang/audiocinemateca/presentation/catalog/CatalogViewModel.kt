package com.johang.audiocinemateca.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.FilterRepository
import com.johang.audiocinemateca.domain.model.FilterOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val filterRepository: FilterRepository
) : ViewModel() {

    private val _genres = MutableStateFlow<List<String>>(emptyList())
    val genres: StateFlow<List<String>> = _genres.asStateFlow()

    private val _currentFilterOptions = MutableStateFlow<FilterOptions?>(null)
    val currentFilterOptions: StateFlow<FilterOptions?> = _currentFilterOptions.asStateFlow()

    init {
        // No need to load initial catalog here, as category ViewModels will handle it
    }

    fun updateFilter(categoryName: String, filterType: String, filterValue: String) {
        viewModelScope.launch {
            filterRepository.updateFilter(categoryName, filterType, filterValue)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun updateSearchQuery(categoryName: String, query: String) {
        val newQuery = query.ifBlank { null }
        viewModelScope.launch {
            filterRepository.updateSearchQuery(categoryName, newQuery)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    suspend fun getGenresForCategory(categoryName: String?): List<String> {
        return catalogRepository.getGenres(categoryName)
    }

    fun loadFilterOptions(categoryName: String) {
        viewModelScope.launch {
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }
}