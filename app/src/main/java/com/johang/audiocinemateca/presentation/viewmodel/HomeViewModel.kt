package com.johang.audiocinemateca.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.usecase.LoadCatalogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val loadCatalogUseCase: LoadCatalogUseCase
) : ViewModel() {

    private val _catalogState = MutableLiveData<CatalogState>()
    val catalogState: LiveData<CatalogState> = _catalogState

    fun loadCatalog() {
        _catalogState.value = CatalogState.Loading(0)
        viewModelScope.launch {
            loadCatalogUseCase.execute().collect {
                when (it) {
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress -> {
                        _catalogState.value = CatalogState.Loading(it.percent)
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                        _catalogState.value = CatalogState.Success(it.catalog)
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                        _catalogState.value = CatalogState.UpdateAvailable(it.serverVersion, it.localVersion)
                    }
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                        _catalogState.value = CatalogState.Error(it.message)
                    }
                }
            }
        }
    }

    

    sealed class CatalogState {
        data class Loading(val progress: Int) : CatalogState()
        data class Success(val catalog: com.johang.audiocinemateca.data.model.CatalogResponse) : CatalogState()
        data class UpdateAvailable(val serverVersion: Date, val localVersion: Date) : CatalogState()
        data class Error(val message: String) : CatalogState()
    }
}