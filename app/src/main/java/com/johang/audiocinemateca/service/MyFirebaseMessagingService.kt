package com.johang.audiocinemateca.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.johang.audiocinemateca.data.local.entities.NotificationEntity

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationDao: com.johang.audiocinemateca.data.local.dao.NotificationDao

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // IMPORTANTE: Ahora el backend solo envía el bloque 'data' para garantizar que onMessageReceived se ejecute siempre
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Audiocinemateca"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""

        val url = remoteMessage.data["url"]
        val destination = remoteMessage.data["destination"]
        val remoteId = remoteMessage.data["remoteId"] ?: "PUSH_${System.currentTimeMillis()}"

        Log.d("FCM", "Mensaje recibido: $title. Destino: $destination. URL: $url")

        // GUARDAR TODAS LAS NOTIFICACIONES EN EL HISTORIAL LOCAL
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!notificationDao.existsByRemoteId(remoteId)) {
                    notificationDao.insert(
                        NotificationEntity(
                            remoteId = remoteId,
                            title = title,
                            body = body,
                            timestamp = System.currentTimeMillis(),
                            linkUrl = url,
                            destination = destination
                        )
                    )
                    Log.d("FCM", "Notificación guardada en historial: $title")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error al guardar notificación en DB", e)
            }
        }

        showNotification(title, body, destination, url)
    }
    private fun showNotification(title: String, message: String, destination: String?, url: String?) {
        val channelId = "community_announcements"
        
        // El toque principal SIEMPRE abre la app y maneja la lógica en MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", destination)
            putExtra("url", url)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Anuncios de la Comunidad",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
