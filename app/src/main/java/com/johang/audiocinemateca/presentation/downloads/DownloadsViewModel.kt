package com.johang.audiocinemateca.presentation.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// This data class will represent a grouped item in our new downloads list.
data class GroupedDownload(
    val contentId: String,
    val title: String,
    val contentType: String,
    val totalSizeMb: Double,
    val downloadedAt: Long, // Using the most recent download time for sorting
    val episodes: List<DownloadEntity>, // Will be a list with 1 item for non-series
    var isExpanded: Boolean = false
)

sealed class DownloadFilter(val displayName: String) {
    object All : DownloadFilter("Todos")
    object Queued : DownloadFilter("En cola")
    object InProgress : DownloadFilter("En progreso")
    object Failed : DownloadFilter("Con fallos")
    object Completed : DownloadFilter("Descargados")
}

sealed class DownloadsUiState {
    object Loading : DownloadsUiState()
    data class Success(val downloads: List<GroupedDownload>) : DownloadsUiState()
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val catalogRepository: CatalogRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _allDownloads = downloadRepository.getAllDownloads()
    private val _currentFilter = MutableStateFlow<DownloadFilter>(DownloadFilter.All)
    private val _expandedSeriesIds = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<DownloadsUiState> = combine(
        _allDownloads,
        _currentFilter,
        _expandedSeriesIds
    ) { allDownloads, currentFilter, expandedSeriesIds ->
        val filteredDownloads = when (currentFilter) {
            DownloadFilter.All -> allDownloads
            DownloadFilter.Queued -> allDownloads.filter { it.downloadStatus == "QUEUED" }
            DownloadFilter.InProgress -> allDownloads.filter { it.downloadStatus == "DOWNLOADING" }
            DownloadFilter.Failed -> allDownloads.filter { it.downloadStatus == "FAILED" }
            DownloadFilter.Completed -> allDownloads.filter { it.downloadStatus == "COMPLETE" }
        }

        val catalog = catalogRepository.getCatalog()
        val grouped = filteredDownloads
            .groupBy { it.contentId }
            .mapNotNull { (contentId, groupedEntities) ->
                val firstEntity = groupedEntities.first()
                val seriesTitleFromCatalog = if (firstEntity.contentType == "serie") {
                    catalog?.series?.find { it.id == contentId }?.title
                } else null

                val title = if (firstEntity.contentType == "serie") {
                    seriesTitleFromCatalog
                        ?: firstEntity.title.substringBefore(" - ").substringBefore(":E").ifBlank { firstEntity.title }
                } else {
                    firstEntity.title
                }

                GroupedDownload(
                    contentId = contentId,
                    title = title,
                    contentType = firstEntity.contentType,
                    totalSizeMb = groupedEntities.sumOf { it.totalSizeMb },
                    downloadedAt = groupedEntities.maxOf { it.downloadedAt },
                    episodes = groupedEntities.sortedWith(compareBy({ it.partIndex }, { it.episodeIndex })),
                    isExpanded = expandedSeriesIds.contains(contentId)
                )
            }
            .sortedByDescending { it.downloadedAt }
        DownloadsUiState.Success(grouped)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DownloadsUiState.Loading)


    val totalSizeMb: StateFlow<Double> = _allDownloads.map { allDownloads ->
        allDownloads
            .filter { it.downloadStatus == "COMPLETE" }
            .sumOf { it.totalSizeMb }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val totalDurationMs: StateFlow<Long> = _allDownloads.map { allDownloads ->
        allDownloads
            .filter { it.downloadStatus == "COMPLETE" }
            .sumOf { it.durationMs }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    val currentFilter: StateFlow<DownloadFilter> = _currentFilter

    // Sealed class for one-time events
    sealed class ViewAction {
        data class NavigateToPlayer(val catalogItem: CatalogItem, val partIndex: Int, val episodeIndex: Int) : ViewAction()
        data class ShowItemActions(val item: GroupedDownload) : ViewAction()
        data class ShowEpisodeActions(val episode: DownloadEntity) : ViewAction()
        data class ShowDeleteConfirmation(val title: String, val contentId: String, val partIndex: Int, val episodeIndex: Int, val isGroup: Boolean) : ViewAction()
        data class ShowContentDetail(val itemId: String, val itemType: String) : ViewAction()
        data class ShowError(val message: String) : ViewAction()
    }

    private val _viewActions = kotlinx.coroutines.flow.MutableSharedFlow<Event<ViewAction>>()
    val viewActions: kotlinx.coroutines.flow.SharedFlow<Event<ViewAction>> = _viewActions.asSharedFlow()

    fun setFilter(filter: DownloadFilter) {
        _currentFilter.value = filter
    }

    fun onSeriesGroupToggled(contentId: String) {
        _expandedSeriesIds.value = if (_expandedSeriesIds.value.contains(contentId)) {
            _expandedSeriesIds.value - contentId
        } else {
            _expandedSeriesIds.value + contentId
        }
    }

    fun onPlayItem(downloadEntity: DownloadEntity) {
        viewModelScope.launch {
            val catalog = catalogRepository.getCatalog()
            val itemFromCatalog = when (downloadEntity.contentType) {
                "movie" -> catalog?.movies?.find { it.id == downloadEntity.contentId }
                "serie" -> catalog?.series?.find { it.id == downloadEntity.contentId }
                "documentary" -> catalog?.documentaries?.find { it.id == downloadEntity.contentId }
                "shortfilm" -> catalog?.shortFilms?.find { it.id == downloadEntity.contentId }
                else -> null
            }

            val effectiveItem = itemFromCatalog ?: when (downloadEntity.contentType) {
                "movie" -> com.johang.audiocinemateca.data.model.Movie(
                    id = downloadEntity.contentId,
                    title = downloadEntity.title,
                    enlaces = listOf(downloadEntity.filePath ?: "")
                )
                "serie" -> {
                    val downloadedEpisodes = downloadRepository.getDownloadsForContent(downloadEntity.contentId).firstOrNull() ?: listOf(downloadEntity)
                    val chaptersMap = mutableMapOf<String, MutableList<com.johang.audiocinemateca.data.model.Episode>>()
                    
                    downloadedEpisodes.forEach { dep ->
                        val seasonKey = "Temporada ${dep.partIndex + 1}"
                        val list = chaptersMap.getOrPut(seasonKey) { mutableListOf() }
                        list.add(
                            com.johang.audiocinemateca.data.model.Episode(
                                capitulo = (dep.episodeIndex + 1).toString(),
                                titulo = dep.title,
                                enlace = dep.filePath ?: ""
                            )
                        )
                    }
                    
                    com.johang.audiocinemateca.data.model.Serie(
                        id = downloadEntity.contentId,
                        title = downloadEntity.title.substringBefore(" - ").substringBefore(":E").ifBlank { downloadEntity.title },
                        capitulos = chaptersMap
                    )
                }
                "documentary" -> com.johang.audiocinemateca.data.model.Documentary(
                    id = downloadEntity.contentId,
                    title = downloadEntity.title,
                    enlace = downloadEntity.filePath ?: ""
                )
                "shortfilm" -> com.johang.audiocinemateca.data.model.ShortFilm(
                    id = downloadEntity.contentId,
                    title = downloadEntity.title,
                    enlace = downloadEntity.filePath ?: ""
                )
                else -> null
            }

            if (effectiveItem != null) {
                _viewActions.emit(Event(ViewAction.NavigateToPlayer(
                    catalogItem = effectiveItem,
                    partIndex = downloadEntity.partIndex,
                    episodeIndex = downloadEntity.episodeIndex
                )))
            } else {
                _viewActions.emit(Event(ViewAction.ShowError("No se pudo iniciar la reproducción del archivo descargado.")))
            }
        }
    }

    fun onMoreClick(item: GroupedDownload) {
        viewModelScope.launch {
            _viewActions.emit(Event(ViewAction.ShowItemActions(item)))
        }
    }

    fun onEpisodeMoreClick(episode: DownloadEntity) {
        viewModelScope.launch {
            _viewActions.emit(Event(ViewAction.ShowEpisodeActions(episode)))
        }
    }

    fun onViewDetails(item: GroupedDownload) {
        viewModelScope.launch {
            val itemType = when (item.contentType) {
                "movie" -> "peliculas"
                "serie" -> "series"
                "documentary" -> "documentales"
                "shortfilm" -> "cortometrajes"
                else -> "unknown"
            }
            _viewActions.emit(Event(ViewAction.ShowContentDetail(item.contentId, itemType)))
        }
    }

    fun onDeleteItem(contentId: String, partIndex: Int, episodeIndex: Int, title: String, isGroup: Boolean) {
        viewModelScope.launch {
            _viewActions.emit(Event(ViewAction.ShowDeleteConfirmation(title, contentId, partIndex, episodeIndex, isGroup)))
        }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            val allDownloads = _allDownloads.first()
            // First, delete all the files
            allDownloads.forEach { entity ->
                entity.filePath?.let {
                    // We don't need to handle the result here as we are about to wipe the DB anyway
                    downloadRepository.deleteDownloadedFile(context, it)
                }
            }
            // After deleting files, clear the entire database table
            downloadRepository.deleteAllDownloads()
        }
    }


    fun confirmDeleteItem(contentId: String, partIndex: Int, episodeIndex: Int, isGroup: Boolean) {
        viewModelScope.launch {
            if (isGroup) {
                val group = (uiState.value as? DownloadsUiState.Success)?.downloads?.find { it.contentId == contentId }
                group?.episodes?.forEach { episode ->
                    deleteEpisode(episode)
                }
                // Attempt to delete the parent directory for older Android versions
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                    group?.episodes?.firstOrNull()?.filePath?.let { firstEpisodeFilePath ->
                        val absolutePath = downloadRepository.getFilePathFromUri(context, android.net.Uri.parse(firstEpisodeFilePath))
                        if (absolutePath != null) {
                            downloadRepository.deleteEmptyDirectory(absolutePath)
                        }
                    }
                }
            } else {
                val entityToDelete = downloadRepository.getDownload(contentId, partIndex, episodeIndex)
                if (entityToDelete != null) {
                    if (entityToDelete.contentType != "serie") {
                        // It's a movie, documentary, or short film. Delete all parts.
                        downloadRepository.getDownloadsForContent(contentId).firstOrNull()?.forEach { downloadPart ->
                            deleteEpisode(downloadPart)
                        }
                    } else {
                        // It's a single episode of a series. Delete only this one.
                        deleteEpisode(entityToDelete)
                    }
                } else {
                    _viewActions.emit(Event(ViewAction.ShowError("No se encontró la descarga para eliminar.")))
                }
            }
        }
    }

    private suspend fun deleteEpisode(episode: DownloadEntity) {
        episode.filePath?.let {
            when (val result = downloadRepository.deleteDownloadedFile(context, it)) {
                is DownloadRepository.FileDeletionResult.Success,
                is DownloadRepository.FileDeletionResult.FileDoesNotExist -> {
                    downloadRepository.deleteDownload(episode.contentId, episode.partIndex, episode.episodeIndex)
                }
                is DownloadRepository.FileDeletionResult.Failure -> {
                    _viewActions.emit(Event(ViewAction.ShowError("Error al borrar el archivo: ${result.exception.message}")))
                }
            }
        } ?: run {
            // If filePath is null, just delete the DB record
            downloadRepository.deleteDownload(episode.contentId, episode.partIndex, episode.episodeIndex)
        }
    }
}
