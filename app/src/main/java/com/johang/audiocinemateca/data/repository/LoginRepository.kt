package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.domain.model.LoginResult

interface LoginRepository {
    suspend fun login(username: String, password: String): LoginResult
    fun isBanned(): Boolean
    fun getRemainingBanTime(): String
    suspend fun isUserLoggedIn(): Boolean
}
