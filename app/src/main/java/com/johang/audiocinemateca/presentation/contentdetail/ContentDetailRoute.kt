package com.johang.audiocinemateca.presentation.contentdetail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.domain.DownloadRequest
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.download.DownloadService
import com.johang.audiocinemateca.util.ShareUtils
import com.johang.audiocinemateca.util.TtsManager
import kotlinx.coroutines.launch

@Composable
fun ContentDetailRoute(
    itemId: String,
    itemType: String,
    playbackProgressRepository: PlaybackProgressRepository,
    downloadManager: DownloadManager,
    downloadRepository: DownloadRepository,
    ttsManager: TtsManager,
    geminiRepository: GeminiRepository,
    sharedPreferencesManager: SharedPreferencesManager,
    onBackClick: () -> Unit,
    onNavigateToPlayer: (CatalogItem, Int, Int) -> Unit,
    viewModel: ContentDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val contentItem by viewModel.contentItem.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val ratingStats by viewModel.ratingStats.collectAsState()
    val viewCountText by viewModel.viewCountText.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val singleProgress by viewModel.singleDownloadProgress.collectAsState()
    val seasonProgress by viewModel.seasonProgressState.collectAsState()
    val episodeStates by viewModel.episodeDownloadStates.collectAsState()
    val selectedSeasonIndex by viewModel.selectedSeasonIndex.collectAsState()

    var playbackProgressList by remember { mutableStateOf<List<PlaybackProgressEntity>>(emptyList()) }
    var isTtsSpeaking by remember { mutableStateOf(false) }

    var pendingActionDialog by remember { mutableStateOf<ViewAction?>(null) }
    var showOfflineWarningDialog by remember { mutableStateOf(false) }

    fun refreshPlaybackProgress() {
        scope.launch {
            playbackProgressList = playbackProgressRepository.getPlaybackProgressForContent(itemId)
        }
    }

    LaunchedEffect(itemId, itemType) {
        viewModel.loadContentDetail(itemId, itemType)
        refreshPlaybackProgress()
    }

    LaunchedEffect(viewModel) {
        viewModel.viewActions.collect { event ->
            event.getContentIfNotHandled()?.let { action ->
                when (action) {
                    is ViewAction.StartDownload -> {
                        val request = DownloadRequest(
                            url = action.url,
                            title = action.title,
                            contentId = action.contentId,
                            contentType = action.contentType,
                            partIndex = action.partIndex,
                            episodeIndex = action.episodeIndex,
                            seriesTitle = action.seriesTitle
                        )
                        downloadManager.enqueueDownload(request)
                        Toast.makeText(context, "Añadido a la cola de descargas.", Toast.LENGTH_SHORT).show()
                    }
                    is ViewAction.ShowCancelConfirmation,
                    is ViewAction.ShowDeleteConfirmation,
                    is ViewAction.ShowDeleteSeasonConfirmation,
                    is ViewAction.ShowCancelSeasonConfirmation,
                    is ViewAction.ShowDownloadFailed,
                    is ViewAction.ShowError -> {
                        pendingActionDialog = action
                    }
                    is ViewAction.ShowMessage -> {
                        Toast.makeText(context, action.message, Toast.LENGTH_SHORT).show()
                    }
                    is ViewAction.ShowSeasonDownloadStarted -> {
                        Toast.makeText(
                            context,
                            "Iniciando descarga de ${action.count} episodios de la Temporada ${action.seasonNumber}...",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ViewAction.ShowSeasonAlreadyDownloaded -> {
                        Toast.makeText(
                            context,
                            "Todos los episodios de la Temporada ${action.seasonNumber} ya están descargados.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is ViewAction.StopDownloadService -> {
                        context.stopService(Intent(context, DownloadService::class.java))
                    }
                    is ViewAction.UpdateFavoriteIcon -> {}
                }
            }
        }
    }

    DisposableEffect(itemId, lifecycleOwner) {
        val playbackUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA) {
                    if (intent.getStringExtra(MainActivity.EXTRA_ITEM_ID) == itemId) {
                        refreshPlaybackProgress()
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            playbackUpdateReceiver,
            IntentFilter(MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA)
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> refreshPlaybackProgress()
                Lifecycle.Event.ON_PAUSE -> {
                    if (isTtsSpeaking) {
                        ttsManager.stop()
                        isTtsSpeaking = false
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            if (isTtsSpeaking) {
                ttsManager.stop()
                isTtsSpeaking = false
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            LocalBroadcastManager.getInstance(context).unregisterReceiver(playbackUpdateReceiver)
        }
    }

    ContentDetailScreen(
        item = contentItem,
        isLoading = isLoading,
        isFavorite = isFavorite,
        ratingStats = ratingStats,
        viewCountText = viewCountText,
        downloadState = downloadState,
        singleProgress = singleProgress,
        seasonProgress = seasonProgress,
        episodeStates = episodeStates,
        selectedSeasonIndex = selectedSeasonIndex,
        playbackProgressList = playbackProgressList,
        isTtsSpeaking = isTtsSpeaking,
        onBackClick = onBackClick,
        onFavoriteToggle = { viewModel.toggleFavorite() },
        onShareClick = {
            contentItem?.let { item ->
                ShareUtils.shareContent(context, item, geminiRepository)
            }
        },
        onPlayClick = { partIndex, episodeIndex ->
            val item = contentItem ?: return@ContentDetailScreen
            val isOfflineMode = sharedPreferencesManager.getBoolean("pref_offline_mode", false)
            if (isOfflineMode) {
                scope.launch {
                    val d = downloadRepository.getDownload(item.id, partIndex, if (item is Serie) episodeIndex else -1)
                    val isDownloaded = d != null && d.downloadStatus == "COMPLETE" && !d.filePath.isNullOrBlank()
                    if (!isDownloaded) {
                        showOfflineWarningDialog = true
                    } else {
                        onNavigateToPlayer(item, partIndex, episodeIndex)
                    }
                }
            } else {
                onNavigateToPlayer(item, partIndex, episodeIndex)
            }
        },
        onDownloadClick = { viewModel.onDownloadAction() },
        onSeasonSelect = { index -> viewModel.setSelectedSeasonIndex(index) },
        onSeasonDownloadClick = { index -> viewModel.onSeasonDownloadAction(index) },
        onEpisodeDownloadClick = { seasonIndex, episodeIndex ->
            viewModel.onEpisodeDownloadAction(seasonIndex, episodeIndex)
        },
        onOpenFilmAffinity = { url ->
            if (url.isNotBlank()) {
                val fullUrl = if (!url.startsWith("http")) "https://$url" else url
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                } catch (_: Exception) {
                    Toast.makeText(context, "Error al abrir el enlace", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onTtsToggle = {
            val sinopsis = contentItem?.sinopsis ?: ""
            if (isTtsSpeaking) {
                ttsManager.stop()
                isTtsSpeaking = false
            } else if (sinopsis.isNotBlank()) {
                isTtsSpeaking = true
                ttsManager.speak(sinopsis, interrupt = true, utteranceId = "SINOPSIS_DETAIL_TTS")
                ttsManager.onSpeechFinished = { id ->
                    if (id == "SINOPSIS_DETAIL_TTS") {
                        isTtsSpeaking = false
                    }
                }
            } else {
                Toast.makeText(context, "No hay sinopsis disponible para leer", Toast.LENGTH_SHORT).show()
            }
        },
        onSubmitRating = { stars -> viewModel.submitRating(stars) }
    )

    if (showOfflineWarningDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineWarningDialog = false },
            title = { Text("Modo sin conexión activado") },
            text = {
                Text("No es posible reproducir este contenido porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes.")
            },
            confirmButton = {
                TextButton(onClick = { showOfflineWarningDialog = false }) {
                    Text("Aceptar")
                }
            }
        )
    }

    when (val action = pendingActionDialog) {
        is ViewAction.ShowCancelConfirmation -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Cancelar descarga") },
                text = { Text("¿Estás seguro de que deseas cancelar la descarga?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelDownload(action.partIndex, action.episodeIndex)
                            pendingActionDialog = null
                        }
                    ) {
                        Text("Sí, cancelar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("No")
                    }
                }
            )
        }
        is ViewAction.ShowDeleteConfirmation -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Eliminar descarga") },
                text = { Text("¿Estás seguro de que deseas eliminar este contenido de tus descargas?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteDownload(action.partIndex, action.episodeIndex)
                            pendingActionDialog = null
                        }
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
        is ViewAction.ShowDeleteSeasonConfirmation -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Eliminar temporada ${action.seasonName}") },
                text = { Text("¿Deseas eliminar todos los capítulos descargados de la Temporada ${action.seasonName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteSeasonDownloads(action.seasonIndex)
                            pendingActionDialog = null
                        }
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }
        is ViewAction.ShowCancelSeasonConfirmation -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Cancelar descargas de temporada ${action.seasonName}") },
                text = { Text("¿Deseas detener y cancelar las descargas en curso de la Temporada ${action.seasonName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelSeasonDownloads(action.seasonIndex)
                            pendingActionDialog = null
                        }
                    ) {
                        Text("Detener")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("Continuar")
                    }
                }
            )
        }
        is ViewAction.ShowDownloadFailed -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Error en la descarga") },
                text = { Text(action.reason) },
                confirmButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        is ViewAction.ShowError -> {
            AlertDialog(
                onDismissRequest = { pendingActionDialog = null },
                title = { Text("Error") },
                text = { Text(action.message) },
                confirmButton = {
                    TextButton(onClick = { pendingActionDialog = null }) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> {}
    }
}
