package com.johang.audiocinemateca.domain.repository

import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.util.Resource

interface UpdateRepository {
    suspend fun getLatestRelease(): Resource<UpdateInfo>
}
