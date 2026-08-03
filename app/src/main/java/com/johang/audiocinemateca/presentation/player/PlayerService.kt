package com.johang.audiocinemateca.presentation.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
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
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: Player
    private lateinit var playerListener: Player.Listener
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var lastKnownCastPositionMs: Long = -1L
    private var castProgressListenerRegistered = false

    private val progressSaveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch {
                val castSession = getActiveCastSession()
                val castPos = castSession?.remoteMediaClient?.approximateStreamPosition ?: -1L
                val pos = if (castPos >= 0) castPos else if (lastKnownCastPositionMs >= 0) lastKnownCastPositionMs else player.currentPosition
                if (pos >= 0) {
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
                    mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(catalogItem.title).setExtras(meta).build()).build())
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
                mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(catalogItem.title).setExtras(meta).build()).build())
            }
            is com.johang.audiocinemateca.data.model.ShortFilm -> {
                val uri = android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${catalogItem.enlace.removePrefix("/")}")
                val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "cortometrajes"); putInt("partIndex", 0); putInt("episodeIndex", -1) }
                mediaItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(catalogItem.title).setExtras(meta).build()).build())
            }
        }

        if (mediaItems.isNotEmpty()) {
            val targetPos = positionMs.coerceAtLeast(0L)
            player.setMediaItems(mediaItems, 0, targetPos)
            player.prepare()
            if (isPlaying) player.play() else player.pause()
        }
    }

    private fun getActiveCastSession(): com.google.android.gms.cast.framework.CastSession? {
        return try {
            val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
            val session = castContext.sessionManager.currentCastSession
            if (session != null && session.isConnected) session else null
        } catch (e: Exception) {
            null
        }
    }

    private val playerActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val activePlayer = mediaSession?.player ?: return
            val castSession = getActiveCastSession()
            val remoteClient = castSession?.remoteMediaClient

            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    if (player.isPlaying) {
                        serviceScope.launch { savePlaybackProgress(syncToCloud = true) }
                        player.pause()
                    } else {
                        player.play()
                    }
                }
                ACTION_STOP -> {
                    Log.d("PlayerService", "Cierre forzado solicitado (Botón X).")
                    val currentPos = player.currentPosition
                    try {
                        kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                            savePlaybackProgress(position = currentPos, syncToCloud = true)
                        }
                    } catch (e: Exception) {
                        Log.e("PlayerService", "Error al guardar progreso síncrono al cerrar con X: ${e.message}")
                    }
                    player.stop()
                    player.clearMediaItems()
                    stopForeground(true)
                    stopSelf()
                }
                MainActivity.ACTION_REQUEST_PLAYBACK_STATE -> {
                    val intentResponse = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                        putExtra(MainActivity.EXTRA_IS_PLAYING, player.isPlaying)
                    }
                    LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intentResponse)
                }
                MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE -> broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
                MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS -> serviceScope.launch { savePlaybackProgress() }
                MainActivity.ACTION_SEEK_TO_PREVIOUS -> {
                    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
                }
                MainActivity.ACTION_SEEK_TO_NEXT -> {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
                ACTION_SYNC_JAM_STATE -> {
                    val pos = intent?.getLongExtra(EXTRA_JAM_POSITION, -1L) ?: -1L
                    val isPlaying = intent?.getBooleanExtra(EXTRA_JAM_IS_PLAYING, true) ?: true
                    val contentId = intent?.getStringExtra("extra_content_id")
                    val contentType = intent?.getStringExtra("extra_content_type")

                    if (!contentId.isNullOrBlank()) {
                        val currentMediaId = player.currentMediaItem?.mediaMetadata?.extras?.getString("itemId")
                        if (currentMediaId != contentId) {
                            serviceScope.launch {
                                loadAndPlayJamContent(contentId, contentType ?: "pelicula", pos, isPlaying)
                            }
                            return
                        }
                    }

                    if (pos >= 0) {
                        val diff = kotlin.math.abs(player.currentPosition - pos)
                        if (diff > 1200) {
                            player.seekTo(pos)
                        }
                    }
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    if (isPlaying && !player.isPlaying) {
                        player.play()
                    } else if (!isPlaying && player.isPlaying) {
                        player.pause()
                    }
                }
                com.johang.audiocinemateca.presentation.cast.CastSessionListener.ACTION_CAST_CONNECTED -> {
                    sendCurrentMediaToCast()
                }
                com.johang.audiocinemateca.presentation.cast.CastSessionListener.ACTION_CAST_DISCONNECTED -> {
                    try {
                        exoPlayer.volume = 1f
                        val posFromIntent = intent?.getLongExtra(com.johang.audiocinemateca.presentation.cast.CastSessionListener.EXTRA_LAST_CAST_POSITION, -1L) ?: -1L
                        val pos = if (posFromIntent > 0) posFromIntent else if (lastKnownCastPositionMs > 0) lastKnownCastPositionMs else -1L
                        if (pos > 0) {
                            exoPlayer.seekTo(pos)
                            Log.d("PlayerService", "Reanudando reproducción local en pos: $pos ms")
                        }
                        lastKnownCastPositionMs = -1L
                        castProgressListenerRegistered = false
                    } catch (e: Exception) {
                        Log.e("PlayerService", "Error al restaurar posición tras desconectar Cast: ${e.message}")
                    }
                    exoPlayer.play()
                }
            }
        }
    }

    private fun sendCurrentMediaToCast() {
        try {
            val castSession = getActiveCastSession() ?: return
            val remoteMediaClient = castSession.remoteMediaClient ?: return

            // 1. Silenciar y pausar ExoPlayer local
            exoPlayer.volume = 0f
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            }

            if (!castProgressListenerRegistered) {
                try {
                    remoteMediaClient.addProgressListener({ progressMs, _ ->
                        if (progressMs >= 0) {
                            lastKnownCastPositionMs = progressMs
                            notifyCastStatusChanged(remoteMediaClient.isPlaying, progressMs)
                        }
                    }, 500)
                    castProgressListenerRegistered = true
                } catch (e: Exception) {
                    Log.e("PlayerService", "Error al registrar ProgressListener de Cast: ${e.message}")
                }
            }

            remoteMediaClient.registerCallback(object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
                override fun onStatusUpdated() {
                    val isPlayingOnCast = remoteMediaClient.isPlaying
                    val pos = remoteMediaClient.approximateStreamPosition
                    if (pos >= 0) {
                        lastKnownCastPositionMs = pos
                    }
                    
                    notifyCastStatusChanged(isPlayingOnCast, pos)

                    val intent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                        putExtra(MainActivity.EXTRA_IS_PLAYING, isPlayingOnCast)
                    }
                    LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)
                    sharedPreferencesManager.saveBoolean("is_currently_playing", isPlayingOnCast)
                }
            })

            val currentItem = exoPlayer.currentMediaItem ?: return
            val rawUri = currentItem.localConfiguration?.uri?.toString() ?: return
            val proxiedUrl = com.johang.audiocinemateca.util.AudioProxyUtil.buildCastProxyUrl(rawUri)

            val currentRemoteMediaInfo = remoteMediaClient.mediaInfo
            val isSameMedia = currentRemoteMediaInfo?.contentId == proxiedUrl

            if (isSameMedia && (remoteMediaClient.isPlaying || remoteMediaClient.isBuffering || remoteMediaClient.isPaused)) {
                Log.d("PlayerService", "📡 Cast ya está reproduciendo el medio actual. Preservando reproducción remota.")
                return
            }

            val title = currentItem.mediaMetadata.title?.toString() ?: "Audiocinemateca"
            val artist = currentItem.mediaMetadata.artist?.toString() ?: "Audiocinemateca"

            val castMetadata = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, title)
                putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, artist)
                putString(com.google.android.gms.cast.MediaMetadata.KEY_SUBTITLE, artist)
            }

            val mediaInfo = com.google.android.gms.cast.MediaInfo.Builder(proxiedUrl)
                .setStreamType(com.google.android.gms.cast.MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType("audio/mpeg")
                .setMetadata(castMetadata)
                .build()

            val castPos = remoteMediaClient.approximateStreamPosition
            val currentPos = when {
                castPos > 0 -> castPos
                lastKnownCastPositionMs > 0 -> lastKnownCastPositionMs
                else -> exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            lastKnownCastPositionMs = currentPos

            val loadOptions = com.google.android.gms.cast.MediaLoadOptions.Builder()
                .setAutoplay(true)
                .setPlayPosition(currentPos)
                .build()

            remoteMediaClient.load(mediaInfo, loadOptions)
            notifyCastStatusChanged(true, currentPos)
            Log.d("PlayerService", "📡 Audio cargado exitosamente en Chromecast (pos $currentPos ms): $title ($artist)")
        } catch (e: Exception) {
            Log.e("PlayerService", "Error al transferir medios a Google Cast: ${e.message}")
        }
    }

    private fun notifyCastStatusChanged(isPlaying: Boolean, positionMs: Long) {
        (player as? CastAwareForwardingPlayer)?.notifyCastStatusChanged(isPlaying, positionMs)
    }

    inner class CastAwareForwardingPlayer(player: Player) : ForwardingPlayer(player) {
        private val customListeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            super.addListener(listener)
            customListeners.add(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            super.removeListener(listener)
            customListeners.remove(listener)
        }

        fun notifyCastStatusChanged(isPlaying: Boolean, positionMs: Long) {
            for (listener in customListeners) {
                try {
                    listener.onIsPlayingChanged(isPlaying)
                    listener.onEvents(
                        this,
                        Player.Events(
                            androidx.media3.common.FlagSet.Builder()
                                .add(Player.EVENT_IS_PLAYING_CHANGED)
                                .add(Player.EVENT_PLAYBACK_STATE_CHANGED)
                                .add(Player.EVENT_POSITION_DISCONTINUITY)
                                .build()
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PlayerService", "Error notificando listener de Cast: ${e.message}")
                }
            }
        }

        override fun getCurrentPosition(): Long {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                val pos = remoteClient.approximateStreamPosition
                if (pos > 0) {
                    lastKnownCastPositionMs = pos
                    return pos
                } else if (lastKnownCastPositionMs > 0) {
                    return lastKnownCastPositionMs
                }
            }
            return super.getCurrentPosition()
        }

        override fun getDuration(): Long {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                val dur = remoteClient.streamDuration
                if (dur > 0) return dur
            }
            return super.getDuration()
        }

        override fun isPlaying(): Boolean {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                return remoteClient.isPlaying
            }
            return super.isPlaying()
        }

        override fun getPlayWhenReady(): Boolean {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                return remoteClient.isPlaying
            }
            return super.getPlayWhenReady()
        }

        override fun getPlaybackState(): Int {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                return when {
                    remoteClient.isBuffering -> Player.STATE_BUFFERING
                    remoteClient.isPlaying || remoteClient.isPaused -> Player.STATE_READY
                    else -> super.getPlaybackState()
                }
            }
            return super.getPlaybackState()
        }

        override fun seekToPrevious() {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                val rewindMs = (sharedPreferencesManager.getString("rewind_interval", "5")?.toLongOrNull() ?: 5L) * 1000
                val targetPos = (getCurrentPosition() - rewindMs).coerceAtLeast(0L)
                seekTo(targetPos)
            } else {
                if (hasPreviousMediaItem()) seekToPreviousMediaItem() else seekTo(0)
            }
        }

        override fun seekToNext() {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                val forwardMs = (sharedPreferencesManager.getString("forward_interval", "15")?.toLongOrNull() ?: 15L) * 1000
                val targetPos = getCurrentPosition() + forwardMs
                seekTo(targetPos)
            } else {
                if (hasNextMediaItem()) seekToNextMediaItem()
            }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null && positionMs >= 0) {
                lastKnownCastPositionMs = positionMs
                try {
                    val resumeState = if (remoteClient.isPlaying) {
                        com.google.android.gms.cast.MediaSeekOptions.RESUME_STATE_PLAY
                    } else {
                        com.google.android.gms.cast.MediaSeekOptions.RESUME_STATE_PAUSE
                    }
                    val seekOptions = com.google.android.gms.cast.MediaSeekOptions.Builder()
                        .setPosition(positionMs)
                        .setResumeState(resumeState)
                        .build()
                    remoteClient.seek(seekOptions)
                    notifyCastStatusChanged(remoteClient.isPlaying, positionMs)
                    Log.d("PlayerService", "📡 Seek enviado a Google Cast: $positionMs ms")
                } catch (e: Exception) {
                    Log.e("PlayerService", "Error al realizar seek en RemoteMediaClient: ${e.message}")
                }
            } else {
                super.seekTo(mediaItemIndex, positionMs)
            }
        }

        override fun play() {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                remoteClient.play()
                notifyCastStatusChanged(true, getCurrentPosition())
            } else {
                super.play()
            }
        }

        override fun pause() {
            val remoteClient = getActiveCastSession()?.remoteMediaClient
            if (remoteClient != null) {
                remoteClient.pause()
                notifyCastStatusChanged(false, getCurrentPosition())
            } else {
                super.pause()
            }
        }
    }

    private fun setupPlayer() {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        player = CastAwareForwardingPlayer(exoPlayer)

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val intent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying) }
                LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)
                
                sharedPreferencesManager.saveBoolean("is_currently_playing", isPlaying)
                if (isPlaying) {
                    serviceScope.launch { savePlaybackProgress() }
                }
                serviceScope.launch { syncHostJamProgressIfNeeded() }
            }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    serviceScope.launch { syncHostJamProgressIfNeeded() }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateSessionActivity()
                broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
                serviceScope.launch { savePlaybackProgress() }
                try {
                    val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(this@PlayerService)
                    if (castContext.sessionManager.currentCastSession?.isConnected == true) {
                        sendCurrentMediaToCast()
                    }
                } catch (e: Exception) {}
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    serviceScope.launch { savePlaybackProgress() }
                }
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupEqualizer(audioSessionId)
                setupHapticSystem(audioSessionId)
            }
        }
        player.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, player).setId("AudiocinematecaPlayerSession").build()
        updateSessionActivity()
        progressSaveHandler.post(progressSaveRunnable)
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

    private fun updateSessionActivity() {
        val mediaItem = player.currentMediaItem ?: return
        val extras = mediaItem.mediaMetadata.extras ?: return
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAYER
            putExtra(MainActivity.EXTRA_ITEM_ID, extras.getString("itemId"))
            putExtra(MainActivity.EXTRA_ITEM_TYPE, extras.getString("itemType"))
            putExtra(MainActivity.EXTRA_PART_INDEX, extras.getInt("partIndex", -1))
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras.getInt("episodeIndex", -1))
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        mediaSession?.setSessionActivity(pendingIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        if (mediaSession == null) {
            setupPlayer()
        }
        return mediaSession
    }

    private suspend fun savePlaybackProgress(itemId: String? = null, itemType: String? = null, partIndex: Int = -1, episodeIndex: Int = -1, position: Long? = null, syncToCloud: Boolean = true) {
        val mediaItem = player.currentMediaItem ?: return
        val meta = mediaItem.mediaMetadata.extras ?: return
        val id = itemId ?: meta.getString("itemId") ?: return
        val type = itemType ?: meta.getString("itemType") ?: return
        val pos = position ?: player.currentPosition
        val dur = player.duration
        if (dur <= 0) return
        val pIndex = (if (partIndex != -1) partIndex else meta.getInt("partIndex", 0)).coerceAtLeast(0)
        val eIndex = (if (episodeIndex != -1) episodeIndex else meta.getInt("episodeIndex", 0)).coerceAtLeast(0)
        val isCompleted = (dur > 0 && pos >= dur - 15000) || player.playbackState == Player.STATE_ENDED

        val progress = PlaybackProgressEntity(
            contentId = id,
            contentType = type,
            currentPositionMs = pos,
            totalDurationMs = dur,
            partIndex = pIndex,
            episodeIndex = eIndex,
            lastPlayedTimestamp = System.currentTimeMillis(),
            isFinished = isCompleted
        )
        playbackProgressRepository.savePlaybackProgress(progress, syncToCloud)
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
        mediaSession?.run {
            try { player.removeListener(playerListener); player.release() } catch (e: Exception) {}
            release(); mediaSession = null
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerActionReceiver)
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
