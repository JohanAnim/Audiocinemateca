package com.johang.audiocinemateca.presentation.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.equalizer.EqualizerDialogFragment
import com.johang.audiocinemateca.presentation.player.compose.PlayerScreen
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.util.ShareUtils
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private val viewModel: PlayerViewModel by viewModels()
    private val args: PlayerFragmentArgs by navArgs()

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var downloadRepository: com.johang.audiocinemateca.data.repository.DownloadRepository

    @Inject
    lateinit var geminiRepository: com.johang.audiocinemateca.data.repository.GeminiRepository

    @Inject
    lateinit var ttsManager: com.johang.audiocinemateca.util.TtsManager

    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private var currentContentItem: CatalogItem? = null
    private var currentPartIndex: Int = -1
    private var currentEpisodeIndex: Int = -1

    private val isPlayingState = MutableStateFlow(false)
    private val currentPositionState = MutableStateFlow(0L)
    private val totalDurationState = MutableStateFlow(0L)
    private val playbackSpeedState = MutableStateFlow(1.0f)
    private val rewindIntervalSecState = MutableStateFlow(5)
    private val forwardIntervalSecState = MutableStateFlow(15)
    private val sleepTimerTextState = MutableStateFlow<String?>(null)
    private val hasPreviousState = MutableStateFlow(false)
    private val hasNextState = MutableStateFlow(false)

    private var sleepTimer: CountDownTimer? = null
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    private val BASE_URL = "https://audiocinemateca.com/"

    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            updatePlaybackProgress()
            timeUpdateHandler.postDelayed(this, 500)
        }
    }

    private val playerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON) {
                val isPlaying = intent.getBooleanExtra(MainActivity.EXTRA_IS_PLAYING, false)
                isPlayingState.value = isPlaying
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        currentContentItem = args.catalogItem
        currentPartIndex = args.partIndex
        currentEpisodeIndex = args.episodeIndex

        currentContentItem?.let { viewModel.setContentItem(it, currentPartIndex, currentEpisodeIndex) }

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        val filter = IntentFilter(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(playerStateReceiver, filter)

        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "rewind_interval" || key == "forward_interval") {
                reloadIntervalSettings()
            }
        }
        reloadIntervalSettings()

        initializeMediaController()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    val isPlaying by isPlayingState.collectAsState()
                    val currentPos by currentPositionState.collectAsState()
                    val totalDur by totalDurationState.collectAsState()
                    val speed by playbackSpeedState.collectAsState()
                    val rewindSec by rewindIntervalSecState.collectAsState()
                    val forwardSec by forwardIntervalSecState.collectAsState()
                    val timerText by sleepTimerTextState.collectAsState()
                    val hasPrev by hasPreviousState.collectAsState()
                    val hasNext by hasNextState.collectAsState()

                    val titleText = currentContentItem?.title ?: "Reproduciendo"
                    val subtitleText = viewModel.getDetailedTitle()

                    PlayerScreen(
                        title = titleText,
                        subtitle = if (subtitleText != titleText) subtitleText else "",
                        isPlaying = isPlaying,
                        currentPositionMs = currentPos,
                        totalDurationMs = totalDur,
                        playbackSpeed = speed,
                        rewindIntervalSec = rewindSec,
                        forwardIntervalSec = forwardSec,
                        sleepTimerText = timerText,
                        isAutoPlayEnabled = sharedPreferencesManager.getBoolean("autoplay", true),
                        hasPrevious = hasPrev,
                        hasNext = hasNext,
                        viewModel = viewModel,
                        onBackClick = { findNavController().popBackStack() },
                        onPlayPauseClick = { togglePlayPause() },
                        onRewindClick = { seekRelative(-rewindSec * 1000L) },
                        onForwardClick = { seekRelative(forwardSec * 1000L) },
                        onPreviousClick = { handlePrevious() },
                        onNextClick = { handleNext() },
                        onSeekTo = { pos: Long -> mediaController?.seekTo(pos) },
                        onPlaybackSpeedChange = { s: Float -> setPlaybackSpeed(s) },
                        onRewindIntervalChange = { sec: Int ->
                            sharedPreferencesManager.saveString("rewind_interval", sec.toString())
                            reloadIntervalSettings()
                        },
                        onForwardIntervalChange = { sec: Int ->
                            sharedPreferencesManager.saveString("forward_interval", sec.toString())
                            reloadIntervalSettings()
                        },
                        onSleepTimerSelect = { min: Long -> startSleepTimer(min * 60 * 1000L) },
                        onCancelSleepTimer = { cancelSleepTimer(true) },
                        onAutoPlayToggle = {
                            val cur = sharedPreferencesManager.getBoolean("autoplay", true)
                            sharedPreferencesManager.saveBoolean("autoplay", !cur)
                        },
                        onEqualizerClick = {
                            EqualizerDialogFragment().show(childFragmentManager, "EqualizerDialogFragment")
                        },
                        onOpenCommentsClick = { showKeyboard: Boolean ->
                            CommentsBottomSheetFragment.newInstance(showKeyboard).show(childFragmentManager, "CommentsBottomSheet")
                        },
                        onShareClick = {
                            currentContentItem?.let { item ->
                                ShareUtils.shareContent(requireContext(), item, geminiRepository)
                            }
                        },
                        onTitleClick = { navigateToDetail() },
                        onNavigateToPlaybackSettings = {
                            val action = MainNavGraphDirections.actionGlobalCategorySettingsFragment("playback")
                            findNavController().navigate(action)
                        },
                        onAnnounceStatus = { text ->
                            ttsManager.speak(text, interrupt = true)
                        }
                    )
                }
            }
        }
    }

    private fun reloadIntervalSettings() {
        rewindIntervalSecState.value = sharedPreferencesManager.getString("rewind_interval", "5")?.toIntOrNull() ?: 5
        forwardIntervalSecState.value = sharedPreferencesManager.getString("forward_interval", "15")?.toIntOrNull() ?: 15
    }

    private fun navigateToDetail() {
        currentContentItem?.let { item ->
            val itemType = when (item) {
                is Movie -> "peliculas"
                is Serie -> "series"
                is Documentary -> "documentales"
                is ShortFilm -> "cortometrajes"
                else -> "unknown"
            }
            val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId = item.id, itemType = itemType)
            findNavController().navigate(action)
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(MainActivity.ACTION_HIDE_MINI_PLAYER)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        val requestIntent = Intent(MainActivity.ACTION_REQUEST_PLAYBACK_STATE)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(requestIntent)
        setupInitialTimerState()
        sharedPreferencesManager.getPrefs().registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferencesManager.getPrefs().unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onStop() {
        super.onStop()
        val saveProgressIntent = Intent(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(saveProgressIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelSleepTimer(updatePreference = false)

        val playbackState = mediaController?.playbackState
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
            currentContentItem?.let {
                val contentType = when (it) {
                    is Movie -> "peliculas"
                    is Serie -> "series"
                    is Documentary -> "documentales"
                    is ShortFilm -> "cortometrajes"
                    else -> "unknown"
                }
                val intent = Intent(MainActivity.ACTION_SHOW_MINI_PLAYER).apply {
                    val metadata = mediaController?.currentMediaItem?.mediaMetadata
                    putExtra(MainActivity.EXTRA_TITLE, metadata?.title?.toString())
                    putExtra(MainActivity.EXTRA_SUBTITLE, metadata?.artist?.toString())
                    putExtra(MainActivity.EXTRA_IS_PLAYING, mediaController?.isPlaying == true)
                    putExtra(MainActivity.EXTRA_ITEM_ID, it.id)
                    putExtra(MainActivity.EXTRA_ITEM_TYPE, contentType)
                    putExtra(MainActivity.EXTRA_PART_INDEX, currentPartIndex)
                    putExtra(MainActivity.EXTRA_EPISODE_INDEX, currentEpisodeIndex)
                }
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            }
        }

        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playerStateReceiver)
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    private fun updatePlaybackProgress() {
        val controller = mediaController ?: return
        isPlayingState.value = controller.isPlaying
        currentPositionState.value = controller.currentPosition.coerceAtLeast(0L)
        totalDurationState.value = controller.duration.coerceAtLeast(0L)
        playbackSpeedState.value = controller.playbackParameters.speed

        hasPreviousState.value = controller.hasPreviousMediaItem()
        hasNextState.value = controller.hasNextMediaItem()
    }

    private fun togglePlayPause() {
        val controller = mediaController ?: return
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

    private fun seekRelative(offsetMs: Long) {
        val controller = mediaController ?: return
        val currentPos = controller.currentPosition
        val duration = controller.duration
        val targetPos = (currentPos + offsetMs).coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)
        controller.seekTo(targetPos)
        updatePlaybackProgress()
    }

    private fun setPlaybackSpeed(speed: Float) {
        mediaController?.playbackParameters = PlaybackParameters(speed)
        playbackSpeedState.value = speed
    }

    private fun setupInitialTimerState() {
        if (sharedPreferencesManager.getBoolean("sleep_timer_enabled", false)) {
            val durationMinutes = sharedPreferencesManager.getString("sleep_timer_duration", "60")?.toLongOrNull() ?: 60L
            startSleepTimer(durationMinutes * 60 * 1000L)
        } else {
            cancelSleepTimer(updatePreference = false)
        }
    }

    private fun startSleepTimer(durationInMillis: Long) {
        cancelSleepTimer(updatePreference = false)
        sleepTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                sleepTimerTextState.value = String.format("Temporizador: %02d:%02d", minutes, seconds)
            }
            override fun onFinish() {
                mediaController?.pause()
                sleepTimerTextState.value = null
                if (isAdded) showStillListeningDialog()
            }
        }.start()
    }

    private fun cancelSleepTimer(updatePreference: Boolean) {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerTextState.value = null
        if (updatePreference) sharedPreferencesManager.saveBoolean("sleep_timer_enabled", false)
    }

    private fun showStillListeningDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Oye, ¿sigues ahí?")
            .setMessage("Pausaremos la reproducción por ti para que no te pierdas en lo que estás escuchando y no te siga consumiendo batería.")
            .setPositiveButton("Sí, aún estoy aquí") { dialog, _ ->
                mediaController?.play()
                cancelSleepTimer(updatePreference = true)
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                cancelSleepTimer(updatePreference = true)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun initializeMediaController() {
        val serviceIntent = Intent(requireContext(), PlayerService::class.java)
        try {
            androidx.core.content.ContextCompat.startForegroundService(requireContext(), serviceIntent)
        } catch (e: Exception) {
            Log.e("PlayerFragment", "Error al iniciar PlayerService: ${e.message}")
        }

        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlayerService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(createPlayerListener())
            prepareAndPlay()
            timeUpdateHandler.post(timeUpdateRunnable)
        }, MoreExecutors.directExecutor())
    }

    private suspend fun isContentDownloaded(catalogItem: CatalogItem, partIndex: Int, episodeIndex: Int): Boolean {
        val d = when (catalogItem) {
            is Movie -> downloadRepository.getDownload(catalogItem.id, partIndex.coerceAtLeast(0), -1)
            is Serie -> downloadRepository.getDownload(catalogItem.id, partIndex.coerceAtLeast(0), episodeIndex.coerceAtLeast(0))
            is Documentary -> downloadRepository.getDownload(catalogItem.id, 0, -1)
            is ShortFilm -> downloadRepository.getDownload(catalogItem.id, 0, -1)
            else -> null
        }
        return d != null && d.downloadStatus == "COMPLETE" && !d.filePath.isNullOrBlank()
    }

    private fun prepareAndPlay() {
        val catalogItem = currentContentItem ?: return
        val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)

        viewLifecycleOwner.lifecycleScope.launch {
            if (isOfflineMode) {
                val isDownloaded = isContentDownloaded(catalogItem, currentPartIndex, currentEpisodeIndex)
                if (!isDownloaded) {
                    if (isAdded) {
                        AlertDialog.Builder(requireContext())
                            .setTitle("Modo sin conexión activado")
                            .setMessage("No es posible reproducir este contenido porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes.")
                            .setPositiveButton("Aceptar") { dialog, _ ->
                                dialog.dismiss()
                                findNavController().popBackStack()
                            }
                            .setCancelable(false)
                            .show()
                    }
                    return@launch
                }
            }

            val activeItemId = mediaController?.currentMediaItem?.mediaMetadata?.extras?.getString("itemId")
            if (activeItemId == catalogItem.id) {
                return@launch
            }

            val mediaItems = createMediaItems(catalogItem)
            if (mediaItems.isEmpty()) return@launch
            var startIndex = 0
            if (catalogItem is Movie || catalogItem is Documentary || catalogItem is ShortFilm) {
                startIndex = currentPartIndex.coerceAtLeast(0).coerceAtMost(mediaItems.size - 1)
            } else if (catalogItem is Serie) {
                for ((index, mediaItem) in mediaItems.withIndex()) {
                    val extras = mediaItem.mediaMetadata.extras
                    if (extras?.getInt("partIndex", -1) == currentPartIndex && extras?.getInt("episodeIndex", -1) == currentEpisodeIndex) {
                        startIndex = index; break
                    }
                }
            }
            val savedProgress = playbackProgressRepository.getPlaybackProgress(catalogItem.id, currentPartIndex, currentEpisodeIndex)
            
            val startPosition = if (savedProgress != null) {
                if (savedProgress.currentPositionMs >= (savedProgress.totalDurationMs - 2000)) 0L 
                else savedProgress.currentPositionMs
            } else 0L

            mediaController?.setMediaItems(mediaItems, startIndex, startPosition)
            mediaController?.prepare()
            mediaController?.playWhenReady = true
        }
    }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingState.value = isPlaying
            val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying) }
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(playPauseIntent)
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                currentContentItem?.let { viewModel.trackViewStart(it.id) }
            }
            val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, mediaController?.isPlaying == true) }
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(playPauseIntent)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)
            val uriStr = mediaItem?.localConfiguration?.uri?.toString() ?: ""
            if (isOfflineMode && (uriStr.startsWith("http://", ignoreCase = true) || uriStr.startsWith("https://", ignoreCase = true))) {
                mediaController?.pause()
                if (isAdded) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Modo sin conexión activado")
                        .setMessage("No es posible reproducir este episodio porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes.")
                        .setPositiveButton("Aceptar") { dialog, _ ->
                            dialog.dismiss()
                            findNavController().popBackStack()
                        }
                        .setCancelable(false)
                        .show()
                }
                return
            }

            val extras = mediaItem?.mediaMetadata?.extras
            currentPartIndex = extras?.getInt("partIndex", -1) ?: -1
            currentEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1
            viewModel.updateCurrentEpisode(currentPartIndex, currentEpisodeIndex)
            
            lifecycleScope.launch {
                val contentId = currentContentItem?.id ?: return@launch
                val savedProgress = playbackProgressRepository.getPlaybackProgress(contentId, currentPartIndex, currentEpisodeIndex)
                if (savedProgress != null && !savedProgress.isFinished) {
                    val pos = savedProgress.currentPositionMs
                    val dur = savedProgress.totalDurationMs
                    if (pos > 0 && (dur <= 0 || pos < dur - 15000)) {
                        delay(100)
                        mediaController?.seekTo(pos)
                    }
                }
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val isOfflineMode = sharedPreferencesManager.getBoolean("offline_mode", false)
            if (isOfflineMode && isAdded) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Modo sin conexión activado")
                    .setMessage("No es posible reproducir este contenido sin conexión porque no está descargado en tu dispositivo.")
                    .setPositiveButton("Aceptar") { dialog, _ ->
                        dialog.dismiss()
                        findNavController().popBackStack()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun handlePrevious() {
        val extras = mediaController?.currentMediaItem?.mediaMetadata?.extras
        val intent = Intent(MainActivity.ACTION_SEEK_TO_PREVIOUS).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
            putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
            putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1))
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1))
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, mediaController?.currentPosition ?: 0L)
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
        mediaController?.playWhenReady = true
        mediaController?.play()
    }

    private fun handleNext() {
        val extras = mediaController?.currentMediaItem?.mediaMetadata?.extras
        val intent = Intent(MainActivity.ACTION_SEEK_TO_NEXT).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, extras?.getString("itemId"))
            putExtra(MainActivity.EXTRA_ITEM_TYPE, extras?.getString("itemType"))
            putExtra(MainActivity.EXTRA_PART_INDEX, extras?.getInt("partIndex", -1))
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, extras?.getInt("episodeIndex", -1))
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, mediaController?.currentPosition ?: 0L)
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
        mediaController?.playWhenReady = true
        mediaController?.play()
    }

    private suspend fun createMediaItems(catalogItem: CatalogItem): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()
        val typeStr = when (catalogItem) {
            is Movie -> "peliculas"
            is Serie -> "series"
            is Documentary -> "documentales"
            is ShortFilm -> "cortometrajes"
            else -> "unknown"
        }

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

                    val metadata = androidx.media3.common.MediaMetadata.Builder()
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
                val metadata = androidx.media3.common.MediaMetadata.Builder()
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

            val metadata = androidx.media3.common.MediaMetadata.Builder()
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
}
