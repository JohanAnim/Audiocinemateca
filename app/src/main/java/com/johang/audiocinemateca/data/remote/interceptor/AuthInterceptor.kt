package com.johang.audiocinemateca.data.remote.interceptor

import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val sharedPreferencesManager: SharedPreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val username = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY)
        val password = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_PASSWORD_KEY)

        val request = chain.request()
        val authenticatedRequest = if (username != null && password != null) {
            val credential = Credentials.basic(username, password)
            request.newBuilder()
                .header("Authorization", credential)
                .build()
        } else {
            request
        }

        return chain.proceed(authenticatedRequest)
    }
}
