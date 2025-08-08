package com.johang.audiocinemateca.presentation.player

import androidx.lifecycle.ViewModel
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {

    private val _contentItem = MutableStateFlow<CatalogItem?>(null)
    val contentItem: StateFlow<CatalogItem?> = _contentItem

}