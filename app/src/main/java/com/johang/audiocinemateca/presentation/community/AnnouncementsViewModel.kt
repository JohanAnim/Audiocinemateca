package com.johang.audiocinemateca.presentation.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.model.Announcement
import com.johang.audiocinemateca.data.repository.AnnouncementRepository
import com.johang.audiocinemateca.data.repository.ServerMigrationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementRepository,
    private val serverMigrationManager: ServerMigrationManager
) : ViewModel() {

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    init {
        loadAnnouncements()
    }

    fun retryLoad() {
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAnnouncements()
                .catch { e ->
                    serverMigrationManager.reportFirestoreError(e)
                    _connectionError.value = serverMigrationManager.getFriendlyFirestoreErrorMessage(e)
                    _isLoading.value = false
                }
                .collectLatest {
                    serverMigrationManager.clearTransientErrors()
                    _connectionError.value = null
                    _announcements.value = it
                    _isLoading.value = false
                }
        }
    }

    fun postAnnouncement(text: String) {
        viewModelScope.launch {
            try {
                repository.createAnnouncement(text)
            } catch (e: Exception) {
                serverMigrationManager.reportFirestoreError(e)
                _connectionError.value = serverMigrationManager.getFriendlyFirestoreErrorMessage(e)
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteAnnouncement(id)
            } catch (e: Exception) {
                serverMigrationManager.reportFirestoreError(e)
                _connectionError.value = serverMigrationManager.getFriendlyFirestoreErrorMessage(e)
            }
        }
    }

    fun toggleReaction(announcementId: String, reactionEmoji: String) {
        viewModelScope.launch {
            try {
                repository.toggleReaction(announcementId, reactionEmoji)
            } catch (e: Exception) {
                serverMigrationManager.reportFirestoreError(e)
                _connectionError.value = serverMigrationManager.getFriendlyFirestoreErrorMessage(e)
            }
        }
    }
}
