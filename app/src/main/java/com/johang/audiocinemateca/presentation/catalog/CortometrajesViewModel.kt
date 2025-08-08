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
class CortometrajesViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val filterRepository: FilterRepository,
    private val catalogFilter: CatalogFilter
) : ViewModel() {

    private val _cortometrajes = MutableStateFlow<List<com.johang.audiocinemateca.data.model.ShortFilm>>(emptyList())
    val cortometrajes: StateFlow<List<com.johang.audiocinemateca.data.model.ShortFilm>> = _cortometrajes.asStateFlow()

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
            filterRepository.getFilterOptionsFlow("cortometrajes").collect {
                currentPage = 0
                isLastPage = false
                _cortometrajes.value = emptyList()
                loadCortometrajes()
            }
        }
    }

    fun loadCortometrajes() {
        if (_isLoading.value || isLastPage) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val filterOptions = filterRepository.getFilterOptionsFlow("cortometrajes").first()
                val fullCatalog = catalogRepository.getCatalog()
                if (fullCatalog != null) {
                    val rawItems = fullCatalog.shortFilms.orEmpty()

                    val catalogItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = rawItems

                    val finalFilteredItems = catalogFilter.applyFilter(catalogItems, filterOptions).filterIsInstance<com.johang.audiocinemateca.data.model.ShortFilm>()

                    val newCortometrajes = finalFilteredItems.drop(currentPage * pageSize).take(pageSize)

                    if (newCortometrajes.isEmpty()) {
                        isLastPage = true
                    } else {
                        _cortometrajes.value = (_cortometrajes.value) + newCortometrajes
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