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
import kotlinx.coroutines.flow.catch
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
import com.google.firebase.auth.FirebaseAuth
import androidx.media3.common.util.UnstableApi
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.johang.audiocinemateca.service.CatalogUpdateWorker
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var authCatalogRepository: com.johang.audiocinemateca.data.AuthCatalogRepository
    @Inject lateinit var sharedPreferencesManager: SharedPreferencesManager
    @Inject lateinit var cloudRepository: com.johang.audiocinemateca.data.repository.CloudRepository
    @Inject lateinit var favoritesDao: com.johang.audiocinemateca.data.local.dao.FavoritesDao
    @Inject lateinit var playbackProgressDao: com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
    @Inject lateinit var globalChatRepository: com.johang.audiocinemateca.data.repository.GlobalChatRepository
    @Inject lateinit var soundEffectsManager: com.johang.audiocinemateca.util.SoundEffectsManager
    @Inject lateinit var notificationDao: com.johang.audiocinemateca.data.local.dao.NotificationDao
    @Inject lateinit var ttsManager: com.johang.audiocinemateca.util.TtsManager

    private val accountViewModel: AccountViewModel by viewModels()
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var miniPlayerContainer: View
    private lateinit var miniPlayerTitle: TextView
    private lateinit var miniPlayerSubtitle: TextView
    private lateinit var miniPlayerPlayPauseButton: Button
    private lateinit var miniPlayerCloseButton: Button
    private lateinit var gestureDetector: GestureDetector
    
    private var currentPlayingItemId: String? = null
    private var currentPlayingItemType: String? = null
    private var currentPlayingPartIndex: Int = -1
    private var currentPlayingEpisodeIndex: Int = -1

    private var isPlayerActive = false
    private var appStartTime = com.google.firebase.Timestamp.now()
    private var chatMessagesJob: kotlinx.coroutines.Job? = null
    private var chatStatusJob: kotlinx.coroutines.Job? = null
    private var syncFavoritesJob: kotlinx.coroutines.Job? = null
    private var syncHistoryJob: kotlinx.coroutines.Job? = null
    private var announcementsListener: com.google.firebase.firestore.ListenerRegistration? = null

    private fun startGlobalChatWatcher() {
        stopGlobalChatWatcher()
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        var isFirstLoad = true
        chatMessagesJob = lifecycleScope.launch {
            globalChatRepository.getMessages()
                .catch { e -> Log.e("MainActivity", "Error flujo chat: ${e.message}") }
                .collect { messages ->
                    if (isFirstLoad) { isFirstLoad = false; return@collect }
                    val last = messages.lastOrNull() ?: return@collect
                    if (last.senderId != user.uid) handleChatAnnouncement(last, null, am, auth)
                }
        }

        chatStatusJob = lifecycleScope.launch {
            var lastStatus: Boolean? = null
            globalChatRepository.getChatStatus()
                .catch { e -> Log.e("MainActivity", "Error flujo status chat: ${e.message}") }
                .collect { isOpen ->
                    if (lastStatus != null && lastStatus != isOpen) {
                        val text = if (isOpen) "El chat global ha sido abierto." else "El chat global ha sido cerrado."
                        handleChatAnnouncement(null, text, am, auth, force = true)
                    }
                    lastStatus = isOpen
                }
        }
    }

    private fun stopGlobalChatWatcher() {
        chatMessagesJob?.cancel(); chatStatusJob?.cancel()
        chatMessagesJob = null; chatStatusJob = null
    }

    private fun handleChatAnnouncement(msg: com.johang.audiocinemateca.data.model.ChatMessage?, txt: String?, am: AccessibilityManager, auth: FirebaseAuth, force: Boolean = false) {
        val mode = sharedPreferencesManager.getString("chat_announcement_mode", "idle_only")
        if (mode == "never" && !force) return
        if (mode == "idle_only" && isPlayerActive && !force) return

        val finalMsg = txt ?: msg?.let {
            val isMe = it.mentions.contains(auth.currentUser?.uid ?: "") || it.text.contains("@${auth.currentUser?.displayName}", true)
            if (isMe) "${it.senderName} te mencionó: ${it.text}" else "${it.senderName} escribió: ${it.text}"
        }

        finalMsg?.let {
            soundEffectsManager.playSound("receive_message")
            vibrateManually()
            ttsManager.speak(it, interrupt = false)
        }
    }

    private fun vibrateManually() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) v.vibrate(android.os.VibrationEffect.createOneShot(150, 150)) else v.vibrate(150)
    }

    private val miniPlayerUpdateReceiver = object : BroadcastReceiver() {
        @OptIn(UnstableApi::class)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_MINI_PLAYER, ACTION_UPDATE_MINI_PLAYER_METADATA -> {
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    currentPlayingItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                    currentPlayingItemType = intent.getStringExtra(EXTRA_ITEM_TYPE)
                    currentPlayingPartIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                    currentPlayingEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)
                    updateMiniPlayerContent(intent.getStringExtra(EXTRA_TITLE), intent.getStringExtra(EXTRA_SUBTITLE), isPlaying)
                    isPlayerActive = isPlaying
                    if (intent.action == ACTION_SHOW_MINI_PLAYER && navController.currentDestination?.id != R.id.playerFragment) miniPlayerContainer.visibility = View.VISIBLE
                }
                ACTION_HIDE_MINI_PLAYER -> { miniPlayerContainer.visibility = View.GONE; isPlayerActive = false }
                ACTION_UPDATE_PLAY_PAUSE_BUTTON -> { isPlayerActive = intent.getBooleanExtra(EXTRA_IS_PLAYING, false); updateMiniPlayerPlayPauseButton(isPlayerActive) }
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
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        appBarConfiguration = AppBarConfiguration(setOf(R.id.catalogFragment, R.id.myListsFragment, R.id.aiChatFragment, R.id.accountFragment, R.id.communityFragment, R.id.notificationsFragment), drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        // Forzar sincronización del menú con el destino actual al arrancar
        navController.currentDestination?.let { dest -> navView.setCheckedItem(dest.id) }

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                // Forzar la marca visual correcta según el fragmento actual
                val currentId = navController.currentDestination?.id
                if (currentId != null) {
                    navView.setCheckedItem(currentId)
                }

                ttsManager.speak("Menú lateral abierto.", interrupt = true)
                }            override fun onDrawerClosed(drawerView: View) { 
                ttsManager.speak("Menú lateral cerrado", interrupt = true) 
            }
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // Manejar botón atrás con OnBackPressedCallback para cerrar el menú
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            try {
                navView.menu.findItem(R.id.communityFragment)?.isVisible = (user != null)
                val header = if (navView.headerCount > 0) navView.getHeaderView(0) else null
                header?.let {
                    it.findViewById<TextView>(R.id.tv_header_user_name)?.text = user?.displayName ?: "Audiocinemateca"
                    it.findViewById<TextView>(R.id.tv_header_user_email)?.text = user?.email ?: "Versión Accesible"
                    try {
                        val pInfo = packageManager.getPackageInfo(packageName, 0)
                        it.findViewById<TextView>(R.id.tv_app_version)?.text = "Versión ${pInfo.versionName}"
                    } catch (e: Exception) { it.findViewById<TextView>(R.id.tv_app_version)?.text = "Versión 3.0.0" }
                }
            } catch (e: Exception) { Log.e("MainActivity", "Error actualizando UI", e) }

            if (user != null) {
                startGlobalChatWatcher()
                startRealtimeSync()
                startAnnouncementsWatcher()
                checkCatalogUpdateAutomatically()
                setupCatalogUpdateWorker()
            } else {
                stopGlobalChatWatcher()
                stopRealtimeSync()
                stopAnnouncementsWatcher()
            }
        }

        navView.setNavigationItemSelectedListener { item ->
            // Primero cerramos el menú
            drawerLayout.closeDrawer(GravityCompat.START)
            
            // Si el ítem ya está seleccionado, no hacemos nada (evita recargar la misma pantalla)
            if (item.itemId == navController.currentDestination?.id) return@setNavigationItemSelectedListener true

            when (item.itemId) {
                R.id.communityFragment -> {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        navController.navigate(R.id.communityFragment)
                        true
                    } else {
                        Toast.makeText(this, "Inicia sesión para acceder a la comunidad", Toast.LENGTH_SHORT).show()
                        false
                    }
                }
                else -> {
                    // Para el resto de ítems, dejamos que NavigationUI maneje la navegación estándar
                    androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                }
            }
        }

        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = miniPlayerContainer.findViewById(R.id.mini_player_subtitle)
        miniPlayerPlayPauseButton = miniPlayerContainer.findViewById(R.id.mini_player_play_pause)
        miniPlayerCloseButton = miniPlayerContainer.findViewById(R.id.mini_player_close)

        bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, dest, _ ->
            when (dest.id) {
                R.id.playerFragment, R.id.contentDetailFragment, R.id.communityFragment, R.id.aiChatFragment, R.id.notificationsFragment -> {
                    bottomNav.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(if (dest.id == R.id.playerFragment || dest.id == R.id.contentDetailFragment) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                else -> { bottomNav.visibility = View.VISIBLE; drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED) }
            }
        }

        miniPlayerPlayPauseButton.setOnClickListener { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(PlayerService.ACTION_PLAY_PAUSE)) }
        miniPlayerCloseButton.setOnClickListener { LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(PlayerService.ACTION_STOP)); miniPlayerContainer.visibility = View.GONE; isPlayerActive = false }
        miniPlayerContainer.setOnClickListener {
            currentPlayingItemId?.let { id -> currentPlayingItemType?.let { type ->
                lifecycleScope.launch {
                    searchRepository.getCatalogItemByIdAndType(id, type)?.let {
                        if (navController.currentDestination?.id != R.id.contentDetailFragment) navController.navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(id, type))
                        navController.navigate(R.id.action_global_playerFragment, Bundle().apply { putParcelable("catalogItem", it); putInt("partIndex", currentPlayingPartIndex); putInt("episodeIndex", currentPlayingEpisodeIndex) })
                    }
                }
            }}
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(miniPlayerUpdateReceiver, IntentFilter().apply { addAction(ACTION_SHOW_MINI_PLAYER); addAction(ACTION_HIDE_MINI_PLAYER); addAction(ACTION_UPDATE_PLAY_PAUSE_BUTTON); addAction(ACTION_UPDATE_MINI_PLAYER_METADATA) })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e2.y - e1.y > 300 && Math.abs(velocityY) > 200) {
                    Log.d("MainActivity", "Gesto deslizar abajo detectado. Deteniendo TTS.")
                    ttsManager.stop()
                    vibrateManually()
                    return true
                }
                return false
            }
        })

        handleIntent(intent)
    }

    private fun checkCatalogUpdateAutomatically() {
        val autoUpdate = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("auto_check_catalog", true)
        if (!autoUpdate) return

        lifecycleScope.launch {
            authCatalogRepository.loadCatalog().collect { result ->
                when (result) {
                    is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                        Log.d("MainActivity", "Automatic catalog update available, starting download...")
                        Toast.makeText(this@MainActivity, "Actualizando catálogo automáticamente...", Toast.LENGTH_SHORT).show()
                        authCatalogRepository.downloadAndSaveCatalog(result.serverVersion).collect { downloadResult ->
                            if (downloadResult is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
                                Toast.makeText(this@MainActivity, "Catálogo actualizado.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupCatalogUpdateWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<CatalogUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CatalogUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
        Log.d("MainActivity", "CatalogUpdateWorker scheduled every 6 hours")
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun startAnnouncementsWatcher() {
        stopAnnouncementsWatcher()
        var isFirstLoad = true
        announcementsListener = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("anuncios")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.e("MainActivity", "Error en anuncios: ${error.message}"); return@addSnapshotListener }
                if (isFirstLoad) { isFirstLoad = false; return@addSnapshotListener }
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val ann = doc.toObject(com.johang.audiocinemateca.data.model.Announcement::class.java)
                if (ann != null) {
                    lifecycleScope.launch { if (!notificationDao.existsByRemoteId(doc.id)) notificationDao.insert(com.johang.audiocinemateca.data.local.entities.NotificationEntity(remoteId = doc.id, title = "Anuncio de ${ann.adminName}", body = ann.text, timestamp = ann.timestamp?.toDate()?.time ?: System.currentTimeMillis())) }
                }
            }
    }

    private fun stopAnnouncementsWatcher() { try { announcementsListener?.remove() } catch (e: Exception) {}; announcementsListener = null }

    private fun startRealtimeSync() {
        stopRealtimeSync()
        syncFavoritesJob = lifecycleScope.launch { 
            cloudRepository.getFavoritesRealtimeFlow()
                .catch { e -> Log.e("MainActivity", "Error sync favoritos: ${e.message}") }
                .collect { favs -> 
                    favs.forEach { f -> favoritesDao.insertFavorite(com.johang.audiocinemateca.data.local.entities.FavoriteEntity(contentId = f.contentId, title = f.title, contentType = f.contentType, addedAt = f.addedAt)) } 
                } 
        }
        syncHistoryJob = lifecycleScope.launch { 
            cloudRepository.getHistoryRealtimeFlow()
                .catch { e -> Log.e("MainActivity", "Error sync historial: ${e.message}") }
                .collect { hist -> 
                    hist.forEach { h -> playbackProgressDao.insertPlaybackProgress(com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity(contentId = h.contentId, contentType = h.contentType, currentPositionMs = h.currentPositionMs, totalDurationMs = h.totalDurationMs, partIndex = h.partIndex, episodeIndex = h.episodeIndex, lastPlayedTimestamp = h.lastPlayedTimestamp, isFinished = h.isFinished)) } 
                } 
        }
    }

    private fun stopRealtimeSync() { syncFavoritesJob?.cancel(); syncHistoryJob?.cancel(); syncFavoritesJob = null; syncHistoryJob = null }

    override fun onSupportNavigateUp(): Boolean = navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent) }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            it.getStringExtra("navigate_to")?.let { nav -> if (nav == "announcements") navController.navigate(R.id.communityFragment, Bundle().apply { putString("select_tab", "announcements") }); it.removeExtra("navigate_to") }
            it.getStringExtra("url")?.let { url -> try { val uri = Uri.parse(url); if (uri.host?.contains("audiocinemateca.com") == true) handleDeepLink(Intent(Intent.ACTION_VIEW, uri)) else startActivity(Intent(Intent.ACTION_VIEW, uri)); it.removeExtra("url") } catch (e: Exception) {} }
            if (it.action == ACTION_OPEN_PLAYER) handleOpenPlayer(it)
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        val path = uri.pathSegments
        if (path.isEmpty()) return
        val id = uri.getQueryParameter("id") ?: if (path.size >= 2) path[1] else null
        if (id != null) lifecycleScope.launch { searchRepository.findCatalogItemById(id)?.let { val type = when(it){ is Movie -> "peliculas"; is Serie -> "series"; is Documentary -> "documentales"; else -> "cortometrajes" }; navController.navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(it.id, type)) } }
    }

    private fun handleOpenPlayer(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ITEM_ID) ?: return
        val type = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: return
        lifecycleScope.launch { searchRepository.getCatalogItemByIdAndType(id, type)?.let {
            if (navController.currentDestination?.id != R.id.contentDetailFragment) navController.navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(id, type))
            navController.navigate(R.id.action_global_playerFragment, Bundle().apply { putParcelable("catalogItem", it); putInt("partIndex", intent.getIntExtra(EXTRA_PART_INDEX, -1)); putInt("episodeIndex", intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)) })
        }}
    }

    private fun updateMiniPlayerContent(t: String?, s: String?, isP: Boolean) { miniPlayerTitle.text = "Reproduciendo: ${t ?: ""}"; miniPlayerSubtitle.text = s; updateMiniPlayerPlayPauseButton(isP) }
    private fun updateMiniPlayerPlayPauseButton(isP: Boolean) { miniPlayerPlayPauseButton.text = if (isP) "Pausar" else "Reproducir" }

    override fun onStart() {
        super.onStart()
        // Registrar presencia online
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            globalChatRepository.setUserPresence(uid, true)
        }
    }

    override fun onStop() {
        super.onStop()
        // Registrar presencia offline
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            globalChatRepository.setUserPresence(uid, false)
        }
    }

    override fun onDestroy() { super.onDestroy(); try { LocalBroadcastManager.getInstance(this).unregisterReceiver(miniPlayerUpdateReceiver) } catch (e: Exception) {} }

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
