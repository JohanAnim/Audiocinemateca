package com.johang.audiocinemateca.presentation.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.util.Base64
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
@UnstableApi
class PlayerService : MediaSessionService() {

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var playbackProgressRepository: com.johang.audiocinemateca.data.repository.PlaybackProgressRepository

    private var mediaSession: MediaSession? = null
    private lateinit var player: Player
    private lateinit var playerListener: Player.Listener
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val progressSaveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { savePlaybackProgress() }
            progressSaveHandler.postDelayed(this, 2000) // Guardar cada 2 segundos
        }
    }

    private val playerActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val activePlayer = mediaSession?.player ?: return
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    Log.d("PlayerService", "Received ACTION_PLAY_PAUSE.")
                    activePlayer.run {
                        if (isPlaying) pause() else play()
                    }
                }
                ACTION_STOP -> {
                    Log.d("PlayerService", "Received ACTION_STOP.")
                    runBlocking { savePlaybackProgress() } // Guardar progreso antes de detener
                    activePlayer.stop()
                    activePlayer.clearMediaItems()
                    stopSelf()
                }
                MainActivity.ACTION_REQUEST_PLAYBACK_STATE -> {
                    Log.d("PlayerService", "Received ACTION_REQUEST_PLAYBACK_STATE. Sending current playback state.")
                    val isPlaying = activePlayer.isPlaying
                    val responseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                        putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying)
                    }
                    LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(responseIntent)
                }
                MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE -> {
                    Log.d("PlayerService", "Received ACTION_REQUEST_MINI_PLAYER_STATE.")
                    broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
                }
                MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS -> {
                    Log.d("PlayerService", "Received ACTION_SAVE_PLAYBACK_PROGRESS. Saving current playback state.")
                    serviceScope.launch { savePlaybackProgress() }
                }
                MainActivity.ACTION_SEEK_TO_PREVIOUS -> {
                    Log.d("PlayerService", "Received ACTION_SEEK_TO_PREVIOUS. Saving progress and seeking to previous.")
                    val itemId = intent.getStringExtra(MainActivity.EXTRA_ITEM_ID)
                    val itemType = intent.getStringExtra(MainActivity.EXTRA_ITEM_TYPE)
                    val partIndex = intent.getIntExtra(MainActivity.EXTRA_PART_INDEX, -1)
                    val episodeIndex = intent.getIntExtra(MainActivity.EXTRA_EPISODE_INDEX, -1)
                    val currentPosition = intent.getLongExtra(MainActivity.EXTRA_CURRENT_POSITION, 0L)

                    runBlocking {
                        if (itemId != null && itemType != null) {
                            savePlaybackProgress(itemId, itemType, partIndex, episodeIndex, currentPosition)
                        }
                        activePlayer.seekToPreviousMediaItem()
                    }
                }
                MainActivity.ACTION_SEEK_TO_NEXT -> {
                    Log.d("PlayerService", "Received ACTION_SEEK_TO_NEXT. Saving progress and seeking to next.")
                    val itemId = intent.getStringExtra(MainActivity.EXTRA_ITEM_ID)
                    val itemType = intent.getStringExtra(MainActivity.EXTRA_ITEM_TYPE)
                    val partIndex = intent.getIntExtra(MainActivity.EXTRA_PART_INDEX, -1)
                    val episodeIndex = intent.getIntExtra(MainActivity.EXTRA_EPISODE_INDEX, -1)
                    val currentPosition = intent.getLongExtra(MainActivity.EXTRA_CURRENT_POSITION, 0L)

                    runBlocking {
                        if (itemId != null && itemType != null) {
                            savePlaybackProgress(itemId, itemType, partIndex, episodeIndex, currentPosition)
                        }
                        activePlayer.seekToNextMediaItem()
                    }
                }
            }
        }
    }

    private fun broadcastMiniPlayerState(action: String) {
        player.currentMediaItem?.let { mediaItem ->
            val metadata = mediaItem.mediaMetadata
            val extras = metadata.extras
            val intent = Intent(action).apply {
                putExtra(MainActivity.EXTRA_TITLE, metadata.albumTitle?.toString() ?: metadata.title?.toString())
                putExtra(MainActivity.EXTRA_SUBTITLE, metadata.displayTitle?.toString())
                putExtra(MainActivity.EXTRA_IS_PLAYING, player.isPlaying)
                putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
                putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
                putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1) ?: -1)
                putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1) ?: -1)
            }
            LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)
            Log.d("PlayerService", "Sent $action in response.")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()

        val username = sharedPreferencesManager.getString("storedUsername")
        val password = sharedPreferencesManager.getString("storedPassword")

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        if (username != null && password != null) {
            val authString = "Basic " + Base64.encodeToString("${username}:${password}".toByteArray(), Base64.NO_WRAP)
            httpDataSourceFactory.setDefaultRequestProperties(mapOf("Authorization" to authString))
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                val intent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                    putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying)
                }
                LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)
                Log.d("PlayerService", "onIsPlayingChanged: isPlaying = $isPlaying. Broadcast sent.")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                updateSessionActivity()
                broadcastMiniPlayerState(MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN_STATE"
                }
                Log.d("PlayerService", "onPlaybackStateChanged: playbackState = $stateString")
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (equalizer == null) {
                    equalizer = Equalizer(0, audioSessionId)
                    val enabled = sharedPreferencesManager.getBoolean("equalizer_enabled", false)
                    equalizer?.enabled = enabled
                    if (enabled) {
                        for (i in 0 until equalizer!!.numberOfBands) {
                            val level = sharedPreferencesManager.getInt("equalizer_band_${i}", 0)
                            equalizer?.setBandLevel(i.toShort(), level.toShort())
                        }
                    }
                }
            }
        }
        player.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, player)
            .setId("AudiocinematecaPlayerSession")
            .build()

        updateSessionActivity() // Set initial session activity

        progressSaveHandler.post(progressSaveRunnable) // Start periodic saving

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_STOP)
            addAction(MainActivity.ACTION_REQUEST_PLAYBACK_STATE)
            addAction(MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE)
            addAction(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS)
            addAction(MainActivity.ACTION_SEEK_TO_PREVIOUS)
            addAction(MainActivity.ACTION_SEEK_TO_NEXT)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(playerActionReceiver, filter)
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

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession?.setSessionActivity(pendingIntent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val mediaItem = player.currentMediaItem
        val customMetadata = mediaItem?.mediaMetadata?.extras
        val itemId = customMetadata?.getString("itemId")
        val itemType = customMetadata?.getString("itemType")
        val partIndex = customMetadata?.getInt("partIndex", -1) ?: -1
        val episodeIndex = customMetadata?.getInt("episodeIndex", -1) ?: -1
        val currentPosition = player.currentPosition

        runBlocking { 
            if (itemId != null && itemType != null) {
                savePlaybackProgress(itemId, itemType, partIndex, episodeIndex, currentPosition)
            }
        }
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private suspend fun savePlaybackProgress(
        itemId: String? = null,
        itemType: String? = null,
        partIndex: Int = -1,
        episodeIndex: Int = -1,
        position: Long? = null
    ) {
        val mediaItem = player.currentMediaItem ?: return
        val customMetadata = mediaItem.mediaMetadata.extras ?: return

        val currentItem = itemId ?: customMetadata.getString("itemId") ?: return
        val currentType = itemType ?: customMetadata.getString("itemType") ?: return
        val currentPartIndex = if (partIndex != -1) partIndex else customMetadata.getInt("partIndex", -1)
        val currentEpisodeIndex = if (episodeIndex != -1) episodeIndex else customMetadata.getInt("episodeIndex", -1)

        val currentPositionMs = position ?: player.currentPosition
        val totalDuration = player.duration

        Log.d("PlayerService", "savePlaybackProgress: Saving for $currentItem, Type: $currentType, Part: $currentPartIndex, Episode: $currentEpisodeIndex, Position: $currentPositionMs, Duration: $totalDuration")

        if (totalDuration <= 0) return

        val progress = PlaybackProgressEntity(
            contentId = currentItem,
            contentType = currentType,
            currentPositionMs = currentPositionMs,
            totalDurationMs = totalDuration,
            partIndex = currentPartIndex,
            episodeIndex = currentEpisodeIndex,
            lastPlayedTimestamp = System.currentTimeMillis()
        )

        playbackProgressRepository.savePlaybackProgress(progress)
        Log.d("PlayerService", "Progreso guardado: $currentItem - Posición: $currentPositionMs")
    }

    override fun onDestroy() {
        runBlocking { savePlaybackProgress() }
        serviceScope.cancel()
        progressSaveHandler.removeCallbacks(progressSaveRunnable)
        mediaSession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(playerActionReceiver)
        super.onDestroy()
    }

    companion object {
        var equalizer: Equalizer? = null
        const val ACTION_PLAY_PAUSE = "com.johang.audiocinemateca.PLAY_PAUSE"
        const val ACTION_STOP = "com.johang.audiocinemateca.STOP"
    }
}