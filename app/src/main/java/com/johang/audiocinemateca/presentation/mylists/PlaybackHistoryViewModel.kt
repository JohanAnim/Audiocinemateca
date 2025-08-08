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
        val groupedList = mutableListOf<HistoryListItem>()
        var lastHeader: String? = null

        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val twoDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
        val lastWeek = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -7) } // Corregido para 7 días
        val lastMonth = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val lastYear = Calendar.getInstance().apply { add(Calendar.YEAR, -1) }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

        for (item in items) {
            val itemDate = Calendar.getInstance().apply { timeInMillis = item.playbackProgress.lastPlayedTimestamp }
            val headerText = when {
                isSameDay(itemDate, today) -> "Hoy"
                isSameDay(itemDate, yesterday) -> "Ayer"
                isSameDay(itemDate, twoDaysAgo) -> "Hace 2 días"
                itemDate.after(lastWeek) -> "Esta semana"
                itemDate.after(lastMonth) -> "Este mes"
                itemDate.after(lastYear) -> "Este año"
                else -> yearFormat.format(itemDate.time) // Año específico
            }

            if (headerText != lastHeader) {
                groupedList.add(HistoryListItem.Header(headerText))
                lastHeader = headerText
            }
            groupedList.add(HistoryListItem.Item(item.playbackProgress, item.catalogItem)) // Crear HistoryListItem.Item directamente
        }
        return groupedList
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
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