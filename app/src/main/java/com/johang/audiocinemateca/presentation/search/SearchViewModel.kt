package com.johang.audiocinemateca.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val searchHistoryRepository: SearchHistoryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _recentSearches = MutableStateFlow<List<com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity>>(emptyList())
    val recentSearches: StateFlow<List<com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity>> = _recentSearches

    init {
        viewModelScope.launch {
            searchHistoryRepository.getRecentSearches().collect { 
                _recentSearches.value = it
            }
        }
    }

    @OptIn(FlowPreview::class)
    val searchState: StateFlow<SearchState> = _searchQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flow { emit(SearchState.Initial) }
            } else {
                flow {
                    val results = searchRepository.searchCatalog(query)
                    if (results.isEmpty()) {
                        emit(SearchState.NoResults(query))
                    } else {
                        searchHistoryRepository.saveSearchQuery(query) // Save successful search
                        emit(SearchState.HasResults(results, query))
                    }
                }.onStart { emit(SearchState.Loading) } // Emit Loading state before starting the search
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchState.Initial
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            searchHistoryRepository.clearSearchHistory()
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryRepository.deleteSearchHistoryItem(query)
        }
    }
}