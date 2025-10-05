package com.johang.audiocinemateca.presentation.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
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
import com.johang.audiocinemateca.databinding.FragmentPlayerBinding
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.equalizer.EqualizerDialogFragment
import com.johang.audiocinemateca.util.TimeFormatUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private var isInitialLoading = true

    private lateinit var customPrevMediaButton: ImageButton
    private lateinit var customNextMediaButton: ImageButton
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    private var sleepTimer: CountDownTimer? = null
    private var sleepTimerMenuItem: MenuItem? = null

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
                    binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.apply {
                        if (isPlaying) {
                            setImageDrawable(ContextCompat.getDrawable(requireContext(), androidx.media3.ui.R.drawable.exo_ic_pause_circle_filled))
                        } else {
                            setImageDrawable(ContextCompat.getDrawable(requireContext(), androidx.media3.ui.R.drawable.exo_ic_play_circle_filled))
                        }
                    }
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

        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "rewind_interval" || key == "forward_interval") {
                        activity?.runOnUiThread {
                            updateSkipButtonsContentDescription()
                        }
                    }        }

        initializeMediaController()
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
        updateNavigationButtonsState()
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
                    putExtra(MainActivity.EXTRA_TITLE, metadata?.albumTitle?.toString() ?: metadata?.title?.toString())
                    putExtra(MainActivity.EXTRA_SUBTITLE, metadata?.displayTitle?.toString())
                    putExtra(MainActivity.EXTRA_IS_PLAYING, mediaController?.isPlaying == true)
                    putExtra(MainActivity.EXTRA_ITEM_ID, it.id)
                    putExtra(MainActivity.EXTRA_ITEM_TYPE, contentType)
                    putExtra(MainActivity.EXTRA_PART_INDEX, currentPartIndex)
                    putExtra(MainActivity.EXTRA_EPISODE_INDEX, currentEpisodeIndex)
                }
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent)
            }
        }

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
        binding.toolbar.navigationContentDescription = getString(R.string.close_button_description)
        binding.toolbar.inflateMenu(R.menu.player_toolbar_menu)

        val moreOptionsMenu = binding.toolbar.menu.findItem(R.id.action_more_options)
        val subMenu = moreOptionsMenu?.subMenu

        val autoplayMenuItem = subMenu?.findItem(R.id.action_autoplay)
        autoplayMenuItem?.isChecked = sharedPreferencesManager.getBoolean("autoplay", true)

        sleepTimerMenuItem = subMenu?.findItem(R.id.action_sleep_timer)

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_playback_speed -> {
                    showPlaybackSpeedDialog()
                    true
                }
                R.id.action_equalizer -> {
                    EqualizerDialogFragment().show(childFragmentManager, "EqualizerDialogFragment")
                    true
                }
                R.id.action_autoplay -> {
                    menuItem.isChecked = !menuItem.isChecked
                    sharedPreferencesManager.saveBoolean("autoplay", menuItem.isChecked)
                    true
                }
                R.id.action_sleep_timer -> {
                    showSleepTimerDialog()
                    true
                }
                else -> false
            }
        }

        binding.toolbar.setOnClickListener {
            currentContentItem?.let { item ->
                val previousBackStackEntry = findNavController().previousBackStackEntry
                val previousDestinationId = previousBackStackEntry?.destination?.id
                val previousItemId = previousBackStackEntry?.arguments?.getString("itemId")

                if (previousDestinationId == R.id.contentDetailFragment && previousItemId == item.id) {
                    findNavController().popBackStack()
                } else {
                    val itemType = when (item) {
                        is Movie -> "peliculas"
                        is Serie -> "series"
                        is Documentary -> "documentales"
                        is ShortFilm -> "cortometrajes"
                        else -> "unknown"
                    }
                    findNavController().popBackStack()
                    val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId = item.id, itemType = itemType)
                    findNavController().navigate(action)
                }
            }
        }
    }

    private fun setupInitialTimerState() {
        if (sharedPreferencesManager.getBoolean("sleep_timer_enabled", false)) {
            val durationMinutes = sharedPreferencesManager.getString("sleep_timer_duration", "60")?.toLongOrNull() ?: 60L
            startSleepTimer(durationMinutes * 60 * 1000)
        } else {
            cancelSleepTimer(updatePreference = false)
        }
    }

    private fun showPlaybackSpeedDialog() {
        val speedOptions = arrayOf("0.75x", "Normal", "1.25x", "1.5x", "2x")
        val speedValues = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentSpeed = mediaController?.playbackParameters?.speed ?: 1.0f
        val checkedItem = speedValues.asList().indexOf(currentSpeed).takeIf { it != -1 } ?: 1

        AlertDialog.Builder(requireContext())
            .setTitle("Velocidad de reproducción")
            .setSingleChoiceItems(speedOptions, checkedItem) { dialog, which ->
                val selectedSpeed = speedValues[which]
                mediaController?.playbackParameters = PlaybackParameters(selectedSpeed)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val timerOptions = arrayOf("Desactivado", "30 minutos", "1 hora", "2 horas")
        val timerValuesMinutes = arrayOf(0L, 30L, 60L, 120L)

        val isEnabled = sharedPreferencesManager.getBoolean("sleep_timer_enabled", false)
        val currentDurationMinutes = sharedPreferencesManager.getString("sleep_timer_duration", "60")?.toLongOrNull() ?: 60L

        val checkedItem = if (!isEnabled) {
            0
        } else {
            timerValuesMinutes.indexOf(currentDurationMinutes).takeIf { it != -1 } ?: 2
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Temporizador de apagado")
            .setSingleChoiceItems(timerOptions, checkedItem) { dialog, which ->
                val selectedMinutes = timerValuesMinutes[which]
                if (selectedMinutes == 0L) {
                    cancelSleepTimer(updatePreference = true)
                } else {
                    sharedPreferencesManager.saveBoolean("sleep_timer_enabled", true)
                    sharedPreferencesManager.saveString("sleep_timer_duration", selectedMinutes.toString())
                    startSleepTimer(selectedMinutes * 60 * 1000)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun startSleepTimer(durationInMillis: Long) {
        cancelSleepTimer(updatePreference = false)
        sleepTimer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                sleepTimerMenuItem?.title = String.format("Temporizador: %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                mediaController?.pause()
                if (isAdded) {
                    showStillListeningDialog()
                }
            }
        }.start()
    }

    private fun cancelSleepTimer(updatePreference: Boolean) {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerMenuItem?.title = "Temporizador de apagado"
        if (updatePreference) {
            sharedPreferencesManager.saveBoolean("sleep_timer_enabled", false)
        }
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

    private fun setupCustomControlListeners() {
        customPrevMediaButton = binding.exoplayerView.findViewById(R.id.custom_prev_media)!!
        customNextMediaButton = binding.exoplayerView.findViewById(R.id.custom_next_media)!!

        val rewindButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)
        val forwardButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)

        rewindButton?.setOnClickListener {
            val rewindMs = (sharedPreferencesManager.getString("rewind_interval", "5")?.toLongOrNull() ?: 5L) * 1000
            val newPosition = (mediaController?.currentPosition ?: 0) - rewindMs
            mediaController?.seekTo(newPosition.coerceAtLeast(0))
            timeUpdateHandler.postDelayed({ updateSkipButtonsContentDescription() }, 100)
        }

        forwardButton?.setOnClickListener {
            val forwardMs = (sharedPreferencesManager.getString("forward_interval", "15")?.toLongOrNull() ?: 15L) * 1000
            val newPosition = (mediaController?.currentPosition ?: 0) + forwardMs
            val duration = mediaController?.duration ?: 0
            if (duration > 0) {
                mediaController?.seekTo(newPosition.coerceAtMost(duration))
            } else {
                mediaController?.seekTo(newPosition)
            }
            timeUpdateHandler.postDelayed({ updateSkipButtonsContentDescription() }, 100)
        }

        rewindButton?.setOnLongClickListener {
            showRewindIntervalDialog()
            true
        }

        forwardButton?.setOnLongClickListener {
            showForwardIntervalDialog()
            true
        }

        customPrevMediaButton.setOnClickListener { handlePrevious() }
        customNextMediaButton.setOnClickListener { handleNext() }
    }

    private fun updateSkipButtonsContentDescription() {
        val rewindSeconds = sharedPreferencesManager.getString("rewind_interval", "5")
        val forwardSeconds = sharedPreferencesManager.getString("forward_interval", "15")
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)?.contentDescription = "Retroceder ${rewindSeconds} segundos"
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)?.contentDescription = "Adelantar ${forwardSeconds} segundos"
    }

    private fun setPlayerControlsEnabled(enabled: Boolean) {
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.isEnabled = enabled
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)?.isEnabled = enabled
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)?.isEnabled = enabled
    }

    private fun showRewindIntervalDialog() {
        val entries = resources.getStringArray(R.array.rewind_interval_entries)
        val values = resources.getStringArray(R.array.rewind_interval_values)
        val currentValue = sharedPreferencesManager.getString("rewind_interval", "5")
        val checkedItem = values.indexOf(currentValue).takeIf { it != -1 } ?: 0

        AlertDialog.Builder(requireContext())
            .setTitle("Intervalo de retroceso")
            .setSingleChoiceItems(entries, checkedItem) { dialog, which ->
                val selectedValue = values[which]
                sharedPreferencesManager.saveString("rewind_interval", selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showForwardIntervalDialog() {
        val entries = resources.getStringArray(R.array.forward_interval_entries)
        val values = resources.getStringArray(R.array.forward_interval_values)
        val currentValue = sharedPreferencesManager.getString("forward_interval", "15")
        val checkedItem = values.indexOf(currentValue).takeIf { it != -1 } ?: 0

        AlertDialog.Builder(requireContext())
            .setTitle("Intervalo de avance")
            .setSingleChoiceItems(entries, checkedItem) { dialog, which ->
                val selectedValue = values[which]
                sharedPreferencesManager.saveString("forward_interval", selectedValue)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
        setPlayerControlsEnabled(false)
        val catalogItem = currentContentItem ?: return
        val mediaItems = createMediaItems(catalogItem)
        if (mediaItems.isEmpty()) {
            showErrorDialog("Error de Reproducción", "No se pudo encontrar el medio para reproducir.")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            var startIndex = 0
            var startPosition = 0L

            if (catalogItem is Movie) {
                startIndex = currentPartIndex.coerceAtLeast(0).coerceAtMost(mediaItems.size - 1)
            } else if (catalogItem is Serie) {
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
            delay(100)

            mediaController?.setMediaItems(mediaItems, startIndex, startPosition)
            mediaController?.prepare()
            mediaController?.playWhenReady = true
            updateToolbarTitle()
            updateNavigationButtonsState()
        }
    }

    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        setPlayerControlsEnabled(false)
                        if (isInitialLoading) {
                            Toast.makeText(requireContext(), "Cargando reproductor...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Player.STATE_READY -> {
                        setPlayerControlsEnabled(true)
                        if (isInitialLoading) {
                            Toast.makeText(requireContext(), "Listo", Toast.LENGTH_SHORT).show()
                            isInitialLoading = false
                        }
                        updateSkipButtonsContentDescription()
                    }
                    Player.STATE_ENDED -> {
                        Toast.makeText(requireContext(), "Reproducción finalizada", Toast.LENGTH_SHORT).show()
                        val currentItem = currentContentItem ?: return
                        lifecycleScope.launch {
                            playbackProgressRepository.deletePlaybackProgress(currentItem.id, currentPartIndex, currentEpisodeIndex)
                        }
                    }
                }
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
                val extras = mediaItem?.mediaMetadata?.extras
                val newPartIndex = extras?.getInt("partIndex", -1) ?: -1
                val newEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1

                if (newPartIndex != -1) {
                    currentPartIndex = newPartIndex
                }
                if (newEpisodeIndex != -1) {
                    currentEpisodeIndex = newEpisodeIndex
                }

                lifecycleScope.launch {
                    val savedProgress = playbackProgressRepository.getPlaybackProgress(currentContentItem!!.id, currentPartIndex, currentEpisodeIndex)
                    val startPosition = savedProgress?.currentPositionMs ?: 0L
                    if (startPosition > 0) {
                        delay(100)
                        mediaController?.seekTo(startPosition)
                    }
                }

                updateToolbarTitle()
                updateNavigationButtonsState()
                autoAdvanceTriggeredForCurrentItem = false
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

        val shouldAutoAdvance = when (currentItem) {
            is Movie -> currentItem.enlaces.size > 1
            is Serie -> true
            else -> false
        }

        val autoplayEnabled = sharedPreferencesManager.getBoolean("autoplay", true)

        if (shouldAutoAdvance && autoplayEnabled && timeLeftSeconds <= 10 && timeLeftSeconds > 0 && !autoAdvanceTriggeredForCurrentItem) {
            val nextItemText = if (currentItem is Serie) "próximo episodio" else "próxima parte"
            Toast.makeText(requireContext(), "$nextItemText en ${timeLeftSeconds} segundos", Toast.LENGTH_SHORT).show()
            autoAdvanceTriggeredForCurrentItem = true
        } else if (shouldAutoAdvance && timeLeftSeconds <= 1 && autoAdvanceTriggeredForCurrentItem) {
            handleNext()
            autoAdvanceTriggeredForCurrentItem = false
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
                                    .setDisplayTitle(partTitle)
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
                                        .setDisplayTitle(episodeTitleForUi)
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