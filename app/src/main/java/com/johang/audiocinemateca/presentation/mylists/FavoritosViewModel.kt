package com.johang.audiocinemateca.presentation.mylists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.domain.usecase.GetFavoritesUseCase
import com.johang.audiocinemateca.domain.usecase.RemoveFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
        .map { list -> 
            list.map { fav ->
                var fixedAt = fav.addedAt
                
                // Si el número empieza por 20 (formato YYYYMMDD de Windows)
                // lo convertimos a un valor bajo (ej. año 2024) para que 
                // cualquier cosa nueva agregada en Android (que empieza por 17...)
                // sea cronológicamente superior en milisegundos reales.
                if (fixedAt > 200000000000L && fixedAt < 210000000000L) {
                    fixedAt = 1704067200000L // Reset a Enero 2024
                } 
                // Si son segundos, pasar a milisegundos
                else if (fixedAt in 1L..9999999999L) {
                    fixedAt *= 1000
                }

                fav.copy(addedAt = fixedAt)
            }.sortedByDescending { it.addedAt }
        }
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
                
                // Buscar el item en el catálogo siendo flexibles con el tipo
                val item = when (favorite.contentType.lowercase()) {
                    "movie", "peliculas" -> catalog.movies?.find { it.id == favorite.contentId }
                    "serie", "series" -> catalog.series?.find { it.id == favorite.contentId }
                    "documentary", "documentales" -> catalog.documentaries?.find { it.id == favorite.contentId }
                    "shortfilm", "short", "cortometrajes" -> catalog.shortFilms?.find { it.id == favorite.contentId }
                    else -> {
                        // Búsqueda exhaustiva si el tipo no coincide
                        catalog.movies?.find { it.id == favorite.contentId }
                            ?: catalog.series?.find { it.id == favorite.contentId }
                            ?: catalog.documentaries?.find { it.id == favorite.contentId }
                            ?: catalog.shortFilms?.find { it.id == favorite.contentId }
                    }
                }

                if (item != null) {
                    // Buscar el único progreso guardado para este contenido
                    val progress = playbackProgressRepository.getPlaybackProgress(item.id, -1, -1)

                    if (progress != null) {
                        _navigateToPlayer.emit(Triple(item, progress.partIndex, progress.episodeIndex))
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
