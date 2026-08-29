package com.johang.audiocinemateca.presentation.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.Comment
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.CommentRepository
import com.johang.audiocinemateca.domain.usecase.AddCommentUseCase
import com.johang.audiocinemateca.domain.usecase.IncrementViewCountUseCase
import com.johang.audiocinemateca.data.repository.VoteStats
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.usecase.ManageVoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChapterSynopsisState {
    object Initial : ChapterSynopsisState()
    object Loading : ChapterSynopsisState()
    data class Success(val synopsis: String) : ChapterSynopsisState()
    data class Error(val message: String) : ChapterSynopsisState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val incrementViewCountUseCase: IncrementViewCountUseCase,
    private val manageVoteUseCase: ManageVoteUseCase,
    private val commentRepository: CommentRepository,
    private val addCommentUseCase: AddCommentUseCase,
    private val geminiRepository: GeminiRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem

    private val _chapterSynopsisState = MutableStateFlow<ChapterSynopsisState>(ChapterSynopsisState.Initial)
    val chapterSynopsisState: StateFlow<ChapterSynopsisState> = _chapterSynopsisState.asStateFlow()

    private val _isSynopsisExpanded = MutableStateFlow(false)
    val isSynopsisExpanded: StateFlow<Boolean> = _isSynopsisExpanded.asStateFlow()

    private val synopsisCache = mutableMapOf<String, String>()

    private val _voteStats = MutableStateFlow(VoteStats())
    val voteStats: StateFlow<VoteStats> = _voteStats.asStateFlow()

    private val _commentPreview = MutableStateFlow<Pair<Long, Comment?>>(Pair(0L, null))
    val commentPreview: StateFlow<Pair<Long, Comment?>> = _commentPreview.asStateFlow()

    private val _sortOption = MutableStateFlow<SortOption>(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val _commentPostedEvent = MutableSharedFlow<Boolean>()
    val commentPostedEvent = _commentPostedEvent.asSharedFlow()

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    private val _rawComments = MutableStateFlow<List<Comment>>(emptyList())
    private val _pendingComments = MutableStateFlow<List<Comment>>(emptyList())

    val allComments: Flow<List<Comment>> = combine(_rawComments, _pendingComments, _sortOption) { raw: List<Comment>, pending: List<Comment>, sort: SortOption ->
        val merged = pending + raw
        when (sort) {
            SortOption.RECENT -> merged.sortedByDescending { if (it.isPending) Long.MAX_VALUE else it.timestamp.seconds }
            SortOption.OLDEST -> merged.sortedBy { if (it.isPending) Long.MAX_VALUE else it.timestamp.seconds }
            SortOption.POPULAR -> merged.sortedByDescending { it.likes.size }
            SortOption.UNPOPULAR -> merged.sortedBy { it.likes.size }
        }
    }

    private var viewCountJob: Job? = null
    private val countedContentIds = mutableSetOf<String>()
    
    private var currentPartIndex = -1
    private var currentEpisodeIndex = -1

    fun toggleSynopsis() {
        val nextExpanded = !_isSynopsisExpanded.value
        _isSynopsisExpanded.value = nextExpanded
        if (nextExpanded) {
            val currentState = _chapterSynopsisState.value
            if (currentState is ChapterSynopsisState.Initial || currentState is ChapterSynopsisState.Error) {
                loadChapterSynopsis()
            }
        }
    }

    fun loadChapterSynopsis(forceRefresh: Boolean = false) {
        val item = _contentItem.value ?: return
        val cacheKey = "${item.id}_${currentPartIndex}_${currentEpisodeIndex}"
        if (!forceRefresh && synopsisCache.containsKey(cacheKey)) {
            _chapterSynopsisState.value = ChapterSynopsisState.Success(synopsisCache[cacheKey]!!)
            return
        }

        viewModelScope.launch {
            _chapterSynopsisState.value = ChapterSynopsisState.Loading
            try {
                val chapterTitle = when (item) {
                    is Serie -> {
                        val sKeys = item.capitulos.keys.sorted()
                        val sKey = sKeys.getOrNull(currentPartIndex)
                        val ep = sKey?.let { item.capitulos[it]?.getOrNull(currentEpisodeIndex) }
                        if (ep != null) "T${currentPartIndex + 1}:E${ep.capitulo} - ${ep.titulo}" else "Capítulo"
                    }
                    is Movie -> if (item.enlaces.size > 1) "Parte ${currentPartIndex + 1}" else item.title
                    is Documentary -> item.title
                    is ShortFilm -> item.title
                    else -> "Capítulo actual"
                }

                val seasonNum = if (item is Serie) currentPartIndex + 1 else null
                val epNum = if (item is Serie) currentEpisodeIndex + 1 else null

                val synopsis = geminiRepository.generateChapterSynopsis(
                    title = item.title,
                    chapterTitle = chapterTitle,
                    generalDescription = item.sinopsis,
                    seasonNumber = seasonNum,
                    episodeNumber = epNum
                )

                if (!synopsis.isNullOrBlank()) {
                    synopsisCache[cacheKey] = synopsis
                    _chapterSynopsisState.value = ChapterSynopsisState.Success(synopsis)
                } else {
                    _chapterSynopsisState.value = ChapterSynopsisState.Error("No se pudo generar la sinopsis para este capítulo.")
                }
            } catch (e: Exception) {
                _chapterSynopsisState.value = ChapterSynopsisState.Error("Error al consultar la IA: ${e.message}")
            }
        }
    }

    fun updateCurrentEpisode(partIndex: Int, episodeIndex: Int) {
        currentPartIndex = partIndex
        currentEpisodeIndex = episodeIndex
        _contentItem.value?.let { 
            if (isUserLoggedIn) {
                loadVoteStats(it.id)
                loadCommentsPreview(it.id)
                loadAllComments(it.id)
            }
            val cacheKey = "${it.id}_${currentPartIndex}_${currentEpisodeIndex}"
            if (synopsisCache.containsKey(cacheKey)) {
                _chapterSynopsisState.value = ChapterSynopsisState.Success(synopsisCache[cacheKey]!!)
            } else {
                _chapterSynopsisState.value = ChapterSynopsisState.Initial
                if (_isSynopsisExpanded.value) {
                    loadChapterSynopsis()
                }
            }
        }
    }

    private fun loadVoteStats(contentId: String) {
        viewModelScope.launch {
            try {
                manageVoteUseCase.getVoteStats(contentId, currentPartIndex, currentEpisodeIndex)
                    .collectLatest { stats ->
                        _voteStats.value = stats
                    }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Error loading votes", e)
            }
        }
    }

    private fun loadCommentsPreview(contentId: String) {
        viewModelScope.launch {
            try {
                commentRepository.getCommentsPreview(contentId, currentPartIndex, currentEpisodeIndex)
                    .collectLatest { preview ->
                        _commentPreview.value = preview
                    }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Error loading comments preview", e)
            }
        }
    }

    private fun loadAllComments(contentId: String) {
        viewModelScope.launch {
            try {
                commentRepository.getAllComments(contentId, currentPartIndex, currentEpisodeIndex)
                    .collectLatest { comments ->
                        _rawComments.value = comments
                    }
            } catch (e: Exception) {
                Log.e("PlayerVM", "Error loading all comments", e)
            }
        }
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun onAddCommentClicked(text: String) {
        val item = _contentItem.value ?: return
        val user = auth.currentUser ?: return
        
        val type = when (item) {
            is Movie -> "peliculas"
            is Serie -> "series"
            is Documentary -> "documentales"
            is ShortFilm -> "cortometrajes"
            else -> ""
        }

        val tempComment = Comment(
            id = "temp_${System.currentTimeMillis()}",
            userId = user.uid,
            userName = user.displayName ?: "Tú",
            commentText = text,
            isPending = true,
            contentId = item.id,
            contentTitle = item.title,
            contentType = type,
            partIndex = currentPartIndex,
            episodeIndex = currentEpisodeIndex
        )

        viewModelScope.launch {
            try {
                _pendingComments.value = _pendingComments.value + tempComment
                addCommentUseCase(
                    contentId = item.id,
                    contentTitle = item.title,
                    contentType = type,
                    partIndex = currentPartIndex,
                    episodeIndex = currentEpisodeIndex,
                    text = text
                )
                _pendingComments.value = _pendingComments.value - tempComment
                _commentPostedEvent.emit(true)
            } catch (e: Exception) {
                _pendingComments.value = _pendingComments.value - tempComment
                Log.e("PlayerVM", "Fallo al publicar comentario: ${e.message}")
                _toastMessage.emit("No se pudo publicar (Cuota de Firebase excedida).")
            }
        }
    }

    fun onCommentLikeClicked(commentId: String) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                commentRepository.toggleCommentLike(item.id, currentPartIndex, currentEpisodeIndex, commentId)
            } catch (e: Exception) {
                _toastMessage.emit("Error al procesar el like.")
            }
        }
    }

    fun onDeleteCommentClicked(commentId: String) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                commentRepository.deleteComment(item.id, currentPartIndex, currentEpisodeIndex, commentId)
            } catch (e: Exception) {
                _toastMessage.emit("No se pudo eliminar el comentario.")
            }
        }
    }

    fun onReportCommentClicked(comment: Comment) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                commentRepository.reportComment(item.id, currentPartIndex, currentEpisodeIndex, comment)
            } catch (e: Exception) {
                _toastMessage.emit("No se pudo enviar el reporte.")
            }
        }
    }

    fun onLikeClicked() {
        val item = _contentItem.value ?: return
        if (auth.currentUser == null) return 
        viewModelScope.launch {
            try {
                manageVoteUseCase.toggleVote(item.id, currentPartIndex, currentEpisodeIndex, _voteStats.value.userVote, 1)
            } catch (e: Exception) {
                _toastMessage.emit("No se pudo registrar tu voto.")
            }
        }
    }

    fun onDislikeClicked() {
        val item = _contentItem.value ?: return
        if (auth.currentUser == null) return
        viewModelScope.launch {
            try {
                manageVoteUseCase.toggleVote(item.id, currentPartIndex, currentEpisodeIndex, _voteStats.value.userVote, -1)
            } catch (e: Exception) {
                _toastMessage.emit("No se pudo registrar tu voto.")
            }
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun setContentItem(item: CatalogItem, partIndex: Int, episodeIndex: Int) {
        _contentItem.value = item
        currentPartIndex = partIndex
        currentEpisodeIndex = episodeIndex
        if (isUserLoggedIn) {
            loadVoteStats(item.id)
            loadCommentsPreview(item.id)
            loadAllComments(item.id)
        }
        val cacheKey = "${item.id}_${currentPartIndex}_${currentEpisodeIndex}"
        if (synopsisCache.containsKey(cacheKey)) {
            _chapterSynopsisState.value = ChapterSynopsisState.Success(synopsisCache[cacheKey]!!)
        } else {
            _chapterSynopsisState.value = ChapterSynopsisState.Initial
            if (_isSynopsisExpanded.value) {
                loadChapterSynopsis()
            }
        }
    }

    fun getDetailedTitle(): String {
        val item = _contentItem.value ?: return ""
        if (currentEpisodeIndex != -1 && item is Serie) {
            val seasons = item.capitulos.keys.sorted()
            val seasonKey = seasons.getOrNull(currentPartIndex)
            val episode = item.capitulos[seasonKey]?.getOrNull(currentEpisodeIndex)
            if (episode != null) {
                return "T${currentPartIndex + 1}:E${episode.capitulo} - ${episode.titulo}"
            }
        }
        return item.title
    }

    fun trackViewStart(contentId: String) {
        if (!isUserLoggedIn) return
        if (countedContentIds.contains(contentId)) return
        viewCountJob?.cancel()
        viewCountJob = viewModelScope.launch {
            delay(15000)
            try {
                incrementViewCountUseCase(contentId)
                countedContentIds.add(contentId)
            } catch (e: Exception) {}
        }
    }
}