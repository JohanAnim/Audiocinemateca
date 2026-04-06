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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private lateinit var player: Player
    private lateinit var playerListener: Player.Listener
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val progressSaveHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressSaveRunnable = object : Runnable {
        override fun run() {
            serviceScope.launch { savePlaybackProgress(syncToCloud = false) }
            progressSaveHandler.postDelayed(this, 2000) 
        }
    }

    private val playerActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val activePlayer = mediaSession?.player ?: return
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> {
                    if (activePlayer.isPlaying) {
                        serviceScope.launch { savePlaybackProgress(syncToCloud = true) }
                        activePlayer.pause()
                    } else { activePlayer.play() }
                }
                ACTION_STOP -> {
                    Log.d("PlayerService", "Cierre forzado solicitado (Botón X).")
                    serviceScope.launch { savePlaybackProgress(syncToCloud = true) }
                    
                    // IMPORTANTE: Liberar recursos antes de parar el servicio
                    mediaSession?.run {
                        player.stop()
                        player.clearMediaItems()
                        release()
                        mediaSession = null
                    }
                    stopForeground(true)
                    stopSelf()
                }
                MainActivity.ACTION_REQUEST_PLAYBACK_STATE -> {
                    val responseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, activePlayer.isPlaying) }
                    LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(responseIntent)
                }
                MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE -> broadcastMiniPlayerState(MainActivity.ACTION_SHOW_MINI_PLAYER)
                MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS -> serviceScope.launch { savePlaybackProgress() }
                MainActivity.ACTION_SEEK_TO_PREVIOUS -> { if (activePlayer.hasPreviousMediaItem()) activePlayer.seekToPreviousMediaItem() else activePlayer.seekTo(0) }
                MainActivity.ACTION_SEEK_TO_NEXT -> activePlayer.seekToNextMediaItem()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupPlayer()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE); addAction(ACTION_STOP)
            addAction(MainActivity.ACTION_REQUEST_PLAYBACK_STATE); addAction(MainActivity.ACTION_REQUEST_MINI_PLAYER_STATE)
            addAction(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS); addAction(MainActivity.ACTION_SEEK_TO_PREVIOUS); addAction(MainActivity.ACTION_SEEK_TO_NEXT)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(playerActionReceiver, filter)
    }

    private fun setupPlayer() {
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this).setDataSourceFactory(dataSourceFactory)

        val basePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            
        player = object : ForwardingPlayer(basePlayer) {
            override fun seekToPrevious() { if (hasPreviousMediaItem()) seekToPreviousMediaItem() else seekTo(0) }
        }

        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val intent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying) }
                LocalBroadcastManager.getInstance(this@PlayerService).sendBroadcast(intent)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateSessionActivity()
                broadcastMiniPlayerState(MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA)
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
    private var equalizer: Equalizer? = null

    private fun setupEqualizer(audioSessionId: Int) {
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private suspend fun savePlaybackProgress(itemId: String? = null, itemType: String? = null, partIndex: Int = -1, episodeIndex: Int = -1, position: Long? = null, syncToCloud: Boolean = true) {
        val mediaItem = player.currentMediaItem ?: return
        val meta = mediaItem.mediaMetadata.extras ?: return
        val id = itemId ?: meta.getString("itemId") ?: return
        val type = itemType ?: meta.getString("itemType") ?: return
        val pos = position ?: player.currentPosition
        val dur = player.duration
        if (pos < 5000 || dur <= 0) return
        val progress = PlaybackProgressEntity(contentId = id, contentType = type, currentPositionMs = pos, totalDurationMs = dur, partIndex = if (partIndex != -1) partIndex else meta.getInt("partIndex", -1), episodeIndex = if (episodeIndex != -1) episodeIndex else meta.getInt("episodeIndex", -1), lastPlayedTimestamp = System.currentTimeMillis())
        playbackProgressRepository.savePlaybackProgress(progress, syncToCloud)
    }

    override fun onDestroy() {
        serviceScope.cancel(); progressSaveHandler.removeCallbacks(progressSaveRunnable)
        mediaSession?.run {
            try { player.removeListener(playerListener); player.release() } catch (e: Exception) {}
            release(); mediaSession = null
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
