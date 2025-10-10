package com.johang.audiocinemateca

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.preference.PreferenceManager
import com.johang.audiocinemateca.presentation.settings.ThemeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AudiocinematecaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Apply the saved theme on startup
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = sharedPreferences.getString("theme", "system")
        ThemeManager.applyTheme(theme ?: "system")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Descargas"
            val descriptionText = "Notificaciones sobre el progreso de las descargas"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(DOWNLOAD_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
    }
}