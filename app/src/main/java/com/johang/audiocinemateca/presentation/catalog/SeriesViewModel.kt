package com.johang.audiocinemateca.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.FilterRepository
import com.johang.audiocinemateca.domain.model.CatalogFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val filterRepository: FilterRepository,
    private val catalogFilter: CatalogFilter
) : ViewModel() {

    private val _series = MutableStateFlow<List<com.johang.audiocinemateca.data.model.Serie>>(emptyList())
    val series: StateFlow<List<com.johang.audiocinemateca.data.model.Serie>> = _series.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentPage = 0
    val pageSize = 20
    var isLastPage = false

    init {
        observeFilterChanges()
    }

    private fun observeFilterChanges() {
        viewModelScope.launch {
            filterRepository.getFilterOptionsFlow("series").collect {
                // Reset pagination when filters change
                currentPage = 0
                isLastPage = false
                _series.value = emptyList() // Clear the current list
                loadSeries()
            }
        }
    }

    fun loadSeries() {
        if (_isLoading.value || isLastPage) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val filterOptions = filterRepository.getFilterOptionsFlow("series").first()
                val fullCatalog = catalogRepository.getCatalog()
                if (fullCatalog != null) {
                    val rawItems = fullCatalog.series.orEmpty()

                    val catalogItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = rawItems

                    val finalFilteredItems = catalogFilter.applyFilter(catalogItems, filterOptions).filterIsInstance<com.johang.audiocinemateca.data.model.Serie>()

                    val newSeries = finalFilteredItems.drop(currentPage * pageSize).take(pageSize)

                    if (newSeries.isEmpty()) {
                        isLastPage = true
                    } else {
                        _series.value = (_series.value) + newSeries
                        currentPage++
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
}