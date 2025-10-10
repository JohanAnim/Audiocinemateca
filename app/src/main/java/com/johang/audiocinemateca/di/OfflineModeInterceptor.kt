package com.johang.audiocinemateca.di

import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class OfflineModeInterceptor @Inject constructor(
    private val sharedPreferencesManager: SharedPreferencesManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val isOffline = sharedPreferencesManager.getBoolean("offline_mode", false)
        if (isOffline) {
            throw IOException("Modo offline activado. No se permiten peticiones de red.")
        }
        return chain.proceed(chain.request())
    }
}
