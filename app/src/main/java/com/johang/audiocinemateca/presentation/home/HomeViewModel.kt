package com.johang.audiocinemateca.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.FeaturedBanner
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.FeaturedBannerRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.repository.RankingRepository
import com.johang.audiocinemateca.data.repository.RecommendationRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.usecase.AddFavoriteUseCase
import com.johang.audiocinemateca.domain.usecase.CheckFavoriteStatusUseCase
import com.johang.audiocinemateca.domain.usecase.GetRatingUseCase
import com.johang.audiocinemateca.domain.usecase.RemoveFavoriteUseCase
import com.johang.audiocinemateca.util.TimeFormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

import com.johang.audiocinemateca.presentation.home.components.ContinueListeningItem
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import java.util.Locale

import com.johang.audiocinemateca.data.model.CuratedCollection
import com.johang.audiocinemateca.data.model.CuratedItem

data class HomeUiState(
    val isLoggedIn: Boolean = false,
    val userName: String? = null,
    val userEmail: String? = null,
    val featuredBanner: FeaturedBanner? = null,
    val bannerPlayText: String = "Empezar a oír",
    val isLoading: Boolean = true,
    val isBannerLoading: Boolean = true,
    val recommendations: List<CatalogItem> = emptyList(),
    val isRecommendationsLoading: Boolean = true,
    val top10Ranking: List<Pair<Int, CatalogItem>> = emptyList(),
    val isTop10Loading: Boolean = true,
    val continueListeningList: List<ContinueListeningItem> = emptyList(),
    val curatedCollections: List<CuratedCollection> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val featuredBannerRepository: FeaturedBannerRepository,
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val catalogRepository: CatalogRepository,
    private val contentRepository: com.johang.audiocinemateca.data.repository.ContentRepository,
    private val recommendationRepository: RecommendationRepository,
    private val rankingRepository: RankingRepository,
    private val checkFavoriteStatusUseCase: CheckFavoriteStatusUseCase,
    private val addFavoriteUseCase: AddFavoriteUseCase,
    private val removeFavoriteUseCase: RemoveFavoriteUseCase,
    private val getRatingUseCase: GetRatingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
        observeFeaturedBanner()
        loadRecommendations()
        observeWeeklyTop10()
        observeContinueListening()
        observeCuratedCollections()
    }

    private fun observeCuratedCollections() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("global_rankings")
                    .document("curated_collections")
                    .get()
                    .await()

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val rawCollections = snapshot.get("collections") as? List<Map<String, Any>> ?: return@launch
                    val parsedList = rawCollections.mapNotNull { map ->
                        val id = map["id"] as? String ?: ""
                        val title = map["title"] as? String ?: ""
                        val subtitle = map["subtitle"] as? String ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val rawItems = map["items"] as? List<Map<String, Any>> ?: emptyList()
                        val items = rawItems.mapNotNull { iMap ->
                            val itemId = iMap["id"] as? String ?: return@mapNotNull null
                            val itemTitle = iMap["title"] as? String ?: ""
                            val itemType = iMap["type"] as? String ?: "peliculas"
                            val itemYear = iMap["year"] as? String ?: ""
                            val itemGenre = iMap["genre"] as? String ?: ""
                            CuratedItem(itemId, itemTitle, itemType, itemYear, itemGenre)
                        }
                        if (title.isNotBlank() && items.isNotEmpty()) {
                            CuratedCollection(id, title, subtitle, items)
                        } else null
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(curatedCollections = parsedList)
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching curated collections", e)
            }
        }
    }

    private var allUnfinishedContinueListening: List<ContinueListeningItem> = emptyList()
    private var visibleContinueCount = 10

    private fun getEffectiveTimestamp(timestamp: Long): Long {
        val now = System.currentTimeMillis()
        return if (timestamp > now + 60_000L) now else timestamp
    }

    private var cachedCatalogIndex: Map<String, CatalogItem>? = null

    private fun calculateBannerProgressAndText(
        banner: FeaturedBanner?,
        mappedItems: List<ContinueListeningItem>
    ): Pair<Boolean, String> {
        if (banner == null || banner.itemId.isBlank()) {
            return Pair(false, "Empezar a oír")
        }

        val bannerProgress = mappedItems.find { item ->
            item.catalogItem.id.equals(banner.itemId, ignoreCase = true) ||
                    item.progress.contentId.equals(banner.itemId, ignoreCase = true)
        }

        if (bannerProgress != null && bannerProgress.progress.currentPositionMs > 0 && !bannerProgress.progress.isFinished) {
            val remainingStr = bannerProgress.remainingTimeText.replace("Quedan ", "").trim()
            val suffix = if (remainingStr == "1 min" || remainingStr == "1 min.") "restante" else "restantes"
            val text = if (bannerProgress.subtitleText.startsWith("T", ignoreCase = true)) {
                "Continuar ${bannerProgress.subtitleText} ($remainingStr $suffix)"
            } else {
                "Continuar ($remainingStr $suffix)"
            }
            return Pair(true, text)
        }

        return Pair(false, "Empezar a oír")
    }

    private fun observeContinueListening() {
        viewModelScope.launch(Dispatchers.IO) {
            playbackProgressRepository.getAllPlaybackProgress().collectLatest { progressList ->
                val fullCatalog = contentRepository.getCatalogResponse()

                val displayList = progressList.mapNotNull { progress ->
                    val cleanId = progress.contentId.trim()
                    if (cleanId.isBlank() || cleanId.lowercase(Locale.ROOT) in listOf("pelicula", "serie", "cortometraje", "documental", "documentales")) {
                        return@mapNotNull null
                    }
                    val catalogItem = contentRepository.getContentItem(cleanId, progress.contentType, fullCatalog)
                    if (catalogItem != null) {
                        Pair(progress, catalogItem)
                    } else null
                }

                // Deduplicación estricta por ID real de catálogo para el carrusel de Inicio
                val recentProgressList = displayList
                    .groupBy { it.second.id.lowercase(Locale.ROOT) }
                    .mapNotNull { entry -> entry.value.maxByOrNull { getEffectiveTimestamp(it.first.lastPlayedTimestamp) } }
                    .sortedByDescending { getEffectiveTimestamp(it.first.lastPlayedTimestamp) }
                    .filterNot { (progress, _) ->
                        val remainingMs = (progress.totalDurationMs - progress.currentPositionMs).coerceAtLeast(0L)
                        val ratio = if (progress.totalDurationMs > 0) (progress.currentPositionMs.toDouble() / progress.totalDurationMs.toDouble()) else 0.0
                        progress.isFinished || (progress.totalDurationMs > 0 && remainingMs <= 60_000L) || ratio >= 0.95
                    }

                val mappedItems = recentProgressList.mapNotNull { (progress, matchedItem) ->
                    val itemType = when (matchedItem) {
                        is Serie -> "serie"
                        is Movie -> "pelicula"
                        is Documentary -> "documental"
                        is ShortFilm -> "cortometraje"
                        else -> "pelicula"
                    }

                    val remainingMs = (progress.totalDurationMs - progress.currentPositionMs).coerceAtLeast(0L)
                    val isFinished = progress.isFinished || (progress.totalDurationMs > 0 && remainingMs <= 15_000L) ||
                            (progress.totalDurationMs > 0 && (progress.currentPositionMs.toDouble() / progress.totalDurationMs.toDouble()) >= 0.95)

                    val ratio = if (isFinished) {
                        1f
                    } else if (progress.totalDurationMs > 0) {
                        (progress.currentPositionMs.toFloat() / progress.totalDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    val remainingText = if (isFinished || remainingMs <= 1000) {
                        "Completado"
                    } else {
                        val remainingMinutes = (remainingMs / 1000 / 60).toInt()
                        "Quedan ${TimeFormatUtils.formatDuration(remainingMinutes)}"
                    }

                    val (displayItem, subtitleText) = if (matchedItem is Serie) {
                        val seasonIndex = progress.partIndex.coerceAtLeast(0)
                        val episodeIndex = progress.episodeIndex.coerceAtLeast(0)
                        val seasons = matchedItem.capitulos.keys.sorted()
                        val seasonKey = seasons.getOrNull(seasonIndex)
                        val epObj = matchedItem.capitulos[seasonKey]?.getOrNull(episodeIndex)
                        val epTitle = epObj?.titulo

                        val formattedTitle = if (!epTitle.isNullOrEmpty()) {
                            "${matchedItem.title} - $epTitle"
                        } else {
                            matchedItem.title
                        }
                        Pair(matchedItem.copy(title = formattedTitle), "T${seasonIndex + 1}:E${episodeIndex + 1}")
                    } else {
                        Pair(matchedItem, itemType.uppercase(Locale.ROOT))
                    }

                    ContinueListeningItem(
                        catalogItem = displayItem,
                        progress = progress,
                        itemType = itemType,
                        subtitleText = subtitleText,
                        remainingTimeText = remainingText,
                        progressRatio = ratio
                    )
                }

                allUnfinishedContinueListening = mappedItems.take(10)
                visibleContinueCount = 10

                val currentBanner = _uiState.value.featuredBanner
                val (hasStartedBanner, bannerPlayTextUpdated) = calculateBannerProgressAndText(currentBanner, mappedItems)

                // Depuración reactiva instantánea: si el usuario inició o vio una obra recomendada, sacarla de la lista
                val playedIds = progressList.filter { p ->
                    p.isFinished || p.currentPositionMs > 60_000L || (p.totalDurationMs > 0 && (p.currentPositionMs.toDouble() / p.totalDurationMs.toDouble()) >= 0.15)
                }.map { it.contentId.lowercase(Locale.ROOT) }.toSet()

                val currentRecs = _uiState.value.recommendations
                val updatedRecs = if (currentRecs.isNotEmpty() && currentRecs.any { playedIds.contains(it.id.lowercase(Locale.ROOT)) }) {
                    currentRecs.filterNot { playedIds.contains(it.id.lowercase(Locale.ROOT)) }
                } else {
                    currentRecs
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        continueListeningList = allUnfinishedContinueListening.take(visibleContinueCount),
                        bannerPlayText = bannerPlayTextUpdated,
                        featuredBanner = currentBanner?.copy(hasStartedListening = hasStartedBanner),
                        recommendations = updatedRecs
                    )
                }
            }
        }
    }

    fun loadMoreContinueListening() {
        if (visibleContinueCount >= allUnfinishedContinueListening.size) return
        visibleContinueCount += 10
        _uiState.value = _uiState.value.copy(
            continueListeningList = allUnfinishedContinueListening.take(visibleContinueCount)
        )
    }

    fun markAsWatched(progress: PlaybackProgressEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            playbackProgressRepository.savePlaybackProgress(
                progress.copy(
                    currentPositionMs = maxOf(progress.currentPositionMs, progress.totalDurationMs),
                    lastPlayedTimestamp = System.currentTimeMillis(),
                    isFinished = true
                )
            )
        }
    }

    fun checkAuthState() {
        viewModelScope.launch(Dispatchers.IO) {
            val user = firebaseAuth.currentUser
            val isNowLoggedIn = user != null

            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isLoggedIn = isNowLoggedIn,
                    userName = user?.displayName ?: user?.email?.substringBefore("@"),
                    userEmail = user?.email,
                    isLoading = false
                )
            }
        }
    }

    fun loadRecommendations(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isRecommendationsLoading = true)
            }
            val userId = _uiState.value.userEmail ?: _uiState.value.userName ?: "guest"
            val recommendationsList = recommendationRepository.getRecommendations(
                userId = userId,
                forceRefresh = forceRefresh
            ).distinctBy { it.id }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    recommendations = recommendationsList,
                    isRecommendationsLoading = false
                )
            }
        }
    }

    private fun observeWeeklyTop10() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isTop10Loading = true)
            }
            rankingRepository.observeWeeklyTop10().collectLatest { top10List ->
                val cleanList = top10List.distinctBy { it.second.id }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        top10Ranking = cleanList,
                        isTop10Loading = false
                    )
                }
            }
        }
    }

    fun loadWeeklyTop10(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isTop10Loading = true)
            }
            val top10List = rankingRepository.getWeeklyTop10(forceRefresh = forceRefresh).distinctBy { it.second.id }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    top10Ranking = top10List,
                    isTop10Loading = false
                )
            }
        }
    }

    companion object {
        val DEFAULT_BANNER = FeaturedBanner(
            id = "default_featured",
            title = "Audiocinemateca: Cine y Series Audiodescritas",
            description = "Explora contenido audiodescrito en español (audesc). Un narrador relata lo que sucede en pantalla para llevar el entretenimiento al siguiente nivel de manera fácil y accesible.",
            rating = 5.0,
            maxRating = 5,
            genres = listOf("Audiodescrito", "Cine", "Accesible"),
            linkUrl = "",
            itemId = "",
            itemType = "pelicula",
            hasStartedListening = false,
            isFavorite = false
        )
    }

    private var bannerJob: kotlinx.coroutines.Job? = null

    fun observeFeaturedBanner() {
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(isBannerLoading = _uiState.value.featuredBanner == null)
            }
            featuredBannerRepository.getFeaturedBanner()
                .catch { e ->
                    Log.e("HomeViewModel", "Error al observar banner destacado", e)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            isBannerLoading = false
                        )
                    }
                }
                .collectLatest { banner ->
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            featuredBanner = banner,
                            isBannerLoading = false
                        )
                    }

                    if (banner != null && (banner.itemId.isNotEmpty() || banner.linkUrl.isNotEmpty())) {
                        syncBannerStates(banner)
                    }
                }
        }
    }

    private var bannerSyncJob: kotlinx.coroutines.Job? = null

    private fun syncBannerStates(banner: FeaturedBanner) {
        bannerSyncJob?.cancel()
        bannerSyncJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val catalog = catalogRepository.getCatalog() ?: return@launch
                
                val cleanUrl = banner.linkUrl.trim().trimEnd('/')
                val urlUri = try { android.net.Uri.parse(cleanUrl) } catch (e: Exception) { null }
                
                val extractedFromUrl = urlUri?.getQueryParameter("id")
                    ?: urlUri?.getQueryParameter("itemId")
                    ?: cleanUrl.split('/').lastOrNull()?.takeIf { it.lowercase() != "serie" && it.lowercase() != "series" }
                    ?: ""

                val targetId = extractedFromUrl.ifEmpty { banner.itemId }
                val isSeriesUrl = cleanUrl.contains("/serie", ignoreCase = true)

                val allItems = mutableListOf<CatalogItem>()
                if (isSeriesUrl) {
                    catalog.series?.let { allItems.addAll(it) }
                    catalog.movies?.let { allItems.addAll(it) }
                    catalog.documentaries?.let { allItems.addAll(it) }
                    catalog.shortFilms?.let { allItems.addAll(it) }
                } else {
                    catalog.movies?.let { allItems.addAll(it) }
                    catalog.series?.let { allItems.addAll(it) }
                    catalog.documentaries?.let { allItems.addAll(it) }
                    catalog.shortFilms?.let { allItems.addAll(it) }
                }

                val foundItem = allItems.find { item ->
                    targetId.isNotEmpty() && item.id.equals(targetId, ignoreCase = true)
                }

                val currentUiBanner = _uiState.value.featuredBanner
                val currentFav = currentUiBanner?.isFavorite ?: banner.isFavorite

                if (foundItem != null) {
                    val realType = when (foundItem) {
                        is Serie -> "serie"
                        is Movie -> "pelicula"
                        is Documentary -> "documental"
                        is ShortFilm -> "cortometraje"
                        else -> "pelicula"
                    }

                    val rawGenre = when (foundItem) {
                        is Movie -> foundItem.genero
                        is Serie -> foundItem.genero
                        is Documentary -> foundItem.genero
                        is ShortFilm -> foundItem.genero
                        else -> ""
                    }

                    val parsedGenres = rawGenre.split(".", "|", ",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }

                    val officialRatingRaw = foundItem.filmaffinity.trim().replace(",", ".")
                    val parsedOfficialRating = officialRatingRaw.toDoubleOrNull()?.let { rawVal ->
                        if (rawVal > 5.0) rawVal / 2.0 else rawVal
                    } ?: if (banner.rating > 0.0) banner.rating else 5.0

                    val candidateBanner = banner.copy(
                        itemId = foundItem.id,
                        itemType = realType,
                        rating = parsedOfficialRating,
                        genres = if (parsedGenres.isNotEmpty()) parsedGenres else banner.genres,
                        title = banner.title.ifBlank { foundItem.title },
                        description = banner.description.ifBlank {
                            when (foundItem) {
                                is Movie -> foundItem.sinopsis
                                is Serie -> foundItem.sinopsis
                                is Documentary -> foundItem.sinopsis
                                is ShortFilm -> foundItem.sinopsis
                                else -> banner.description
                            }
                        },
                        isFavorite = currentFav
                    )

                    val (hasStartedBanner, bannerPlayTextUpdated) = calculateBannerProgressAndText(
                        candidateBanner, allUnfinishedContinueListening
                    )
                    val updatedBanner = candidateBanner.copy(hasStartedListening = hasStartedBanner)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            featuredBanner = updatedBanner,
                            bannerPlayText = bannerPlayTextUpdated
                        )
                    }
                    observeBannerFavoriteStatus(foundItem.id)
                } else {
                    val (hasStartedBanner, bannerPlayTextUpdated) = calculateBannerProgressAndText(
                        banner, allUnfinishedContinueListening
                    )
                    val updatedBanner = banner.copy(
                        isFavorite = currentFav,
                        hasStartedListening = hasStartedBanner
                    )
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            featuredBanner = updatedBanner,
                            bannerPlayText = bannerPlayTextUpdated
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var bannerFavJob: kotlinx.coroutines.Job? = null

    private fun observeBannerFavoriteStatus(itemId: String) {
        bannerFavJob?.cancel()
        if (itemId.isBlank()) return
        bannerFavJob = viewModelScope.launch(Dispatchers.IO) {
            checkFavoriteStatusUseCase(itemId).collectLatest { isFav ->
                val current = _uiState.value.featuredBanner
                if (current != null && current.itemId.equals(itemId, ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            featuredBanner = current.copy(isFavorite = isFav)
                        )
                    }
                }
            }
        }
    }

    fun toggleFavoriteBanner() {
        val banner = _uiState.value.featuredBanner ?: return
        if (banner.itemId.isEmpty()) return

        viewModelScope.launch {
            if (banner.isFavorite) {
                removeFavoriteUseCase(banner.itemId)
            } else {
                addFavoriteUseCase(
                    contentId = banner.itemId,
                    title = banner.title,
                    contentType = banner.itemType
                )
            }
        }
    }

    fun publishNewBanner(
        title: String,
        description: String,
        linkUrl: String,
        rating: Double = 5.0,
        genres: List<String> = emptyList(),
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val result = featuredBannerRepository.publishFeaturedBanner(
                title = title,
                description = description,
                linkUrl = linkUrl,
                rating = rating,
                genres = genres
            )
            onResult(result.isSuccess)
        }
    }
}
