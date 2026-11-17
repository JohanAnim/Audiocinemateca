package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.model.GithubRelease
import com.johang.audiocinemateca.data.remote.GithubApiService
import com.johang.audiocinemateca.domain.model.SemVer
import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.repository.UpdateRepository
import com.johang.audiocinemateca.util.Resource
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val githubApiService: GithubApiService
) : UpdateRepository {

    override suspend fun getLatestRelease(channel: String): Resource<UpdateInfo> {
        val normalizedChannel = UpdateChannels.normalize(channel)
        return try {
            if (normalizedChannel == UpdateChannels.BETA) {
                val response = githubApiService.getReleases(perPage = 20)
                if (!response.isSuccessful) {
                    return Resource.Error("Error de la API: ${response.code()}")
                }
                val releases = response.body().orEmpty()
                    .filter { !it.draft }
                val candidates = releases.mapNotNull { toUpdateInfo(it, normalizedChannel) }
                if (candidates.isEmpty()) {
                    return Resource.Error("No se encontró ninguna versión con APK disponible.")
                }
                val bestRelease = candidates.maxWithOrNull { a, b ->
                    val va = SemVer.parseOrNull(a.version)
                    val vb = SemVer.parseOrNull(b.version)
                    when {
                        va != null && vb != null -> va.compareTo(vb)
                        va != null -> 1
                        vb != null -> -1
                        else -> a.updatedAt.compareTo(b.updatedAt)
                    }
                } ?: candidates.first()
                Resource.Success(bestRelease)
            } else {
                val response = githubApiService.getLatestRelease()
                if (!response.isSuccessful) {
                    return Resource.Error("Error de la API: ${response.code()}")
                }
                val releaseDto = response.body()
                    ?: return Resource.Error("La respuesta de la API está vacía.")
                val updateInfo = toUpdateInfo(releaseDto, normalizedChannel)
                    ?: return Resource.Error("No se encontró el archivo APK en la release.")
                Resource.Success(updateInfo)
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Ocurrió un error desconocido.")
        }
    }

    private fun toUpdateInfo(releaseDto: GithubRelease, channel: String): UpdateInfo? {
        val apkAsset = releaseDto.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return null
        val parsed = SemVer.parseOrNull(releaseDto.tagName)
        return UpdateInfo(
            version = releaseDto.tagName,
            name = releaseDto.name,
            changelog = releaseDto.body,
            downloadUrl = apkAsset.browserDownloadUrl,
            updatedAt = releaseDto.updatedAt,
            downloadCount = apkAsset.downloadCount,
            isPrerelease = releaseDto.prerelease || (parsed?.isPrerelease == true),
            channel = channel
        )
    }
}
