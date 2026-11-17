package com.johang.audiocinemateca.domain.usecase

import android.util.Log
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.model.SemVer
import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.repository.UpdateRepository
import com.johang.audiocinemateca.util.Resource
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val sharedPreferencesManager: SharedPreferencesManager
) {

    suspend operator fun invoke(
        currentVersion: String,
        channelOverride: String? = null
    ): UpdateCheckResult {
        val activeChannel = UpdateChannels.normalize(
            channelOverride ?: sharedPreferencesManager.getString("update_channel", UpdateChannels.STABLE)
        )
        return when (val resource = updateRepository.getLatestRelease(activeChannel)) {
            is Resource.Success -> {
                val updateInfo = resource.data!!
                val remoteVersion = updateInfo.version.removePrefix("v").removePrefix("V")
                Log.d(
                    "CheckForUpdateUseCase",
                    "Comparing versions (channel=$activeChannel): remote='$remoteVersion', current='$currentVersion'"
                )
                val isNewer = SemVer.isNewer(remoteVersion, currentVersion)
                Log.d("CheckForUpdateUseCase", "Is newer version: $isNewer")
                if (isNewer) {
                    UpdateCheckResult.UpdateAvailable(updateInfo)
                } else {
                    UpdateCheckResult.NoUpdateAvailable(updateInfo)
                }
            }
            is Resource.Error -> UpdateCheckResult.Error(resource.message ?: "Error desconocido")
            is Resource.Loading -> UpdateCheckResult.Loading
        }
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    data class NoUpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult() // Modified to carry UpdateInfo
    data class Error(val message: String) : UpdateCheckResult()
    object Loading : UpdateCheckResult()
}
