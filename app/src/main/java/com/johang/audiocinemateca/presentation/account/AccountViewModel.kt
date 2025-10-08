package com.johang.audiocinemateca.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.CheckForUpdateUseCase
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.util.AppUpdateDownloader
import com.johang.audiocinemateca.util.DownloadProgress
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val appUpdateDownloader: AppUpdateDownloader
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Loading)
    val updateState: StateFlow<UpdateCheckResult> = _updateState

    val downloadProgress: StateFlow<DownloadProgress> = appUpdateDownloader.downloadProgress

    fun checkForUpdates(currentVersion: String) {
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Loading
            val result = checkForUpdateUseCase(currentVersion)
            _updateState.value = result
        }
    }

    fun downloadUpdate(updateInfo: UpdateInfo) {
        appUpdateDownloader.downloadAndInstall(updateInfo, viewModelScope)
    }

    fun installUpdate(uri: Uri) {
        appUpdateDownloader.installPackage(uri)
    }

    suspend fun manualCheckForUpdates(currentVersion: String): UpdateCheckResult {
        return checkForUpdateUseCase(currentVersion)
    }
}
