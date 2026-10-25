@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.johang.audiocinemateca

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.NotificationDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.presentation.DonationDialog
import com.johang.audiocinemateca.presentation.WhatsNewDialog
import com.johang.audiocinemateca.presentation.account.AccountViewModel
import com.johang.audiocinemateca.presentation.cast.CastSessionListener
import com.johang.audiocinemateca.presentation.components.MiniPlayerBar
import com.johang.audiocinemateca.presentation.navigation.AudiocinematecaBottomNavigation
import com.johang.audiocinemateca.presentation.navigation.AudiocinematecaNavHost
import com.johang.audiocinemateca.presentation.navigation.NavRoutes
import com.johang.audiocinemateca.presentation.player.PlayerService
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.util.SoundEffectsManager
import com.johang.audiocinemateca.util.TtsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var catalogRepository: CatalogRepository
    @Inject lateinit var searchRepository: SearchRepository
    @Inject lateinit var authCatalogRepository: AuthCatalogRepository
    @Inject lateinit var sharedPreferencesManager: SharedPreferencesManager
    @Inject lateinit var cloudRepository: CloudRepository
    @Inject lateinit var favoritesDao: FavoritesDao
    @Inject lateinit var playbackProgressDao: PlaybackProgressDao
    @Inject lateinit var playbackProgressRepository: PlaybackProgressRepository
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var globalChatRepository: GlobalChatRepository
    @Inject lateinit var soundEffectsManager: SoundEffectsManager
    @Inject lateinit var notificationDao: NotificationDao
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var geminiRepository: GeminiRepository
    @Inject lateinit var castSessionListener: CastSessionListener

    private val accountViewModel: AccountViewModel by viewModels()
    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private var activeNavController: NavHostController? = null

    private val miniPlayerTitleState = MutableStateFlow("")
    private val miniPlayerSubtitleState = MutableStateFlow("")
    private val miniPlayerIsPlayingState = MutableStateFlow(false)

    private val headerUsernameState = MutableStateFlow("Audiocinemateca")
    private val headerEmailState = MutableStateFlow("Versión Accesible")
    private val appVersionState = MutableStateFlow("3.3.2")
    private val isCommunityVisibleState = MutableStateFlow(false)
    private val isHomeVisibleState = MutableStateFlow(true)
    private val whatsNewChangelogState = MutableStateFlow<String?>(null)
    private val showGlobalDonationDialogState = MutableStateFlow(false)
    private val pendingNavigationRouteState = MutableStateFlow<String?>(null)

    private var currentPlayingItemId: String? = null
    private var currentPlayingItemType: String? = null
    private var currentPlayingPartIndex: Int = -1
    private var currentPlayingEpisodeIndex: Int = -1

    private var isPlayerActive = false
    private var chatMessagesJob: kotlinx.coroutines.Job? = null
    var openDrawerCallback: (() -> Unit)? = null

    fun openNavigationDrawer() {
        openDrawerCallback?.invoke()
    }

    private val miniPlayerUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_SHOW_MINI_PLAYER, ACTION_UPDATE_MINI_PLAYER_METADATA -> {
                    val currentRoute = activeNavController?.currentDestination?.route
                    if (currentRoute?.startsWith("player") == true) {
                        miniPlayerTitleState.value = ""
                        return
                    }
                    val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    currentPlayingItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                    currentPlayingItemType = intent.getStringExtra(EXTRA_ITEM_TYPE)
                    currentPlayingPartIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                    currentPlayingEpisodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)
                    miniPlayerTitleState.value = intent.getStringExtra(EXTRA_TITLE) ?: ""
                    miniPlayerSubtitleState.value = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""
                    miniPlayerIsPlayingState.value = isPlaying
                    isPlayerActive = isPlaying
                }
                ACTION_HIDE_MINI_PLAYER -> {
                    miniPlayerTitleState.value = ""
                    isPlayerActive = false
                }
                ACTION_UPDATE_PLAY_PAUSE_BUTTON -> {
                    isPlayerActive = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)
                    miniPlayerIsPlayingState.value = isPlayerActive
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Audiocinemateca)
        super.onCreate(null)
        supportActionBar?.hide()
        castSessionListener.startListening(this)

        val hideHomeFeed = sharedPreferencesManager.getBoolean("hide_home_feed", false)
        isHomeVisibleState.value = !hideHomeFeed

        if (!sharedPreferencesManager.getBoolean("migrated_startup_tab_to_home_v3", false)) {
            sharedPreferencesManager.saveString("startup_tab", "home")
            sharedPreferencesManager.saveBoolean("migrated_startup_tab_to_home_v3", true)
        }

        var startupTab = sharedPreferencesManager.getString("startup_tab", if (hideHomeFeed) "catalog" else "home")
        if (hideHomeFeed && startupTab == "home") {
            startupTab = "catalog"
            sharedPreferencesManager.saveString("startup_tab", "catalog")
        }

        val initialStartDestination = when (startupTab) {
            "catalog" -> NavRoutes.CATALOG
            "mylists" -> NavRoutes.MY_LISTS
            "account" -> NavRoutes.ACCOUNT
            else -> if (hideHomeFeed) NavRoutes.CATALOG else NavRoutes.HOME
        }

        FirebaseAuth.getInstance().addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            isCommunityVisibleState.value = (user != null)
            headerUsernameState.value = user?.displayName ?: "Audiocinemateca"
            headerEmailState.value = user?.email ?: "Versión Accesible"
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionName = pInfo.versionName ?: "3.3.2"
                appVersionState.value = currentVersionName

                val lastSeenVersionName = sharedPreferencesManager.getString("last_seen_version_name", "") ?: ""
                if (currentVersionName != lastSeenVersionName && lastSeenVersionName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = accountViewModel.manualCheckForUpdates(currentVersionName)
                        if (result is UpdateCheckResult.NoUpdateAvailable) {
                            whatsNewChangelogState.value = result.updateInfo.changelog
                            sharedPreferencesManager.saveString("last_seen_version_name", currentVersionName)
                        }
                    }
                } else {
                    if (lastSeenVersionName.isEmpty()) {
                        sharedPreferencesManager.saveString("last_seen_version_name", currentVersionName)
                    }
                    checkAppUpdateAutomatically(currentVersionName)
                }
            } catch (e: Exception) {
                appVersionState.value = "3.3.2"
            }

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

        LocalBroadcastManager.getInstance(this).registerReceiver(
            miniPlayerUpdateReceiver,
            IntentFilter().apply {
                addAction(ACTION_SHOW_MINI_PLAYER)
                addAction(ACTION_HIDE_MINI_PLAYER)
                addAction(ACTION_UPDATE_PLAY_PAUSE_BUTTON)
                addAction(ACTION_UPDATE_MINI_PLAYER_METADATA)
            }
        )

        setContent {
            AudiocinematecaTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

                SideEffect {
                    activeNavController = navController
                }

                val username by headerUsernameState.collectAsState()
                val userEmail by headerEmailState.collectAsState()
                val appVersion by appVersionState.collectAsState()
                val isCommunityVisible by isCommunityVisibleState.collectAsState()
                val isHomeVisible by isHomeVisibleState.collectAsState()
                val whatsNewChangelog by whatsNewChangelogState.collectAsState()
                val showGlobalDonation by showGlobalDonationDialogState.collectAsState()
                val pendingRoute by pendingNavigationRouteState.collectAsState()

                LaunchedEffect(pendingRoute, navController) {
                    val route = pendingRoute ?: return@LaunchedEffect
                    try {
                        navController.navigate(route) {
                            launchSingleTop = true
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error navegando a ruta $route: ${e.message}")
                    } finally {
                        pendingNavigationRouteState.value = null
                    }
                }

                MainAppScreen(
                    navController = navController,
                    startDestination = initialStartDestination,
                    drawerState = drawerState,
                    username = username,
                    userEmail = userEmail,
                    appVersion = appVersion,
                    isCommunityVisible = isCommunityVisible,
                    isHomeVisible = isHomeVisible,
                    accountViewModel = accountViewModel,
                    catalogRepository = catalogRepository,
                    searchRepository = searchRepository,
                    playbackProgressRepository = playbackProgressRepository,
                    sharedPreferencesManager = sharedPreferencesManager,
                    geminiRepository = geminiRepository,
                    authCatalogRepository = authCatalogRepository,
                    cloudRepository = cloudRepository,
                    favoritesDao = favoritesDao,
                    playbackProgressDao = playbackProgressDao,
                    downloadManager = downloadManager,
                    downloadRepository = downloadRepository,
                    ttsManager = ttsManager,
                    miniPlayerTitleState = miniPlayerTitleState,
                    miniPlayerSubtitleState = miniPlayerSubtitleState,
                    miniPlayerIsPlayingState = miniPlayerIsPlayingState,
                    onMiniPlayerBarClick = {
                        val id = currentPlayingItemId
                        val type = currentPlayingItemType
                        if (!id.isNullOrBlank() && !type.isNullOrBlank()) {
                            navController.navigate(
                                NavRoutes.player(
                                    itemId = id,
                                    itemType = type,
                                    partIndex = currentPlayingPartIndex.coerceAtLeast(0),
                                    episodeIndex = currentPlayingEpisodeIndex
                                )
                            ) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onRegisterOpenDrawer = { callback ->
                        openDrawerCallback = callback
                    }
                )

                whatsNewChangelog?.let { changelog ->
                    WhatsNewDialog(
                        changelog = changelog,
                        onDismiss = { whatsNewChangelogState.value = null }
                    )
                }

                if (showGlobalDonation) {
                    DonationDialog(
                        onDismiss = { showGlobalDonationDialogState.value = false }
                    )
                }
            }
        }

        handleIntent(intent)
        checkAndRequestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(miniPlayerUpdateReceiver)
        stopGlobalChatWatcher()
        stopAnnouncementsWatcher()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        if (Build.VERSION.SDK_INT >= 37 || Build.VERSION.CODENAME.equals("Baklava", true)) {
            val accessLocalNetwork = "android.permission.ACCESS_LOCAL_NETWORK"
            if (ContextCompat.checkSelfPermission(this, accessLocalNetwork) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(accessLocalNetwork)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startAnnouncementsWatcher() {
        stopAnnouncementsWatcher()
        var isFirstLoad = true
        announcementsListener = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("anuncios")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("MainActivity", "Error en anuncios: ${error.message}")
                    return@addSnapshotListener
                }
                if (isFirstLoad) {
                    isFirstLoad = false
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val ann = doc.toObject(com.johang.audiocinemateca.data.model.Announcement::class.java)
                if (ann != null) {
                    lifecycleScope.launch {
                        if (!notificationDao.existsByRemoteId(doc.id)) {
                            notificationDao.insert(
                                com.johang.audiocinemateca.data.local.entities.NotificationEntity(
                                    remoteId = doc.id,
                                    title = "Anuncio de ${ann.adminName}",
                                    body = ann.text,
                                    timestamp = ann.timestamp?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
    }

    private var announcementsListener: com.google.firebase.firestore.ListenerRegistration? = null
    private fun stopAnnouncementsWatcher() {
        announcementsListener?.remove()
        announcementsListener = null
    }

    private fun startGlobalChatWatcher() {
        stopGlobalChatWatcher()
        chatMessagesJob = lifecycleScope.launch {
            globalChatRepository.getMessages().collect { messages ->
                val latest = messages.firstOrNull() ?: return@collect
                val lastNotifiedId = sharedPreferencesManager.getString("last_notified_chat_id", "")
                if (latest.id != lastNotifiedId) {
                    sharedPreferencesManager.saveString("last_notified_chat_id", latest.id)
                    val currentRoute = activeNavController?.currentDestination?.route
                    if (currentRoute?.startsWith("community") != true) {
                        val auth = FirebaseAuth.getInstance()
                        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                        handleChatAnnouncement(latest, null, am, auth)
                    }
                }
            }
        }
    }

    private fun stopGlobalChatWatcher() {
        chatMessagesJob?.cancel()
        chatMessagesJob = null
    }

    private fun handleChatAnnouncement(
        msg: com.johang.audiocinemateca.data.model.ChatMessage?,
        txt: String?,
        am: android.view.accessibility.AccessibilityManager,
        auth: FirebaseAuth,
        force: Boolean = false
    ) {
        val finalMsg = txt ?: msg?.let {
            val isMentionAll = it.mentions.any { m -> m.equals("todos", true) } || it.text.contains("@todos", true)
            val currentUsername = auth.currentUser?.displayName ?: ""
            val isMentionMe = currentUsername.isNotBlank() &&
                (it.mentions.any { m -> m.equals(currentUsername, true) } || it.text.contains("@$currentUsername", true))
            val isMyMsg = auth.currentUser?.uid == it.senderId
            if (!isMyMsg && (isMentionAll || isMentionMe || force)) {
                val prefix = if (isMentionAll) "Aviso para todos de ${it.senderName}" else "Mención de ${it.senderName}"
                "$prefix: ${it.text}"
            } else null
        }
        if (finalMsg != null) {
            ttsManager.speak(finalMsg, interrupt = true)
        }
    }

    private fun startRealtimeSync() {
        FirebaseAuth.getInstance().currentUser ?: return
        lifecycleScope.launch {
            try {
                cloudRepository.syncUserProfile()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en sync inicial: ${e.message}")
            }
        }
    }

    private fun stopRealtimeSync() {}

    private fun checkCatalogUpdateAutomatically() {
        lifecycleScope.launch {
            try {
                authCatalogRepository.loadCatalog().collect { res ->
                    when (res) {
                        is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                            Log.d("MainActivity", "Catálogo auto-actualizado en segundo plano.")
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en comprobación de catálogo: ${e.message}")
            }
        }
    }

    private fun setupCatalogUpdateWorker() {
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            val updateWorkRequest = androidx.work.PeriodicWorkRequestBuilder<com.johang.audiocinemateca.service.CatalogUpdateWorker>(
                24, java.util.concurrent.TimeUnit.HOURS
            ).setConstraints(constraints).build()

            androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "CatalogUpdateWorker",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                updateWorkRequest
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error configurando Worker: ${e.message}")
        }
    }

    private fun checkAppUpdateAutomatically(currentVersionName: String) {
        lifecycleScope.launch {
            try {
                val result = accountViewModel.manualCheckForUpdates(currentVersionName)
                if (result is UpdateCheckResult.UpdateAvailable) {
                    val intent = Intent(ACTION_SHOW_UPDATE_INDICATOR)
                    LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(intent)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error comprobando actualización: ${e.message}")
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == ACTION_OPEN_PLAYER) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: currentPlayingItemId
            val itemType = intent.getStringExtra(EXTRA_ITEM_TYPE) ?: currentPlayingItemType
            val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, currentPlayingPartIndex.coerceAtLeast(0))
            val epIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, currentPlayingEpisodeIndex)
            if (!itemId.isNullOrBlank() && !itemType.isNullOrBlank()) {
                pendingNavigationRouteState.value = NavRoutes.player(itemId, itemType, partIndex, epIndex)
            }
            return
        }

        val navigateTo = intent.getStringExtra("navigate_to")
        val linkUrl = intent.getStringExtra("url")
        val contentId = intent.getStringExtra("contentId")
        val contentType = intent.getStringExtra("contentType")

        if (!navigateTo.isNullOrBlank() || !linkUrl.isNullOrBlank()) {
            intent.removeExtra("navigate_to")
            intent.removeExtra("url")
            when (navigateTo) {
                "donation" -> showGlobalDonationDialogState.value = true
                "announcements" -> pendingNavigationRouteState.value = NavRoutes.community("announcements")
                "global_chat", "comments" -> pendingNavigationRouteState.value = NavRoutes.community("chat")
                "catalog" -> pendingNavigationRouteState.value = NavRoutes.CATALOG
                "content_detail" -> {
                    if (!contentId.isNullOrBlank() && !contentType.isNullOrBlank()) {
                        pendingNavigationRouteState.value = NavRoutes.contentDetail(contentId, contentType)
                    } else if (!linkUrl.isNullOrBlank()) {
                        handleDeepLink(Uri.parse(linkUrl))
                    }
                }
                else -> {
                    if (!linkUrl.isNullOrBlank()) {
                        if (linkUrl.contains("audiocinemateca.com")) {
                            handleDeepLink(Uri.parse(linkUrl))
                        } else {
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            return
        }

        val appLinkAction = intent.action
        val appLinkData = intent.data
        if (Intent.ACTION_VIEW == appLinkAction && appLinkData != null) {
            handleDeepLink(appLinkData)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleDeepLink(data: Uri) {
        val segments = data.pathSegments
        if (segments.size >= 2) {
            val type = segments[0]
            val id = segments[1]
            lifecycleScope.launch {
                val item = searchRepository.getCatalogItemByIdAndType(id, type)
                if (item != null) {
                    pendingNavigationRouteState.value = NavRoutes.contentDetail(item.id, type)
                } else {
                    Toast.makeText(this@MainActivity, "No se encontró el contenido solicitado.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW_UPDATE_INDICATOR = "com.johang.audiocinemateca.SHOW_UPDATE_INDICATOR"
        const val ACTION_HIDE_UPDATE_INDICATOR = "com.johang.audiocinemateca.HIDE_UPDATE_INDICATOR"
        const val ACTION_SHOW_MINI_PLAYER = "com.johang.audiocinemateca.SHOW_MINI_PLAYER"
        const val ACTION_HIDE_MINI_PLAYER = "com.johang.audiocinemateca.HIDE_MINI_PLAYER"
        const val ACTION_UPDATE_PLAY_PAUSE_BUTTON = "com.johang.audiocinemateca.UPDATE_PLAY_PAUSE_BUTTON"
        const val ACTION_UPDATE_MINI_PLAYER_METADATA = "com.johang.audiocinemateca.UPDATE_MINI_PLAYER_METADATA"
        const val ACTION_REQUEST_PLAYBACK_STATE = "com.johang.audiocinemateca.REQUEST_PLAYBACK_STATE"
        const val ACTION_REQUEST_MINI_PLAYER_STATE = "com.johang.audiocinemateca.REQUEST_MINI_PLAYER_STATE"
        const val ACTION_SAVE_PLAYBACK_PROGRESS = "com.johang.audiocinemateca.SAVE_PLAYBACK_PROGRESS"
        const val ACTION_SEEK_TO_PREVIOUS = "com.johang.audiocinemateca.SEEK_TO_PREVIOUS"
        const val ACTION_SEEK_TO_NEXT = "com.johang.audiocinemateca.SEEK_TO_NEXT"
        const val ACTION_OPEN_PLAYER = "com.johang.audiocinemateca.OPEN_PLAYER"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_TYPE = "extra_item_type"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_EPISODE_INDEX = "extra_episode_index"
        const val EXTRA_CURRENT_POSITION = "extra_current_position"
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MainAppScreen(
    navController: NavHostController,
    startDestination: String,
    drawerState: DrawerState,
    username: String,
    userEmail: String,
    appVersion: String,
    isCommunityVisible: Boolean,
    isHomeVisible: Boolean,
    accountViewModel: AccountViewModel,
    catalogRepository: CatalogRepository,
    searchRepository: SearchRepository,
    playbackProgressRepository: PlaybackProgressRepository,
    sharedPreferencesManager: SharedPreferencesManager,
    geminiRepository: GeminiRepository,
    authCatalogRepository: AuthCatalogRepository,
    cloudRepository: CloudRepository,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    downloadManager: DownloadManager,
    downloadRepository: DownloadRepository,
    ttsManager: TtsManager,
    miniPlayerTitleState: StateFlow<String>,
    miniPlayerSubtitleState: StateFlow<String>,
    miniPlayerIsPlayingState: StateFlow<Boolean>,
    onMiniPlayerBarClick: () -> Unit,
    onRegisterOpenDrawer: ((() -> Unit)?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val baseRoute = remember(currentRoute) {
        currentRoute?.substringBefore("?")?.substringBefore("/")
    }

    DisposableEffect(drawerState) {
        onRegisterOpenDrawer {
            scope.launch {
                drawerState.open()
            }
        }
        onDispose {
            onRegisterOpenDrawer(null)
        }
    }

    val navigateFromDrawer: (String) -> Unit = { targetRoute ->
        scope.launch {
            drawerState.close()
            if (currentRoute != targetRoute) {
                navController.navigate(targetRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = username.ifBlank { "Audiocinemateca" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        Text(
                            text = userEmail.ifBlank { "Versión Accesible" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Versión $appVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isHomeVisible) {
                    NavigationDrawerItem(
                        label = { Text("Inicio") },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        selected = baseRoute == NavRoutes.HOME,
                        onClick = { navigateFromDrawer(NavRoutes.HOME) }
                    )
                }

                if (isCommunityVisible) {
                    NavigationDrawerItem(
                        label = { Text("Comunidad") },
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        selected = baseRoute == "community",
                        onClick = { navigateFromDrawer(NavRoutes.community("chat")) }
                    )
                }

                NavigationDrawerItem(
                    label = { Text("Asistente IA") },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    selected = baseRoute == NavRoutes.AI_CHAT,
                    onClick = { navigateFromDrawer(NavRoutes.AI_CHAT) }
                )

                NavigationDrawerItem(
                    label = { Text("Notificaciones") },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    selected = baseRoute == NavRoutes.NOTIFICATIONS,
                    onClick = { navigateFromDrawer(NavRoutes.NOTIFICATIONS) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val title by miniPlayerTitleState.collectAsState()
                    val subtitle by miniPlayerSubtitleState.collectAsState()
                    val isPlaying by miniPlayerIsPlayingState.collectAsState()

                    if (title.isNotBlank() && baseRoute != "player") {
                        MiniPlayerBar(
                            title = title,
                            subtitle = subtitle,
                            isPlaying = isPlaying,
                            onBarClick = onMiniPlayerBarClick,
                            onPlayPauseClick = {
                                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(PlayerService.ACTION_PLAY_PAUSE))
                            },
                            onCloseClick = {
                                LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(PlayerService.ACTION_STOP))
                            }
                        )
                    }

                    AudiocinematecaBottomNavigation(navController, sharedPreferencesManager)
                }
            }
        ) { paddingValues ->
            AudiocinematecaNavHost(
                navController = navController,
                startDestination = startDestination,
                accountViewModel = accountViewModel,
                catalogRepository = catalogRepository,
                searchRepository = searchRepository,
                playbackProgressRepository = playbackProgressRepository,
                sharedPreferencesManager = sharedPreferencesManager,
                geminiRepository = geminiRepository,
                authCatalogRepository = authCatalogRepository,
                cloudRepository = cloudRepository,
                favoritesDao = favoritesDao,
                playbackProgressDao = playbackProgressDao,
                downloadManager = downloadManager,
                downloadRepository = downloadRepository,
                ttsManager = ttsManager,
                onOpenDrawer = {
                    scope.launch {
                        drawerState.open()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        }
    }
}
