package com.johang.audiocinemateca.presentation.player

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.databinding.FragmentPlayerBinding
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.C
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.util.TimeFormatUtils
import com.johang.audiocinemateca.MainNavGraphDirections

@AndroidEntryPoint
class PlayerFragment : Fragment() {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlayerViewModel by viewModels()
    private val args: PlayerFragmentArgs by navArgs()

    

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    private var mediaController: MediaController? = null
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    private var currentContentItem: CatalogItem? = null
    private var currentPartIndex: Int = -1
    private var currentEpisodeIndex: Int = -1
    private var autoAdvanceTriggeredForCurrentItem = false

    private lateinit var customPrevMediaButton: ImageButton
    private lateinit var customNextMediaButton: ImageButton

    private val BASE_URL = "https://audiocinemateca.com/"

    private val timeUpdateHandler = Handler(Looper.getMainLooper())
    private val timeUpdateRunnable = object : Runnable {
        override fun run() {
            updateTimestamps()
            checkAutoAdvanceAndNotify()
            timeUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val playerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON -> {
                    val isPlaying = intent.getBooleanExtra(MainActivity.EXTRA_IS_PLAYING, false)
                    // Update the play/pause button in the custom controls
                    binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.apply {
                        if (isPlaying) {
                            // Comentario añadido por Gemini para forzar un cambio en el archivo.
                            setImageDrawable(ContextCompat.getDrawable(requireContext(), androidx.media3.ui.R.drawable.exo_ic_pause_circle_filled))
                        } else {
                            setImageDrawable(ContextCompat.getDrawable(requireContext(), androidx.media3.ui.R.drawable.exo_ic_play_circle_filled))
                        }
                    }
                    Log.d("PlayerFragment", "playerStateReceiver: Received ACTION_UPDATE_PLAY_PAUSE_BUTTON. isPlaying = $isPlaying")
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentContentItem = args.catalogItem
        currentPartIndex = args.partIndex
        currentEpisodeIndex = args.episodeIndex

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        setupToolbar()
        setupCustomControlListeners()

        val filter = IntentFilter(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(playerStateReceiver, filter)

        // Initialize MediaController once the view is created
        initializeMediaController()
    }

    override fun onStart() {
        super.onStart()
        // Notify MainActivity that PlayerFragment is active to hide the mini-player
        val intent = Intent(MainActivity.ACTION_HIDE_MINI_PLAYER)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    override fun onResume() {
        super.onResume()
        // Request current playback state from PlayerService to ensure UI is synchronized
        val requestIntent = Intent(MainActivity.ACTION_REQUEST_PLAYBACK_STATE)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(requestIntent)
        Log.d("PlayerFragment", "onResume: Sent ACTION_REQUEST_PLAYBACK_STATE to PlayerService.")
        updateNavigationButtonsState()
    }

    override fun onStop() {
        super.onStop()
        // The controller is no longer released here.
        // We only save the progress when the fragment is stopped.
        val saveProgressIntent = Intent(MainActivity.ACTION_SAVE_PLAYBACK_PROGRESS)
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(saveProgressIntent)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Show the mini-player only if the user is navigating away and something is playing or paused.
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
                    putExtra(MainActivity.EXTRA_TITLE, metadata?.albumTitle?.toString() ?: metadata?.title?.toString())
                    putExtra(MainActivity.EXTRA_SUBTITLE, metadata?.displayTitle?.toString())
                    val isPlayingState = mediaController?.isPlaying == true
                    putExtra(MainActivity.EXTRA_IS_PLAYING, isPlayingState)
                    Log.d("PlayerFragment", "onDestroyView: Sending EXTRA_IS_PLAYING = $isPlayingState")
                    putExtra(MainActivity.EXTRA_ITEM_ID, it.id)
                    putExtra(MainActivity.EXTRA_ITEM_TYPE, contentType)
                    putExtra(MainActivity.EXTRA_PART_INDEX, currentPartIndex)
                    putExtra(MainActivity.EXTRA_EPISODE_INDEX, currentEpisodeIndex)
                }
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            }
        }

        // Release the controller and clean up resources only when the view is destroyed
        binding.exoplayerView.player = null
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        timeUpdateHandler.removeCallbacks(timeUpdateRunnable)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(playerStateReceiver)
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        _binding = null
    }

    private fun updateTimestamps() {
        val currentPosition = mediaController?.currentPosition ?: 0L
        val duration = mediaController?.duration ?: 0L

        binding.exoplayerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.text = TimeFormatUtils.formatDuration(currentPosition)
        binding.exoplayerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration)?.text = TimeFormatUtils.formatDuration(duration)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
        binding.toolbar.navigationContentDescription = getString(com.johang.audiocinemateca.R.string.close_button_description)
        binding.toolbar.inflateMenu(com.johang.audiocinemateca.R.menu.player_toolbar_menu)

        // Add click listener to the toolbar title
        binding.toolbar.setOnClickListener { 
            currentContentItem?.let { item ->
                val previousBackStackEntry = findNavController().previousBackStackEntry
                val previousDestinationId = previousBackStackEntry?.destination?.id
                val previousItemId = previousBackStackEntry?.arguments?.getString("itemId")

                // Check if the previous screen was the detail screen for the *same item*
                if (previousDestinationId == com.johang.audiocinemateca.R.id.contentDetailFragment && previousItemId == item.id) {
                    // If so, just pop back to it
                    findNavController().popBackStack()
                } else {
                    // Otherwise, pop the player and navigate to the correct detail screen
                    val itemType = when (item) {
                        is Movie -> "peliculas"
                        is Serie -> "series"
                        is Documentary -> "documentales"
                        is ShortFilm -> "cortometrajes"
                        else -> "unknown"
                    }
                    // Pop back from player fragment first
                    findNavController().popBackStack()
                    // Then navigate to the content detail fragment
                    val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId = item.id, itemType = itemType)
                    findNavController().navigate(action)
                }
            }
        }

        val autoplaySwitch = binding.toolbar.menu.findItem(com.johang.audiocinemateca.R.id.action_autoplay).actionView?.findViewById<androidx.appcompat.widget.SwitchCompat>(com.johang.audiocinemateca.R.id.autoplay_switch)
        autoplaySwitch?.isChecked = sharedPreferencesManager.getBoolean("autoplay_enabled", true)

        autoplaySwitch?.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferencesManager.saveBoolean("autoplay_enabled", isChecked)
        }
    }

    private fun setupCustomControlListeners() {
        customPrevMediaButton = binding.exoplayerView.findViewById(com.johang.audiocinemateca.R.id.custom_prev_media)!!
        customNextMediaButton = binding.exoplayerView.findViewById(com.johang.audiocinemateca.R.id.custom_next_media)!!

        customPrevMediaButton.setOnClickListener { handlePrevious() }
        customNextMediaButton.setOnClickListener { handleNext() }
    }

    private fun initializeMediaController() {
        val serviceIntent = Intent(requireContext(), PlayerService::class.java)
        requireContext().startService(serviceIntent)

        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), PlayerService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
                binding.exoplayerView.player = mediaController
                binding.exoplayerView.controllerAutoShow = false
                binding.exoplayerView.controllerHideOnTouch = false
                binding.exoplayerView.controllerShowTimeoutMs = 0
                binding.exoplayerView.showController()
                mediaController?.addListener(createPlayerListener())
                prepareAndPlay()
                timeUpdateHandler.post(timeUpdateRunnable)
                updateNavigationButtonsState()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun prepareAndPlay() {
        val catalogItem = currentContentItem ?: return

        val mediaItems = createMediaItems(catalogItem)
        if (mediaItems.isEmpty()) {
            showErrorDialog("Error de Reproducción", "No se pudo encontrar el medio para reproducir.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var startIndex = 0
            var startPosition = 0L

            // Find the correct startIndex based on currentPartIndex and currentEpisodeIndex
            if (catalogItem is Movie) {
                startIndex = currentPartIndex.coerceAtLeast(0).coerceAtMost(mediaItems.size - 1)
            } else if (catalogItem is Serie) {
                // Iterate through mediaItems to find the matching episode
                for ((index, mediaItem) in mediaItems.withIndex()) {
                    val extras = mediaItem.mediaMetadata.extras
                    val itemPartIndex = extras?.getInt("partIndex", -1) ?: -1
                    val itemEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1

                    if (itemPartIndex == currentPartIndex && itemEpisodeIndex == currentEpisodeIndex) {
                        startIndex = index
                        break
                    }
                }
            }

            val savedProgress = playbackProgressRepository.getPlaybackProgress(catalogItem.id, currentPartIndex, currentEpisodeIndex)
            startPosition = savedProgress?.currentPositionMs ?: 0L
            Log.d("PlayerFragment", "prepareAndPlay: savedProgress.currentPositionMs = ${savedProgress?.currentPositionMs}, startPosition = $startPosition")

            // Add a small delay to ensure player is ready
            delay(100) // Delay for 100 milliseconds

            mediaController?.setMediaItems(mediaItems, startIndex, startPosition)
            mediaController?.prepare()
            mediaController?.playWhenReady = true
            updateToolbarTitle()
            updateNavigationButtonsState() // Update after media item is set
        }
    }

    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    Toast.makeText(requireContext(), "Reproducción finalizada", Toast.LENGTH_SHORT).show()
                    val currentItem = currentContentItem ?: return
                    lifecycleScope.launch {
                        playbackProgressRepository.deletePlaybackProgress(currentItem.id, currentPartIndex, currentEpisodeIndex)
                        Log.d("PlayerFragment", "Progreso reiniciado para: ${currentItem.title}")
                    }
                } else if (playbackState == Player.STATE_READY) {
                    // No longer seek here. Initial seek is handled in prepareAndPlay.
                    // Seek on media item transition is handled in onMediaItemTransition.
                }
                // Update mini-player play/pause button
                val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply {
                    putExtra(MainActivity.EXTRA_IS_PLAYING, mediaController?.isPlaying == true)
                }
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(playPauseIntent)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerFragment", "Error de reproducción: ${error.message}", error)
                val cause = error.cause?.message ?: "No hay detalles adicionales."
                showErrorDialog("Error de Reproducción", "Ocurrió un error al reproducir el contenido.\nDetalles: $cause")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                Log.d("PlayerFragment", "onMediaItemTransition: New media item loaded. Reason: $reason")

                // Update currentPartIndex and currentEpisodeIndex based on the new media item
                val extras = mediaItem?.mediaMetadata?.extras
                val newPartIndex = extras?.getInt("partIndex", -1) ?: -1
                val newEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1

                Log.d("PlayerFragment", "onMediaItemTransition: Extracted newPartIndex = $newPartIndex, newEpisodeIndex = $newEpisodeIndex")

                // IMPORTANT: Update fragment's current indices BEFORE fetching saved progress
                if (newPartIndex != -1) {
                    currentPartIndex = newPartIndex
                }
                if (newEpisodeIndex != -1) {
                    currentEpisodeIndex = newEpisodeIndex
                }

                // Seek to saved progress for the new media item
                lifecycleScope.launch {
                    val savedProgress = playbackProgressRepository.getPlaybackProgress(currentContentItem!!.id, currentPartIndex, currentEpisodeIndex)
                    val startPosition = savedProgress?.currentPositionMs ?: 0L
                    Log.d("PlayerFragment", "onMediaItemTransition: Saved progress for current item: $savedProgress. Start position: $startPosition")

                    if (startPosition > 0) {
                        delay(100) // Add a small delay to ensure player is ready for seek
                        mediaController?.seekTo(startPosition)
                        Log.d("PlayerFragment", "Seeked to saved position on transition: $startPosition")
                    } else {
                        Log.d("PlayerFragment", "No saved progress found or startPosition is 0. Starting from beginning.")
                    }
                }

                updateToolbarTitle() // Update toolbar title for the new media item
                updateNavigationButtonsState() // Update navigation buttons for the new media item
                autoAdvanceTriggeredForCurrentItem = false // Reset for the new item
            }
        }
    }

    private fun checkAutoAdvanceAndNotify() {
        val player = mediaController ?: return
        val currentItem = currentContentItem ?: return

        val duration = player.duration
        val currentPosition = player.currentPosition

        if (duration == C.TIME_UNSET || duration <= 0) return

        val timeLeft = duration - currentPosition
        val timeLeftSeconds = timeLeft / 1000

        // Only auto-advance for Movies with multiple parts or Series
        val shouldAutoAdvance = when (currentItem) {
            is Movie -> currentItem.enlaces.size > 1
            is Serie -> true
            else -> false
        }

        val autoplayEnabled = sharedPreferencesManager.getBoolean("autoplay_enabled", true)

        if (shouldAutoAdvance && autoplayEnabled && timeLeftSeconds <= 10 && timeLeftSeconds > 0 && !autoAdvanceTriggeredForCurrentItem) {
            val nextItemText = if (currentItem is Serie) "próximo episodio" else "próxima parte"
            Toast.makeText(requireContext(), "$nextItemText en ${timeLeftSeconds} segundos", Toast.LENGTH_SHORT).show()
            autoAdvanceTriggeredForCurrentItem = true
        } else if (shouldAutoAdvance && timeLeftSeconds <= 1 && autoAdvanceTriggeredForCurrentItem) {
            // Trigger auto-advance when time is almost up and notification has been shown
            handleNext() // This will trigger the service to save and seek
            autoAdvanceTriggeredForCurrentItem = false // Reset for next item
        }
    }

    private fun handlePrevious() {
        val currentMediaItem = mediaController?.currentMediaItem
        val extras = currentMediaItem?.mediaMetadata?.extras
        val itemId = extras?.getString("itemId")
        val itemType = extras?.getString("itemType")
        val partIndex = extras?.getInt("partIndex", -1) ?: -1
        val episodeIndex = extras?.getInt("episodeIndex", -1) ?: -1
        val currentPosition = mediaController?.currentPosition ?: 0L

        val intent = Intent(MainActivity.ACTION_SEEK_TO_PREVIOUS).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, itemId)
            putExtra(MainActivity.EXTRA_ITEM_TYPE, itemType)
            putExtra(MainActivity.EXTRA_PART_INDEX, partIndex)
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, episodeIndex)
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, currentPosition)
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    private fun handleNext() {
        val currentMediaItem = mediaController?.currentMediaItem
        val extras = currentMediaItem?.mediaMetadata?.extras
        val itemId = extras?.getString("itemId")
        val itemType = extras?.getString("itemType")
        val partIndex = extras?.getInt("partIndex", -1) ?: -1
        val episodeIndex = extras?.getInt("episodeIndex", -1) ?: -1
        val currentPosition = mediaController?.currentPosition ?: 0L

        val intent = Intent(MainActivity.ACTION_SEEK_TO_NEXT).apply {
            putExtra(MainActivity.EXTRA_ITEM_ID, itemId)
            putExtra(MainActivity.EXTRA_ITEM_TYPE, itemType)
            putExtra(MainActivity.EXTRA_PART_INDEX, partIndex)
            putExtra(MainActivity.EXTRA_EPISODE_INDEX, episodeIndex)
            putExtra(MainActivity.EXTRA_CURRENT_POSITION, currentPosition)
        }
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
    }

    private fun updateToolbarTitle() {
        val metadata = mediaController?.currentMediaItem?.mediaMetadata
        binding.toolbar.title = metadata?.albumTitle?.toString() ?: metadata?.title?.toString()
        binding.toolbar.subtitle = metadata?.displayTitle?.toString()
    }

    private fun updateNavigationButtonsState() {
        val item = currentContentItem ?: return

        when (item) {
            is Movie -> {
                val totalParts = item.enlaces.size
                if (totalParts <= 1) {
                    customPrevMediaButton.visibility = View.GONE
                    customNextMediaButton.visibility = View.GONE
                } else {
                    customPrevMediaButton.visibility = View.VISIBLE
                    customNextMediaButton.visibility = View.VISIBLE
                    customPrevMediaButton.isEnabled = mediaController?.hasPreviousMediaItem() ?: false
                    customNextMediaButton.isEnabled = mediaController?.hasNextMediaItem() ?: false
                }
            }
            is Serie -> {
                customPrevMediaButton.visibility = View.VISIBLE
                customNextMediaButton.visibility = View.VISIBLE
                customPrevMediaButton.isEnabled = mediaController?.hasPreviousMediaItem() ?: false
                customNextMediaButton.isEnabled = mediaController?.hasNextMediaItem() ?: false
            }
            is Documentary, is ShortFilm -> {
                customPrevMediaButton.visibility = View.GONE
                customNextMediaButton.visibility = View.GONE
            }
        }
    }

    

    private fun createMediaItems(catalogItem: CatalogItem): List<MediaItem> {
        val mediaItems = mutableListOf<MediaItem>()

        when (catalogItem) {
            is Movie -> {
                catalogItem.enlaces.forEachIndexed { index, urlPath ->
                    val fullUrl = "${BASE_URL.removeSuffix("/")}/${urlPath.removePrefix("/")}"
                    val customMetadata = Bundle().apply {
                        putString("itemId", catalogItem.id)
                        putString("itemType", "peliculas")
                        putInt("partIndex", index)
                        putInt("episodeIndex", -1)
                    }
                    val hasParts = catalogItem.enlaces.size > 1
                    val partTitle = if (hasParts) "Parte ${index + 1}" else null
                    val notificationTitle = if (hasParts) "${catalogItem.title} - $partTitle" else catalogItem.title

                    mediaItems.add(
                        MediaItem.Builder()
                            .setUri(fullUrl)
                            .setMimeType("audio/mpeg")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(notificationTitle)
                                    .setAlbumTitle(catalogItem.title)
                                    .setDisplayTitle(partTitle) // Correct subtitle for UI
                                    .setArtist(catalogItem.narracion ?: catalogItem.director)
                                    .setExtras(customMetadata)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            is Serie -> {
                val sortedSeasons = catalogItem.capitulos.keys.sorted()
                sortedSeasons.forEachIndexed { seasonIndex, seasonKey ->
                    catalogItem.capitulos[seasonKey]?.forEachIndexed { episodeIndex, episode ->
                        val fullUrl = "${BASE_URL.removeSuffix("/")}/${episode.enlace.removePrefix("/")}"
                        val customMetadata = Bundle().apply {
                            putString("itemId", catalogItem.id)
                            putString("itemType", "series")
                            putInt("partIndex", seasonIndex)
                            putInt("episodeIndex", episodeIndex)
                        }
                        val episodeTitleForUi = "T${seasonIndex + 1}:E${episode.capitulo} - ${episode.titulo}"
                        val notificationTitle = "$episodeTitleForUi - ${catalogItem.title}"
                        mediaItems.add(
                            MediaItem.Builder()
                                .setUri(fullUrl)
                                .setMimeType("audio/mpeg")
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(notificationTitle)
                                        .setAlbumTitle(catalogItem.title)
                                        .setDisplayTitle(episodeTitleForUi) // Correct subtitle for UI
                                        .setArtist(catalogItem.narracion ?: catalogItem.director)
                                        .setExtras(customMetadata)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
            }
            is Documentary -> {
                catalogItem.enlace?.let { urlPath ->
                    val fullUrl = "${BASE_URL.removeSuffix("/")}/${urlPath.removePrefix("/")}"
                    val customMetadata = Bundle().apply {
                        putString("itemId", catalogItem.id)
                        putString("itemType", "documentales")
                        putInt("partIndex", 0)
                        putInt("episodeIndex", -1)
                    }
                    mediaItems.add(
                        MediaItem.Builder()
                            .setUri(fullUrl)
                            .setMimeType("audio/mpeg")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(catalogItem.title)
                                    .setAlbumTitle(catalogItem.title)
                                    .setArtist(catalogItem.narracion ?: catalogItem.director)
                                    .setExtras(customMetadata)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            is ShortFilm -> {
                catalogItem.enlace?.let { urlPath ->
                    val fullUrl = "${BASE_URL.removeSuffix("/")}/${urlPath.removePrefix("/")}"
                    val customMetadata = Bundle().apply {
                        putString("itemId", catalogItem.id)
                        putString("itemType", "cortometrajes")
                        putInt("partIndex", 0)
                        putInt("episodeIndex", -1)
                    }
                    mediaItems.add(
                        MediaItem.Builder()
                            .setUri(fullUrl)
                            .setMimeType("audio/mpeg")
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(catalogItem.title)
                                    .setAlbumTitle(catalogItem.title)
                                    .setArtist(catalogItem.narracion ?: catalogItem.director)
                                    .setExtras(customMetadata)
                                    .build()
                            )
                            .build()
                    )
                }
               }
        }
        return mediaItems
    }

    private fun showErrorDialog(title: String, message: String) {
        if (isAdded) {
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    findNavController().popBackStack()
                }
                .setCancelable(false)
                .show()
        }
    }
}