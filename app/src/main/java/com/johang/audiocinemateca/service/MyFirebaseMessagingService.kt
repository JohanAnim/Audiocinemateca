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
import com.johang.audiocinemateca.AudiocinematecaApp
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.util.SoundEffectsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var ttsManager: com.johang.audiocinemateca.util.TtsManager

    @Inject
    lateinit var notificationDao: com.johang.audiocinemateca.data.local.dao.NotificationDao

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Mensaje recibido de: ${remoteMessage.from}")

        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: "Audiocinemateca"

        val message = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: ""

        val destination = remoteMessage.data["destination"]
        val url = remoteMessage.data["url"]
        val contentId = remoteMessage.data["contentId"]
        val contentType = remoteMessage.data["contentType"] ?: remoteMessage.data["type"]
        val remoteId = remoteMessage.data["remoteId"] ?: "fcm_${System.currentTimeMillis()}"
        val senderId = remoteMessage.data["senderId"]
        val targetKey = remoteMessage.data["targetKey"]
        val targetUid = remoteMessage.data["targetUid"]

        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val currentUid = currentUser?.uid
        if (senderId != null && currentUid != null && senderId.equals(currentUid, ignoreCase = true)) {
            Log.d("FCM", "Ignorando notificación propia.")
            return
        }

        // 1. Verificación estricta de UID destinatario
        if (!targetUid.isNullOrBlank()) {
            if (currentUid == null || !targetUid.equals(currentUid, ignoreCase = true)) {
                Log.d("FCM", "Ignorando notificación dirigida a otro UID: $targetUid (mi UID es $currentUid)")
                return
            }
        }

        // 2. Verificación estricta de TargetKey (nombres / emails / @todos)
        if (!targetKey.isNullOrBlank()) {
            val cleanTarget = targetKey.lowercase().replace("@", "").trim()
            if (cleanTarget != "todos") {
                val currentDisplayName = (currentUser?.displayName ?: "").lowercase().trim()
                val currentEmailPrefix = (currentUser?.email ?: "").split("@")[0].lowercase().trim()
                val currentEmail = (currentUser?.email ?: "").lowercase().trim()
                val cUid = (currentUid ?: "").lowercase().trim()

                val isForMe = (cUid.isNotEmpty() && cleanTarget == cUid) ||
                        (currentDisplayName.isNotEmpty() && cleanTarget == currentDisplayName) ||
                        (currentEmailPrefix.isNotEmpty() && cleanTarget == currentEmailPrefix) ||
                        (currentEmail.isNotEmpty() && cleanTarget == currentEmail)

                if (!isForMe) {
                    Log.d("FCM", "Ignorando notificación dirigida a otro destinatario: $targetKey")
                    return
                }
            }
        }

        if (title.isNotEmpty() || message.isNotEmpty()) {
            var isAlreadyProcessed = false
            try {
                isAlreadyProcessed = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    notificationDao.existsByRemoteId(remoteId)
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error comprobando duplicados en DB local: ${e.message}")
            }

            if (isAlreadyProcessed) {
                Log.d("FCM", "Ignorando notificación duplicada procesada previamente (remoteId: $remoteId)")
                return
            }

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    notificationDao.insert(
                        com.johang.audiocinemateca.data.local.entities.NotificationEntity(
                            remoteId = remoteId,
                            title = title,
                            body = message,
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            linkUrl = url,
                            destination = destination
                        )
                    )
                } catch (e: Exception) {
                    Log.e("FCM", "Error guardando notificación en DB local: ${e.message}")
                }
            }

            val isChatActive = com.johang.audiocinemateca.presentation.community.GlobalChatState.isChatScreenActive
            if (destination == "global_chat" && isChatActive) {
                Log.d("FCM", "Ignorando notificación push de chat porque el usuario está enfocado en la pantalla de chat.")
                return
            }

            showNotification(title, message, destination, url, contentId, contentType)
        }
    }

    private fun showNotification(
        title: String, 
        message: String, 
        destination: String?, 
        url: String?,
        contentId: String? = null,
        contentType: String? = null
    ) {
        val channelId = AudiocinematecaApp.COMMUNITY_CHANNEL_ID
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", destination)
            putExtra("url", url)
            if (!contentId.isNullOrEmpty()) putExtra("contentId", contentId)
            if (!contentType.isNullOrEmpty()) putExtra("contentType", contentType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Anuncios de la Comunidad", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                enableLights(true)
                setSound(defaultSoundUri, android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())

        // Anuncio por voz TTS en segundo plano / fuera de la pantalla de chat
        val announcementPref = sharedPreferencesManager.getString("chat_accessibility_announcements", "always")
        if (announcementPref != "never") {
            try {
                val cleanTitle = title.replace("💬 ", "").trim()
                ttsManager.speak("$cleanTitle. $message")
            } catch (e: Exception) {
                Log.e("FCM", "Error reproduciendo TTS push: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Nuevo token: $token")
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val data = mapOf(
                "fcmToken" to token,
                "userId" to currentUser.uid,
                "email" to (currentUser.email ?: ""),
                "displayName" to (currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Usuario")
            )
            db.collection("users").document(currentUser.uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            db.collection("presence").document(currentUser.uid).set(data, com.google.firebase.firestore.SetOptions.merge())
        }
    }
}
