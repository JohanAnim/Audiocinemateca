package com.johang.audiocinemateca.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.CheckForUpdateUseCase
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.util.AppUpdateDownloader
import com.johang.audiocinemateca.util.DownloadProgress
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val checkForUpdateUseCase: CheckForUpdateUseCase,
    private val appUpdateDownloader: AppUpdateDownloader,
    private val sharedPreferencesManager: SharedPreferencesManager
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Loading)
    val updateState: StateFlow<UpdateCheckResult> = _updateState

    private val _updateChannel = MutableStateFlow(
        UpdateChannels.normalize(sharedPreferencesManager.getString("update_channel", UpdateChannels.STABLE))
    )
    val updateChannel: StateFlow<String> = _updateChannel.asStateFlow()

    val downloadProgress: StateFlow<DownloadProgress> = appUpdateDownloader.downloadProgress

    fun refreshUpdateChannel(): String {
        val current = UpdateChannels.normalize(
            sharedPreferencesManager.getString("update_channel", UpdateChannels.STABLE)
        )
        _updateChannel.value = current
        return current
    }

    fun checkForUpdates(currentVersion: String) {
        val channel = refreshUpdateChannel()
        viewModelScope.launch {
            _updateState.value = UpdateCheckResult.Loading
            val result = checkForUpdateUseCase(currentVersion, channel)
            _updateState.value = result
        }
    }

    fun downloadUpdate(updateInfo: UpdateInfo) {
        appUpdateDownloader.downloadAndInstall(updateInfo, viewModelScope)
    }

    fun installUpdate(uri: Uri) {
        appUpdateDownloader.installPackage(uri)
    }

    suspend fun manualCheckForUpdates(
        currentVersion: String,
        channelOverride: String? = null
    ): UpdateCheckResult {
        val channel = channelOverride?.let { UpdateChannels.normalize(it) } ?: refreshUpdateChannel()
        return checkForUpdateUseCase(currentVersion, channel)
    }
}
