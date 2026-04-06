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
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.presentation.account.AccountViewModel
import com.johang.audiocinemateca.presentation.player.PlayerService
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.johang.audiocinemateca.presentation.WhatsNewDialogFragment
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.navigateUp
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

@AndroidEntryPoint
@OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var searchRepository: SearchRepository

    @Inject
    lateinit var authCatalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var cloudRepository: com.johang.audiocinemateca.data.repository.CloudRepository

    @Inject
    lateinit var favoritesDao: com.johang.audiocinemateca.data.local.dao.FavoritesDao

    @Inject
    lateinit var playbackProgressDao: com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao

    @Inject
    lateinit var globalChatRepository: com.johang.audiocinemateca.data.repository.GlobalChatRepository

    @Inject
    lateinit var soundEffectsManager: com.johang.audiocinemateca.util.SoundEffectsManager

    @Inject
    lateinit var notificationDao: com.johang.audiocinemateca.data.local.dao.NotificationDao

    private val accountViewModel: AccountViewModel by viewModels()

    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var miniPlayerContainer: View
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: TextView
    private lateinit var miniPlayerPlayPauseButton: Button
    private lateinit var miniPlayerCloseButton: Button
    
    private var currentPlayingItemId: String? = null
    private var currentPlayingItemType: String? = null
    private var currentPlayingPartIndex: Int = -1
    private var currentPlayingEpisodeIndex: Int = -1

    // --- LÓGICA DE CHAT GLOBAL (SILENCIOSA) ---
    private var isPlayerActive = false
    private var appStartTime = com.google.firebase.Timestamp.now()

    private fun startGlobalChatWatcher() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

        // 1. Vigilar Mensajes
        lifecycleScope.launch {
            globalChatRepository.getMessages().collect { messages ->
                val lastMessage = messages.lastOrNull() ?: return@collect
                
                // CONDICIONES DE ORO:
                // - No es nuestro
                // - Es NUEVO (creado después de abrir la app)
                if (lastMessage.senderId != auth.currentUser?.uid && 
                    lastMessage.timestamp.seconds > appStartTime.seconds) {
                    handleChatAnnouncement(lastMessage, null, am, auth)
                }
            }
        }

        // 2. Vigilar Estado
        lifecycleScope.launch {
            var lastStatus: Boolean? = null
            globalChatRepository.getChatStatus().collect { isOpen ->
                if (lastStatus != null && lastStatus != isOpen) {
                    val text = if (isOpen) "El chat global ha sido abierto." else "El chat global ha sido cerrado."
                    handleChatAnnouncement(null, text, am, auth, force = true)
                }
                lastStatus = isOpen
            }
        }
    }

    private fun handleChatAnnouncement(
        message: com.johang.audiocinemateca.data.model.ChatMessage?,
        directText: String?,
        am: AccessibilityManager,
        auth: com.google.firebase.auth.FirebaseAuth,
        force: Boolean = false
    ) {
        val mode = sharedPreferencesManager.getString("chat_announcement_mode", "idle_only")
        if (mode == "never" && !force) return

        // Si modo es 'idle_only', no hablamos si el mini reproductor es visible (algo está cargado/sonando)
        val isBusy = miniPlayerContainer.visibility == View.VISIBLE
        if (mode == "idle_only" && isBusy && !force) return

        val text = directText ?: message?.let { msg ->
            val isMentioned = msg.mentions.contains(auth.currentUser?.uid) || msg.text.contains("@${auth.currentUser?.displayName}", true)
            if (isMentioned) "${msg.senderName} te mencionó: ${msg.text}" else "${msg.senderName} escribió: ${msg.text}"
        }

        text?.let {
            soundEffectsManager.playSound("receive_message")
            vibrateManually()
            if (am.isEnabled) {
                val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                event.text.add(it)
                am.sendAccessibilityEvent(event)
            }
        }
    }

    private fun vibrateManually() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(150)
        }
    }

    private val miniPlayerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_MINI_PLAYER, ACTION_UPDATE_MINI_PLAYER_METADATA -> {
                    val title = intent.getStringExtra(EXTRA_TITLE)
                    val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    currentPlayingItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                    currentPlayingItemType = intent.getStringExtra(EXTRA_ITEM_TYPE)
                    currentPlayingPartIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                    currentPlayingEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)
                    updateMiniPlayerContent(title, subtitle, isPlaying)
                    if (intent.action == ACTION_SHOW_MINI_PLAYER && navController.currentDestination?.id != R.id.playerFragment) {
                        miniPlayerContainer.visibility = View.VISIBLE
                    }
                }
                ACTION_HIDE_MINI_PLAYER -> miniPlayerContainer.visibility = View.GONE
                ACTION_UPDATE_PLAY_PAUSE_BUTTON -> {
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    isPlayerActive = isPlaying
                    updateMiniPlayerPlayPauseButton(isPlaying)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.catalogFragment, R.id.myListsFragment, R.id.aiChatFragment, R.id.accountFragment, R.id.communityFragment, R.id.notificationsFragment),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        navView.setNavigationItemSelectedListener { menuItem ->
            val handled = when (menuItem.itemId) {
                R.id.catalogFragment -> { navController.navigate(R.id.catalogFragment); true }
                R.id.communityFragment -> { navController.navigate(R.id.communityFragment); true }
                R.id.notificationsFragment -> { navController.navigate(R.id.notificationsFragment); true }
                R.id.aiChatFragment -> { navController.navigate(R.id.aiChatFragment); true }
                else -> androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
            }
            if (handled) drawerLayout.closeDrawer(GravityCompat.START)
            handled
        }

        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = miniPlayerContainer.findViewById(R.id.mini_player_subtitle)
        miniPlayerPlayPauseButton = miniPlayerContainer.findViewById(R.id.mini_player_play_pause)
        miniPlayerCloseButton = miniPlayerContainer.findViewById(R.id.mini_player_close)

        bottomNavigationView.setupWithNavController(navController)
        navView.setCheckedItem(R.id.catalogFragment)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.playerFragment, R.id.contentDetailFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                else -> {
                    bottomNavigationView.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
            }
        }

        miniPlayerPlayPauseButton.setOnClickListener { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(PlayerService.ACTION_PLAY_PAUSE)) }
        miniPlayerCloseButton.setOnClickListener { 
            LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(PlayerService.ACTION_STOP))
            miniPlayerContainer.visibility = View.GONE
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW_MINI_PLAYER); addAction(ACTION_HIDE_MINI_PLAYER)
            addAction(ACTION_UPDATE_PLAY_PAUSE_BUTTON); addAction(ACTION_UPDATE_MINI_PLAYER_METADATA)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(miniPlayerUpdateReceiver, filter)

        // START WATCHERS
        startRealtimeSync()
        startAnnouncementsWatcher()
        startGlobalChatWatcher()
        handleIntent(intent)
    }

    private fun startAnnouncementsWatcher() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("anuncios")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snapshot, _ ->
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val ann = doc.toObject(com.johang.audiocinemateca.data.model.Announcement::class.java)
                if (ann != null && ann.timestamp.seconds > appStartTime.seconds) {
                    lifecycleScope.launch {
                        if (!notificationDao.existsByRemoteId(doc.id)) {
                            notificationDao.insert(com.johang.audiocinemateca.data.local.entities.NotificationEntity(remoteId = doc.id, title = "Anuncio de ${ann.adminName}", body = ann.text, timestamp = ann.timestamp.toDate().time))
                        }
                    }
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean = navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private fun startRealtimeSync() {
        lifecycleScope.launch { cloudRepository.getFavoritesRealtimeFlow().collect { favs -> favs.forEach { f -> favoritesDao.insertFavorite(com.johang.audiocinemateca.data.local.entities.FavoriteEntity(contentId = f.contentId, title = f.title, contentType = f.contentType, addedAt = f.addedAt)) } } }
        lifecycleScope.launch { cloudRepository.getHistoryRealtimeFlow().collect { hist -> hist.forEach { h -> playbackProgressDao.insertPlaybackProgress(com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity(contentId = h.contentId, contentType = h.contentType, currentPositionMs = h.currentPositionMs, totalDurationMs = h.totalDurationMs, partIndex = h.partIndex, episodeIndex = h.episodeIndex, lastPlayedTimestamp = h.lastPlayedTimestamp, isFinished = h.isFinished)) } } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val navTo = intent.getStringExtra("navigate_to")
        if (navTo == "announcements") { navController.navigate(R.id.communityFragment, Bundle().apply { putString("select_tab", "announcements") }); intent.removeExtra("navigate_to") }
        val url = intent.getStringExtra("url")
        if (!url.isNullOrBlank()) { try { val uri = Uri.parse(url); if (uri.host?.contains("audiocinemateca.com") == true) handleDeepLink(Intent(Intent.ACTION_VIEW, uri)) else startActivity(Intent(Intent.ACTION_VIEW, uri)); intent.removeExtra("url") } catch (e: Exception) {} }
        if (intent.action == ACTION_OPEN_PLAYER) handleOpenPlayer(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        val path = uri.pathSegments
        if (path.isEmpty()) return
        val itemId = uri.getQueryParameter("id") ?: if (path.size >= 2) path[1] else null
        if (itemId != null) {
            lifecycleScope.launch {
                val item = searchRepository.findCatalogItemById(itemId)
                item?.let { 
                    val type = when(it){ is Movie -> "peliculas"; is Serie -> "series"; is Documentary -> "documentales"; else -> "cortometrajes" }
                    navController.navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(it.id, type)) 
                }
            }
        }
    }

    private fun handleOpenPlayer(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val type = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: return
        lifecycleScope.launch {
            val item = searchRepository.getCatalogItemByIdAndType(id, type)
            item?.let {
                if (navController.currentDestination?.id != R.id.contentDetailFragment) navController.navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(id, type))
                val bundle = Bundle().apply { putParcelable("catalogItem", it); putInt("partIndex", intent.getIntExtra(EXTRA_PART_INDEX, -1)); putInt("episodeIndex", intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)) }
                navController.navigate(R.id.action_global_playerFragment, bundle)
            }
        }
    }

    private fun updateMiniPlayerContent(title: String?, subtitle: String?, isPlaying: Boolean) {
        miniPlayerTitle.text = "Reproduciendo: ${title ?: ""}"
        miniPlayerSubtitle.text = subtitle
        updateMiniPlayerPlayPauseButton(isPlaying)
    }

    private fun updateMiniPlayerPlayPauseButton(isPlaying: Boolean) {
        miniPlayerPlayPauseButton.text = if (isPlaying) "Pausar" else "Reproducir"
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(miniPlayerUpdateReceiver)
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
        const val EXTRA_TITLE = "extra_title"; const val EXTRA_SUBTITLE = "extra_subtitle"; const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ITEM_ID = "extra_item_id"; const val EXTRA_ITEM_TYPE = "extra_item_type"
        const val EXTRA_PART_INDEX = "extra_part_index"; const val EXTRA_EPISODE_INDEX = "extra_episode_index"; const val EXTRA_CURRENT_POSITION = "extra_current_position"
        const val ACTION_SEEK_TO_PREVIOUS = "com.johang.audiocinemateca.SEEK_TO_PREVIOUS"; const val ACTION_SEEK_TO_NEXT = "com.johang.audiocinemateca.SEEK_TO_NEXT"
        const val ACTION_SHOW_UPDATE_INDICATOR = "com.johang.audiocinemateca.SHOW_UPDATE_INDICATOR"; const val ACTION_HIDE_UPDATE_INDICATOR = "com.johang.audiocinemateca.HIDE_UPDATE_INDICATOR"
        const val LAST_CATALOG_UPDATE_TIMESTAMP_KEY = "last_catalog_update_timestamp"; const val CATALOG_UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val SHARED_PREFS_NAME = "app_preferences"; const val LAST_SEEN_VERSION_CODE_KEY = "last_seen_version_code"
    }
}
