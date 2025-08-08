package com.johang.audiocinemateca.domain.usecase

import com.johang.audiocinemateca.data.repository.LoginRepository
import com.johang.audiocinemateca.domain.model.LoginResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val loginRepository: LoginRepository
) {

    suspend fun execute(username: String, password: String): LoginResult {
        return loginRepository.login(username, password)
    }

    fun isBanned(): Boolean {
        return loginRepository.isBanned()
    }

    fun getRemainingBanTime(): String {
        return loginRepository.getRemainingBanTime()
    }

    suspend fun isUserLoggedIn(): Boolean {
        return loginRepository.isUserLoggedIn()
    }
}