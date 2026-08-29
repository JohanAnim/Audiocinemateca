package com.johang.audiocinemateca.presentation.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.MediaQueueItem
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.util.AudioProxyUtil
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
@UnstableApi
class PlayerService : MediaSessionService() {

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    @Named("AudiocinematecaClient")
    lateinit var okHttpClient: OkHttpClient

    private var mediaSession: MediaSession? = null
    private var castPlayer: CastPlayer? = null
    private var exoPlayer: ExoPlayer? = null
    private val player: Player
        get() = mediaSession?.player ?: exoPlayer ?: castPlayer ?: throw IllegalStateException("No player available")
    private lateinit var playerListener: Player.Listener
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var savedMediaItems = listOf<MediaItem>()
    private var savedWindowIndex = 0
    private var savedPositionMs = 0L

    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireCastLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Audiocinemateca:CastStreamingWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L)
            }
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                wifiLock = wifiManager?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Audiocinemateca:CastStreamingWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e("PlayerService", "Error al adquirir bloqueos de energía para Cast: ${e.message}")
        }
    }

    private fun releaseCastLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            Log.e("PlayerService", "Error al liberar bloqueos de energía: ${e.message}")
        }
    }

    private val castSessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            Log.d("PlayerService", "onCastSessionAvailable: Cast session is now available")
            acquireCastLocks()
            onCastAvailable()
        }

        override fun onCastSessionUnavailable() {
            Log.d("PlayerService", "onCastSessionUnavailable: Cast session is no longer available")
            releaseCastLocks()
            onCastUnavailable()
        }
    }

    private fun updateSavedState(p: Player = player) {
        try {
            if (p.mediaItemCount > 0) {
                val idx = p.currentMediaItemIndex
                val pos = p.currentPosition
                if (idx >= 0 && idx < p.mediaItemCount) {
                    savedWindowIndex = idx
                }
                if (pos >= 0) {
                    savedPositionMs = pos
                }
                // Solo guardar la lista de MediaItems si provienen del ExoPlayer local con URIs válidos
                if (p === exoPlayer) {
                    val items = mutableListOf<MediaItem>()
                    for (i in 0 until p.mediaItemCount) {
                        val item = p.getMediaItemAt(i)
                        if (item.localConfiguration?.uri != null) {
                            items.add(item)
                        }
                    }
                    if (items.isNotEmpty()) {
                        savedMediaItems = items
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerService", "Error updating saved state: ${e.message}")
        }
    }

    private fun onCastAvailable() {
        val localPlayer = exoPlayer ?: return
        val cPlayer = castPlayer ?: return

        Log.d("PlayerService", "Transferring playback from ExoPlayer to CastPlayer")

        // Guardar progreso local y en la nube antes de transferir a Cast
        serviceScope.launch {
            try {
                savePlaybackProgress(syncToCloud = true)
            } catch (e: Exception) {}
        }

        val mediaItems = mutableListOf<MediaItem>()
        val itemCount = localPlayer.mediaItemCount
        for (i in 0 until itemCount) {
            val item = localPlayer.getMediaItemAt(i)
            mediaItems.add(item)
        }

        val targetItems = if (mediaItems.isNotEmpty()) mediaItems else savedMediaItems
        val targetIndex = if (itemCount > 0 && localPlayer.currentMediaItemIndex >= 0) localPlayer.currentMediaItemIndex else savedWindowIndex
        val targetPos = if (itemCount > 0 && localPlayer.currentPosition >= 0) localPlayer.currentPosition else savedPositionMs
        val wasPlaying = localPlayer.isPlaying || (localPlayer.playWhenReady && localPlayer.playbackState != Player.STATE_ENDED)

        // Aislar temporalmente el listener local para que pause()/stop() no envíe isPlaying=false a la UI
        try {
            localPlayer.removeListener(playerListener)
            localPlayer.pause()
            localPlayer.stop()
            localPlayer.addListener(playerListener)
        } catch (e: Exception) {}

        if (targetItems.isNotEmpty()) {
            savedMediaItems = targetItems.filter { it.localConfiguration?.uri != null }
            savedWindowIndex = targetIndex
            savedPositionMs = targetPos

            try {
                cPlayer.playWhenReady = wasPlaying
                cPlayer.setMediaItems(targetItems, targetIndex, targetPos.coerceAtLeast(0L))
                cPlayer.prepare()
                if (wasPlaying) {
                    cPlayer.play()
                } else {
                    cPlayer.pause()
                }
            } catch (e: Exception) {
                Log.e("PlayerService", "Error transferring playback to CastPlayer: ${e.message}", e)
            }
        }

        mediaSession?.player = cPlayer

        // Refuerzo asíncrono para asegurar que el receptor de Google Cast inicie la reproducción de inmediato tras el handshake
        if (wasPlaying) {
            serviceScope.launch {
                kotlinx.coroutines.delay(250)
                try {
                    if (cPlayer.isCastSessionAvailable) {
                        cPlayer.playWhenReady = true
                        cPlayer.play()
                    }
                } catch (e: Exception) {}
                kotlinx.coroutines.delay(500)
                try {
                    if (cPlayer.isCastSessionAvailable) {
                        cPlayer.playWhenReady = true
                        cPlayer.play()
                    }
                } catch (e: Exception) {}
            }
        }

        val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
            putExtra(MainActivity.EXTRA_IS_PLAYING, wasPlaying)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(playPauseIntent)
        sharedPreferencesManager.saveBoolean("is_currently_playing", wasPlaying)

        ensureForegroundNotification()
        broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
    }

    private fun onCastUnavailable() {
        val localPlayer = exoPlayer ?: return
        val cPlayer = castPlayer ?: return

        Log.d("PlayerService", "Transferring playback from CastPlayer to ExoPlayer")

        val castIndex = try { cPlayer.currentMediaItemIndex } catch (e: Exception) { -1 }
        val castPos = try { cPlayer.currentPosition } catch (e: Exception) { -1L }

        val targetIndex = if (castIndex >= 0 && castIndex < savedMediaItems.size) castIndex else savedWindowIndex
        val targetPos = if (castPos >= 0) castPos else savedPositionMs

        // Guardar progreso del Cast en la nube antes de volver a ExoPlayer
        serviceScope.launch {
            try {
                savePlaybackProgress(position = targetPos, syncToCloud = true)
            } catch (e: Exception) {}
        }

        val validItems = savedMediaItems.filter { it.localConfiguration?.uri != null }
        val wasPlaying = try { cPlayer.isPlaying || (cPlayer.playWhenReady && cPlayer.playbackState != Player.STATE_ENDED) } catch (e: Exception) { true }

        // Aislar temporalmente el listener de Cast para que stop() no emita isPlaying=false
        try {
            cPlayer.removeListener(playerListener)
            cPlayer.stop()
            cPlayer.addListener(playerListener)
        } catch (e: Exception) {
            Log.e("PlayerService", "Error stopping CastPlayer: ${e.message}")
        }

        if (validItems.isNotEmpty()) {
            val safeIndex = targetIndex.coerceAtLeast(0).coerceAtMost(validItems.size - 1)
            savedWindowIndex = safeIndex
            savedPositionMs = targetPos

            try {
                localPlayer.playWhenReady = wasPlaying
                localPlayer.setMediaItems(validItems, safeIndex, targetPos.coerceAtLeast(0L))
                localPlayer.prepare()
                if (wasPlaying) {
                    localPlayer.play()
                } else {
                    localPlayer.pause()
                }
            } catch (e: Exception) {
                Log.e("PlayerService", "Error restoring playback to ExoPlayer: ${e.message}", e)
            }
        }

        mediaSession?.player = localPlayer

        val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
            putExtra(MainActivity.EXTRA_IS_PLAYING, wasPlaying)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(playPauseIntent)
        sharedPreferencesManager.saveBoolean("is_currently_playing", wasPlaying)

        ensureForegroundNotification()
        broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
    }

    private val progressSaveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                val pos = try { player.currentPosition } catch (e: Exception) { -1L }
                if (pos >= 0) {
                    updateSavedState()
                    savePlaybackProgress(position = pos, syncToCloud = false)
                }
            }
            progressSaveHandler.postDelayed(this, 2000) 
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaSession == null) {
            setupPlayer()
        }
        ensureForegroundNotification()
        handleServiceIntent(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    private val playerActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            handleServiceIntent(intent)
        }
    }

    private fun handleServiceIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        val action = safeIntent.action ?: return

        if (mediaSession == null) {
            setupPlayer()
        }

        val activePlayer = mediaSession?.player ?: player

        when (action) {
            ACTION_PLAY_PAUSE -> {
                if (activePlayer.isPlaying) {
                    serviceScope.launch { savePlaybackProgress(syncToCloud = true) }
                    activePlayer.pause()
                } else {
                    activePlayer.play()
                }
            }
            ACTION_STOP -> {
                Log.d("PlayerService", "Cierre solicitado (Botón X del mini reproductor o Notificación).")
                serviceScope.launch {
                    try {
                        savePlaybackProgress(syncToCloud = true)
                    } catch (e: Exception) {
                        Log.e("PlayerService", "Error al guardar progreso al cerrar: ${e.message}")
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        val isCastActive = castPlayer?.isCastSessionAvailable == true
                        try {
                            activePlayer.stop()
                            activePlayer.clearMediaItems()
                        } catch (e: Exception) {}

                        savedMediaItems = emptyList()
                        savedWindowIndex = 0
                        savedPositionMs = 0L

                        stopForeground(STOP_FOREGROUND_REMOVE)
                        broadcastMiniPlayerState(MainActivity.ACTION_HIDE_MINI_PLAYER)

                        val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                            putExtra(MainActivity.EXTRA_IS_PLAYING, false)
                        }
                        LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(playPauseIntent)
                        sharedPreferencesManager.saveBoolean("is_currently_playing", false)

                        if (!isCastActive) {
                            try {
                                mediaSession?.release()
                            } catch (e: Exception) {}
                            mediaSession = null
                            stopSelf()
                        } else {
                            Log.d("PlayerService", "Sesión de Cast activa: Se detuvo el contenido actual y se cerró la UI, pero se mantiene la conexión con el dispositivo de Cast.")
                        }
                    }
                }
            }
            MainActivity.ACTION_REQUEST_PLAYBACK_STATE -> {
                val intentResponse = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                    putExtra(MainActivity.EXTRA_IS_PLAYING, activePlayer.isPlaying)
                }
                LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intentResponse)
            }
            MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE -> broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
            MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS -> serviceScope.launch { savePlaybackProgress() }
            MainActivity.ACTION_SEEK_TO_PREVIOUS -> {
                if (activePlayer.hasPreviousMediaItem()) activePlayer.seekToPreviousMediaItem() else activePlayer.seekTo(0)
                activePlayer.playWhenReady = true
                activePlayer.play()
            }
            MainActivity.ACTION_SEEK_TO_NEXT -> {
                if (activePlayer.hasNextMediaItem()) activePlayer.seekToNextMediaItem()
                activePlayer.playWhenReady = true
                activePlayer.play()
            }
        }
    }

    @Synchronized
    private fun setupPlayer() {
        if (mediaSession != null) return

        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(defaultDataSourceFactory)

        val localExoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        exoPlayer = localExoPlayer

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val intent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                    putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying)
                }
                LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)

                sharedPreferencesManager.saveBoolean("is_currently_playing", isPlaying)
                if (isPlaying) {
                    serviceScope.launch { savePlaybackProgress() }
                }
                ensureForegroundNotification()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                updateSavedState()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateSavedState()
                updateSessionActivity()
                ensureForegroundNotification()
                broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
                serviceScope.launch { savePlaybackProgress() }
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateSavedState()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateSavedState()
                if (playbackState == Player.STATE_ENDED) {
                    serviceScope.launch { savePlaybackProgress() }
                }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                updateSavedState(player)
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupEqualizer(audioSessionId)
                setupHapticSystem(audioSessionId)
            }
        }

        localExoPlayer.addListener(playerListener)

        try {
            com.johang.audiocinemateca.util.LocalCastProxyServer.start(this, okHttpClient)
        } catch (e: Exception) {
            Log.e("PlayerService", "Error starting LocalCastProxyServer: ${e.message}")
        }

        try {
            val castContext = CastContext.getSharedInstance(this)
            val cPlayer = CastPlayer(castContext, CastMediaItemConverter())
            cPlayer.setSessionAvailabilityListener(castSessionAvailabilityListener)
            cPlayer.addListener(playerListener)
            castPlayer = cPlayer
        } catch (e: Exception) {
            Log.e("PlayerService", "Error initializing CastPlayer: ${e.message}", e)
        }

        val initialPlayer: Player = if (castPlayer?.isCastSessionAvailable == true) {
            castPlayer!!
        } else {
            localExoPlayer
        }

        try {
            mediaSession?.release()
        } catch (e: Exception) {}
        mediaSession = null

        mediaSession = MediaSession.Builder(this, initialPlayer)
            .setId("AudiocinematecaPlayerSession_${System.currentTimeMillis()}")
            .build()

        updateSessionActivity()
        progressSaveHandler.post(progressSaveRunnable)
    }

    private class CastMediaItemConverter : MediaItemConverter {
        private val defaultConverter = DefaultMediaItemConverter()

        override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
            val extras = mediaItem.mediaMetadata.extras
            val originalUri = mediaItem.localConfiguration?.uri?.toString() ?: ""
            val remoteUrl = extras?.getString("remoteUrl")

            // Construir URL optimizada para Cast mediante el servidor proxy local (con soporte de CORS, Auth y archivos descargados)
            val streamUrl = com.johang.audiocinemateca.util.LocalCastProxyServer.buildCastUrl(originalUri, remoteUrl)

            val itemToConvert = mediaItem.buildUpon()
                .setUri(android.net.Uri.parse(streamUrl))
                .setMimeType("audio/mpeg")
                .setMediaId(mediaItem.mediaId.ifEmpty { "${extras?.getString("itemId")}_${extras?.getInt("partIndex")}_${extras?.getInt("episodeIndex")}" })
                .build()

            val queueItem = defaultConverter.toMediaQueueItem(itemToConvert)
            val mediaInfo = queueItem.media
            if (mediaInfo != null) {
                // Preservar el objeto customData original que contiene la clave "mediaItem" requerida por Media3
                val customJson = mediaInfo.customData ?: org.json.JSONObject()
                if (extras != null) {
                    customJson.put("itemId", extras.getString("itemId", ""))
                    customJson.put("itemType", extras.getString("itemType", "movie"))
                    customJson.put("partIndex", extras.getInt("partIndex", -1))
                    customJson.put("episodeIndex", extras.getInt("episodeIndex", -1))
                    customJson.put("remoteUrl", extras.getString("remoteUrl", ""))
                }
                customJson.put("title", mediaItem.mediaMetadata.title?.toString() ?: "")
                customJson.put("artist", mediaItem.mediaMetadata.artist?.toString() ?: "")
                customJson.put("originalUri", originalUri)

                val mediaInfoBuilder = com.google.android.gms.cast.MediaInfo.Builder(streamUrl)
                    .setContentType("audio/mpeg")
                    .setStreamType(if (mediaInfo.streamType != com.google.android.gms.cast.MediaInfo.STREAM_TYPE_INVALID) mediaInfo.streamType else com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(mediaInfo.metadata)
                    .setCustomData(customJson)
                return com.google.android.gms.cast.MediaQueueItem.Builder(mediaInfoBuilder.build())
                    .setAutoplay(true)
                    .setPreloadTime(queueItem.preloadTime)
                    .setStartTime(queueItem.startTime)
                    .build()
            }
            return queueItem
        }

        override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
            val baseMediaItem = try {
                defaultConverter.toMediaItem(mediaQueueItem)
            } catch (e: Exception) {
                // Fallback seguro si customData no incluye la estructura estándar
                val mediaInfo = mediaQueueItem.media
                val customData = mediaInfo?.customData
                val uri = customData?.optString("originalUri")?.ifBlank { null }
                    ?: mediaInfo?.contentId?.ifBlank { null }
                    ?: "https://audiocinemateca.com"
                MediaItem.Builder()
                    .setUri(android.net.Uri.parse(uri))
                    .setMediaId(customData?.optString("itemId", "") ?: "")
                    .build()
            }

            val customData = mediaQueueItem.media?.customData
            if (customData != null) {
                val extras = Bundle().apply {
                    putString("itemId", customData.optString("itemId", ""))
                    putString("itemType", customData.optString("itemType", "movie"))
                    putInt("partIndex", customData.optInt("partIndex", -1))
                    putInt("episodeIndex", customData.optInt("episodeIndex", -1))
                    putString("remoteUrl", customData.optString("remoteUrl", ""))
                }
                val title = customData.optString("title", baseMediaItem.mediaMetadata.title?.toString() ?: "")
                val artist = customData.optString("artist", baseMediaItem.mediaMetadata.artist?.toString() ?: "")
                val originalUri = customData.optString("originalUri", "")

                val reconstructedMetadata = baseMediaItem.mediaMetadata.buildUpon()
                    .setTitle(title.ifBlank { null })
                    .setArtist(artist.ifBlank { null })
                    .setExtras(extras)
                    .build()

                val builder = baseMediaItem.buildUpon()
                    .setMediaMetadata(reconstructedMetadata)

                if (originalUri.isNotEmpty()) {
                    builder.setUri(android.net.Uri.parse(originalUri))
                } else if (baseMediaItem.localConfiguration?.uri == null && !mediaQueueItem.media?.contentId.isNullOrBlank()) {
                    builder.setUri(android.net.Uri.parse(mediaQueueItem.media!!.contentId))
                }

                return builder.build()
            }
            return baseMediaItem
        }
    }

    override fun onCreate() {
        super.onCreate()
        val playerActionFilter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_STOP)
            addAction(MainActivity.ACTION_REQUEST_PLAYBACK_STATE)
            addAction(MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE)
            addAction(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS)
            addAction(MainActivity.ACTION_SEEK_TO_PREVIOUS)
            addAction(MainActivity.ACTION_SEEK_TO_NEXT)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(playerActionReceiver, playerActionFilter)
    }

    private var hapticGenerator: android.media.audiofx.HapticGenerator? = null

    private fun setupEqualizer(audioSessionId: Int) {
        if (equalizer == null) {
            try {
                equalizer = Equalizer(0, audioSessionId)
                val enabled = sharedPreferencesManager.getBoolean("equalizer_enabled", false)
                equalizer?.enabled = enabled
                if (enabled) {
                    for (i in 0 until equalizer!!.numberOfBands) {
                        val level = sharedPreferencesManager.getInt("equalizer_band_${i}", 0)
                        equalizer?.setBandLevel(i.toShort(), level.toShort())
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerService", "Error al inicializar el ecualizador", e)
            }
        }
    }

    private fun setupHapticSystem(audioSessionId: Int) {
        if (sharedPreferencesManager.getBoolean("haptic_audio_enabled", false) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                if (android.media.audiofx.HapticGenerator.isAvailable()) {
                    hapticGenerator?.release()
                    hapticGenerator = android.media.audiofx.HapticGenerator.create(audioSessionId)
                    hapticGenerator?.enabled = true
                }
            } catch (e: Exception) { Log.e("PlayerService", "Haptic Error", e) }
        }
    }

    private fun releaseHapticSystem() {
        hapticGenerator?.release(); hapticGenerator = null
    }

    private fun broadcastMiniPlayerState(action: String) {
        if (action == MainActivity.ACTION_HIDE_MINI_PLAYER) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(action))
            return
        }
        player.currentMediaItem?.let { mediaItem ->
            val metadata = mediaItem.mediaMetadata
            val extras = metadata.extras
            val intent = Intent(action).apply {
                putExtra(MainActivity.EXTRA_TITLE, metadata.title?.toString())
                putExtra(MainActivity.EXTRA_SUBTITLE, metadata.artist?.toString()) // Nombre de la serie/película
                putExtra(MainActivity.EXTRA_IS_PLAYING, player.isPlaying)
                putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
                putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
                putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1) ?: -1)
                putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1) ?: -1)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun updateSessionActivity(): PendingIntent {
        val activePlayer = try { player } catch (e: Exception) { null }
        val mediaItem = activePlayer?.currentMediaItem
        val extras = mediaItem?.mediaMetadata?.extras
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (extras != null) {
                putExtra(MainActivity.EXTRA_ITEM_ID, extras.getString("itemId"))
                putExtra(MainActivity.EXTRA_ITEM_TYPE, extras.getString("itemType"))
                putExtra(MainActivity.EXTRA_PART_INDEX, extras.getInt("partIndex", -1))
                putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras.getInt("episodeIndex", -1))
            }
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession?.setSessionActivity(pendingIntent)
        return pendingIntent
    }

    private fun ensureForegroundNotification() {
        val session = mediaSession ?: return
        val activePlayer = try { session.player } catch (e: Exception) { null } ?: return

        // Cuando la sesión de Cast está activa, Google Cast gestiona su propia notificación.
        // Retiramos la notificación del teléfono para evitar notificaciones duplicadas.
        val isCastActive = castPlayer?.isCastSessionAvailable == true || activePlayer === castPlayer
        if (isCastActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        val currentItem = activePlayer.currentMediaItem
        if (currentItem == null && activePlayer.playbackState == Player.STATE_IDLE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproductor Audiocinemateca",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reproducción multimedia"
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val metadata = currentItem?.mediaMetadata
        val title = metadata?.title?.toString()?.ifBlank { null } ?: "Audiocinemateca"
        val subtitle = metadata?.artist?.toString()?.ifBlank { null } ?: "Reproduciendo audio..."
        val isPlaying = activePlayer.isPlaying
        val pendingIntent = updateSessionActivity()

        val prevIntent = PendingIntent.getBroadcast(
            this,
            10,
            Intent(MainActivity.ACTION_SEEK_TO_PREVIOUS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            20,
            Intent(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getBroadcast(
            this,
            30,
            Intent(MainActivity.ACTION_SEEK_TO_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getBroadcast(
            this,
            40,
            Intent(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaStyle = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0, 1, 2)

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pausar" else "Reproducir"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_previous,
                    "Anterior",
                    prevIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    playPauseIcon,
                    playPauseTitle,
                    playPauseIntent
                ).build()
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_next,
                    "Siguiente",
                    nextIntent
                ).build()
            )

        val notification = builder.build()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PlayerService", "Error al iniciar/actualizar foreground notification: ${e.message}", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            setupPlayer()
        }
        return mediaSession
    }

    private suspend fun savePlaybackProgress(itemId: String? = null, itemType: String? = null, partIndex: Int = -1, episodeIndex: Int = -1, position: Long? = null, syncToCloud: Boolean = true) {
        val (mediaItem, pos, dur, state, windowIdx) = kotlinx.coroutines.withContext(Dispatchers.Main) {
            val item = try { player.currentMediaItem } catch (e: Exception) { null }
            val currentPos = position ?: (try { player.currentPosition } catch (e: Exception) { 0L })
            val currentDur = try { player.duration } catch (e: Exception) { 0L }
            val currentState = try { player.playbackState } catch (e: Exception) { Player.STATE_IDLE }
            val currentWinIdx = try { player.currentMediaItemIndex } catch (e: Exception) { savedWindowIndex }
            arrayOf(item, currentPos, currentDur, currentState, currentWinIdx)
        }
        val item = mediaItem as? MediaItem ?: savedMediaItems.getOrNull(windowIdx as Int) ?: return
        val meta = item.mediaMetadata.extras 
            ?: savedMediaItems.getOrNull(windowIdx as Int)?.mediaMetadata?.extras 
            ?: return
        val id = itemId ?: meta.getString("itemId") ?: return
        val type = itemType ?: meta.getString("itemType") ?: return
        val currentPos = pos as Long
        val currentDur = dur as Long
        val currentState = state as Int
        if (currentDur <= 0) return
        val pIndex = (if (partIndex != -1) partIndex else meta.getInt("partIndex", 0)).coerceAtLeast(0)
        val eIndex = (if (episodeIndex != -1) episodeIndex else meta.getInt("episodeIndex", 0)).coerceAtLeast(0)
        val isCompleted = (currentDur > 0 && currentPos >= currentDur - 15000) || currentState == Player.STATE_ENDED

        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val progress = PlaybackProgressEntity(
                contentId = id,
                contentType = type,
                currentPositionMs = currentPos,
                totalDurationMs = currentDur,
                partIndex = pIndex,
                episodeIndex = eIndex,
                lastPlayedTimestamp = System.currentTimeMillis(),
                isFinished = isCompleted
            )
            playbackProgressRepository.savePlaybackProgress(progress, syncToCloud)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel(); progressSaveHandler.removeCallbacks(progressSaveRunnable)
        releaseCastLocks()
        try { 
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.release()
            castPlayer?.removeListener(playerListener)
            castPlayer?.release()
        } catch (e: Exception) {}
        try {
            com.johang.audiocinemateca.util.LocalCastProxyServer.stop()
        } catch (e: Exception) {}
        try {
            mediaSession?.release()
        } catch (e: Exception) {}
        mediaSession = null
        exoPlayer = null
        castPlayer = null
        equalizer?.release()
        equalizer = null
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "audiocinemateca_playback"
        var equalizer: Equalizer? = null
        const val ACTION_PLAY_PAUSE = "com.johang.audiocinemateca.PLAY_PAUSE"
        const val ACTION_STOP = "com.johang.audiocinemateca.STOP"
    }
}
