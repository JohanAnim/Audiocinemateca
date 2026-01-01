package com.johang.audiocinemateca.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R

class FCMService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Nuevo token: $token")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Título y cuerpo por defecto
        var title = "Audiocinemateca"
        var body = "Tienes contenido nuevo disponible"
        var url: String? = null

        // 1. Extraer de la notificación simple
        remoteMessage.notification?.let {
            title = it.title ?: title
            body = it.body ?: body
        }

        // 2. Extraer de los datos (Permite enviar la URL y botones personalizados)
        if (remoteMessage.data.isNotEmpty()) {
            title = remoteMessage.data["title"] ?: title
            body = remoteMessage.data["body"] ?: body
            url = remoteMessage.data["url"] // Buscamos la clave "url"
        }

        showNotification(title, body, url)
    }

    private fun showNotification(title: String, message: String, url: String?) {
        val channelId = "audiocinemateca_notifications"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Avisos de Contenido",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Intento para abrir la App normalmente al pulsar la notificación
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construir la notificación básica
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)

        // 3. SI HAY UNA URL, AÑADIMOS EL BOTÓN "OÍR AHORA"
        url?.let { targetUrl ->
            try {
                val actionIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                val actionPendingIntent = PendingIntent.getActivity(
                    this, 1, actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                // Añadimos el botón de acción
                builder.addAction(0, "¡Llévame ahora!", actionPendingIntent)
            } catch (e: Exception) {
                Log.e("FCMService", "Error al crear el botón de URL: ${e.message}")
            }
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}