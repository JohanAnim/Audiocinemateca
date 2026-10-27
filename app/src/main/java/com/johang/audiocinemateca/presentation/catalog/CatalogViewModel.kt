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

    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    fun setSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index.coerceIn(0, 3)
    }

    private val _lastVisitedItemKey = MutableStateFlow<String?>(null)
    val lastVisitedItemKey: StateFlow<String?> = _lastVisitedItemKey.asStateFlow()

    fun setLastVisitedItemKey(key: String?) {
        _lastVisitedItemKey.value = key
    }

    private val _currentFilterOptions = MutableStateFlow<FilterOptions?>(null)
    val currentFilterOptions: StateFlow<FilterOptions?> = _currentFilterOptions.asStateFlow()

    fun updateSort(categoryName: String, sortType: String, sortValue: String) {
        viewModelScope.launch {
            filterRepository.updateSort(categoryName, sortType, sortValue)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun updateGenre(categoryName: String, genre: String) {
        viewModelScope.launch {
            filterRepository.updateGenre(categoryName, genre)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun updateLanguage(categoryName: String, language: String) {
        viewModelScope.launch {
            filterRepository.updateLanguage(categoryName, language)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun updateCountry(categoryName: String, country: String) {
        viewModelScope.launch {
            filterRepository.updateCountry(categoryName, country)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun updateFilter(categoryName: String, filterType: String, filterValue: String) {
        viewModelScope.launch {
            filterRepository.updateFilter(categoryName, filterType, filterValue)
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }

    fun resetFilters(categoryName: String) {
        viewModelScope.launch {
            filterRepository.resetFilters(categoryName)
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

    suspend fun getCountriesForCategory(categoryName: String?): List<String> {
        return catalogRepository.getCountries(categoryName)
    }

    fun loadFilterOptions(categoryName: String) {
        viewModelScope.launch {
            _currentFilterOptions.value = filterRepository.getFilterOptionsFlow(categoryName).first()
        }
    }
}
