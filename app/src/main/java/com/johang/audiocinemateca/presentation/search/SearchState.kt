package com.johang.audiocinemateca.presentation.search

import com.johang.audiocinemateca.domain.model.CatalogItem

sealed class SearchState {
    object Initial : SearchState()
    object Loading : SearchState()
    data class NoResults(val query: String) : SearchState()
    data class HasResults(val results: List<CatalogItem>, val query: String) : SearchState()
}