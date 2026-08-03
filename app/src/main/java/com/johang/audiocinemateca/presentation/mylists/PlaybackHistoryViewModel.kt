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

    private fun getEffectiveTimestamp(timestamp: Long): Long {
        val now = System.currentTimeMillis()
        return if (timestamp > now + 60_000L) now else timestamp
    }

    init {
        viewModelScope.launch {
            playbackProgressRepository.getAllPlaybackProgress().collect { progressList ->
                val fullCatalog = contentRepository.getCatalogResponse()

                val displayList = progressList.mapNotNull { progress ->
                    val cleanId = progress.contentId.trim()
                    if (cleanId.isBlank() || cleanId.lowercase(Locale.ROOT) in listOf("pelicula", "serie", "cortometraje", "documental", "documentales")) {
                        return@mapNotNull null
                    }
                    val catalogItem = contentRepository.getContentItem(cleanId, progress.contentType, fullCatalog)
                    if (catalogItem != null) {
                        HistoryItemDisplay(progress, catalogItem)
                    } else null
                }

                // Deduplicación estricta por ID real de catálogo para eliminar duplicados/triplicados
                val deduplicatedList = displayList
                    .groupBy { it.catalogItem?.id?.lowercase(Locale.ROOT) ?: it.playbackProgress.contentId.lowercase(Locale.ROOT) }
                    .mapNotNull { entry -> entry.value.maxByOrNull { getEffectiveTimestamp(it.playbackProgress.lastPlayedTimestamp) } }
                    .sortedByDescending { getEffectiveTimestamp(it.playbackProgress.lastPlayedTimestamp) }

                _historyItems.value = groupHistoryItemsByDate(deduplicatedList)
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
            playbackProgressRepository.deleteAllPlaybackProgressForContent(playbackProgress.contentId)
        }
    }
}