package com.johang.audiocinemateca.presentation.mylists

import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem

sealed class HistoryListItem {
    data class Header(val date: String) : HistoryListItem()
    data class Item(val playbackProgress: PlaybackProgressEntity, val catalogItem: CatalogItem?) : HistoryListItem()
}