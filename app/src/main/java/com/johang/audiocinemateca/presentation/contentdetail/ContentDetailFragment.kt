package com.johang.audiocinemateca.presentation.contentdetail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.domain.DownloadRequest
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.util.ShareUtils
import com.johang.audiocinemateca.util.TtsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ContentDetailFragment : Fragment() {

    private val viewModel: ContentDetailViewModel by viewModels()
    private val args: ContentDetailFragmentArgs by navArgs()

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var ttsManager: TtsManager

    @Inject
    lateinit var geminiRepository: GeminiRepository

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private val playbackProgressListState = MutableStateFlow<List<PlaybackProgressEntity>>(emptyList())
    private val isTtsSpeakingState = mutableStateOf(false)

    private val playbackUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA) {
                val itemId = intent.getStringExtra(MainActivity.EXTRA_ITEM_ID)
                if (itemId == args.itemId) {
                    refreshPlaybackProgress()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel.loadContentDetail(args.itemId, args.itemType)
        refreshPlaybackProgress()
        observeViewActions()

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            playbackUpdateReceiver,
            IntentFilter(MainActivity.ACTION_UPDATE_MINI_PLAYER_METADATA)
        )

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
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
                    val playbackProgressList by playbackProgressListState.collectAsState()
                    val isTtsSpeaking by isTtsSpeakingState

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
                        onBackClick = { findNavController().navigateUp() },
                        onFavoriteToggle = { viewModel.toggleFavorite() },
                        onShareClick = {
                            contentItem?.let { item ->
                                ShareUtils.shareContent(requireContext(), item, geminiRepository)
                            }
                        },
                        onPlayClick = { partIndex, episodeIndex ->
                            contentItem?.let { item ->
                                playOrShowOfflineWarning(item, partIndex, episodeIndex)
                            }
                        },
                        onDownloadClick = { viewModel.onDownloadAction() },
                        onSeasonSelect = { index -> viewModel.setSelectedSeasonIndex(index) },
                        onSeasonDownloadClick = { index -> viewModel.onSeasonDownloadAction(index) },
                        onEpisodeDownloadClick = { seasonIndex, episodeIndex ->
                            viewModel.onEpisodeDownloadAction(seasonIndex, episodeIndex)
                        },
                        onOpenFilmAffinity = { url -> openFilmaffinity(url) },
                        onTtsToggle = { toggleTts(contentItem?.sinopsis ?: "") },
                        onSubmitRating = { stars -> viewModel.submitRating(stars) }
                    )
                }
            }
        }
    }

    private fun refreshPlaybackProgress() {
        lifecycleScope.launch {
            val progress = playbackProgressRepository.getPlaybackProgressForContent(args.itemId)
            playbackProgressListState.value = progress
        }
    }

    private fun playOrShowOfflineWarning(item: CatalogItem, partIndex: Int, episodeIndex: Int) {
        val isOfflineMode = sharedPreferencesManager.getBoolean("pref_offline_mode", false)
        if (isOfflineMode) {
            lifecycleScope.launch {
                val d = downloadRepository.getDownload(item.id, partIndex, if (item is Serie) episodeIndex else -1)
                val isDownloaded = d != null && d.downloadStatus == "COMPLETE" && !d.filePath.isNullOrBlank()
                if (!isDownloaded) {
                    if (isAdded) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Modo sin conexión activado")
                            .setMessage("No es posible reproducir este contenido porque el modo offline total está activado y este título no ha sido descargado en tu dispositivo.\n\nPara escucharlo, descárgalo previamente o desactiva el modo offline en los ajustes.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                } else {
                    val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, partIndex, episodeIndex)
                    findNavController().navigate(action)
                }
            }
        } else {
            val action = ContentDetailFragmentDirections.actionContentDetailFragmentToPlayerFragment(item, partIndex, episodeIndex)
            findNavController().navigate(action)
        }
    }

    private fun toggleTts(sinopsis: String) {
        if (isTtsSpeakingState.value) {
            ttsManager.stop()
            isTtsSpeakingState.value = false
        } else {
            if (sinopsis.isNotBlank()) {
                isTtsSpeakingState.value = true
                ttsManager.speak(sinopsis, interrupt = true, utteranceId = "SINOPSIS_DETAIL_TTS")
                ttsManager.onSpeechFinished = { id ->
                    if (id == "SINOPSIS_DETAIL_TTS" && isAdded) {
                        isTtsSpeakingState.value = false
                    }
                }
            } else {
                Toast.makeText(requireContext(), "No hay sinopsis disponible para leer", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilmaffinity(url: String) {
        if (url.isBlank()) return
        val fullUrl = if (!url.startsWith("http")) "https://$url" else url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al abrir el enlace", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeViewActions() {
        lifecycleScope.launch {
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
                            Toast.makeText(requireContext(), "Añadido a la cola de descargas.", Toast.LENGTH_SHORT).show()
                        }
                        is ViewAction.ShowCancelConfirmation -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Cancelar descarga")
                                .setMessage("¿Estás seguro de que deseas cancelar la descarga?")
                                .setNegativeButton("No", null)
                                .setPositiveButton("Sí, cancelar") { _, _ -> viewModel.cancelDownload(action.partIndex, action.episodeIndex) }
                                .show()
                        }
                        is ViewAction.ShowDeleteConfirmation -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Eliminar descarga")
                                .setMessage("¿Estás seguro de que deseas eliminar este contenido de tus descargas?")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteDownload(action.partIndex, action.episodeIndex) }
                                .show()
                        }
                        is ViewAction.ShowDeleteSeasonConfirmation -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Eliminar temporada ${action.seasonName}")
                                .setMessage("¿Deseas eliminar todos los capítulos descargados de la Temporada ${action.seasonName}?")
                                .setNegativeButton("Cancelar", null)
                                .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteSeasonDownloads(action.seasonIndex) }
                                .show()
                        }
                        is ViewAction.ShowCancelSeasonConfirmation -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Cancelar descargas de temporada ${action.seasonName}")
                                .setMessage("¿Deseas detener y cancelar las descargas en curso de la Temporada ${action.seasonName}?")
                                .setNegativeButton("Continuar", null)
                                .setPositiveButton("Detener") { _, _ -> viewModel.cancelSeasonDownloads(action.seasonIndex) }
                                .show()
                        }
                        is ViewAction.ShowDownloadFailed -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error en la descarga")
                                .setMessage(action.reason)
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        is ViewAction.ShowError -> {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error")
                                .setMessage(action.message)
                                .setPositiveButton("Aceptar", null)
                                .show()
                        }
                        is ViewAction.ShowMessage -> {
                            Toast.makeText(requireContext(), action.message, Toast.LENGTH_SHORT).show()
                        }
                        is ViewAction.ShowSeasonDownloadStarted -> {
                            Toast.makeText(requireContext(), "Iniciando descarga de ${action.count} episodios de la Temporada ${action.seasonNumber}...", Toast.LENGTH_LONG).show()
                        }
                        is ViewAction.ShowSeasonAlreadyDownloaded -> {
                            Toast.makeText(requireContext(), "Todos los episodios de la Temporada ${action.seasonNumber} ya están descargados.", Toast.LENGTH_SHORT).show()
                        }
                        is ViewAction.StopDownloadService -> {
                            val intent = Intent(requireContext(), com.johang.audiocinemateca.presentation.download.DownloadService::class.java)
                            requireContext().stopService(intent)
                        }
                        is ViewAction.UpdateFavoriteIcon -> {
                            // Reactively handled by viewModel.isFavorite
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPlaybackProgress()
    }

    override fun onPause() {
        super.onPause()
        if (isTtsSpeakingState.value) {
            ttsManager.stop()
            isTtsSpeakingState.value = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isTtsSpeakingState.value) {
            ttsManager.stop()
            isTtsSpeakingState.value = false
        }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playbackUpdateReceiver)
    }
}
