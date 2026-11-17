package com.johang.audiocinemateca.domain.repository

import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.util.Resource

interface UpdateRepository {
    suspend fun getLatestRelease(channel: String = UpdateChannels.STABLE): Resource<UpdateInfo>
}
