package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.remote.GithubApiService
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.repository.UpdateRepository
import com.johang.audiocinemateca.util.Resource
import javax.inject.Inject

class UpdateRepositoryImpl @Inject constructor(
    private val githubApiService: GithubApiService
) : UpdateRepository {

    override suspend fun getLatestRelease(): Resource<UpdateInfo> {
        return try {
            val response = githubApiService.getLatestRelease()
            if (response.isSuccessful) {
                val releaseDto = response.body()
                if (releaseDto != null) {
                    val apkAsset = releaseDto.assets.find { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        val updateInfo = UpdateInfo(
                            version = releaseDto.tagName,
                            name = releaseDto.name,
                            changelog = releaseDto.body,
                            downloadUrl = apkAsset.browserDownloadUrl,
                            updatedAt = releaseDto.updatedAt,
                            downloadCount = apkAsset.downloadCount
                        )
                        Resource.Success(updateInfo)
                    } else {
                        Resource.Error("No se encontró el archivo APK en la release.")
                    }
                } else {
                    Resource.Error("La respuesta de la API está vacía.")
                }
            } else {
                Resource.Error("Error de la API: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Ocurrió un error desconocido.")
        }
    }
}
