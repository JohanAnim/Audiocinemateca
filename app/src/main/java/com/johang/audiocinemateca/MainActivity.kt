package com.johang.audiocinemateca

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.presentation.account.AccountViewModel
import com.johang.audiocinemateca.presentation.player.PlayerService
import com.johang.audiocinemateca.data.AuthCatalogRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
@androidx.media3.common.util.UnstableApi
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var searchRepository: SearchRepository

    @Inject
    lateinit var authCatalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository

    private val accountViewModel: AccountViewModel by viewModels()

    private lateinit var navController: NavController
    private lateinit var miniPlayerContainer: View
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: TextView
    private lateinit var miniPlayerPlayPauseButton: Button
    private lateinit var miniPlayerCloseButton: Button
    private var currentPlayingItemId: String? = null
    private var currentPlayingItemType: String? = null
    private var currentPlayingPartIndex: Int = -1
    private var currentPlayingEpisodeIndex: Int = -1

    private val miniPlayerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_MINI_PLAYER, ACTION_UPDATE_MINI_PLAYER_METADATA -> {
                    val title = intent.getStringExtra(EXTRA_TITLE)
                    val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    Log.d("MainActivity", "miniPlayerUpdateReceiver: Received ${intent.action}. isPlaying = $isPlaying")
                    currentPlayingItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                    currentPlayingItemType = intent.getStringExtra(EXTRA_ITEM_TYPE)
                    currentPlayingPartIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                    currentPlayingEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)

                    updateMiniPlayerContent(title, subtitle, isPlaying)
                    if (intent.action == ACTION_SHOW_MINI_PLAYER) {
                        if (navController.currentDestination?.id != R.id.playerFragment) {
                            miniPlayerContainer.visibility = View.VISIBLE
                        }
                    }

                    // Request current playback state from PlayerService to ensure button is updated
                    val requestIntent = Intent(ACTION_REQUEST_PLAYBACK_STATE)
                    LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(requestIntent)
                }
                ACTION_HIDE_MINI_PLAYER -> {
                    miniPlayerContainer.visibility = View.GONE
                }
                ACTION_UPDATE_PLAY_PAUSE_BUTTON -> {
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    updateMiniPlayerPlayPauseButton(isPlaying)
                }
            }
        }
    }

    private val updateIndicatorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)
            val accountMenuItem = bottomNavigationView.menu.findItem(R.id.accountFragment)
            when (intent.action) {
                ACTION_SHOW_UPDATE_INDICATOR -> {
                    accountMenuItem.title = "Cuenta (Hay una nueva actualización de la app)"
                }
                ACTION_HIDE_UPDATE_INDICATOR -> {
                    accountMenuItem.title = "Cuenta"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called") // Added log
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        // Automatic catalog update check
        val sharedPrefs = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdateTimestamp = sharedPrefs.getLong(LAST_CATALOG_UPDATE_TIMESTAMP_KEY, 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastUpdateTimestamp > CATALOG_UPDATE_INTERVAL_MS) {
            lifecycleScope.launch {
                Log.d("MainActivity", "Checking for silent catalog update...")
                authCatalogRepository.loadCatalog().collect { result ->
                    when (result) {
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                            Log.d("MainActivity", "Silent catalog update available. Downloading...")
                            authCatalogRepository.downloadAndSaveCatalog(result.serverVersion).collect { downloadResult ->
                                when (downloadResult) {
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                                        Log.d("MainActivity", "Silent catalog update downloaded and saved successfully.")
                                        sharedPrefs.edit().putLong(LAST_CATALOG_UPDATE_TIMESTAMP_KEY, currentTime).apply()
                                    }
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                                        Log.e("MainActivity", "Error during silent catalog download: ${downloadResult.message}")
                                    }
                                    else -> { /* Ignore progress and other states for silent update */ }
                                }
                            }
                        }
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                            Log.e("MainActivity", "Error checking for silent catalog update: ${result.message}")
                        }
                        else -> { /* No update available or still loading, ignore for silent check */ }
                    }
                }
            }
        } else {
            Log.d("MainActivity", "Silent catalog update check skipped. Less than 24 hours since last check.")
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = miniPlayerContainer.findViewById(R.id.mini_player_subtitle)
        miniPlayerPlayPauseButton = miniPlayerContainer.findViewById(R.id.mini_player_play_pause)
        miniPlayerCloseButton = miniPlayerContainer.findViewById(R.id.mini_player_close)

        bottomNavigationView.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.playerFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    miniPlayerContainer.visibility = View.GONE
                }
                R.id.contentDetailFragment -> {
                    bottomNavigationView.visibility = View.GONE
                }
                R.id.accountFragment -> {
                    bottomNavigationView.visibility = View.VISIBLE
                    miniPlayerContainer.visibility = View.GONE
                }
                else -> {
                    bottomNavigationView.visibility = View.VISIBLE
                    // When navigating to any other destination, check if we should show the mini-player.
                    val requestIntent = Intent(ACTION_REQUEST_MINI_PLAYER_STATE)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(requestIntent)
                }
            }
        }

        miniPlayerPlayPauseButton.setOnClickListener {
            Log.d("MainActivity", "Play/Pause button clicked. Sending ACTION_PLAY_PAUSE to PlayerService.")
            val intent = Intent(PlayerService.ACTION_PLAY_PAUSE)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }

        miniPlayerCloseButton.setOnClickListener {
            Log.d("MainActivity", "Close button clicked. Sending ACTION_STOP to PlayerService.")
            val intent = Intent(PlayerService.ACTION_STOP)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            miniPlayerContainer.visibility = View.GONE
        }

        miniPlayerContainer.setOnClickListener {
            currentPlayingItemId?.let { itemId ->
                currentPlayingItemType?.let { itemType ->
                    lifecycleScope.launch {
                        val catalogItem = searchRepository.getCatalogItemByIdAndType(itemId, itemType)
                        catalogItem?.let {
                            val bundle = Bundle().apply {
                                putParcelable("catalogItem", it)
                                putInt("partIndex", currentPlayingPartIndex)
                                putInt("episodeIndex", currentPlayingEpisodeIndex)
                            }
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(navController.graph.startDestinationId, false)
                                .build()
                            navController.navigate(R.id.action_global_playerFragment, bundle, navOptions)
                        } ?: run {
                            Toast.makeText(this@MainActivity, "No se pudo cargar el contenido para reanudar la reproducción.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Register receiver for mini-player updates
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW_MINI_PLAYER)
            addAction(ACTION_HIDE_MINI_PLAYER)
            addAction(ACTION_UPDATE_PLAY_PAUSE_BUTTON)
            addAction(ACTION_UPDATE_MINI_PLAYER_METADATA)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(miniPlayerUpdateReceiver, filter)

        val updateIndicatorFilter = IntentFilter().apply {
            addAction(ACTION_SHOW_UPDATE_INDICATOR)
            addAction(ACTION_HIDE_UPDATE_INDICATOR)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(updateIndicatorReceiver, updateIndicatorFilter)

        // Handle deep links
        handleIntent(intent)

        // Check for app updates
        try {
            packageManager.getPackageInfo(packageName, 0).versionName?.let { currentVersion ->
                accountViewModel.checkForUpdates(currentVersion)

                // Observe the updateState and send broadcast accordingly
                lifecycleScope.launch {
                    delay(1000) // Delay for 1 second
                    accountViewModel.updateState.collect { result ->
                        when (result) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                Log.d("MainActivity", "Update available: ${result.updateInfo.version}")
                                val intent = Intent(ACTION_SHOW_UPDATE_INDICATOR)
                                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                                Toast.makeText(this@MainActivity, "Hola. Hay una nueva versión de la app disponible! Para ver más detalles, dirígete a la pestaña de cuenta", Toast.LENGTH_LONG).show()
                            }
                            is UpdateCheckResult.NoUpdateAvailable -> {
                                Log.d("MainActivity", "No update available.")
                                val intent = Intent(ACTION_HIDE_UPDATE_INDICATOR)
                                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                            }
                            is UpdateCheckResult.Error -> {
                                Log.e("MainActivity", "Error checking for update: ${result.message}")
                                val intent = Intent(ACTION_HIDE_UPDATE_INDICATOR)
                                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                                // Optionally, show a toast or other UI feedback for the error
                            }
                            UpdateCheckResult.Loading -> {
                                // Optionally, show a loading indicator
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking for app update", e)
        }
    }

    override fun onStart() {
        super.onStart()
        // Request mini-player state from service in case the app was closed but service is running
        val requestIntent = Intent(ACTION_REQUEST_MINI_PLAYER_STATE)
        LocalBroadcastManager.getInstance(this).sendBroadcast(requestIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d("MainActivity", "handleIntent called with action: ${intent?.action}")
        when (intent?.action) {
            Intent.ACTION_VIEW -> handleDeepLink(intent)
            ACTION_OPEN_PLAYER -> handleOpenPlayer(intent)
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val uri: Uri? = intent.data
        Log.d("DeepLink", "Intent data URI: $uri")
        uri?.let {
            val pathSegments = it.pathSegments
            Log.d("DeepLink", "URI path segments: $pathSegments")
            if (pathSegments.size >= 1) {
                val itemType = pathSegments[0]
                val itemId = it.getQueryParameter("id")
                Log.d("DeepLink", "Extracted itemType: $itemType, itemId: $itemId")

                if (itemId != null) {
                    lifecycleScope.launch {
                        val catalogItem = searchRepository.getCatalogItemByIdAndType(itemId, itemType)
                        Log.d("DeepLink", "CatalogItem found: ${catalogItem != null}")
                        catalogItem?.let {
                            val action = MainNavGraphDirections.actionGlobalContentDetailFragment(it.id, itemType)
                            Log.d("DeepLink", "Navigating to ContentDetailFragment with itemId: ${it.id}, itemType: $itemType using NavDirections")
                            navController.navigate(action)
                        } ?: run {
                            Toast.makeText(this@MainActivity, "No se pudo cargar el contenido para reanudar la reproducción.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w("DeepLink", "Content ID not found in URI: $uri")
                    Toast.makeText(this@MainActivity, "ID de contenido no encontrado en el enlace.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w("DeepLink", "Invalid URI format: $uri")
                Toast.makeText(this@MainActivity, "Formato de enlace no válido.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleOpenPlayer(intent: Intent) {
        val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
        val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)

        if (itemId != null && itemType != null) {
            lifecycleScope.launch {
                val catalogItem = searchRepository.getCatalogItemByIdAndType(itemId, itemType)
                catalogItem?.let {
                    val bundle = Bundle().apply {
                        putParcelable("catalogItem", it)
                        putInt("partIndex", partIndex)
                        putInt("episodeIndex", episodeIndex)
                    }
                    navController.navigate(R.id.action_global_playerFragment, bundle)
                } ?: run {
                    Toast.makeText(this@MainActivity, "No se pudo cargar el contenido para reanudar la reproducción.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(miniPlayerUpdateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(updateIndicatorReceiver)
    }

    private fun updateMiniPlayerContent(title: String?, subtitle: String?, isPlaying: Boolean) {
        miniPlayerTitle.text = "Reproduciendo: ${title ?: ""}"
        miniPlayerSubtitle.text = subtitle
        updateMiniPlayerPlayPauseButton(isPlaying)
    }

    private fun updateMiniPlayerPlayPauseButton(isPlaying: Boolean) {
        Log.d("MainActivity", "updateMiniPlayerPlayPauseButton: isPlaying = $isPlaying")
        if (isPlaying) {
            miniPlayerPlayPauseButton.text = "Pausar"
        } else {
            miniPlayerPlayPauseButton.text = "Reproducir"
        }
    }

    companion object {
        const val ACTION_SHOW_MINI_PLAYER = "com.johang.audiocinemateca.SHOW_MINI_PLAYER"
        const val ACTION_HIDE_MINI_PLAYER = "com.johang.audiocinemateca.HIDE_MINI_PLAYER"
        const val ACTION_UPDATE_PLAY_PAUSE_BUTTON = "com.johang.audiocinemateca.UPDATE_PLAY_PAUSE_BUTTON"
        const val ACTION_REQUEST_PLAYBACK_STATE = "com.johang.audiocinemateca.REQUEST_PLAYBACK_STATE"
        const val ACTION_REQUEST_MINI_PLAYER_STATE = "com.johang.audiocinemateca.REQUEST_MINI_PLAYER_STATE"
        const val ACTION_SAVE_PLAYBACK_PROGRESS = "com.johang.audiocinemateca.SAVE_PLAYBACK_PROGRESS"
        const val ACTION_OPEN_PLAYER = "com.johang.audiocinemateca.OPEN_PLAYER"
        const val ACTION_UPDATE_MINI_PLAYER_METADATA = "com.johang.audiocinemateca.UPDATE_MINI_PLAYER_METADATA"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_TYPE = "extra_item_type"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_EPISODE_INDEX = "extra_episode_index"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val ACTION_SEEK_TO_PREVIOUS = "com.johang.audiocinemateca.SEEK_TO_PREVIOUS"
        const val ACTION_SEEK_TO_NEXT = "com.johang.audiocinemateca.SEEK_TO_NEXT"

        const val ACTION_SHOW_UPDATE_INDICATOR = "com.johang.audiocinemateca.SHOW_UPDATE_INDICATOR"
        const val ACTION_HIDE_UPDATE_INDICATOR = "com.johang.audiocinemateca.HIDE_UPDATE_INDICATOR"

        const val LAST_CATALOG_UPDATE_TIMESTAMP_KEY = "last_catalog_update_timestamp"
        const val CATALOG_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        const val SHARED_PREFS_NAME = "app_preferences"
    }
}