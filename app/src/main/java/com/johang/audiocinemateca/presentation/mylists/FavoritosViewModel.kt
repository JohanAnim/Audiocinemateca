package com.johang.audiocinemateca.presentation.mylists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.usecase.GetFavoritesUseCase
import com.johang.audiocinemateca.domain.usecase.RemoveFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.data.model.Serie
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class FavoritosViewModel @Inject constructor(
    getFavoritesUseCase: GetFavoritesUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val catalogRepository: CatalogRepository,
    private val playbackProgressRepository: PlaybackProgressRepository
) : ViewModel() {

    private val _navigateToPlayer = MutableSharedFlow<Triple<CatalogItem, Int, Int>>()
    val navigateToPlayer = _navigateToPlayer.asSharedFlow()

    private val _error = MutableSharedFlow<String>()
    val error = _error.asSharedFlow()

    val favorites: StateFlow<List<FavoriteEntity>> = getFavoritesUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun removeFavorite(favorite: FavoriteEntity) {
        viewModelScope.launch {
            removeFavoriteUseCase(favorite.contentId)
        }
    }

    fun playFavorite(favorite: FavoriteEntity) {
        viewModelScope.launch {
            try {
                val catalog = catalogRepository.getCatalog() ?: return@launch
                
                // Buscar el item en el catálogo según su tipo
                val item = when (favorite.contentType) {
                    "movie" -> catalog.movies?.find { it.id == favorite.contentId }
                    "serie" -> catalog.series?.find { it.id == favorite.contentId }
                    "documentary" -> catalog.documentaries?.find { it.id == favorite.contentId }
                    "shortfilm" -> catalog.shortFilms?.find { it.id == favorite.contentId }
                    else -> null
                }

                if (item != null) {
                    // Buscar el progreso más reciente
                    val allProgress = playbackProgressRepository.getPlaybackProgressForContent(item.id)
                    val latestProgress = allProgress.maxByOrNull { it.lastPlayedTimestamp }

                    if (latestProgress != null) {
                        _navigateToPlayer.emit(Triple(item, latestProgress.partIndex, latestProgress.episodeIndex))
                    } else {
                        // Si no hay progreso, empezar desde el principio
                        val episodeIndex = if (item is Serie) 0 else -1
                        _navigateToPlayer.emit(Triple(item, 0, episodeIndex))
                    }
                } else {
                    _error.emit("No se pudo encontrar el contenido en el catálogo.")
                }
            } catch (e: Exception) {
                _error.emit("Error al intentar reproducir: ${e.message}")
            }
        }
    }
}
