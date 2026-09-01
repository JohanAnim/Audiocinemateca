package com.johang.audiocinemateca.presentation.contentdetail

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.usecase.AddFavoriteUseCase
import com.johang.audiocinemateca.domain.usecase.CheckFavoriteStatusUseCase
import com.johang.audiocinemateca.domain.usecase.RemoveFavoriteUseCase
import com.johang.audiocinemateca.domain.usecase.GetContentStatsUseCase
import com.johang.audiocinemateca.domain.usecase.GetRatingUseCase
import com.johang.audiocinemateca.domain.usecase.SetRatingUseCase
import com.johang.audiocinemateca.data.repository.RatingStats
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.domain.DownloadRequest
import com.johang.audiocinemateca.domain.model.DownloadProgressInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.NumberFormat
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth

sealed class ViewAction {
    data class StartDownload(val url: String, val title: String, val contentId: String, val contentType: String, val partIndex: Int, val episodeIndex: Int, val seriesTitle: String? = null) : ViewAction()
    data class ShowDeleteConfirmation(val partIndex: Int, val episodeIndex: Int) : ViewAction()
    data class ShowCancelConfirmation(val partIndex: Int, val episodeIndex: Int) : ViewAction()
    data class ShowDeleteSeasonConfirmation(val seasonIndex: Int, val seasonName: String) : ViewAction()
    data class ShowCancelSeasonConfirmation(val seasonIndex: Int, val seasonName: String) : ViewAction()
    data class ShowDownloadFailed(val reason: String) : ViewAction()
    data class ShowError(val message: String) : ViewAction()
    data class ShowMessage(val message: String) : ViewAction()
    data class UpdateFavoriteIcon(val isFavorite: Boolean) : ViewAction()
    data class ShowSeasonDownloadStarted(val seasonNumber: Int, val count: Int) : ViewAction()
    data class ShowSeasonAlreadyDownloaded(val seasonNumber: Int) : ViewAction()
    object StopDownloadService : ViewAction()
}

sealed class DownloadState {
    object NotDownloaded : DownloadState()
    object Downloading : DownloadState()
    object Downloaded : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

sealed class SeasonDownloadState {
    data class NotDownloaded(val seasonIndex: Int = 0) : SeasonDownloadState()
    data class Downloading(val seasonIndex: Int = 0, val activeCount: Int = 0) : SeasonDownloadState()
    data class Downloaded(val seasonIndex: Int = 0) : SeasonDownloadState()
}

data class SeasonProgressInfo(
    val seasonIndex: Int,
    val totalEpisodes: Int,
    val downloadedEpisodes: Int,
    val downloadingEpisodes: Int,
    val currentDownloadingEpisodeTitle: String?,
    val overallProgress: Int, // 0..100
    val speedBytesPerSec: Long,
    val remainingSeconds: Long,
    val isDownloading: Boolean
)

@HiltViewModel
class ContentDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val progressFlow: MutableStateFlow<Int>,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val checkFavoriteStatusUseCase: CheckFavoriteStatusUseCase,
    private val getContentStatsUseCase: GetContentStatsUseCase,
    private val getRatingUseCase: GetRatingUseCase,
    private val setRatingUseCase: SetRatingUseCase,
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth
) : ViewModel() {

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem.asStateFlow()

    private val _viewCountText = MutableStateFlow<String>("")
    val viewCountText: StateFlow<String> = _viewCountText.asStateFlow()

    private val _ratingStats = MutableStateFlow(RatingStats())
    val ratingStats: StateFlow<RatingStats> = _ratingStats.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.NotDownloaded)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _episodeDownloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val episodeDownloadStates: StateFlow<Map<String, DownloadState>> = _episodeDownloadStates.asStateFlow()

    private val _targetedEpisodeIndices = MutableStateFlow<Pair<Int, Int>?>(null)
    val targetedEpisodeIndices: StateFlow<Pair<Int, Int>?> = _targetedEpisodeIndices.asStateFlow()

    private val _selectedSeasonIndex = MutableStateFlow(0)
    val selectedSeasonIndex: StateFlow<Int> = _selectedSeasonIndex.asStateFlow()

    fun setSelectedSeasonIndex(index: Int) {
        _selectedSeasonIndex.value = index
    }

    val singleDownloadProgress: StateFlow<DownloadProgressInfo?> = combine(
        _contentItem,
        _targetedEpisodeIndices,
        downloadManager.activeProgressFlow
    ) { item, targetIndices, progressMap ->
        if (item == null) {
            null
        } else if (item is Serie) {
            if (targetIndices != null) {
                val (seasonIndex, episodeIndex) = targetIndices
                progressMap["${item.id}_${seasonIndex}_${episodeIndex}"]
            } else {
                null
            }
        } else {
            progressMap["${item.id}_0_-1"]
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val seasonProgressState: StateFlow<SeasonProgressInfo?> = combine(
        _contentItem,
        _selectedSeasonIndex,
        _episodeDownloadStates,
        downloadManager.activeProgressFlow
    ) { item, seasonIndex, states, activeMap ->
        if (item !is Serie) {
            null
        } else {
            val seasons = item.capitulos.keys.sorted()
            val seasonKey = seasons.getOrNull(seasonIndex)
            val episodes = if (seasonKey != null) item.capitulos[seasonKey] ?: emptyList() else emptyList()

            if (episodes.isEmpty()) {
                null
            } else {
                val totalEpisodes = episodes.size
                var downloadedCount = 0
                var downloadingCount = 0

                episodes.indices.forEach { epIndex ->
                    when (states["${seasonIndex}_$epIndex"]) {
                        is DownloadState.Downloaded -> downloadedCount++
                        is DownloadState.Downloading -> downloadingCount++
                        else -> {}
                    }
                }

                val activeSeasonProgress = activeMap.values.filter { it.contentId == item.id && it.partIndex == seasonIndex }
                val isDownloading = downloadingCount > 0 || activeSeasonProgress.isNotEmpty()

                if (isDownloading) {
                    val currentDownloading = activeSeasonProgress.firstOrNull()
                    val totalSpeed = activeSeasonProgress.sumOf { it.speedBytesPerSec }

                    // Progreso ponderado sumando todos los capítulos activos y completados
                    val activeProgressSum = activeSeasonProgress.sumOf { it.progress }
                    val overallProgress = (((downloadedCount * 100) + activeProgressSum) / totalEpisodes.coerceAtLeast(1)).coerceIn(0, 100)

                    // Cálculo realista de tiempo restante considerando todos los capítulos restantes
                    val avgBytesPerEp = activeSeasonProgress.map { it.totalBytes }.filter { it > 0 }.average().takeIf { !it.isNaN() && it > 0 } ?: 35_000_000.0
                    val remainingActiveBytes = activeSeasonProgress.sumOf { (it.totalBytes - it.bytesDownloaded).coerceAtLeast(0L) }
                    val unstartedEpisodes = (totalEpisodes - downloadedCount - activeSeasonProgress.size).coerceAtLeast(0)
                    val totalRemainingBytes = remainingActiveBytes + (unstartedEpisodes * avgBytesPerEp.toLong())
                    val totalEstimatedRemaining = if (totalSpeed > 1024) (totalRemainingBytes / totalSpeed) else 0L

                    SeasonProgressInfo(
                        seasonIndex = seasonIndex,
                        totalEpisodes = totalEpisodes,
                        downloadedEpisodes = downloadedCount,
                        downloadingEpisodes = downloadingCount,
                        currentDownloadingEpisodeTitle = currentDownloading?.title,
                        overallProgress = overallProgress,
                        speedBytesPerSec = totalSpeed,
                        remainingSeconds = totalEstimatedRemaining,
                        isDownloading = true
                    )
                } else {
                    null
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val seasonDownloadState: StateFlow<SeasonDownloadState> = combine(
        _contentItem,
        _selectedSeasonIndex,
        _episodeDownloadStates,
        seasonProgressState
    ) { item, seasonIndex, states, progressInfo ->
        if (item !is Serie) {
            SeasonDownloadState.NotDownloaded(seasonIndex)
        } else {
            val seasons = item.capitulos.keys.sorted()
            val seasonKey = seasons.getOrNull(seasonIndex)
            val episodes = if (seasonKey != null) item.capitulos[seasonKey] ?: emptyList() else emptyList()

            if (episodes.isEmpty()) {
                SeasonDownloadState.NotDownloaded(seasonIndex)
            } else {
                val totalEpisodes = episodes.size
                var downloadedCount = 0

                episodes.indices.forEach { epIndex ->
                    if (states["${seasonIndex}_$epIndex"] is DownloadState.Downloaded) {
                        downloadedCount++
                    }
                }

                if (progressInfo?.isDownloading == true) {
                    SeasonDownloadState.Downloading(seasonIndex, progressInfo.downloadingEpisodes)
                } else if (downloadedCount == totalEpisodes) {
                    SeasonDownloadState.Downloaded(seasonIndex)
                } else {
                    SeasonDownloadState.NotDownloaded(seasonIndex)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeasonDownloadState.NotDownloaded(0))

    private val _viewActions = MutableSharedFlow<com.johang.audiocinemateca.util.Event<ViewAction>>()
    val viewActions: SharedFlow<com.johang.audiocinemateca.util.Event<ViewAction>> = _viewActions.asSharedFlow()

    init {
        // Escuchar progreso de descarga
        viewModelScope.launch {
            progressFlow.collect { progress ->
                _downloadProgress.value = progress
            }
        }
        
        // Sincronizar el estado de descarga principal con el capítulo seleccionado en series
        viewModelScope.launch {
            combine(
                _targetedEpisodeIndices,
                _episodeDownloadStates,
                _contentItem
            ) { target, states, item ->
                if (item is Serie && target != null) {
                    val key = "${target.first}_${target.second}"
                    states[key] ?: DownloadState.NotDownloaded
                } else {
                    null
                }
            }.collect { newState ->
                if (newState != null) {
                    _downloadState.value = newState
                }
            }
        }
    }

    fun loadContentDetail(itemId: String, itemType: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val catalog = catalogRepository.getCatalog()
                val pluralItemType = when (itemType) {
                    "pelicula" -> "peliculas"
                    "serie" -> "series"
                    "cortometraje" -> "cortometrajes"
                    "documental" -> "documentales"
                    else -> itemType
                }
                var item: CatalogItem? = when (pluralItemType) {
                    "peliculas" -> catalog?.movies?.find { it.id == itemId }
                    "series" -> catalog?.series?.find { it.id == itemId }
                    "cortometrajes" -> catalog?.shortFilms?.find { it.id == itemId }
                    "documentales" -> catalog?.documentaries?.find { it.id == itemId }
                    else -> null
                } ?: catalog?.let { cat ->
                    cat.movies?.find { it.id == itemId }
                        ?: cat.series?.find { it.id == itemId }
                        ?: cat.documentaries?.find { it.id == itemId }
                        ?: cat.shortFilms?.find { it.id == itemId }
                }

                if (item == null) {
                    val downloadedEntities = downloadRepository.getDownloadsForContent(itemId).firstOrNull()
                    if (!downloadedEntities.isNullOrEmpty()) {
                        val first = downloadedEntities.first()
                        item = when (first.contentType) {
                            "movie" -> Movie(id = first.contentId, title = first.title, enlaces = listOf(first.filePath ?: ""))
                            "serie" -> {
                                val chaptersMap = mutableMapOf<String, MutableList<com.johang.audiocinemateca.data.model.Episode>>()
                                downloadedEntities.forEach { dep ->
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
                                Serie(
                                    id = first.contentId,
                                    title = first.title.substringBefore(" - ").substringBefore(":E").ifBlank { first.title },
                                    capitulos = chaptersMap
                                )
                            }
                            "documentary" -> Documentary(id = first.contentId, title = first.title, enlace = first.filePath ?: "")
                            "shortfilm" -> ShortFilm(id = first.contentId, title = first.title, enlace = first.filePath ?: "")
                            else -> null
                        }
                    }
                }

                _contentItem.value = item
                if (item != null) {
                    // Favoritos ahora funciona siempre (localmente si no hay sesión)
                    checkFavoriteStatus(item.id)

                    // Cargar datos de la nube solo si hay usuario logueado
                    if (isUserLoggedIn) {
                        observeViewCount(item.id)
                        observeRating(item.id)
                    }
                    
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

    private fun observeViewCount(contentId: String) {
        viewModelScope.launch {
            getContentStatsUseCase(contentId).collect { count ->
                val formattedCount = NumberFormat.getNumberInstance(Locale.getDefault()).format(count)
                val suffix = if (count == 1L) "reproducción" else "reproducciones"
                _viewCountText.value = "$formattedCount $suffix"
            }
        }
    }

    private fun observeRating(contentId: String) {
        viewModelScope.launch {
            getRatingUseCase(contentId).collectLatest { stats ->
                _ratingStats.value = stats
            }
        }
    }

    fun submitRating(rating: Int) {
        if (!isUserLoggedIn) {
            viewModelScope.launch { _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("Inicia sesión para calificar"))) }
            return
        }
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                setRatingUseCase(item.id, rating)
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("¡Gracias por calificar!")))
            } catch (e: Exception) {
                Log.e("ContentDetailVM", "Error al calificar: ${e.message}")
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("No se pudo enviar la calificación. Inténtalo más tarde.")))
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
        }
    }

    private fun updateTargetedEpisode(item: Serie) {
        viewModelScope.launch {
            // Buscamos el progreso para obtener el último capítulo visto
            val allProgress = playbackProgressRepository.getPlaybackProgressForContent(item.id)
            updateTargetedEpisodeFromList(allProgress)
        }
    }

    fun updateTargetedEpisodeFromList(allProgress: List<PlaybackProgressEntity>) {
        val latest = allProgress.maxByOrNull { it.lastPlayedTimestamp }
        if (latest != null) {
            _targetedEpisodeIndices.value = Pair(latest.partIndex, latest.episodeIndex)
        } else {
            // Si no hay progreso, mantenemos el valor actual o 0,0 si es nulo
            if (_targetedEpisodeIndices.value == null) {
                _targetedEpisodeIndices.value = Pair(0, 0)
            }
        }
    }

    private fun initializeDownloadStates(item: CatalogItem) {
        viewModelScope.launch {
            downloadRepository.getDownloadsForContent(item.id).collect { downloadedEntities ->
                if (item !is Serie) {
                    val entity = downloadedEntities.firstOrNull()
                    val newState = when (entity?.downloadStatus) {
                        "COMPLETE" -> DownloadState.Downloaded
                        "DOWNLOADING" -> DownloadState.Downloading
                        "FAILED" -> DownloadState.Failed(entity.errorMessage ?: "La descarga anterior falló")
                        else -> DownloadState.NotDownloaded
                    }
                    _downloadState.value = newState
                } else {
                    val newStates = mutableMapOf<String, DownloadState>()
                    item.capitulos.forEach { (seasonKey, episodes) ->
                        val seasons = item.capitulos.keys.sorted()
                        val seasonIndex = seasons.indexOf(seasonKey)
                        episodes.forEachIndexed { episodeIndex, _ ->
                            val entity = downloadedEntities.find { it.partIndex == seasonIndex && it.episodeIndex == episodeIndex }
                            val key = "${seasonIndex}_${episodeIndex}"
                            val episodeState = when (entity?.downloadStatus) {
                                "COMPLETE" -> DownloadState.Downloaded
                                "DOWNLOADING", "QUEUED" -> DownloadState.Downloading
                                "FAILED" -> DownloadState.Failed(entity.errorMessage ?: "Falló")
                                else -> DownloadState.NotDownloaded
                            }
                            newStates[key] = episodeState
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
                onEpisodeDownloadAction(targetIndices.first, targetIndices.second)
            } else {
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
                                url = fullUrl, title = item.title, contentId = item.id,
                                contentType = contentType, partIndex = 0, episodeIndex = -1
                            )))
                            _downloadState.value = DownloadState.Downloading
                        }
                    }
                    is DownloadState.Downloading -> _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowCancelConfirmation(0, -1)))
                    is DownloadState.Downloaded -> _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDeleteConfirmation(0, -1)))
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
                            url = fullUrl, title = "T${seasonIndex + 1}:E${episode.capitulo} - ${episode.titulo}",
                            contentId = item.id, contentType = "serie", partIndex = seasonIndex,
                            episodeIndex = episodeIndex, seriesTitle = item.title
                        )))
                        val newStates = _episodeDownloadStates.value.toMutableMap()
                        newStates[key] = DownloadState.Downloading
                        _episodeDownloadStates.value = newStates
                    }
                }
                is DownloadState.Downloading -> _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowCancelConfirmation(seasonIndex, episodeIndex)))
                is DownloadState.Downloaded -> _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDeleteConfirmation(seasonIndex, episodeIndex)))
            }
        }
    }

    fun deleteDownload(partIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value ?: return@launch
            val downloadEntity = downloadRepository.getDownload(item.id, partIndex, episodeIndex)
            val filePath = downloadEntity?.filePath
            if (filePath != null) {
                downloadRepository.deleteDownloadedFile(context, filePath)
                downloadRepository.deleteDownload(item.id, partIndex, episodeIndex)
            } else {
                downloadRepository.deleteDownload(item.id, partIndex, episodeIndex)
            }
            if (item is Serie) {
                val remaining = downloadRepository.getDownloadsForContent(item.id).firstOrNull() ?: emptyList()
                if (remaining.none { it.downloadStatus == "COMPLETE" }) {
                    downloadRepository.deleteSeriesDirectoryIfEmpty(item.title)
                }
            }
        }
    }

    fun cancelDownload(partIndex: Int, episodeIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value ?: return@launch
            downloadManager.cancelDownload(item.id, partIndex, episodeIndex)
        }
    }

    fun downloadSeason(seasonIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value as? Serie ?: return@launch
            val seasonKey = item.capitulos.keys.sorted().getOrNull(seasonIndex) ?: return@launch
            val episodes = item.capitulos[seasonKey] ?: return@launch
            val baseUrl = "https://audiocinemateca.com/"

            val downloadedEntities = downloadRepository.getDownloadsForContent(item.id).firstOrNull() ?: emptyList()

            val requests = mutableListOf<DownloadRequest>()
            episodes.forEachIndexed { episodeIndex, episode ->
                val isCompleted = downloadedEntities.any {
                    it.partIndex == seasonIndex && it.episodeIndex == episodeIndex && it.downloadStatus == "COMPLETE"
                }
                val isDownloading = downloadedEntities.any {
                    it.partIndex == seasonIndex && it.episodeIndex == episodeIndex && it.downloadStatus == "DOWNLOADING"
                }
                if (!isCompleted && !isDownloading) {
                    val fullUrl = if (episode.enlace.startsWith("http")) episode.enlace else "${baseUrl.removeSuffix("/")}/${episode.enlace.removePrefix("/")}"
                    requests.add(
                        DownloadRequest(
                            url = fullUrl,
                            title = "T${seasonIndex + 1}:E${episode.capitulo} - ${episode.titulo}",
                            contentId = item.id,
                            contentType = "serie",
                            partIndex = seasonIndex,
                            episodeIndex = episodeIndex,
                            seriesTitle = item.title
                        )
                    )
                }
            }

            if (requests.isNotEmpty()) {
                downloadManager.enqueueBatch(requests)
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowSeasonDownloadStarted(seasonIndex + 1, requests.size)))
            } else {
                _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowSeasonAlreadyDownloaded(seasonIndex + 1)))
            }
        }
    }

    fun onSeasonDownloadAction(seasonIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value as? Serie ?: return@launch
            val seasons = item.capitulos.keys.sorted()
            val seasonName = seasons.getOrNull(seasonIndex) ?: (seasonIndex + 1).toString()
            val state = seasonDownloadState.value

            when (state) {
                is SeasonDownloadState.Downloaded -> {
                    _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowDeleteSeasonConfirmation(seasonIndex, seasonName)))
                }
                is SeasonDownloadState.Downloading -> {
                    _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowCancelSeasonConfirmation(seasonIndex, seasonName)))
                }
                is SeasonDownloadState.NotDownloaded -> {
                    downloadSeason(seasonIndex)
                }
            }
        }
    }

    fun cancelSeasonDownloads(seasonIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value as? Serie ?: return@launch
            downloadManager.cancelSeason(item.id, seasonIndex)
            val remaining = downloadRepository.getDownloadsForContent(item.id).firstOrNull() ?: emptyList()
            if (remaining.none { it.downloadStatus == "COMPLETE" }) {
                downloadRepository.deleteSeriesDirectoryIfEmpty(item.title)
            }
            _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("Descargas de la temporada canceladas.")))
        }
    }

    fun deleteSeasonDownloads(seasonIndex: Int) {
        viewModelScope.launch {
            val item = _contentItem.value as? Serie ?: return@launch
            val seasonKey = item.capitulos.keys.sorted().getOrNull(seasonIndex) ?: return@launch
            val episodes = item.capitulos[seasonKey] ?: return@launch

            episodes.indices.forEach { epIndex ->
                val entity = downloadRepository.getDownload(item.id, seasonIndex, epIndex)
                if (entity != null) {
                    if (entity.filePath != null) {
                        downloadRepository.deleteDownloadedFile(context, entity.filePath)
                    }
                    downloadRepository.deleteDownload(item.id, seasonIndex, epIndex)
                }
            }
            val remaining = downloadRepository.getDownloadsForContent(item.id).firstOrNull() ?: emptyList()
            if (remaining.none { it.downloadStatus == "COMPLETE" }) {
                downloadRepository.deleteSeriesDirectoryIfEmpty(item.title)
            }
            _viewActions.emit(com.johang.audiocinemateca.util.Event(ViewAction.ShowMessage("Capítulos descargados de la temporada eliminados.")))
        }
    }

    suspend fun getPlaybackProgressForContent(contentId: String): List<PlaybackProgressEntity> {
        return playbackProgressRepository.getPlaybackProgressForContent(contentId)
    }
}
