package com.johang.audiocinemateca.presentation.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.model.Comment
import com.johang.audiocinemateca.data.repository.CommentRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.data.repository.ServerMigrationManager
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.johang.audiocinemateca.data.repository.IndexMissingException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch

import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm

import com.google.firebase.firestore.DocumentSnapshot

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
    private val searchRepository: SearchRepository,
    private val serverMigrationManager: ServerMigrationManager
) : ViewModel() {

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _indexErrorUrl = MutableSharedFlow<String>()
    val indexErrorUrl = _indexErrorUrl.asSharedFlow()

    private val _currentFilter = MutableStateFlow("Más recientes")
    val currentFilter: StateFlow<String> = _currentFilter

    private var lastVisibleDocument: DocumentSnapshot? = null
    private var isLastPage = false

    init {
        loadComments(isNextPage = false)
    }

    fun setFilter(filter: String) {
        _currentFilter.value = filter
        loadComments(isNextPage = false)
    }

    fun retryLoad() {
        loadComments(isNextPage = false)
    }

    fun loadMoreComments() {
        if (_isLoading.value || isLastPage) return
        loadComments(isNextPage = true)
    }

    private fun loadComments(isNextPage: Boolean) {
        if (!isNextPage) {
            lastVisibleDocument = null
            isLastPage = false
        }

        viewModelScope.launch {
            _isLoading.value = true
            commentRepository.getGlobalComments(_currentFilter.value, lastVisibleDocument)
                .catch { e ->
                    if (e is IndexMissingException) {
                        _indexErrorUrl.emit(e.url)
                    } else {
                        serverMigrationManager.reportFirestoreError(e)
                        _connectionError.value = serverMigrationManager.getFriendlyFirestoreErrorMessage(e)
                    }
                    _isLoading.value = false
                }
                .collectLatest { result ->
                    serverMigrationManager.clearTransientErrors()
                    _connectionError.value = null
                    if (isNextPage) {
                        _comments.value = _comments.value + result.comments
                    } else {
                        _comments.value = result.comments
                    }
                    
                    lastVisibleDocument = result.lastVisible
                    if (result.comments.size < 20) {
                        isLastPage = true
                    }
                    _isLoading.value = false
                }
        }
    }

    suspend fun getCatalogItem(contentId: String, contentType: String, isSeriesHint: Boolean = false): CatalogItem? {
        return if (contentType.isNotEmpty()) {
            searchRepository.getCatalogItemByIdAndType(contentId, contentType)
        } else {
            searchRepository.findCatalogItemById(contentId, isSeriesHint)
        }
    }
}
