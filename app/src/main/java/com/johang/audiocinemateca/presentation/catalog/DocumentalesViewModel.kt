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
class DocumentalesViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val filterRepository: FilterRepository,
    private val catalogFilter: CatalogFilter
) : ViewModel() {

    private val _documentales = MutableStateFlow<List<com.johang.audiocinemateca.data.model.Documentary>>(emptyList())
    val documentales: StateFlow<List<com.johang.audiocinemateca.data.model.Documentary>> = _documentales.asStateFlow()

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
            filterRepository.getFilterOptionsFlow("documentales").collect {
                currentPage = 0
                isLastPage = false
                _documentales.value = emptyList()
                loadDocumentales()
            }
        }
    }

    fun loadDocumentales() {
        if (_isLoading.value || isLastPage) return

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val filterOptions = filterRepository.getFilterOptionsFlow("documentales").first()
                val fullCatalog = catalogRepository.getCatalog()
                if (fullCatalog != null) {
                    val rawItems = fullCatalog.documentaries.orEmpty()

                    val catalogItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = rawItems

                    val finalFilteredItems = catalogFilter.applyFilter(catalogItems, filterOptions).filterIsInstance<com.johang.audiocinemateca.data.model.Documentary>()

                    val newDocumentales = finalFilteredItems.drop(currentPage * pageSize).take(pageSize)

                    if (newDocumentales.isEmpty()) {
                        isLastPage = true
                    } else {
                        _documentales.value = (_documentales.value) + newDocumentales
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