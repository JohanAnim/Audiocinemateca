package com.johang.audiocinemateca.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.flowOf

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<CatalogItem>>(emptyList())
    val searchResults: StateFlow<List<CatalogItem>> = _searchResults

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(300L) // Esperar 300ms después de la última pulsación
                .filter { it.isNotBlank() } // Solo buscar si el query no está vacío
                .distinctUntilChanged() // Evitar búsquedas duplicadas para el mismo query
                .flatMapLatest { query ->
                    flowOf(searchRepository.searchCatalog(query))
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}