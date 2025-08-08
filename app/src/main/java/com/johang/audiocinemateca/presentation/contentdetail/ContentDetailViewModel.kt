package com.johang.audiocinemateca.presentation.contentdetail

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

@HiltViewModel
class ContentDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playbackProgressRepository: PlaybackProgressRepository
) : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadContentDetail(itemId: String, itemType: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val catalog = catalogRepository.getCatalog()
                Log.d("ContentDetailViewModel", "Catalog loaded: ${catalog != null}")
                val pluralItemType = when (itemType) {
                    "pelicula" -> "peliculas"
                    "serie" -> "series"
                    "cortometraje" -> "cortometrajes"
                    "documental" -> "documentales"
                    else -> itemType // Fallback for other types or if already plural
                }
                Log.d("ContentDetailViewModel", "Original itemType: $itemType, Pluralized itemType: $pluralItemType")
                val item = when (pluralItemType) {
                    "peliculas" -> {
                        Log.d("ContentDetailViewModel", "Searching in movies. Count: ${catalog?.movies?.size ?: 0}")
                        catalog?.movies?.find { it.id == itemId }
                    }
                    "series" -> {
                        Log.d("ContentDetailViewModel", "Searching in series. Count: ${catalog?.series?.size ?: 0}")
                        catalog?.series?.find { it.id == itemId }
                    }
                    "cortometrajes" -> {
                        Log.d("ContentDetailViewModel", "Searching in shortFilms. Count: ${catalog?.shortFilms?.size ?: 0}")
                        catalog?.shortFilms?.find { it.id == itemId }
                    }
                    "documentales" -> {
                        Log.d("ContentDetailViewModel", "Searching in documentaries. Count: ${catalog?.documentaries?.size ?: 0}")
                        catalog?.documentaries?.find { it.id == itemId }
                    }
                    else -> null
                }
                Log.d("ContentDetailViewModel", "Item found in ViewModel: ${item != null}")
                _contentItem.value = item
            } catch (e: Exception) {
                _errorMessage.value = "Error al cargar los detalles: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getPlaybackProgressForContent(contentId: String): List<PlaybackProgressEntity> {
        return playbackProgressRepository.getPlaybackProgressForContent(contentId)
    }
}
