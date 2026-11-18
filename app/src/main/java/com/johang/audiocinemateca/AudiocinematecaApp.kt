package com.johang.audiocinemateca

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.johang.audiocinemateca.presentation.settings.ThemeManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class AudiocinematecaApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Inicializar el cazador de errores global
        com.johang.audiocinemateca.util.CrashLogger(this)

        createNotificationChannel()

        // Apply the saved theme on startup sin instanciar el gestor completo en el hilo principal
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val theme = prefs.getString("theme", "system")
        ThemeManager.applyTheme(theme ?: "system")

        // Diferir suscripción FCM fuera del hilo crítico de arranque
        appScope.launch {
            subscribeToGlobalTopic()
        }
    }

    private fun subscribeToGlobalTopic() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("audiocinemateca_global")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        android.util.Log.d("FCM", "Suscrito con éxito al tema: audiocinemateca_global")
                    } else {
                        android.util.Log.e("FCM", "Error al suscribirse al tema audiocinemateca_global", task.exception)
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("FCM", "Error iniciando FCM topic subscription: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val downloadChannel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones sobre el progreso de las descargas"
            }

            val announcementsChannel = NotificationChannel(
                COMMUNITY_CHANNEL_ID,
                "Anuncios de la Comunidad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones y anuncios globales de la comunidad"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(downloadChannel)
            notificationManager.createNotificationChannel(announcementsChannel)
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        const val COMMUNITY_CHANNEL_ID = "community_announcements"
    }
}
