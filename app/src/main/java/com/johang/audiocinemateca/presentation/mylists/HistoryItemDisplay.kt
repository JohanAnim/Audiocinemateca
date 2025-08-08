package com.johang.audiocinemateca.presentation.mylists

import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem

data class HistoryItemDisplay(
    val playbackProgress: PlaybackProgressEntity,
    val catalogItem: CatalogItem?
)
