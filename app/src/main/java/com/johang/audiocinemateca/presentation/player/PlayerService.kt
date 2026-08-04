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

    @Inject
    lateinit var jamRepository: com.johang.audiocinemateca.data.repository.JamRepository

    @Inject
    lateinit var contentRepository: com.johang.audiocinemateca.data.repository.ContentRepository

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

    private val castSessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() {
            Log.d("PlayerService", "onCastSessionAvailable: Cast session is now available")
            onCastAvailable()
        }

        override fun onCastSessionUnavailable() {
            Log.d("PlayerService", "onCastSessionUnavailable: Cast session is no longer available")
            onCastUnavailable()
        }
    }

    private fun updateSavedState(p: Player = player) {
        try {
            if (p.mediaItemCount > 0) {
                val items = mutableListOf<MediaItem>()
                for (i in 0 until p.mediaItemCount) {
                    items.add(p.getMediaItemAt(i))
                }
                savedMediaItems = items
                savedWindowIndex = p.currentMediaItemIndex
                val pos = p.currentPosition
                if (pos >= 0) {
                    savedPositionMs = pos
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

        val mediaItems = mutableListOf<MediaItem>()
        val itemCount = localPlayer.mediaItemCount
        for (i in 0 until itemCount) {
            mediaItems.add(localPlayer.getMediaItemAt(i))
        }

        val targetItems = if (mediaItems.isNotEmpty()) mediaItems else savedMediaItems
        val targetIndex = if (itemCount > 0) localPlayer.currentMediaItemIndex else savedWindowIndex
        val targetPos = if (itemCount > 0) localPlayer.currentPosition else savedPositionMs
        val playWhenReady = localPlayer.playWhenReady || localPlayer.isPlaying

        if (targetItems.isNotEmpty()) {
            savedMediaItems = targetItems.toList()
            savedWindowIndex = targetIndex
            savedPositionMs = targetPos

            cPlayer.setMediaItems(targetItems, targetIndex, targetPos.coerceAtLeast(0L))
            cPlayer.prepare()
            if (playWhenReady) {
                cPlayer.playWhenReady = true
                cPlayer.play()
            }
        }

        mediaSession?.player = cPlayer

        localPlayer.pause()
        localPlayer.stop()

        broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
    }

    private fun onCastUnavailable() {
        val localPlayer = exoPlayer ?: return
        val cPlayer = castPlayer ?: return

        Log.d("PlayerService", "Transferring playback from CastPlayer to ExoPlayer")

        val mediaItems = mutableListOf<MediaItem>()
        val itemCount = cPlayer.mediaItemCount
        for (i in 0 until itemCount) {
            mediaItems.add(cPlayer.getMediaItemAt(i))
        }

        val targetItems = if (mediaItems.isNotEmpty()) mediaItems else savedMediaItems
        val targetIndex = if (itemCount > 0) cPlayer.currentMediaItemIndex else savedWindowIndex
        val targetPos = if (itemCount > 0) cPlayer.currentPosition else savedPositionMs
        val playWhenReady = cPlayer.playWhenReady || cPlayer.isPlaying

        if (targetItems.isNotEmpty()) {
            savedMediaItems = targetItems.toList()
            savedWindowIndex = targetIndex
            savedPositionMs = targetPos

            localPlayer.setMediaItems(targetItems, targetIndex, targetPos.coerceAtLeast(0L))
            localPlayer.prepare()
            if (playWhenReady) {
                localPlayer.playWhenReady = true
                localPlayer.play()
            }
        }

        mediaSession?.player = localPlayer

        try {
            cPlayer.stop()
        } catch (e: Exception) {
            Log.e("PlayerService", "Error stopping CastPlayer: ${e.message}")
        }

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
                syncHostJamProgressIfNeeded()
            }
            progressSaveHandler.postDelayed(this, 2000) 
        }
    }

    private suspend fun syncHostJamProgressIfNeeded() {
        try {
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser ?: return
            val jam = jamRepository.observeCurrentJam().firstOrNull() ?: return
            if (jam.hostUserId == currentUser.uid) {
                val activePlayer = mediaSession?.player ?: return
                jamRepository.updateJamProgress(
                    positionMs = activePlayer.currentPosition,
                    isPlaying = activePlayer.isPlaying
                )
            }
        } catch (e: Exception) {}
    }

    private suspend fun loadAndPlayJamContent(contentId: String, contentType: String, positionMs: Long, isPlaying: Boolean) {
        val catalogItem = contentRepository.getContentItem(contentId, contentType) ?: return
        val BASE_URL = "https://audiocinemateca.com/"
        val mediaItems = mutableListOf<MediaItem>()

        when (catalogItem) {
            is com.johang.audiocinemateca.data.model.Movie -> {
                catalogItem.enlaces.forEachIndexed { index, urlPath ->
                    val uri = android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${urlPath.removePrefix("/")}")
                    val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "peliculas"); putInt("partIndex", index); putInt("episodeIndex", -1) }
                    val partTitle = if (catalogItem.enlaces.size > 1) "Parte ${index + 1}" else catalogItem.title
                    val artist = if (catalogItem.enlaces.size > 1) catalogItem.title else "Audiocinemateca"
                    mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(partTitle).setArtist(artist).setExtras(meta).build()).build())
                }
            }
            is com.johang.audiocinemateca.data.model.Serie -> {
                catalogItem.capitulos.keys.sorted().forEachIndexed { sIdx, sKey ->
                    catalogItem.capitulos[sKey]?.forEach { ep ->
                        val eIdx = catalogItem.capitulos[sKey]?.indexOf(ep) ?: -1
                        val uri = android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${ep.enlace.removePrefix("/")}")
                        val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "series"); putInt("partIndex", sIdx); putInt("episodeIndex", eIdx) }
                        mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle("T${sIdx + 1}:E${ep.capitulo} - ${ep.titulo}").setArtist(catalogItem.title).setExtras(meta).build()).build())
                    }
                }
            }
            is com.johang.audiocinemateca.data.model.Documentary -> {
                val uri = android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${catalogItem.enlace.removePrefix("/")}")
                val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "documentales"); putInt("partIndex", 0); putInt("episodeIndex", -1) }
                mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(catalogItem.title).setArtist("Audiocinemateca").setExtras(meta).build()).build())
            }
            is com.johang.audiocinemateca.data.model.ShortFilm -> {
                val uri = android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${catalogItem.enlace.removePrefix("/")}")
                val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "cortometrajes"); putInt("partIndex", 0); putInt("episodeIndex", -1) }
                mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(catalogItem.title).setArtist("Audiocinemateca").setExtras(meta).build()).build())
            }
        }

        if (mediaItems.isNotEmpty()) {
            val targetPos = positionMs.coerceAtLeast(0L)
            player.setMediaItems(mediaItems, 0, targetPos)
            player.prepare()
            if (isPlaying) player.play() else player.pause()
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
                Log.d("PlayerService", "Cierre forzado solicitado (Botón X).")
                serviceScope.launch {
                    try {
                        savePlaybackProgress(syncToCloud = true)
                    } catch (e: Exception) {
                        Log.e("PlayerService", "Error al guardar progreso al cerrar con X: ${e.message}")
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        try {
                            activePlayer.stop()
                            activePlayer.clearMediaItems()
                        } catch (e: Exception) {}
                        try {
                            mediaSession?.release()
                        } catch (e: Exception) {}
                        mediaSession = null
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
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
            }
            MainActivity.ACTION_SEEK_TO_NEXT -> {
                if (activePlayer.hasNextMediaItem()) activePlayer.seekToNextMediaItem()
            }
            ACTION_SYNC_JAM_STATE -> {
                val pos = safeIntent.getLongExtra(EXTRA_JAM_POSITION, -1L)
                val isPlaying = safeIntent.getBooleanExtra(EXTRA_JAM_IS_PLAYING, true)
                val contentId = safeIntent.getStringExtra("extra_content_id")
                val rawContentType = safeIntent.getStringExtra("extra_content_type") ?: "pelicula"

                val contentType = when (rawContentType.lowercase(java.util.Locale.ROOT)) {
                    "pelicula", "peliculas", "movie" -> "peliculas"
                    "serie", "series" -> "series"
                    "documental", "documentales", "documentary" -> "documentales"
                    "cortometraje", "cortometrajes", "short", "shortfilm" -> "cortometrajes"
                    else -> rawContentType
                }

                if (!contentId.isNullOrBlank()) {
                    val currentMediaId = activePlayer.currentMediaItem?.mediaMetadata?.extras?.getString("itemId")
                    if (currentMediaId != contentId || activePlayer.mediaItemCount == 0) {
                        serviceScope.launch {
                            loadAndPlayJamContent(contentId, contentType, pos, isPlaying)
                        }
                        return
                    }
                }

                if (pos >= 0) {
                    val diff = kotlin.math.abs(activePlayer.currentPosition - pos)
                    if (diff > 1200) {
                        activePlayer.seekTo(pos)
                    }
                }
                if (activePlayer.playbackState == Player.STATE_IDLE) {
                    activePlayer.prepare()
                }
                if (isPlaying && !activePlayer.isPlaying) {
                    activePlayer.play()
                } else if (!isPlaying && activePlayer.isPlaying) {
                    activePlayer.pause()
                }
            }
        }
    }

    @Synchronized
    private fun setupPlayer() {
        if (mediaSession != null) return

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

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
                serviceScope.launch { syncHostJamProgressIfNeeded() }
                ensureForegroundNotification()
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                updateSavedState()
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    serviceScope.launch { syncHostJamProgressIfNeeded() }
                }
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

        mediaSession = MediaSession.Builder(this, initialPlayer)
            .setId("AudiocinematecaPlayerSession")
            .build()

        updateSessionActivity()
        progressSaveHandler.post(progressSaveRunnable)
    }

    private class CastMediaItemConverter : MediaItemConverter {
        private val defaultConverter = DefaultMediaItemConverter()

        override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
            val originalUri = mediaItem.localConfiguration?.uri?.toString()
            val itemToConvert = if (originalUri != null) {
                val proxiedUrl = AudioProxyUtil.buildCastProxyUrl(originalUri)
                mediaItem.buildUpon().setUri(android.net.Uri.parse(proxiedUrl)).build()
            } else {
                mediaItem
            }
            return defaultConverter.toMediaQueueItem(itemToConvert)
        }

        override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
            return defaultConverter.toMediaItem(mediaQueueItem)
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
            addAction(ACTION_SYNC_JAM_STATE)
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Reproductor Audiocinemateca",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val activePlayer = try { player } catch (e: Exception) { null }
        val currentItem = activePlayer?.currentMediaItem
        val metadata = currentItem?.mediaMetadata
        val title = metadata?.title?.toString()?.ifBlank { null } ?: "Audiocinemateca - Jam en Vivo"
        val subtitle = metadata?.artist?.toString()?.ifBlank { null } ?: "Sincronizando reproducción..."
        val pendingIntent = updateSessionActivity()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        val notification = builder.build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            setupPlayer()
        }
        return mediaSession
    }

    private suspend fun savePlaybackProgress(itemId: String? = null, itemType: String? = null, partIndex: Int = -1, episodeIndex: Int = -1, position: Long? = null, syncToCloud: Boolean = true) {
        val (mediaItem, pos, dur, state) = kotlinx.coroutines.withContext(Dispatchers.Main) {
            val item = try { player.currentMediaItem } catch (e: Exception) { null }
            val currentPos = position ?: (try { player.currentPosition } catch (e: Exception) { 0L })
            val currentDur = try { player.duration } catch (e: Exception) { 0L }
            val currentState = try { player.playbackState } catch (e: Exception) { Player.STATE_IDLE }
            arrayOf(item, currentPos, currentDur, currentState)
        }
        val item = mediaItem as? MediaItem ?: return
        val meta = item.mediaMetadata.extras ?: return
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val jam = jamRepository.observeCurrentJam().firstOrNull()
                    if (jam != null) {
                        if (jam.hostUserId == currentUser.uid) {
                            jamRepository.endJam()
                        } else {
                            jamRepository.leaveJam()
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    val jam = jamRepository.observeCurrentJam().firstOrNull()
                    if (jam != null) {
                        if (jam.hostUserId == currentUser.uid) {
                            jamRepository.endJam()
                        } else {
                            jamRepository.leaveJam()
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        serviceScope.cancel(); progressSaveHandler.removeCallbacks(progressSaveRunnable)
        try { 
            exoPlayer?.removeListener(playerListener)
            exoPlayer?.release()
            castPlayer?.removeListener(playerListener)
            castPlayer?.release()
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
        const val ACTION_SYNC_JAM_STATE = "com.johang.audiocinemateca.SYNC_JAM_STATE"
        const val EXTRA_JAM_POSITION = "extra_jam_position"
        const val EXTRA_JAM_IS_PLAYING = "extra_jam_is_playing"
    }
}
