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
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.databinding.FragmentPlayerBinding
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.equalizer.EqualizerDialogFragment
import com.johang.audiocinemateca.presentation.player.CommentsBottomSheetFragment
import com.johang.audiocinemateca.util.TimeFormatUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.media3.common.util.UnstableApi

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

    private var previousUserVote: Int? = null
    private var isUserVoteAction = false

    private val playerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON) {
                val isPlaying = intent.getBooleanExtra(MainActivity.EXTRA_IS_PLAYING, false)
                binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.apply {
                    val iconRes = if (isPlaying) androidx.media3.ui.R.drawable.exo_ic_pause_circle_filled else androidx.media3.ui.R.drawable.exo_ic_play_circle_filled
                    setImageDrawable(ContextCompat.getDrawable(requireContext(), iconRes))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentContentItem = args.catalogItem
        currentPartIndex = args.partIndex
        currentEpisodeIndex = args.episodeIndex

        currentContentItem?.let { viewModel.setContentItem(it, currentPartIndex, currentEpisodeIndex) }

        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        setupToolbar()
        setupCustomControlListeners()
        observeVoteStats()
        observeCommentsPreview()
        observeAiContentRating()

        val filter = IntentFilter(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(playerStateReceiver, filter)

        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "rewind_interval" || key == "forward_interval") {
                activity?.runOnUiThread { updateSkipButtonsContentDescription() }
            }
        }

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

        val timeBar = binding.exoplayerView.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
        timeBar?.let {
            if (duration > 0) {
                it.setDuration(duration)
                it.setPosition(currentPosition)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { 
            if (isAdded) {
                findNavController().popBackStack() 
            }
        }
        binding.toolbar.navigationContentDescription = getString(R.string.close_button_description)
        binding.toolbar.inflateMenu(R.menu.player_toolbar_menu)

        try {
            com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(
                requireContext(),
                binding.toolbar.menu,
                R.id.action_cast
            )
        } catch (e: Exception) {
            Log.e("PlayerFragment", "Error al configurar botón de Cast en la toolbar del reproductor: ${e.message}")
        }

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

        val checkedItem = if (!isEnabled) 0 else timerValuesMinutes.indexOf(currentDurationMinutes).takeIf { it != -1 } ?: 2

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
                if (isAdded) showStillListeningDialog()
            }
        }.start()
    }

    private fun cancelSleepTimer(updatePreference: Boolean) {
        sleepTimer?.cancel()
        sleepTimer = null
        sleepTimerMenuItem?.title = "Temporizador de apagado"
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

    private fun setupCustomControlListeners() {
        customPrevMediaButton = binding.exoplayerView.findViewById(R.id.custom_prev_media)!!
        customNextMediaButton = binding.exoplayerView.findViewById(R.id.custom_next_media)!!

        val playPauseButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
        val rewindButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)
        val forwardButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)
        val timeBar = binding.exoplayerView.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)

        playPauseButton?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val controller = mediaController ?: return@setOnClickListener
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

        timeBar?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
            override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                (timeBar as? View)?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            }
            override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                binding.exoplayerView.findViewById<TextView>(androidx.media3.ui.R.id.exo_position)?.text = TimeFormatUtils.formatDuration(position)
            }
            override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                if (!canceled) {
                    (timeBar as? View)?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    mediaController?.seekTo(position)
                    updateTimestamps()
                }
            }
        })

        rewindButton?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val rewindMs = (sharedPreferencesManager.getString("rewind_interval", "5")?.toLongOrNull() ?: 5L) * 1000
            val currentPos = mediaController?.currentPosition ?: 0L
            val targetPos = (currentPos - rewindMs).coerceAtLeast(0L)
            mediaController?.seekTo(targetPos)
            updateTimestamps()
            timeUpdateHandler.postDelayed({ updateSkipButtonsContentDescription() }, 100)
        }

        forwardButton?.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val forwardMs = (sharedPreferencesManager.getString("forward_interval", "15")?.toLongOrNull() ?: 15L) * 1000
            val currentPos = mediaController?.currentPosition ?: 0L
            val targetPos = currentPos + forwardMs
            mediaController?.seekTo(targetPos)
            updateTimestamps()
            timeUpdateHandler.postDelayed({ updateSkipButtonsContentDescription() }, 100)
        }

        rewindButton?.setOnLongClickListener { showRewindIntervalDialog(); true }
        forwardButton?.setOnLongClickListener { showForwardIntervalDialog(); true }

        val likeContainer = binding.exoplayerView.findViewById<View>(R.id.like_button_container)
        val dislikeContainer = binding.exoplayerView.findViewById<View>(R.id.dislike_button_container)
        val shareContainer = binding.exoplayerView.findViewById<View>(R.id.share_button_container)
        
        val voteContainer = binding.exoplayerView.findViewById<View>(R.id.vote_container)
        val commentsContainer = binding.exoplayerView.findViewById<View>(R.id.comments_preview_container)

        if (!viewModel.isUserLoggedIn) {
            voteContainer?.visibility = View.GONE
            commentsContainer?.visibility = View.GONE
        } else {
            voteContainer?.visibility = View.VISIBLE
            commentsContainer?.visibility = View.VISIBLE

            likeContainer?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); isUserVoteAction = true; viewModel.onLikeClicked() }
            dislikeContainer?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); isUserVoteAction = true; viewModel.onDislikeClicked() }
            shareContainer?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); shareContent() }
        }

        customPrevMediaButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            handlePrevious()
        }
        customNextMediaButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            handleNext()
        }
    }

    private fun updateSkipButtonsContentDescription() {
        val rewindSeconds = sharedPreferencesManager.getString("rewind_interval", "5")
        val forwardSeconds = sharedPreferencesManager.getString("forward_interval", "15")
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)?.contentDescription = "Retroceder $rewindSeconds segundos"
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)?.contentDescription = "Adelantar $forwardSeconds segundos"
    }

    private fun setPlayerControlsEnabled(enabled: Boolean) {
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.isEnabled = true
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_rew)?.isEnabled = true
        binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_ffwd)?.isEnabled = true
        binding.exoplayerView.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.isEnabled = true
        if (::customPrevMediaButton.isInitialized) customPrevMediaButton.isEnabled = true
        if (::customNextMediaButton.isInitialized) customNextMediaButton.isEnabled = true
    }

    private fun showRewindIntervalDialog() {
        val entries = resources.getStringArray(R.array.rewind_interval_entries)
        val values = resources.getStringArray(R.array.rewind_interval_values)
        val currentValue = sharedPreferencesManager.getString("rewind_interval", "5")
        val checkedItem = values.indexOf(currentValue).takeIf { it != -1 } ?: 0
        AlertDialog.Builder(requireContext()).setTitle("Intervalo de retroceso").setSingleChoiceItems(entries, checkedItem) { dialog, which ->
            sharedPreferencesManager.saveString("rewind_interval", values[which]); dialog.dismiss()
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun showForwardIntervalDialog() {
        val entries = resources.getStringArray(R.array.forward_interval_entries)
        val values = resources.getStringArray(R.array.forward_interval_values)
        val currentValue = sharedPreferencesManager.getString("forward_interval", "15")
        val checkedItem = values.indexOf(currentValue).takeIf { it != -1 } ?: 0
        AlertDialog.Builder(requireContext()).setTitle("Intervalo de avance").setSingleChoiceItems(entries, checkedItem) { dialog, which ->
            sharedPreferencesManager.saveString("forward_interval", values[which]); dialog.dismiss()
        }.setNegativeButton("Cancelar", null).show()
    }

    private fun observeCommentsPreview() {
        lifecycleScope.launch {
            viewModel.commentPreview.collect { (count, latestComment) ->
                val header = binding.exoplayerView.findViewById<TextView>(R.id.comments_header_preview)
                val latestText = binding.exoplayerView.findViewById<TextView>(R.id.latest_comment_text)
                val openButton = binding.exoplayerView.findViewById<View>(R.id.btn_open_comments)
                header?.text = "Comentarios: $count"
                latestText?.text = latestComment?.commentText ?: "Nadie ha comentado esto aún."
                header?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); showCommentsPanel(false) }
                openButton?.setOnClickListener { it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK); showCommentsPanel(true) }
            }
        }
    }

    private fun showCommentsPanel(showKeyboard: Boolean) {
        CommentsBottomSheetFragment.newInstance(showKeyboard).show(childFragmentManager, "CommentsBottomSheet")
    }

    private fun observeVoteStats() {
        lifecycleScope.launch {
            viewModel.voteStats.collect { stats ->
                val likeIcon = binding.exoplayerView.findViewById<ImageView>(R.id.like_icon)
                val dislikeIcon = binding.exoplayerView.findViewById<ImageView>(R.id.dislike_icon)
                val likeText = binding.exoplayerView.findViewById<TextView>(R.id.like_count_text)
                val dislikeText = binding.exoplayerView.findViewById<TextView>(R.id.dislike_count_text)
                if (likeText != null && dislikeText != null) {
                    likeText.text = compactNumberFormat(stats.likeCount)
                    dislikeText.text = compactNumberFormat(stats.dislikeCount)
                    
                    // Restaurar etiquetas de accesibilidad
                    val likeContainer = binding.exoplayerView.findViewById<View>(R.id.like_button_container)
                    val dislikeContainer = binding.exoplayerView.findViewById<View>(R.id.dislike_button_container)
                    likeContainer?.contentDescription = "Me gusta, ${stats.likeCount} votos" + if (stats.userVote == 1) ", seleccionado" else ""
                    dislikeContainer?.contentDescription = "No me gusta, ${stats.dislikeCount} votos" + if (stats.userVote == -1) ", seleccionado" else ""

                    if (isUserVoteAction && previousUserVote != null && previousUserVote != stats.userVote) {
                        val msg = when {
                            previousUserVote == 0 && stats.userVote == 1 -> "Has dado Me gusta"
                            previousUserVote == 0 && stats.userVote == -1 -> "Has dado No me gusta"
                            previousUserVote == 1 && stats.userVote == 0 -> "Se quitó tu Me gusta"
                            previousUserVote == -1 && stats.userVote == 0 -> "Se quitó tu No me gusta"
                            else -> ""
                        }
                        if (msg.isNotEmpty()) binding.exoplayerView.announceForAccessibility(msg)
                        isUserVoteAction = false
                    }
                    previousUserVote = stats.userVote
                    likeIcon?.alpha = if (stats.userVote == 1) 1.0f else 0.7f
                    dislikeIcon?.alpha = if (stats.userVote == -1) 1.0f else 0.7f
                }
            }
        }
    }

    private fun compactNumberFormat(number: Long): String {
        if (number < 1000) return number.toString()
        val exp = (Math.log(number.toDouble()) / Math.log(1000.0)).toInt()
        val suffix = charArrayOf('k', 'M', 'G', 'T')[exp - 1]
        return String.format("%.1f%c", number / Math.pow(1000.0, exp.toDouble()), suffix)
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
            binding.exoplayerView.player = mediaController
            binding.exoplayerView.controllerAutoShow = false
            binding.exoplayerView.controllerHideOnTouch = false
            binding.exoplayerView.controllerShowTimeoutMs = 0
            binding.exoplayerView.showController()
            mediaController?.addListener(createPlayerListener())
            prepareAndPlay()
            timeUpdateHandler.post(timeUpdateRunnable)
            updateNavigationButtonsState()
        }, MoreExecutors.directExecutor())
    }

    private fun prepareAndPlay() {
        val catalogItem = currentContentItem ?: return
        val activeItemId = mediaController?.currentMediaItem?.mediaMetadata?.extras?.getString("itemId")
        if (activeItemId == catalogItem.id) {
            setPlayerControlsEnabled(true)
            updateToolbarTitle()
            updateNavigationButtonsState()
            return
        }

        setPlayerControlsEnabled(true)
        viewLifecycleOwner.lifecycleScope.launch {
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
            
            // SI EL PROGRESO ESTÁ CASI AL FINAL (o completado), EMPEZAMOS DE CERO
            val startPosition = if (savedProgress != null) {
                if (savedProgress.currentPositionMs >= (savedProgress.totalDurationMs - 2000)) 0L 
                else savedProgress.currentPositionMs
            } else 0L

            mediaController?.setMediaItems(mediaItems, startIndex, startPosition)
            mediaController?.prepare(); mediaController?.playWhenReady = true
            updateToolbarTitle(); updateNavigationButtonsState()
        }
    }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, isPlaying) }
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(playPauseIntent)
            
            val playPauseButton = binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
            playPauseButton?.apply {
                val iconRes = if (isPlaying) androidx.media3.ui.R.drawable.exo_ic_pause_circle_filled else androidx.media3.ui.R.drawable.exo_ic_play_circle_filled
                setImageDrawable(ContextCompat.getDrawable(requireContext(), iconRes))
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                setPlayerControlsEnabled(true)
                if (isInitialLoading) {
                    isInitialLoading = false
                    binding.exoplayerView.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)?.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                    currentContentItem?.let { viewModel.trackViewStart(it.id) }
                }
                updateSkipButtonsContentDescription()
            }
            val playPauseIntent = Intent(MainActivity.ACTION_UPDATE_PLAY_PAUSE_BUTTON).apply { putExtra(MainActivity.EXTRA_IS_PLAYING, mediaController?.isPlaying == true) }
            LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(playPauseIntent)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val extras = mediaItem?.mediaMetadata?.extras
            currentPartIndex = extras?.getInt("partIndex", -1) ?: -1
            currentEpisodeIndex = extras?.getInt("episodeIndex", -1) ?: -1
            viewModel.updateCurrentEpisode(currentPartIndex, currentEpisodeIndex)
            lifecycleScope.launch {
                val savedProgress = playbackProgressRepository.getPlaybackProgress(currentContentItem!!.id, currentPartIndex, currentEpisodeIndex)
                if ((savedProgress?.currentPositionMs ?: 0L) > 0) { delay(100); mediaController?.seekTo(savedProgress!!.currentPositionMs) }
            }
            updateToolbarTitle(); updateNavigationButtonsState(); autoAdvanceTriggeredForCurrentItem = false
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
    }

    private fun checkAutoAdvanceAndNotify() {
        val player = mediaController ?: return
        val currentItem = currentContentItem ?: return
        val duration = player.duration
        val timeLeftSeconds = (duration - player.currentPosition) / 1000
        val shouldAuto = when (currentItem) { 
            is Movie -> currentItem.enlaces.size > 1
            is Serie -> true 
            else -> false 
        }
        if (shouldAuto && sharedPreferencesManager.getBoolean("autoplay", true) && timeLeftSeconds <= 10 && timeLeftSeconds > 0 && !autoAdvanceTriggeredForCurrentItem) {
            Toast.makeText(requireContext(), "Siguiente en $timeLeftSeconds segundos", Toast.LENGTH_SHORT).show(); autoAdvanceTriggeredForCurrentItem = true
        } else if (shouldAuto && timeLeftSeconds <= 1 && autoAdvanceTriggeredForCurrentItem) {
            handleNext(); autoAdvanceTriggeredForCurrentItem = false
        }
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
                val hasParts = item.enlaces.size > 1
                customPrevMediaButton.visibility = if (hasParts) View.VISIBLE else View.GONE
                customNextMediaButton.visibility = if (hasParts) View.VISIBLE else View.GONE
                customPrevMediaButton.isEnabled = mediaController?.hasPreviousMediaItem() ?: false
                customNextMediaButton.isEnabled = mediaController?.hasNextMediaItem() ?: false
            }
            is Serie -> {
                customPrevMediaButton.visibility = View.VISIBLE; customNextMediaButton.visibility = View.VISIBLE
                customPrevMediaButton.isEnabled = mediaController?.hasPreviousMediaItem() ?: false
                customNextMediaButton.isEnabled = mediaController?.hasNextMediaItem() ?: false
            }
            else -> { customPrevMediaButton.visibility = View.GONE; customNextMediaButton.visibility = View.GONE }
        }
    }

    private suspend fun createMediaItems(catalogItem: CatalogItem): List<MediaItem> {
        val resultItems = mutableListOf<MediaItem>()
        when (catalogItem) {
            is Movie -> {
                catalogItem.enlaces.forEachIndexed { index, urlPath ->
                    val d = downloadRepository.getDownload(catalogItem.id, index, -1)
                    val uri = if (d?.downloadStatus == "COMPLETE" && d.filePath != null) android.net.Uri.parse(d.filePath) else android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${urlPath.removePrefix("/")}")
                    val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "peliculas"); putInt("partIndex", index); putInt("episodeIndex", -1) }
                    val partTitle = if (catalogItem.enlaces.size > 1) "Parte ${index + 1}" else catalogItem.title
                    val artist = if (catalogItem.enlaces.size > 1) catalogItem.title else "Audiocinemateca"
                    resultItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(MediaMetadata.Builder().setTitle(partTitle).setArtist(artist).setExtras(meta).build()).build())
                }
            }
            is Serie -> {
                catalogItem.capitulos.keys.sorted().forEachIndexed { sIdx, sKey ->
                    catalogItem.capitulos[sKey]?.forEach { ep ->
                        val eIdx = catalogItem.capitulos[sKey]?.indexOf(ep) ?: -1
                        val d = downloadRepository.getDownload(catalogItem.id, sIdx, eIdx)
                        val uri = if (d?.downloadStatus == "COMPLETE" && d.filePath != null) android.net.Uri.parse(d.filePath) else android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${ep.enlace.removePrefix("/")}")
                        val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "series"); putInt("partIndex", sIdx); putInt("episodeIndex", eIdx) }
                        val epTitle = "T${sIdx + 1}:E${ep.capitulo} - ${ep.titulo}"
                        resultItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(MediaMetadata.Builder().setTitle(epTitle).setArtist(catalogItem.title).setExtras(meta).build()).build())
                    }
                }
            }
            is Documentary -> {
                val d = downloadRepository.getDownload(catalogItem.id, 0, -1)
                val uri = if (d?.downloadStatus == "COMPLETE" && d.filePath != null) android.net.Uri.parse(d.filePath) else android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${catalogItem.enlace.removePrefix("/")}")
                val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "documentales"); putInt("partIndex", 0); putInt("episodeIndex", -1) }
                resultItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(MediaMetadata.Builder().setTitle(catalogItem.title).setArtist("Audiocinemateca").setExtras(meta).build()).build())
            }
            is ShortFilm -> {
                val d = downloadRepository.getDownload(catalogItem.id, 0, -1)
                val uri = if (d?.downloadStatus == "COMPLETE" && d.filePath != null) android.net.Uri.parse(d.filePath) else android.net.Uri.parse("${BASE_URL.removeSuffix("/")}/${catalogItem.enlace.removePrefix("/")}")
                val meta = Bundle().apply { putString("itemId", catalogItem.id); putString("itemType", "cortometrajes"); putInt("partIndex", 0); putInt("episodeIndex", -1) }
                resultItems.add(MediaItem.Builder().setUri(uri).setMimeType("audio/mpeg").setMediaMetadata(MediaMetadata.Builder().setTitle(catalogItem.title).setArtist("Audiocinemateca").setExtras(meta).build()).build())
            }
        }
        return resultItems
    }

    private fun showErrorDialog(title: String, message: String) {
        if (isAdded) AlertDialog.Builder(requireContext()).setTitle(title).setMessage(message).setPositiveButton("OK") { d, _ -> d.dismiss(); findNavController().popBackStack() }.setCancelable(false).show()
    }

    private fun shareContent() {
        val item = currentContentItem ?: return
        val typeName = when (item) { is Movie -> "película"; is Serie -> "serie"; is Documentary -> "documental"; is ShortFilm -> "cortometraje"; else -> "contenido" }
        
        val article = when (typeName) {
            "película", "serie" -> "esta increíble $typeName"
            else -> "este increíble $typeName"
        }
        
        val message = "¡Oye! Estoy escuchando $article '${item.title}' en la Audiocinemateca. ¡Seguro que a ti también te podría gustar! Da clic en este enlace para que lo escuches en la app."
        val typeSlug = when (item) { is Movie -> "pelicula"; is Serie -> "serie"; is Documentary -> "documental"; is ShortFilm -> "cortometraje"; else -> "contenido" }
        val url = "https://audiocinemateca.com/$typeSlug?id=${item.id}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$message\n\n$url") }
        startActivity(Intent.createChooser(shareIntent, "Compartir contenido"))
    }

    private fun observeAiContentRating() {
        val item = currentContentItem ?: return
        val enabled = sharedPreferencesManager.getBoolean("ai_content_rating_enabled", true)
        val apiKey = sharedPreferencesManager.getString("gemini_api_key", "") ?: ""
        
        val composeView = binding.exoplayerView.findViewById<androidx.compose.ui.platform.ComposeView>(R.id.compose_ai_content_rating)
        if (!enabled || apiKey.isBlank()) {
            composeView?.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            try {
                val rating = geminiRepository.generateContentRating(item.title, item.sinopsis)
                if (!rating.isNullOrBlank() && isAdded) {
                    composeView?.apply {
                        visibility = View.VISIBLE
                        setContent {
                            com.johang.audiocinemateca.presentation.player.components.ContentRatingCard(
                                ratingText = rating,
                                onDismiss = { visibility = View.GONE }
                            )
                        }
                    }
                    delay(200)
                    ttsManager.speak(rating)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
