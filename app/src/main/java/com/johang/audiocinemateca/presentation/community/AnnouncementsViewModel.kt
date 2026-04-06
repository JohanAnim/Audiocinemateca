package com.johang.audiocinemateca.presentation.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.model.Announcement
import com.johang.audiocinemateca.data.repository.AnnouncementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnnouncementsViewModel @Inject constructor(
    private val repository: AnnouncementRepository
) : ViewModel() {

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getAnnouncements().collectLatest {
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
                // Manejar error de permisos o red
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteAnnouncement(id)
            } catch (e: Exception) {
                // Manejar error
            }
        }
    }

    fun toggleReaction(announcementId: String, reactionEmoji: String) {
        viewModelScope.launch {
            try {
                repository.toggleReaction(announcementId, reactionEmoji)
            } catch (e: Exception) {
                // Manejar error
            }
        }
    }
}
