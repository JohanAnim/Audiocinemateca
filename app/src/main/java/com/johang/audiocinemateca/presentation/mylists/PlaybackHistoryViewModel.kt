package com.johang.audiocinemateca.presentation.mylists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.johang.audiocinemateca.data.repository.ContentRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@HiltViewModel
class PlaybackHistoryViewModel @Inject constructor(
    private val playbackProgressRepository: PlaybackProgressRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _historyItems = MutableStateFlow<List<HistoryListItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryListItem>> = _historyItems

    init {
        viewModelScope.launch {
            val fullCatalog = contentRepository.getCatalogResponse()

            playbackProgressRepository.getAllPlaybackProgress().collect { progressList ->
                val displayList = progressList.map { progress ->
                    val catalogItem = contentRepository.getContentItem(progress.contentId, progress.contentType, fullCatalog)
                    // Crear HistoryItemDisplay aquí para la lógica de agrupación
                    HistoryItemDisplay(progress, catalogItem)
                }.sortedByDescending { it.playbackProgress.lastPlayedTimestamp } // Ordenar por fecha más reciente

                _historyItems.value = groupHistoryItemsByDate(displayList)
            }
        }
    }

    private fun groupHistoryItemsByDate(items: List<HistoryItemDisplay>): List<HistoryListItem> {
        val groupedItems = items.groupBy { 
            com.johang.audiocinemateca.util.RelativeTimeUtils.getRelativeDateLabel(it.playbackProgress.lastPlayedTimestamp)
        }

        val finalList = mutableListOf<HistoryListItem>()
        for ((dateLabel, itemsInGroup) in groupedItems) {
            finalList.add(HistoryListItem.Header(dateLabel))
            itemsInGroup.forEach { displayItem ->
                finalList.add(HistoryListItem.Item(displayItem.playbackProgress, displayItem.catalogItem))
            }
        }
        return finalList
    }

    

    fun clearAllHistory() {
        viewModelScope.launch {
            playbackProgressRepository.deleteAllPlaybackProgress()
        }
    }

    fun deleteHistoryItem(playbackProgress: PlaybackProgressEntity) {
        viewModelScope.launch {
            playbackProgressRepository.deletePlaybackProgress(
                playbackProgress.contentId,
                playbackProgress.partIndex,
                playbackProgress.episodeIndex
            )
        }
    }
}