package com.johang.audiocinemateca.domain.model

sealed class LoginResult {
    data class Success(val updateRequired: Boolean) : LoginResult()
    data class Error(val message: String, val statusCode: Int? = null, val rawResponse: String? = null) : LoginResult()
    object Banned : LoginResult()
}
