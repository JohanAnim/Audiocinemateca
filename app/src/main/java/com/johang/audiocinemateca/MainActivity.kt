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
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.johang.audiocinemateca.presentation.WhatsNewDialogFragment
import java.io.BufferedReader
import java.io.InputStreamReader

import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.navigateUp

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

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Automatic catalog update check
        if (sharedPreferencesManager.getBoolean("auto_check_catalog", true)) {
            val lastUpdateTimestamp = sharedPreferencesManager.getLong(LAST_CATALOG_UPDATE_TIMESTAMP_KEY, 0L)
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
                                            sharedPreferencesManager.saveLong(LAST_CATALOG_UPDATE_TIMESTAMP_KEY, currentTime)
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
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.catalogFragment, R.id.myListsFragment, R.id.aiChatFragment, R.id.accountFragment, R.id.communityFragment, R.id.notificationsFragment),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        // Eliminamos navView.setupWithNavController(navController) para evitar conflictos de selección doble

        // Custom handling for drawer items that are not fragments or need arguments
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.catalogFragment -> {
                    navController.navigate(R.id.catalogFragment)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.communityFragment -> {
                    navController.navigate(R.id.communityFragment)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.notificationsFragment -> {
                    navController.navigate(R.id.notificationsFragment)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                R.id.aiChatFragment -> {
                    navController.navigate(R.id.aiChatFragment)
                    drawerLayout.closeDrawer(GravityCompat.START)
                    true
                }
                else -> {
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
                    if (handled) drawerLayout.closeDrawer(GravityCompat.START)
                    handled
                }
            }
        }

        miniPlayerContainer = findViewById(R.id.mini_player_container)
        miniPlayerTitle = miniPlayerContainer.findViewById(R.id.mini_player_title)
        miniPlayerSubtitle = miniPlayerContainer.findViewById(R.id.mini_player_subtitle)
        miniPlayerPlayPauseButton = miniPlayerContainer.findViewById(R.id.mini_player_play_pause)
        miniPlayerCloseButton = miniPlayerContainer.findViewById(R.id.mini_player_close)

        bottomNavigationView.setupWithNavController(navController)

        // Ensure Inicio is checked in drawer by default
        navView.setCheckedItem(R.id.catalogFragment)

        val startupTab = sharedPreferencesManager.getString("startup_tab", "catalog")
        val startupItemId = when (startupTab) {
            "mylists" -> R.id.myListsFragment
            "account" -> R.id.accountFragment
            else -> R.id.catalogFragment
        }
        bottomNavigationView.selectedItemId = startupItemId

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Sync drawer selection only for main destinations
            when (destination.id) {
                R.id.catalogFragment -> navView.setCheckedItem(R.id.catalogFragment)
                R.id.communityFragment -> navView.setCheckedItem(R.id.communityFragment)
                R.id.aiChatFragment -> navView.setCheckedItem(R.id.aiChatFragment)
                else -> {
                    // Si estamos en cualquier otra pantalla (detalle, cuenta, etc.), 
                    // desmarcamos todo el grupo del drawer para no confundir
                    val menu = navView.menu
                    for (i in 0 until menu.size()) {
                        val item = menu.getItem(i)
                        if (item.hasSubMenu()) {
                            for (j in 0 until item.subMenu!!.size()) {
                                item.subMenu!!.getItem(j).isChecked = false
                            }
                        } else {
                            item.isChecked = false
                        }
                    }
                }
            }

            when (destination.id) {
                R.id.playerFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    miniPlayerContainer.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                R.id.contentDetailFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                }
                R.id.communityFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                R.id.aiChatFragment -> {
                    bottomNavigationView.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                R.id.accountFragment -> {
                    bottomNavigationView.visibility = View.VISIBLE
                    miniPlayerContainer.visibility = View.GONE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                }
                else -> {
                    bottomNavigationView.visibility = View.VISIBLE
                    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
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
                            // 1. Asegurar que los detalles estén en la pila si no estamos ya allí
                            val currentDest = navController.currentDestination?.id
                            if (currentDest != R.id.contentDetailFragment) {
                                val detailAction = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId, itemType)
                                navController.navigate(detailAction)
                            }

                            // 2. Navegar al reproductor sin borrar el historial
                            val bundle = Bundle().apply {
                                putParcelable("catalogItem", it)
                                putInt("partIndex", currentPlayingPartIndex)
                                putInt("episodeIndex", currentPlayingEpisodeIndex)
                            }
                            navController.navigate(R.id.action_global_playerFragment, bundle)
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

        // Handle Back Press to close Drawer if open
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

        // Handle deep links
        handleIntent(intent)

        // Check for app updates
        if (sharedPreferencesManager.getBoolean("auto_check_app", true)) {
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
                                                                    if (sharedPreferencesManager.getBoolean("auto_check_app", true)) {
                                                                        showSimpleUpdatePrompt(result.updateInfo)
                                                                    } else {
                                                                        Toast.makeText(this@MainActivity, "Hola. Hay una nueva versión de la app disponible! Para ver más detalles, dirígete a la pestaña de cuenta", Toast.LENGTH_LONG).show()
                                                                    }                                }
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
        // Check for What's New dialog
        checkAndShowWhatsNewDialog()

        // ACTIVAR SINCRONIZACIÓN EN TIEMPO REAL (Estilo Netflix)
        startRealtimeSync()

        // DESPERTAR SERVICIOS (Reproducción y Anuncios de Chat)
        val chatServiceIntent = Intent(this, com.johang.audiocinemateca.service.ChatAnnouncementService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(chatServiceIntent)
            } else {
                startService(chatServiceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "No se pudo arrancar el servicio de anuncios: ${e.message}")
        }
        
        // Listen for Auth changes to update header
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val communityItem = navView.menu.findItem(R.id.communityFragment)
            communityItem.isVisible = auth.currentUser != null
            
            // Update header with Name, Email and Version
            val headerView = navView.getHeaderView(0)
            val userNameText = headerView.findViewById<TextView>(R.id.tv_header_user_name)
            val userEmailText = headerView.findViewById<TextView>(R.id.tv_header_user_email)
            val appVersionText = headerView.findViewById<TextView>(R.id.tv_app_version)

            val currentUser = auth.currentUser
            if (currentUser != null) {
                userNameText.text = currentUser.displayName ?: "Usuario de Audiocinemateca"
                userEmailText.text = currentUser.email
                userEmailText.visibility = View.VISIBLE
            } else {
                userNameText.text = "Audiocinemateca"
                userEmailText.text = "Versión Accesible"
                userEmailText.visibility = View.VISIBLE
            }

            // Get App Version
            try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                appVersionText.text = "Versión ${pInfo.versionName}"
            } catch (e: Exception) {
                appVersionText.text = "Versión Desconocida"
            }
        }

        // Suscribir a todos los usuarios al tema unificado
        val fcm = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        fcm.subscribeToTopic("audiocinemateca_global")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) Log.d("MainActivity", "Suscrito al tema unificado: audiocinemateca_global")
            }

        // --- VIGILANTE DE ANUNCIOS EN TIEMPO REAL (Gratis y Spark compatible) ---
        startAnnouncementsWatcher()

        // Manejar navegación desde notificación al iniciar (onCreate)
        handleIntent(intent)
    }

    private fun startAnnouncementsWatcher() {
        val startTime = com.google.firebase.Timestamp.now()
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("anuncios")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                
                val doc = snapshot?.documents?.firstOrNull() ?: return@addSnapshotListener
                val announcement = doc.toObject(com.johang.audiocinemateca.data.model.Announcement::class.java)
                
                // Solo notificamos si el anuncio es nuevo (creado después de abrir la app)
                if (announcement != null && announcement.timestamp.seconds > startTime.seconds) {
                    // GUARDAR EN HISTORIAL LOCAL EVITANDO DUPLICADOS
                    // Eliminamos sendLocalAnnouncementNotification porque el Backend ya envia una Push real
                    lifecycleScope.launch {
                        if (!notificationDao.existsByRemoteId(doc.id)) {
                            notificationDao.insert(
                                com.johang.audiocinemateca.data.local.entities.NotificationEntity(
                                    remoteId = doc.id,
                                    title = "Anuncio de ${announcement.adminName}",
                                    body = announcement.text,
                                    timestamp = announcement.timestamp.toDate().time
                                )
                            )
                        }
                    }
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun startRealtimeSync() {
        lifecycleScope.launch {
            // Sincronización de Favoritos
            cloudRepository.getFavoritesRealtimeFlow().collect { cloudFavs ->
                cloudFavs.forEach { cloud ->
                    val local = favoritesDao.isFavorite(cloud.contentId).first()
                    // Solo actualizamos si no existe localmente o si queremos forzar el estado remoto
                    // Para simplificar y dar prioridad a la nube (sincronía total):
                    favoritesDao.insertFavorite(
                        com.johang.audiocinemateca.data.local.entities.FavoriteEntity(
                            contentId = cloud.contentId,
                            title = cloud.title,
                            contentType = cloud.contentType,
                            addedAt = cloud.addedAt
                        )
                    )
                }
            }
        }

        lifecycleScope.launch {
            // Sincronización de Historial
            cloudRepository.getHistoryRealtimeFlow().collect { cloudHist ->
                cloudHist.forEach { cloud ->
                    val local = playbackProgressDao.getPlaybackProgress(cloud.contentId, cloud.partIndex, cloud.episodeIndex)
                    
                    // Solo actualizamos si el de la nube es más reciente
                    if (local == null || cloud.lastPlayedTimestamp > local.lastPlayedTimestamp) {
                        playbackProgressDao.insertPlaybackProgress(
                            com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity(
                                contentId = cloud.contentId,
                                contentType = cloud.contentType,
                                currentPositionMs = cloud.currentPositionMs,
                                totalDurationMs = cloud.totalDurationMs,
                                partIndex = cloud.partIndex,
                                episodeIndex = cloud.episodeIndex,
                                lastPlayedTimestamp = cloud.lastPlayedTimestamp,
                                isFinished = cloud.isFinished
                            )
                        )
                        Log.d("Sync", "Historial actualizado desde la nube: ${cloud.title}")
                    }
                }
            }
        }
    }

    private fun checkAndShowWhatsNewDialog() {
        lifecycleScope.launch {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                @Suppress("DEPRECATION")
                val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
                val lastSeenVersionCode = sharedPreferencesManager.getLong(LAST_SEEN_VERSION_CODE_KEY, 0L)

                if (currentVersionCode > lastSeenVersionCode) {
                    // App has been updated. Wait for the update check to complete and get the changelog.
                    val result = accountViewModel.updateState.first { it !is UpdateCheckResult.Loading }

                    val updateInfo = when (result) {
                        is UpdateCheckResult.UpdateAvailable -> result.updateInfo
                        is UpdateCheckResult.NoUpdateAvailable -> result.updateInfo
                        else -> null // Should not happen due to the 'first' predicate
                    }

                    if (updateInfo != null && updateInfo.changelog.isNotBlank()) {
                        WhatsNewDialogFragment.newInstance(updateInfo.changelog)
                            .show(supportFragmentManager, WhatsNewDialogFragment.TAG)

                        // Save the new version code *after* showing the dialog
                        sharedPreferencesManager.saveLong(LAST_SEEN_VERSION_CODE_KEY, currentVersionCode)
                    } else {
                        // If for some reason we couldn't get a changelog, at least save the version
                        // to prevent showing the dialog on every launch.
                        sharedPreferencesManager.saveLong(LAST_SEEN_VERSION_CODE_KEY, currentVersionCode)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in checkAndShowWhatsNewDialog", e)
            }
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
        if (intent == null) return
        Log.d("MainActivity", "handleIntent processing intent with action: ${intent.action}")

        // 1. Manejar navegación interna (Anuncios)
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "announcements") {
            Log.d("MainActivity", "Navigating to announcements from notification")
            val bundle = Bundle().apply { putString("select_tab", "announcements") }
            navController.navigate(R.id.communityFragment, bundle)
            intent.removeExtra("navigate_to") // Limpiar para evitar duplicados
        }

        // 2. Manejar URLs (Internas vs Externas)
        val url = intent.getStringExtra("url")
        if (!url.isNullOrBlank()) {
            Log.d("MainActivity", "Processing URL from notification: $url")
            try {
                val uri = Uri.parse(url)
                if (uri.host?.contains("audiocinemateca.com") == true) {
                    val deepLinkIntent = Intent(Intent.ACTION_VIEW, uri)
                    handleDeepLink(deepLinkIntent)
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(browserIntent)
                }
                intent.removeExtra("url") // Limpiar para evitar duplicados
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing URL from notification", e)
            }
        }

        // 3. Manejar Deep Links del sistema y el Player
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                handleDeepLink(intent)
                intent.action = null // Limpiar la acción una vez procesada
            }
            ACTION_OPEN_PLAYER -> {
                handleOpenPlayer(intent)
                intent.action = null // Limpiar la acción una vez procesada
            }
        }
    }
    private fun handleDeepLink(intent: Intent) {
        val uri: Uri? = intent.data
        Log.d("DeepLink", "Intent data URI: $uri")
        uri?.let {
            val pathSegments = it.pathSegments
            Log.d("DeepLink", "URI path segments: $pathSegments")
            
            if (pathSegments.isNotEmpty()) {
                // El tipo suele ser el primer segmento (peliculas, series, etc.)
                val rawType = pathSegments[0].lowercase()
                
                // El ID puede venir como parámetro ?id=... o como segundo segmento /peliculas/ID
                val itemId = it.getQueryParameter("id") ?: if (pathSegments.size >= 2) pathSegments[1] else null
                
                Log.d("DeepLink", "Extracted rawType: $rawType, itemId: $itemId")

                if (itemId != null) {
                    lifecycleScope.launch {
                        // Intentamos mapear el tipo de la URL a algo que SearchRepository entienda
                        val normalizedType = when {
                            rawType.contains("pelicula") || rawType.contains("movie") -> "pelicula"
                            rawType.contains("serie") -> "serie"
                            rawType.contains("document") -> "documental"
                            rawType.contains("corto") -> "cortometraje"
                            else -> rawType
                        }

                        // Intentamos buscar por tipo primero
                        var catalogItem = searchRepository.getCatalogItemByIdAndType(itemId, normalizedType)
                        
                        // Si no lo encuentra por tipo, hacemos una búsqueda global (fallback)
                        if (catalogItem == null) {
                            Log.d("DeepLink", "Item not found by type $normalizedType, trying global search...")
                            catalogItem = searchRepository.findCatalogItemById(itemId)
                        }

                        Log.d("DeepLink", "CatalogItem found: ${catalogItem != null}")
                        
                        catalogItem?.let { item ->
                            // IMPORTANTE: El itemType para la navegación debe ser el "plural" que espera el fragmento de detalle
                            val finalType = when (item) {
                                is Movie -> "peliculas"
                                is Serie -> "series"
                                is Documentary -> "documentales"
                                is ShortFilm -> "cortometrajes"
                                else -> normalizedType
                            }
                            
                            val action = MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, finalType)
                            Log.d("DeepLink", "Navigating to ContentDetailFragment with itemId: ${item.id}, itemType: $finalType")
                            navController.navigate(action)
                        } ?: run {
                            Toast.makeText(this@MainActivity, "No se encontró el contenido: $itemId", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w("DeepLink", "Content ID not found in URI: $uri")
                    // Si no hay ID pero es una sección válida, podríamos navegar a esa pestaña
                    handleSectionNavigation(rawType)
                }
            } else {
                Log.w("DeepLink", "Invalid URI format: $uri")
            }
        }
    }

    private fun handleSectionNavigation(rawType: String) {
        when {
            rawType.contains("anuncio") || rawType.contains("comunidad") -> {
                val bundle = Bundle().apply { putString("select_tab", "announcements") }
                navController.navigate(R.id.communityFragment, bundle)
            }
            rawType.contains("chat") || rawType.contains("ia") -> {
                navController.navigate(R.id.aiChatFragment)
            }
            // Agrega más secciones si es necesario
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
                    // Verificar si ya estamos en el detalle de este contenido para no duplicarlo en la pila
                    val currentDest = navController.currentDestination?.id
                    if (currentDest != R.id.contentDetailFragment) {
                        val detailAction = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId, itemType)
                        navController.navigate(detailAction)
                    }

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

    private fun showErrorDialog(message: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Aceptar", null)
            .show()
    }

    private fun showSimpleUpdatePrompt(updateInfo: com.johang.audiocinemateca.domain.model.UpdateInfo) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Actualización Disponible")
            .setMessage("Hay una nueva versión de la aplicación disponible. ¿Deseas descargarla ahora mismo?")
            .setPositiveButton("Sí") { dialog, _ ->
                accountViewModel.downloadUpdate(updateInfo)
                com.johang.audiocinemateca.presentation.account.UpdateProgressDialogFragment().show(supportFragmentManager, "UpdateProgressDialog")
                dialog.dismiss()
            }
            .setNegativeButton("No", null)
            .show()
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
        const val LAST_SEEN_VERSION_CODE_KEY = "last_seen_version_code"
    }
}