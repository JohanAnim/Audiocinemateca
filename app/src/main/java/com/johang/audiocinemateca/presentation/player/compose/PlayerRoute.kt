package com.johang.audiocinemateca.presentation.player.compose

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.equalizer.EqualizerDialog
import com.johang.audiocinemateca.presentation.navigation.NavRoutes
import com.johang.audiocinemateca.presentation.player.CommentsBottomSheet
import com.johang.audiocinemateca.presentation.player.PlayerService
import com.johang.audiocinemateca.presentation.player.PlayerViewModel
import com.johang.audiocinemateca.util.ShareUtils
import com.johang.audiocinemateca.util.TtsManager
import com.johang.audiocinemateca.util.requestComposeAccessibilityFocus
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val BASE_URL = "https://audiocinemateca.com/"

@OptIn(UnstableApi::class)
@Composable
fun PlayerRoute(
    itemId: String,
    itemType: String,
    initialPartIndex: Int,
    initialEpisodeIndex: Int,
    searchRepository: SearchRepository,
    sharedPreferencesManager: SharedPreferencesManager,
    playbackProgressRepository: PlaybackProgressRepository,
    downloadRepository: DownloadRepository,
    geminiRepository: GeminiRepository,
    ttsManager: TtsManager,
    onBackClick: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    onNavigateToPlaybackSettings: () -> Unit,
    onNavigateToHelp: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val rootView = LocalView.current
    val scope = rememberCoroutineScope()

    var currentContentItem by remember {
        mutableStateOf(
            NavRoutes.activePlayerCatalogItem?.takeIf { it.id.equals(itemId, ignoreCase = true) }
        )
    }
    var currentPartIndex by remember { mutableIntStateOf(initialPartIndex) }
    var currentEpisodeIndex by remember { mutableIntStateOf(initialEpisodeIndex) }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableLongStateOf(0L) }
    var totalDur by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var rewindSec by remember {
        mutableIntStateOf(sharedPreferencesManager.getString("rewind_interval", "5")?.toIntOrNull() ?: 5)
    }
    var forwardSec by remember {
        mutableIntStateOf(sharedPreferencesManager.getString("forward_interval", "15")?.toIntOrNull() ?: 15)
    }
    var playerLayoutMode by remember {
        mutableStateOf(sharedPreferencesManager.getString("player_fullscreen_layout", "default") ?: "default")
    }
    var timerText by remember { mutableStateOf<String?>(null) }
    var hasPrev by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }
    var isAutoPlayEnabled by remember {
        mutableStateOf(sharedPreferencesManager.getBoolean("autoplay", true))
    }

    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showStillListeningDialog by remember { mutableStateOf(false) }
    var offlineDialogMessage by remember { mutableStateOf<String?>(null) }

    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var sleepTimer by remember { mutableStateOf<CountDownTimer?>(null) }

    fun reloadIntervalSettings() {
        rewindSec = sharedPreferencesManager.getString("rewind_interval", "5")?.toIntOrNull() ?: 5
        forwardSec = sharedPreferencesManager.getString("forward_interval", "15")?.toIntOrNull() ?: 15
        playerLayoutMode = sharedPreferencesManager.getString("player_fullscreen_layout", "default") ?: "default"
    }

    fun cancelSleepTimer(updatePreference: Boolean) {
        sleepTimer?.cancel()
        sleepTimer = null
        timerText = null
        if (updatePreference) {
            sharedPreferencesManager.saveBoolean("sleep_timer_enabled", false)
        }
    }

    fun startSleepTimer(durationInMillis: Long) {
        cancelSleepTimer(updatePreference = false)
        sleepTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                timerText = String.format("Temporizador: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                mediaController?.pause()
                timerText = null
                showStillListeningDialog = true
            }
        }.start()
    }

    // Resolve CatalogItem if not already in memory
    LaunchedEffect(itemId, itemType) {
        if (currentContentItem == null) {
            currentContentItem = searchRepository.getCatalogItemByIdAndType(itemId, itemType)
                ?: searchRepository.findCatalogItemById(itemId, isSeriesHint = itemType.contains("serie", ignoreCase = true))
        }
        currentContentItem?.let { item ->
            viewModel.setContentItem(item, currentPartIndex, currentEpisodeIndex)
        }
    }

    // Periodic progress updater while controller is active
    LaunchedEffect(mediaController) {
        val controller = mediaController ?: return@LaunchedEffect
        while (isActive) {
            isPlaying = controller.isPlaying
            currentPos = controller.currentPosition.coerceAtLeast(0L)
            totalDur = controller.duration.coerceAtLeast(0L)
            speed = controller.playbackParameters.speed
            hasPrev = controller.hasPreviousMediaItem()
            hasNext = controller.hasNextMediaItem()
            delay(500)
        }
    }

    // Connect MediaController and prepare media when content item is ready
    DisposableEffect(currentContentItem) {
        val catalogItem = currentContentItem
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(MainActivity.ACTION_HIDE_MINI_PLAYER))
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(MainActivity.ACTION_REQUEST_PLAYBACK_STATE))

        if (sharedPreferencesManager.getBoolean("sleep_timer_enabled", false)) {
            val durationMinutes = sharedPreferencesManager.getString("sleep_timer_duration", "60")?.toLongOrNull() ?: 60L
            startSleepTimer(durationMinutes * 60 * 1000L)
        }

        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "rewind_interval" || key == "forward_interval" || key == "player_fullscreen_layout") {
                reloadIntervalSettings()
            } else if (key == "ai_content_rating_enabled") {
                viewModel.refreshContentRatingSetting()
            }
        }
        sharedPreferencesManager.getPrefs().registerOnSharedPreferenceChangeListener(prefListener)

        val playerStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON) {
                    isPlaying = intent.getBooleanExtra(MainActivity.EXTRA_IS_PLAYING, false)
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            playerStateReceiver,
            IntentFilter(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON)
        )

        var controllerFuture: ListenableFuture<MediaController>? = null
        var playerListener: Player.Listener? = null

        if (catalogItem != null) {
            val serviceIntent = Intent(context, PlayerService::class.java)
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e("PlayerRoute", "Error al iniciar PlayerService: ${e.message}")
            }

            val sessionToken = SessionToken(context, ComponentName(context, PlayerService::class.java))
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future

            future.addListener({
                val controller = try { future.get() } catch (_: Exception) { null } ?: return@addListener
                mediaController = controller

                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                        val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                            putExtra(MainActivity.EXTRA_IS_PLAYING, playing)
                        }
                        LocalBroadcastManager.getInstance(context).sendBroadcast(playPauseIntent)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            viewModel.trackViewStart(catalogItem.id)
                        }
                        val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                            putExtra(MainActivity.EXTRA_IS_PLAYING, controller.isPlaying)
                        }
                        LocalBroadcastManager.getInstance(context).sendBroadcast(playPauseIntent)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)
                        val uriStr = mediaItem?.localConfiguration?.uri?.toString() ?: ""
                        if (isOfflineMode && (uriStr.startsWith("http://", true) || uriStr.startsWith("https://", true))) {
                            controller.pause()
                            offlineDialogMessage = "No es posible reproducir este episodio porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes."
                            return
                        }

                        val extras = mediaItem?.mediaMetadata?.extras
                        currentPartIndex = extras?.getInt("partIndex", -1) ?: -1
                        currentEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1
                        viewModel.updateCurrentEpisode(currentPartIndex, currentEpisodeIndex)

                        scope.launch {
                            val savedProgress = playbackProgressRepository.getPlaybackProgress(
                                catalogItem.id,
                                currentPartIndex,
                                currentEpisodeIndex
                            )
                            if (savedProgress != null && !savedProgress.isFinished) {
                                val pos = savedProgress.currentPositionMs
                                val dur = savedProgress.totalDurationMs
                                if (pos > 0 && (dur <= 0 || pos < dur - 15000)) {
                                    delay(100)
                                    controller.seekTo(pos)
                                }
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)
                        if (isOfflineMode) {
                            offlineDialogMessage = "No es posible reproducir este contenido sin conexión porque no está descargado en tu dispositivo."
                        }
                    }
                }
                playerListener = listener
                controller.addListener(listener)

                scope.launch {
                    val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)
                    if (isOfflineMode) {
                        val isDownloaded = isContentDownloaded(downloadRepository, catalogItem, currentPartIndex, currentEpisodeIndex)
                        if (!isDownloaded) {
                            offlineDialogMessage = "No es posible reproducir este contenido porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes."
                            return@launch
                        }
                    }

                    val activeItemId = controller.currentMediaItem?.mediaMetadata?.extras?.getString("itemId")
                    if (activeItemId == catalogItem.id) {
                        return@launch
                    }

                    val mediaItems = createMediaItems(downloadRepository, catalogItem)
                    if (mediaItems.isEmpty()) return@launch

                    var startIndex = 0
                    if (catalogItem is Movie || catalogItem is Documentary || catalogItem is ShortFilm) {
                        startIndex = currentPartIndex.coerceAtLeast(0).coerceAtMost(mediaItems.size - 1)
                    } else if (catalogItem is Serie) {
                        for ((index, mediaItem) in mediaItems.withIndex()) {
                            val extras = mediaItem.mediaMetadata.extras
                            if (extras?.getInt("partIndex", -1) == currentPartIndex &&
                                extras.getInt("episodeIndex", -1) == currentEpisodeIndex
                            ) {
                                startIndex = index
                                break
                            }
                        }
                    }

                    val savedProgress = playbackProgressRepository.getPlaybackProgress(
                        catalogItem.id,
                        currentPartIndex,
                        currentEpisodeIndex
                    )
                    val startPosition = if (savedProgress != null) {
                        if (savedProgress.currentPositionMs >= (savedProgress.totalDurationMs - 2000)) 0L
                        else savedProgress.currentPositionMs
                    } else 0L

                    controller.setMediaItems(mediaItems, startIndex, startPosition)
                    controller.prepare()
                    controller.playWhenReady = true
                }
            }, MoreExecutors.directExecutor())
        }

        onDispose {
            cancelSleepTimer(updatePreference = false)
            LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS))

            val controller = mediaController
            val playbackState = controller?.playbackState
            if ((playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) && catalogItem != null) {
                val contentType = NavRoutes.itemTypeOf(catalogItem)
                val intent = Intent(MainActivity.ACTION_SHOW_MINI_PLAYER).apply {
                    val metadata = controller.currentMediaItem?.mediaMetadata
                    putExtra(MainActivity.EXTRA_TITLE, metadata?.title?.toString())
                    putExtra(MainActivity.EXTRA_SUBTITLE, metadata?.artist?.toString())
                    putExtra(MainActivity.EXTRA_IS_PLAYING, controller.isPlaying)
                    putExtra(MainActivity.EXTRA_ITEM_ID, catalogItem.id)
                    putExtra(MainActivity.EXTRA_ITEM_TYPE, contentType)
                    putExtra(MainActivity.EXTRA_PART_INDEX, currentPartIndex)
                    putExtra(MainActivity.EXTRA_EPISODE_INDEX, currentEpisodeIndex)
                }
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            }

            playerListener?.let { mediaController?.removeListener(it) }
            controllerFuture?.let { MediaController.releaseFuture(it) }
            mediaController = null
            sharedPreferencesManager.getPrefs().unregisterOnSharedPreferenceChangeListener(prefListener)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playerStateReceiver)
        }
    }

    val contentRating by viewModel.contentRating.collectAsState()
    val baseSubtitleText by viewModel.primarySubtitle.collectAsState()
    val titleText = currentContentItem?.title ?: "Reproduciendo"
    val subtitleText = remember(baseSubtitleText, contentRating) {
        listOfNotNull(
            baseSubtitleText.takeIf { it.isNotBlank() },
            contentRating?.takeIf { it.isNotBlank() }
        ).joinToString(" • ")
    }

    val doPlayPause: () -> Unit = {
        val controller = mediaController
        if (controller != null) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                    controller.prepare()
                }
                controller.playWhenReady = true
                controller.play()
            }
        }
    }

    val doRewind: () -> Unit = {
        val controller = mediaController
        if (controller != null) {
            val duration = controller.duration
            val targetPos = (controller.currentPosition - rewindSec * 1000L)
                .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
            controller.seekTo(targetPos)
            currentPos = targetPos
        }
    }

    val doForward: () -> Unit = {
        val controller = mediaController
        if (controller != null) {
            val duration = controller.duration
            val targetPos = (controller.currentPosition + forwardSec * 1000L)
                .coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
            controller.seekTo(targetPos)
            currentPos = targetPos
        }
    }

    val doPreviousChapter: () -> Unit = {
        val extras = mediaController?.currentMediaItem?.mediaMetadata?.extras
        val intent = Intent(MainActivity.ACTION_SEEK_TO_PREVIOUS).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
            putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
            putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1))
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1))
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, mediaController?.currentPosition ?: 0L)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        mediaController?.playWhenReady = true
        mediaController?.play()
    }

    val doNextChapter: () -> Unit = {
        val extras = mediaController?.currentMediaItem?.mediaMetadata?.extras
        val intent = Intent(MainActivity.ACTION_SEEK_TO_NEXT).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
            putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
            putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1))
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1))
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, mediaController?.currentPosition ?: 0L)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        mediaController?.playWhenReady = true
        mediaController?.play()
    }

    DisposableEffect(mediaController, titleText, subtitleText, rewindSec, forwardSec, hasPrev, hasNext) {
        val actions = object : com.johang.audiocinemateca.media.PlayerGamepadActions {
            override fun onRewind() {
                doRewind()
            }

            override fun onForward() {
                doForward()
            }

            override fun onPreviousChapter() {
                val hadPrev = mediaController?.hasPreviousMediaItem() == true
                doPreviousChapter()
                if (hadPrev) {
                    ttsManager.speak("Capítulo anterior", interrupt = true)
                } else {
                    ttsManager.speak("Inicio de la reproducción", interrupt = true)
                }
            }

            override fun onNextChapter() {
                val canNext = mediaController?.hasNextMediaItem() == true
                if (canNext) {
                    doNextChapter()
                    ttsManager.speak("Siguiente capítulo", interrupt = true)
                } else {
                    ttsManager.speak("No hay más capítulos disponibles", interrupt = true)
                }
            }

            override fun onAnnounceInfo() {
                val controller = mediaController
                val playing = controller?.isPlaying ?: isPlaying
                val pos = (controller?.currentPosition ?: currentPos).coerceAtLeast(0L)
                val dur = (controller?.duration ?: totalDur).coerceAtLeast(0L)
                val stateStr = if (playing) "Reproduciendo" else "Pausado"
                val workInfo = if (subtitleText.isNotBlank() && subtitleText != titleText) {
                    "$titleText, $subtitleText"
                } else {
                    titleText
                }
                val currentFormatted = com.johang.audiocinemateca.util.TimeFormatUtils.formatDuration(pos)
                val totalFormatted = com.johang.audiocinemateca.util.TimeFormatUtils.formatDuration(dur)
                ttsManager.speak(
                    "$stateStr: $workInfo. $currentFormatted transcurridos de $totalFormatted.",
                    interrupt = true
                )
            }

            override fun onTogglePlayPause() {
                doPlayPause()
            }
        }
        com.johang.audiocinemateca.media.DualSenseControllerManager.activePlayerActions = actions
        onDispose {
            if (com.johang.audiocinemateca.media.DualSenseControllerManager.activePlayerActions === actions) {
                com.johang.audiocinemateca.media.DualSenseControllerManager.activePlayerActions = null
            }
        }
    }

    PlayerScreen(
        title = titleText,
        subtitle = subtitleText,
        isPlaying = isPlaying,
        currentPositionMs = currentPos,
        totalDurationMs = totalDur,
        playbackSpeed = speed,
        rewindIntervalSec = rewindSec,
        forwardIntervalSec = forwardSec,
        sleepTimerText = timerText,
        isAutoPlayEnabled = isAutoPlayEnabled,
        hasPrevious = hasPrev,
        hasNext = hasNext,
        fullscreenLayoutMode = playerLayoutMode,
        viewModel = viewModel,
        onBackClick = onBackClick,
        onPlayPauseClick = doPlayPause,
        onRewindClick = doRewind,
        onForwardClick = doForward,
        onPreviousClick = doPreviousChapter,
        onNextClick = doNextChapter,
        onSeekTo = { pos -> mediaController?.seekTo(pos) },
        onPlaybackSpeedChange = { s ->
            mediaController?.playbackParameters = PlaybackParameters(s)
            speed = s
        },
        onRewindIntervalChange = { sec ->
            sharedPreferencesManager.saveString("rewind_interval", sec.toString())
            reloadIntervalSettings()
        },
        onForwardIntervalChange = { sec ->
            sharedPreferencesManager.saveString("forward_interval", sec.toString())
            reloadIntervalSettings()
        },
        onSleepTimerSelect = { min -> startSleepTimer(min * 60 * 1000L) },
        onCancelSleepTimer = { cancelSleepTimer(true) },
        onAutoPlayToggle = {
            val nextVal = !isAutoPlayEnabled
            isAutoPlayEnabled = nextVal
            sharedPreferencesManager.saveBoolean("autoplay", nextVal)
        },
        onEqualizerClick = { showEqualizerDialog = true },
        onOpenCommentsClick = { showCommentsSheet = true },
        onShareClick = {
            currentContentItem?.let { item ->
                ShareUtils.shareContent(context, item, geminiRepository)
            }
        },
        onTitleClick = {
            currentContentItem?.let { item ->
                onNavigateToDetail(item.id, NavRoutes.itemTypeOf(item))
            }
        },
        onNavigateToPlaybackSettings = onNavigateToPlaybackSettings,
        onNavigateToHelp = onNavigateToHelp,
        onToggleFullscreenLayout = {
            val nextMode = if (playerLayoutMode == "youtube_top") "default" else "youtube_top"
            sharedPreferencesManager.saveString("player_fullscreen_layout", nextMode)
            playerLayoutMode = nextMode
        },
        onAnnounceStatus = { text ->
            ttsManager.speak(text, interrupt = true)
        }
    )

    if (showEqualizerDialog) {
        EqualizerDialog(
            sharedPreferencesManager = sharedPreferencesManager,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showCommentsSheet) {
        CommentsBottomSheet(
            viewModel = viewModel,
            sharedPreferencesManager = sharedPreferencesManager,
            onDismiss = {
                showCommentsSheet = false
                scope.launch {
                    delay(80)
                    rootView.requestComposeAccessibilityFocus("player_comments_preview_card")
                }
            }
        )
    }

    if (showStillListeningDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Oye, ¿sigues ahí?") },
            text = {
                Text("Pausaremos la reproducción por ti para que no te pierdas en lo que estás escuchando y no te siga consumiendo batería.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mediaController?.play()
                        cancelSleepTimer(updatePreference = true)
                        showStillListeningDialog = false
                    }
                ) {
                    Text("Sí, aún estoy aquí")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cancelSleepTimer(updatePreference = true)
                        showStillListeningDialog = false
                    }
                ) {
                    Text("Cerrar")
                }
            }
        )
    }

    offlineDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Modo sin conexión activado") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        offlineDialogMessage = null
                        onBackClick()
                    }
                ) {
                    Text("Aceptar")
                }
            }
        )
    }
}

private suspend fun isContentDownloaded(
    downloadRepository: DownloadRepository,
    catalogItem: CatalogItem,
    partIndex: Int,
    episodeIndex: Int
): Boolean {
    val d = when (catalogItem) {
        is Movie -> downloadRepository.getDownload(catalogItem.id, partIndex.coerceAtLeast(0), -1)
        is Serie -> downloadRepository.getDownload(catalogItem.id, partIndex.coerceAtLeast(0), episodeIndex.coerceAtLeast(0))
        is Documentary -> downloadRepository.getDownload(catalogItem.id, 0, -1)
        is ShortFilm -> downloadRepository.getDownload(catalogItem.id, 0, -1)
        else -> null
    }
    return d != null && d.downloadStatus == "COMPLETE" && !d.filePath.isNullOrBlank()
}

private suspend fun createMediaItems(
    downloadRepository: DownloadRepository,
    catalogItem: CatalogItem
): List<MediaItem> {
    val mediaItems = mutableListOf<MediaItem>()
    val typeStr = NavRoutes.itemTypeOf(catalogItem)

    if (catalogItem is Serie) {
        val sortedSeasons = catalogItem.capitulos.keys.sorted()
        sortedSeasons.forEachIndexed { sIndex, seasonKey ->
            val episodeList = catalogItem.capitulos[seasonKey] ?: emptyList()
            episodeList.forEachIndexed { epIndex, ep ->
                val download = downloadRepository.getDownload(catalogItem.id, sIndex, epIndex)
                val uri = if (download != null && download.downloadStatus == "COMPLETE" && !download.filePath.isNullOrBlank()) {
                    Uri.parse(download.filePath)
                } else {
                    val subPath = ep.enlace.trim()
                    val fullUrl = if (subPath.startsWith("http://") || subPath.startsWith("https://")) subPath else "$BASE_URL$subPath"
                    Uri.parse(fullUrl)
                }

                val extras = Bundle().apply {
                    putString("itemId", catalogItem.id)
                    putString("itemType", typeStr)
                    putInt("partIndex", sIndex)
                    putInt("episodeIndex", epIndex)
                }

                val metadata = MediaMetadata.Builder()
                    .setTitle(catalogItem.title)
                    .setArtist("T${sIndex + 1}:E${ep.capitulo} - ${ep.titulo}")
                    .setExtras(extras)
                    .build()

                mediaItems.add(
                    MediaItem.Builder()
                        .setUri(uri)
                        .setMediaMetadata(metadata)
                        .setMediaId("${catalogItem.id}_${sIndex}_$epIndex")
                        .build()
                )
            }
        }
    } else if (catalogItem is Movie && catalogItem.enlaces.size > 1) {
        catalogItem.enlaces.forEachIndexed { pIndex, link ->
            val download = downloadRepository.getDownload(catalogItem.id, pIndex, -1)
            val uri = if (download != null && download.downloadStatus == "COMPLETE" && !download.filePath.isNullOrBlank()) {
                Uri.parse(download.filePath)
            } else {
                val fullUrl = if (link.startsWith("http://") || link.startsWith("https://")) link else "$BASE_URL$link"
                Uri.parse(fullUrl)
            }

            val extras = Bundle().apply {
                putString("itemId", catalogItem.id)
                putString("itemType", typeStr)
                putInt("partIndex", pIndex)
                putInt("episodeIndex", -1)
            }

            val subtitleText = if (catalogItem.enlaces.size > 1) "Parte ${pIndex + 1} de ${catalogItem.enlaces.size}" else ""
            val metadata = MediaMetadata.Builder()
                .setTitle(catalogItem.title)
                .setArtist(subtitleText)
                .setExtras(extras)
                .build()

            mediaItems.add(
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(metadata)
                    .setMediaId("${catalogItem.id}_${pIndex}_-1")
                    .build()
            )
        }
    } else {
        val download = downloadRepository.getDownload(catalogItem.id, 0, -1)
        val uri = if (download != null && download.downloadStatus == "COMPLETE" && !download.filePath.isNullOrBlank()) {
            Uri.parse(download.filePath)
        } else {
            val rawUrl = when (catalogItem) {
                is Movie -> catalogItem.enlaces.firstOrNull() ?: ""
                is Documentary -> catalogItem.enlace
                is ShortFilm -> catalogItem.enlace
                else -> ""
            }
            val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "$BASE_URL$rawUrl"
            Uri.parse(fullUrl)
        }

        val extras = Bundle().apply {
            putString("itemId", catalogItem.id)
            putString("itemType", typeStr)
            putInt("partIndex", 0)
            putInt("episodeIndex", -1)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(catalogItem.title)
            .setArtist("")
            .setExtras(extras)
            .build()

        mediaItems.add(
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(metadata)
                .setMediaId("${catalogItem.id}_0_-1")
                .build()
        )
    }

    return mediaItems
}
