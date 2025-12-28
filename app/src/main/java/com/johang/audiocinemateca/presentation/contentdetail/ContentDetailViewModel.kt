package com.johang.audiocinemateca.presentation.contentdetail

import android.content.Context
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

import android.util.Log
import androidx.lifecycle.ViewModel
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.usecase.AddFavoriteUseCase
import com.johang.audiocinemateca.domain.usecase.CheckFavoriteStatusUseCase
import com.johang.audiocinemateca.domain.usecase.RemoveFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject


// Sealed class to represent the download state
sealed class ViewAction {
    data class StartDownload(val url: String, val title: String, val contentId: String, val contentType: String, val partIndex: Int, val episodeIndex: Int, val seriesTitle: String? = null) : ViewAction()
    data class ShowDeleteConfirmation(val partIndex: Int, val episodeIndex: Int) : ViewAction()
    data class ShowCancelConfirmation(val partIndex: Int, val episodeIndex: Int) : ViewAction()
    data class ShowDownloadFailed(val reason: String) : ViewAction()
    data class ShowError(val message: String) : ViewAction()
    data class ShowMessage(val message: String) : ViewAction()
    data class UpdateFavoriteIcon(val isFavorite: Boolean) : ViewAction()
    object StopDownloadService : ViewAction()
}

sealed class DownloadState {
    object NotDownloaded : DownloadState()
    object Downloading : DownloadState()
    object Downloaded : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

@HiltViewModel
class ContentDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val downloadRepository: DownloadRepository,
    private val progressFlow: MutableStateFlow<Int>,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val checkFavoriteStatusUseCase: CheckFavoriteStatusUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()


    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // State for the main download button
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    // State for individual episode downloads
    private val _episodeDownloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val episodeDownloadStates: StateFlow<Map<String, DownloadState>> = _episodeDownloadStates.asStateFlow()

    // Holds the season/episode index for the main button's context (for series)
    private val _targetedEpisodeIndices = MutableStateFlow<Pair<Int, Int>?>(null)
    val targetedEpisodeIndices: StateFlow<Pair<Int, Int>?> = _targetedEpisodeIndices.asStateFlow()

    private val _viewActions = MutableSharedFlow<com.johang.audiocinemateca.util.Event<ViewAction>>()
    val viewActions: SharedFlow<com.johang.audiocinemateca.util.Event<ViewAction>> = _viewActions.asSharedFlow()

    init {
        listenForDownloadProgress()
    }

    private fun listenForDownloadProgress() {
        viewModelScope.launch {
            progressFlow.collect { progress ->
                // Only update progress if a download is active
                if (_downloadState.value is DownloadState.Downloading || _episodeDownloadStates.value.any { it.value is DownloadState.Downloading }) {
                    _downloadProgress.value = progress
                }
            }
        }
    }


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
                if (item != null) {
                    checkFavoriteStatus(item.id)
                    initializeDownloadStates(item)
                    if (item is Serie) {
                        updateTargetedEpisode(item)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error al cargar los detalles: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun checkFavoriteStatus(contentId: String) {
        viewModelScope.launch {
            checkFavoriteStatusUseCase(contentId).collectLatest { isFav ->
                _isFavorite.value = isFav
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.UpdateFavoriteIcon(isFav)))
            }
        }
    }

    fun toggleFavorite() {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                removeFavoriteUseCase(item.id)
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("Se ha eliminado este título de tus favoritos")))
            } else {
                val type = when (item) {
                    is Movie -> "movie"
                    is Serie -> "serie"
                    is Documentary -> "documentary"
                    is ShortFilm -> "shortfilm"
                    else -> "unknown"
                }
                addFavoriteUseCase(item.id, item.title, type)
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("Se ha agregado este título a tus favoritos")))
            }
            // The flow in checkFavoriteStatus will update the UI automatically
        }
    }

    private fun updateTargetedEpisode(item: Serie) {
        viewModelScope.launch {
            val allProgress = playbackProgressRepository.getPlaybackProgressForContent(item.id)
            val latestProgress = allProgress.maxByOrNull { it.lastPlayedTimestamp }
            if (latestProgress != null) {
                _targetedEpisodeIndices.value = Pair(latestProgress.partIndex, latestProgress.episodeIndex)
            } else {
                _targetedEpisodeIndices.value = Pair(0, 0) // Default to first episode
            }
        }
    }

    private fun initializeDownloadStates(item: CatalogItem) {
        viewModelScope.launch {
            downloadRepository.getDownloadsForContent(item.id).collect { downloadedEntities ->
                // Logic for single-part content (Movies, Docs, Shorts)
                if (item !is Serie) {
                    val entity = downloadedEntities.firstOrNull()
                    val oldState = _downloadState.value
                    val newState = when (entity?.downloadStatus) {
                        "COMPLETE" -> DownloadState.Downloaded
                        "DOWNLOADING" -> DownloadState.Downloading
                        "FAILED" -> DownloadState.Failed(entity.errorMessage ?: "La descarga anterior falló")
                        else -> DownloadState.NotDownloaded
                    }
                    _downloadState.value = newState

                    if (newState is DownloadState.Failed && oldState !is DownloadState.Failed) {
                        _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDownloadFailed(newState.reason)))
                    }
                } else {
                    // Logic for series
                    val oldStates = _episodeDownloadStates.value
                    val newStates = mutableMapOf<String, DownloadState>()
                    item.capitulos.forEach { (seasonKey, episodes) ->
                        val seasons = item.capitulos.keys.sorted()
                        val seasonIndex = seasons.indexOf(seasonKey)
                        episodes.forEachIndexed { episodeIndex, _ ->
                            val entity = downloadedEntities.find { it.partIndex == seasonIndex && it.episodeIndex == episodeIndex }
                            val key = "${seasonIndex}_${episodeIndex}"
                            val episodeState = when (entity?.downloadStatus) {
                                "COMPLETE" -> DownloadState.Downloaded
                                "DOWNLOADING" -> DownloadState.Downloading
                                "FAILED" -> DownloadState.Failed(entity.errorMessage ?: "Falló")
                                else -> DownloadState.NotDownloaded
                            }
                            newStates[key] = episodeState

                            val oldEpisodeState = oldStates[key]
                            if (episodeState is DownloadState.Failed && oldEpisodeState !is DownloadState.Failed) {
                                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDownloadFailed(episodeState.reason)))
                            }
                        }
                    }
                    _episodeDownloadStates.value = newStates
                }
            }
        }
    }

    fun onDownloadAction() {
        viewModelScope.launch {
            val item = _contentItem.value ?: return@launch

            if (item is Serie) {
                val targetIndices = _targetedEpisodeIndices.value ?: return@launch
                val (seasonIndex, episodeIndex) = targetIndices
                // For series, the main button's action is a proxy for the targeted episode's action
                onEpisodeDownloadAction(seasonIndex, episodeIndex)
            } else {
                // Logic for non-series content
                val currentState = _downloadState.value
                when (currentState) {
                    is DownloadState.NotDownloaded, is DownloadState.Failed -> {
                        val url = when(item) {
                            is Movie -> item.enlaces.getOrNull(0)
                            is Documentary -> item.enlace
                            is ShortFilm -> item.enlace
                            else -> null
                        }
                        if (url != null) {
                            val baseUrl = "https://audiocinemateca.com/"
                            val fullUrl = if (url.startsWith("http")) url else "${baseUrl.removeSuffix("/")}/${url.removePrefix("/")}"
                            val contentType = when(item) {
                                is Movie -> "movie"
                                is Documentary -> "documentary"
                                is ShortFilm -> "shortfilm"
                                else -> "unknown"
                            }
                            _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.StartDownload(
                                url = fullUrl,
                                title = item.title,
                                contentId = item.id,
                                contentType = contentType,
                                partIndex = 0,
                                episodeIndex = -1
                            )))
                            _downloadState.value = DownloadState.Downloading
                        } else {
                            _errorMessage.value = "No se encontró URL para la descarga."
                        }
                    }
                    is DownloadState.Downloading -> {
                        _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowCancelConfirmation(0, -1)))
                    }
                    is DownloadState.Downloaded -> {
                        _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDeleteConfirmation(0, -1)))
                    }
                }
            }
        }
    }

    fun onEpisodeDownloadAction(seasonIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value as? Serie ?: return@launch
            val key = "${seasonIndex}_${episodeIndex}"
            val currentState = _episodeDownloadStates.value[key]

            when (currentState) {
                is DownloadState.NotDownloaded, is DownloadState.Failed, null -> {
                    val seasonKey = item.capitulos.keys.sorted().getOrNull(seasonIndex)
                    val episode = item.capitulos[seasonKey]?.getOrNull(episodeIndex)

                    if (episode != null) {
                        val baseUrl = "https://audiocinemateca.com/"
                        val fullUrl = if (episode.enlace.startsWith("http")) episode.enlace else "${baseUrl.removeSuffix("/")}/${episode.enlace.removePrefix("/")}"
                        _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.StartDownload(
                            url = fullUrl,
                            title = "T${seasonIndex + 1}:E${episode.capitulo} - ${episode.titulo}", // Just the episode title
                            contentId = item.id,
                            contentType = "serie",
                            partIndex = seasonIndex,
                            episodeIndex = episodeIndex,
                            seriesTitle = item.title // Pass the series title separately
                        )))
                        val newStates = _episodeDownloadStates.value.toMutableMap()
                        newStates[key] = DownloadState.Downloading
                        _episodeDownloadStates.value = newStates
                    } else {
                         _errorMessage.value = "No se encontró URL para la descarga del episodio."
                    }
                }
                is DownloadState.Downloading -> {
                    _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowCancelConfirmation(seasonIndex, episodeIndex)))
                }
                is DownloadState.Downloaded -> {
                    _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDeleteConfirmation(seasonIndex, episodeIndex)))
                }
            }
        }
    }

    fun deleteDownload(partIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value ?: return@launch
            val downloadEntity = downloadRepository.getDownload(item.id, partIndex, episodeIndex)
            val filePath = downloadEntity?.filePath

            if (filePath != null) {
                when (val result = downloadRepository.deleteDownloadedFile(context, filePath)) {
                    is DownloadRepository.FileDeletionResult.Success,
                    is DownloadRepository.FileDeletionResult.FileDoesNotExist -> {
                        // Deletion was successful or file was already gone.
                        // Now, delete the DB record.
                        downloadRepository.deleteDownload(item.id, partIndex, episodeIndex)

                        // After deletion, check if we need to remove an empty series directory.
                        if (item is Serie && android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                            // On Android 9 (Pie) or lower, we can attempt to delete the empty directory.
                            // On newer versions, Scoped Storage prevents this, so we don't even try.
                            kotlinx.coroutines.delay(200) // Short delay to allow DB to update
                            val remainingDownloads = downloadRepository.getAllDownloads().first()
                                .filter { it.contentId == item.id }

                            if (remainingDownloads.isEmpty()) {
                                // This was the last episode. Try to delete the folder.
                                val absolutePath = downloadRepository.getFilePathFromUri(context, android.net.Uri.parse(filePath))
                                if (absolutePath != null) {
                                    downloadRepository.deleteEmptyDirectory(absolutePath)
                                }
                            }
                        }
                    }
                    is DownloadRepository.FileDeletionResult.Failure -> {
                        // If file deletion failed, show an error and DON'T delete the DB record.
                        _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowError("Error al borrar: ${result.exception.message}")))
                    }
                }
            } else {
                // If there's no file path, the download was incomplete or failed. Just delete the record.
                downloadRepository.deleteDownload(item.id, partIndex, episodeIndex)
            }
        }
    }

    fun cancelDownload(partIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value ?: return@launch
            _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.StopDownloadService))
            // We also need to update the entity status from DOWNLOADING to FAILED or just delete it
            downloadRepository.deleteDownload(item.id, partIndex, episodeIndex)
        }
    }

    suspend fun getPlaybackProgressForContent(contentId: String): List<PlaybackProgressEntity> {
        return playbackProgressRepository.getPlaybackProgressForContent(contentId)
    }
}
