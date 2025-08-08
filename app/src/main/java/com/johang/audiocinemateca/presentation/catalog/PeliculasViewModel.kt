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

import android.util.Log // Keep this import for debugging

@HiltViewModel
class PeliculasViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    val filterRepository: FilterRepository,
    private val catalogFilter: CatalogFilter
) : ViewModel() {

    private val _movies = MutableStateFlow<List<com.johang.audiocinemateca.data.model.Movie>>(emptyList())
    val movies: StateFlow<List<com.johang.audiocinemateca.data.model.Movie>> = _movies.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentPage = 0
    val pageSize = 20 // Make pageSize public
    var isLastPage = false

    init {
        observeFilterChanges()
    }

    private fun observeFilterChanges() {
        viewModelScope.launch {
            filterRepository.getFilterOptionsFlow("peliculas").collect {
                Log.d("PeliculasViewModel", "observeFilterChanges: Filter options for 'peliculas' changed to: $it")
                // Reset pagination when filters change
                currentPage = 0
                isLastPage = false
                _movies.value = emptyList() // Clear the current list
                Log.d("PeliculasViewModel", "observeFilterChanges: Calling loadMovies() after filter change.")
                loadMovies()
            }
        }
    }

    fun loadMovies() {
        if (_isLoading.value || isLastPage) {
            Log.d("PeliculasViewModel", "loadMovies: Already loading or last page. isLoading: ${_isLoading.value}, isLastPage: $isLastPage")
            return
        }

        _isLoading.value = true
        viewModelScope.launch {
            try {
                val filterOptions = filterRepository.getFilterOptionsFlow("peliculas").first()
                Log.d("PeliculasViewModel", "loadMovies: Current filter options: $filterOptions")

                val fullCatalog = catalogRepository.getCatalog()
                if (fullCatalog != null) {
                    val rawItems = fullCatalog.movies.orEmpty()

                    val catalogItems: List<com.johang.audiocinemateca.domain.model.CatalogItem> = rawItems

                    val finalFilteredItems = catalogFilter.applyFilter(catalogItems, filterOptions).filterIsInstance<com.johang.audiocinemateca.data.model.Movie>()
                    Log.d("PeliculasViewModel", "loadMovies: Filtered items size after applyFilter: ${finalFilteredItems.size}")

                    val newMovies = finalFilteredItems.drop(currentPage * pageSize).take(pageSize)
                    Log.d("PeliculasViewModel", "loadMovies: New movies for current page size: ${newMovies.size}")

                    if (newMovies.isEmpty() && currentPage == 0) {
                        _movies.value = emptyList() // Ensure empty list is emitted if no results on first page
                        isLastPage = true
                        Log.d("PeliculasViewModel", "loadMovies: No movies found for current filters on first page.")
                    } else if (newMovies.isEmpty()) {
                        isLastPage = true
                        Log.d("PeliculasViewModel", "loadMovies: No more movies, setting isLastPage to true.")
                    } else {
                        _movies.value = (_movies.value) + newMovies
                        currentPage++
                        Log.d("PeliculasViewModel", "loadMovies: Appended ${newMovies.size} movies. Current total: ${_movies.value.size}")
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
                Log.e("PeliculasViewModel", "Error loading movies: ${e.message}", e)
            } finally {
                _isLoading.value = false
                Log.d("PeliculasViewModel", "loadMovies: Loading finished. isLoading: ${_isLoading.value}")
            }
        }
    }
}