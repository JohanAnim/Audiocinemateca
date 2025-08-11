package com.johang.audiocinemateca.domain.usecase

import android.util.Log
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.repository.UpdateRepository
import com.johang.audiocinemateca.util.Resource
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val updateRepository: UpdateRepository
) {

    suspend operator fun invoke(currentVersion: String): UpdateCheckResult {
        return when (val resource = updateRepository.getLatestRelease()) {
            is Resource.Success -> {
                val remoteVersion = resource.data!!.version.removePrefix("v")
                Log.d("CheckForUpdateUseCase", "Comparing versions: remote='$remoteVersion', current='$currentVersion'")
                if (isNewerVersion(remoteVersion, currentVersion)) {
                    UpdateCheckResult.UpdateAvailable(resource.data)
                } else {
                    UpdateCheckResult.NoUpdateAvailable
                }
            }
            is Resource.Error -> UpdateCheckResult.Error(resource.message ?: "Error desconocido")
            is Resource.Loading -> UpdateCheckResult.Loading // Not typically used here, but handled
        }
    }

    private fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val remoteParts = remoteVersion.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val currentParts = currentVersion.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }

            val commonLength = minOf(remoteParts.size, currentParts.size)

            for (i in 0 until commonLength) {
                if (remoteParts[i] > currentParts[i]) {
                    return true
                }
                if (remoteParts[i] < currentParts[i]) {
                    return false
                }
            }

            return remoteParts.size > currentParts.size
        } catch (e: Exception) {
            Log.e("CheckForUpdateUseCase", "Error comparing versions: ${e.message}")
            // Fallback to simple string comparison in case of parsing errors
            return remoteVersion > currentVersion
        }
    }
}

sealed class UpdateCheckResult {
    data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateCheckResult()
    object NoUpdateAvailable : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
    object Loading : UpdateCheckResult()
}
