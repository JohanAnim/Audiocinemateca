package com.johang.audiocinemateca.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.Comment
import com.johang.audiocinemateca.data.repository.CommentRepository
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption { RECENT, OLDEST, POPULAR, UNPOPULAR }

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val incrementViewCountUseCase: IncrementViewCountUseCase,
    private val manageVoteUseCase: ManageVoteUseCase,
    private val commentRepository: CommentRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem

    private val _voteStats = MutableStateFlow(VoteStats())
    val voteStats: StateFlow<VoteStats> = _voteStats.asStateFlow()

    private val _commentPreview = MutableStateFlow<Pair<Long, Comment?>>(Pair(0L, null))
    val commentPreview: StateFlow<Pair<Long, Comment?>> = _commentPreview.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RECENT)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    private val _rawComments = MutableStateFlow<List<Comment>>(emptyList())
    val allComments: Flow<List<Comment>> = combine(_rawComments, _sortOption) { comments, sort ->
        when (sort) {
            SortOption.RECENT -> comments.sortedByDescending { it.timestamp }
            SortOption.OLDEST -> comments.sortedBy { it.timestamp }
            SortOption.POPULAR -> comments.sortedByDescending { it.likes.size }
            SortOption.UNPOPULAR -> comments.sortedBy { it.likes.size }
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
            manageVoteUseCase.getVoteStats(contentId, currentPartIndex, currentEpisodeIndex)
                .collectLatest { stats ->
                    _voteStats.value = stats
                }
        }
    }

    private fun loadCommentsPreview(contentId: String) {
        viewModelScope.launch {
            commentRepository.getCommentsPreview(contentId, currentPartIndex, currentEpisodeIndex)
                .collectLatest { preview ->
                    _commentPreview.value = preview
                }
        }
    }

    private fun loadAllComments(contentId: String) {
        viewModelScope.launch {
            commentRepository.getAllComments(contentId, currentPartIndex, currentEpisodeIndex)
                .collectLatest { comments ->
                    _rawComments.value = comments
                }
        }
    }
    
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun onAddCommentClicked(text: String) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            commentRepository.addComment(item.id, currentPartIndex, currentEpisodeIndex, text)
        }
    }

    fun onCommentLikeClicked(commentId: String) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            commentRepository.toggleCommentLike(item.id, currentPartIndex, currentEpisodeIndex, commentId)
        }
    }

    fun onDeleteCommentClicked(commentId: String) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                commentRepository.deleteComment(item.id, currentPartIndex, currentEpisodeIndex, commentId)
            } catch (e: Exception) {}
        }
    }

    fun onReportCommentClicked(comment: Comment) {
        val item = _contentItem.value ?: return
        viewModelScope.launch {
            try {
                commentRepository.reportComment(item.id, currentPartIndex, currentEpisodeIndex, comment)
            } catch (e: Exception) {}
        }
    }

    fun onLikeClicked() {
        val item = _contentItem.value ?: return
        if (auth.currentUser == null) return 
        viewModelScope.launch {
            manageVoteUseCase.toggleVote(item.id, currentPartIndex, currentEpisodeIndex, _voteStats.value.userVote, 1)
        }
    }

    fun onDislikeClicked() {
        val item = _contentItem.value ?: return
        if (auth.currentUser == null) return
        viewModelScope.launch {
            manageVoteUseCase.toggleVote(item.id, currentPartIndex, currentEpisodeIndex, _voteStats.value.userVote, -1)
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
        return if (currentEpisodeIndex != -1) {
            "${item.title} - T${currentPartIndex + 1} E${currentEpisodeIndex + 1}"
        } else {
            item.title
        }
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