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

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val incrementViewCountUseCase: IncrementViewCountUseCase,
    private val manageVoteUseCase: ManageVoteUseCase,
    private val commentRepository: CommentRepository,
    private val addCommentUseCase: AddCommentUseCase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem

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

    fun updateCurrentEpisode(partIndex: Int, episodeIndex: Int) {
        currentPartIndex = partIndex
        currentEpisodeIndex = episodeIndex
        _contentItem.value?.let { 
            if (isUserLoggedIn) {
                loadVoteStats(it.id)
                loadCommentsPreview(it.id)
                loadAllComments(it.id)
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