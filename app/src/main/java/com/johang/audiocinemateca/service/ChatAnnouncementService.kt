package com.johang.audiocinemateca.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import com.johang.audiocinemateca.presentation.player.PlayerService
import com.johang.audiocinemateca.util.SoundEffectsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatAnnouncementService : Service() {

    @Inject
    lateinit var globalChatRepository: GlobalChatRepository

    @Inject
    lateinit var soundEffectsManager: SoundEffectsManager

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller: MediaController?
        get() = if (controllerFuture?.isDone == true) controllerFuture?.get() else null

    private var lastKnownChatCount = -1
    private var isChatOpenLocal = true

    override fun onCreate() {
        super.onCreate()
        Log.d("ChatService", "Servicio de Anuncios Iniciado.")
        
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            startForeground(99, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(99, notification)
        }
        
        setupMediaController()
        startChatWatcher()
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                // Verificamos si se inicializó bien sin bloquear
                Log.d("ChatService", "MediaController conectado: ${controller != null}")
            } catch (e: Exception) {
                Log.e("ChatService", "Error al conectar MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startChatWatcher() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val auth = FirebaseAuth.getInstance()

        // 1. Vigilar Mensajes
        serviceScope.launch {
            globalChatRepository.getMessages().collect { messages ->
                if (lastKnownChatCount != -1 && messages.size > lastKnownChatCount) {
                    val lastMessage = messages.last()
                    if (lastMessage.senderId != auth.currentUser?.uid) {
                        handleAnnouncement(lastMessage, null, am, auth)
                    }
                }
                lastKnownChatCount = messages.size
            }
        }

        // 2. Vigilar Estado (Apertura/Cierre)
        var isFirstLoad = true
        serviceScope.launch {
            globalChatRepository.getChatStatus().collect { isOpen ->
                if (!isFirstLoad && isChatOpenLocal != isOpen) {
                    val text = if (isOpen) "El chat global ha sido abierto." else "El chat global ha sido cerrado."
                    handleAnnouncement(null, text, am, auth, force = true)
                }
                isChatOpenLocal = isOpen
                isFirstLoad = false
            }
        }
    }

    private fun handleAnnouncement(
        message: com.johang.audiocinemateca.data.model.ChatMessage?,
        directText: String?,
        am: AccessibilityManager,
        auth: FirebaseAuth,
        force: Boolean = false
    ) {
        val mode = sharedPreferencesManager.getString("chat_announcement_mode", "idle_only")
        if (mode == "never" && !force) return

        // DETECCIÓN INTELIGENTE: ¿Está sonando el PlayerService?
        val isPlaying = controller?.isPlaying ?: false
        if (mode == "idle_only" && isPlaying && !force) return

        val text = directText ?: message?.let { msg ->
            val isMentioned = msg.mentions.contains(auth.currentUser?.uid) || msg.text.contains("@${auth.currentUser?.displayName}", true)
            if (isMentioned) "${msg.senderName} te mencionó: ${msg.text}" else "${msg.senderName} escribió: ${msg.text}"
        }

        text?.let {
            soundEffectsManager.playSound("receive_message")
            vibrate()
            if (am.isEnabled) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(it)
                am.sendAccessibilityEvent(event)
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(150)
        }
    }

    private fun createNotification(): Notification {
        val channelId = "chat_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Vigilante del Chat", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Audiocinemateca")
            .setContentText("Vigilando el chat global...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        MediaController.releaseFuture(controllerFuture!!)
        super.onDestroy()
    }
}
