package com.johang.audiocinemateca.presentation.navigation

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.domain.DownloadManager
import com.johang.audiocinemateca.presentation.DonationDialog
import com.johang.audiocinemateca.presentation.account.AccountScreen
import com.johang.audiocinemateca.presentation.account.AccountViewModel
import com.johang.audiocinemateca.presentation.account.LoginCloudRoute
import com.johang.audiocinemateca.presentation.account.ProfileRoute
import com.johang.audiocinemateca.presentation.account.RegisterCloudRoute
import com.johang.audiocinemateca.presentation.account.UpdateProgressDialog
import com.johang.audiocinemateca.presentation.aichat.AiChatRoute
import com.johang.audiocinemateca.presentation.catalog.CatalogScreen
import com.johang.audiocinemateca.presentation.catalog.CatalogViewModel
import com.johang.audiocinemateca.presentation.catalog.CortometrajesViewModel
import com.johang.audiocinemateca.presentation.catalog.DocumentalesViewModel
import com.johang.audiocinemateca.presentation.catalog.PeliculasViewModel
import com.johang.audiocinemateca.presentation.catalog.SeriesViewModel
import com.johang.audiocinemateca.presentation.community.AnnouncementsViewModel
import com.johang.audiocinemateca.presentation.community.CommunityViewModel
import com.johang.audiocinemateca.presentation.community.GlobalChatViewModel
import com.johang.audiocinemateca.presentation.community.compose.CommunityScreen
import com.johang.audiocinemateca.presentation.contentdetail.ContentDetailRoute
import com.johang.audiocinemateca.presentation.downloads.DownloadsViewModel
import com.johang.audiocinemateca.presentation.home.HomeScreen
import com.johang.audiocinemateca.presentation.home.HomeViewModel
import com.johang.audiocinemateca.presentation.mylists.FavoritosViewModel
import com.johang.audiocinemateca.presentation.mylists.MyListsScreen
import com.johang.audiocinemateca.presentation.mylists.PlaybackHistoryViewModel
import com.johang.audiocinemateca.presentation.notifications.NotificationsScreen
import com.johang.audiocinemateca.presentation.notifications.NotificationsViewModel
import com.johang.audiocinemateca.presentation.player.compose.PlayerRoute
import com.johang.audiocinemateca.presentation.search.SearchScreen
import com.johang.audiocinemateca.presentation.search.SearchViewModel
import com.johang.audiocinemateca.presentation.settings.compose.*
import com.johang.audiocinemateca.presentation.webview.WebViewScreen
import com.johang.audiocinemateca.util.ShareUtils
import com.johang.audiocinemateca.util.TtsManager
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AudiocinematecaNavHost(
    navController: NavHostController,
    startDestination: String,
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
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None }
    ) {
        // 1. HOME
        composable(NavRoutes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val lifecycleOwner = LocalLifecycleOwner.current

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.checkAuthState()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onNavigateToLogin = { navController.navigate(NavRoutes.LOGIN_CLOUD) },
                onNavigateToRegister = { navController.navigate(NavRoutes.REGISTER_CLOUD) },
                onNavigateToExplore = { navController.navigate(NavRoutes.CATALOG) },
                onNavigateToDetail = { itemId, itemType ->
                    navController.navigate(NavRoutes.contentDetail(itemId, itemType))
                },
                onPlayContent = { itemId, itemType ->
                    scope.launch {
                        try {
                            val foundItem = searchRepository.findCatalogItemById(
                                itemId = itemId,
                                isSeriesHint = itemType.isBlank() || itemType.contains("serie", ignoreCase = true)
                            )

                            if (foundItem != null) {
                                val allProgress = playbackProgressRepository.getPlaybackProgressForContent(foundItem.id)
                                val latest = allProgress.maxByOrNull { it.lastPlayedTimestamp }
                                val partIndex = latest?.partIndex ?: if (foundItem is Serie) 0 else -1
                                val episodeIndex = latest?.episodeIndex ?: if (foundItem is Serie) 0 else -1
                                navController.navigate(NavRoutes.player(foundItem, partIndex, episodeIndex))
                            } else {
                                navController.navigate(NavRoutes.contentDetail(itemId, itemType))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onResumeContentWithPosition = { itemId, itemType, explicitPartIndex, explicitEpisodeIndex ->
                    scope.launch {
                        try {
                            val foundItem = searchRepository.findCatalogItemById(
                                itemId = itemId,
                                isSeriesHint = itemType.isBlank() || itemType.contains("serie", ignoreCase = true)
                            )

                            if (foundItem != null) {
                                navController.navigate(NavRoutes.player(foundItem, explicitPartIndex, explicitEpisodeIndex))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onMarkAsWatched = { item -> viewModel.markAsWatched(item.progress) },
                onOpenDrawer = onOpenDrawer,
                onSearchClick = { navController.navigate(NavRoutes.SEARCH) },
                onSurpriseMeClick = {
                    scope.launch {
                        try {
                            val randomItem = searchRepository.getRandomCatalogItem("peliculas")
                                ?: searchRepository.getRandomCatalogItem("series")
                            randomItem?.let { item ->
                                navController.navigate(
                                    NavRoutes.contentDetail(
                                        itemId = item.id,
                                        itemType = if (item is Serie) "series" else "peliculas"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                onNavigateToProfile = { navController.navigate(NavRoutes.PROFILE) }
            )
        }

        // 2. CATALOG
        composable(NavRoutes.CATALOG) {
            val catalogViewModel: CatalogViewModel = hiltViewModel()
            val peliculasViewModel: PeliculasViewModel = hiltViewModel()
            val seriesViewModel: SeriesViewModel = hiltViewModel()
            val cortometrajesViewModel: CortometrajesViewModel = hiltViewModel()
            val documentalesViewModel: DocumentalesViewModel = hiltViewModel()

            val defaultTab = remember { sharedPreferencesManager.getString("default_content_tab", "peliculas") }
            val initialTabIndex = remember(defaultTab) {
                when (defaultTab) {
                    "series" -> 1
                    "cortometrajes" -> 2
                    "documentales" -> 3
                    else -> 0
                }
            }

            CatalogScreen(
                initialTabIndex = initialTabIndex,
                catalogViewModel = catalogViewModel,
                peliculasViewModel = peliculasViewModel,
                seriesViewModel = seriesViewModel,
                cortometrajesViewModel = cortometrajesViewModel,
                documentalesViewModel = documentalesViewModel,
                onMenuClick = onOpenDrawer,
                onSearchClick = { navController.navigate(NavRoutes.SEARCH) },
                onSurpriseMeClick = { category ->
                    scope.launch {
                        val randomItem = searchRepository.getRandomCatalogItem(category)
                        randomItem?.let { item ->
                            navController.navigate(NavRoutes.contentDetail(item.id, category))
                        } ?: run {
                            Toast.makeText(
                                context,
                                "No se pudo encontrar un elemento aleatorio en esta categoría.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onItemClick = { itemId, category ->
                    navController.navigate(NavRoutes.contentDetail(itemId, category))
                },
                onShareItem = { item ->
                    ShareUtils.shareContent(context, item, geminiRepository)
                }
            )
        }

        // 3. SEARCH
        composable(NavRoutes.SEARCH) {
            val viewModel: SearchViewModel = hiltViewModel()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchState by viewModel.searchState.collectAsState()
            val recentSearches by viewModel.recentSearches.collectAsState()

            val startVoiceRecognitionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spokenText = result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        viewModel.setSearchQuery(spokenText)
                        viewModel.saveQueryToHistory(spokenText)
                    }
                } else if (result.resultCode == Activity.RESULT_CANCELED) {
                    Toast.makeText(context, "Búsqueda por voz cancelada.", Toast.LENGTH_SHORT).show()
                }
            }

            val launchVoiceIntent = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Di algo para buscar...")
                }
                try {
                    startVoiceRecognitionLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Tu dispositivo no soporta el reconocimiento de voz.", Toast.LENGTH_SHORT).show()
                }
            }

            val requestPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    launchVoiceIntent()
                } else {
                    Toast.makeText(context, "Permiso de micrófono denegado.", Toast.LENGTH_SHORT).show()
                }
            }

            SearchScreen(
                searchQuery = searchQuery,
                searchState = searchState,
                recentSearches = recentSearches,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSearchAction = { viewModel.saveQueryToHistory(it) },
                onVoiceClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        launchVoiceIntent()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onItemClick = { item ->
                    val itemType = NavRoutes.itemTypeOf(item)
                    navController.navigate(NavRoutes.contentDetail(item.id, itemType))
                },
                onDeleteRecentItem = { viewModel.deleteSearchHistoryItem(it) },
                onClearHistory = { viewModel.clearSearchHistory() },
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // 4. MY LISTS
        composable(NavRoutes.MY_LISTS) {
            val playbackHistoryViewModel: PlaybackHistoryViewModel = hiltViewModel()
            val favoritosViewModel: FavoritosViewModel = hiltViewModel()
            val downloadsViewModel: DownloadsViewModel = hiltViewModel()

            LaunchedEffect(favoritosViewModel) {
                favoritosViewModel.navigateToPlayer.collect { (item, partIndex, episodeIndex) ->
                    navController.navigate(NavRoutes.player(item, partIndex, episodeIndex))
                }
            }

            LaunchedEffect(favoritosViewModel) {
                favoritosViewModel.error.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            LaunchedEffect(downloadsViewModel) {
                downloadsViewModel.viewActions.collect { event ->
                    event.getContentIfNotHandled()?.let { action ->
                        when (action) {
                            is DownloadsViewModel.ViewAction.NavigateToPlayer -> {
                                navController.navigate(
                                    NavRoutes.player(
                                        action.catalogItem,
                                        action.partIndex,
                                        action.episodeIndex
                                    )
                                )
                            }
                            is DownloadsViewModel.ViewAction.ShowContentDetail -> {
                                navController.navigate(NavRoutes.contentDetail(action.itemId, action.itemType))
                            }
                            is DownloadsViewModel.ViewAction.ShowError -> {
                                Toast.makeText(context, action.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> Unit
                        }
                    }
                }
            }

            MyListsScreen(
                playbackHistoryViewModel = playbackHistoryViewModel,
                favoritosViewModel = favoritosViewModel,
                downloadsViewModel = downloadsViewModel,
                onNavigateToPlayer = { item, partIndex, episodeIndex, _ ->
                    navController.navigate(NavRoutes.player(item, partIndex, episodeIndex))
                },
                onNavigateToContentDetail = { contentId, contentType ->
                    navController.navigate(NavRoutes.contentDetail(contentId, contentType))
                }
            )
        }

        // 5. ACCOUNT
        composable(NavRoutes.ACCOUNT) {
            var showDonationDialog by remember { mutableStateOf(false) }
            var showUpdateProgressDialog by remember { mutableStateOf(false) }

            AccountScreen(
                viewModel = accountViewModel,
                authCatalogRepository = authCatalogRepository,
                onNavigateToProfile = { navController.navigate(NavRoutes.PROFILE) },
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onNavigateToDocumentation = { navController.navigate(NavRoutes.DOCUMENTATION) },
                onNavigateToWebView = { url -> navController.navigate(NavRoutes.webView(url)) },
                onShowDonation = { showDonationDialog = true },
                onShowUpdateProgress = { showUpdateProgressDialog = true }
            )

            if (showDonationDialog) {
                DonationDialog(onDismiss = { showDonationDialog = false })
            }

            if (showUpdateProgressDialog) {
                UpdateProgressDialog(
                    viewModel = accountViewModel,
                    onDismiss = { showUpdateProgressDialog = false }
                )
            }
        }

        // 5b. DOCUMENTATION
        composable(NavRoutes.DOCUMENTATION) {
            com.johang.audiocinemateca.presentation.account.DocumentationScreen(
                ttsManager = ttsManager,
                onBack = { navController.navigateUp() }
            )
        }

        // 6. PROFILE
        composable(NavRoutes.PROFILE) {
            ProfileRoute(
                authCatalogRepository = authCatalogRepository,
                sharedPreferencesManager = sharedPreferencesManager,
                cloudRepository = cloudRepository,
                favoritesDao = favoritesDao,
                playbackProgressDao = playbackProgressDao,
                onBackClick = { navController.navigateUp() },
                onNavigateToLogin = { navController.navigate(NavRoutes.LOGIN_CLOUD) },
                onNavigateToRegister = { navController.navigate(NavRoutes.REGISTER_CLOUD) }
            )
        }

        // 7. LOGIN CLOUD
        composable(NavRoutes.LOGIN_CLOUD) {
            LoginCloudRoute(
                cloudRepository = cloudRepository,
                favoritesDao = favoritesDao,
                playbackProgressDao = playbackProgressDao,
                sharedPreferencesManager = sharedPreferencesManager,
                onBackClick = { navController.navigateUp() }
            )
        }

        // 8. REGISTER CLOUD
        composable(NavRoutes.REGISTER_CLOUD) {
            RegisterCloudRoute(
                authCatalogRepository = authCatalogRepository,
                cloudRepository = cloudRepository,
                onBackClick = { navController.navigateUp() }
            )
        }

        // 9. SETTINGS
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateToCategory = { categoryTag ->
                    navController.navigate(NavRoutes.categorySettings(categoryTag))
                },
                onNavigateToPrivacy = {
                    navController.navigate(NavRoutes.webView("https://audiocinemateca.com/privacidad"))
                },
                onNavigateToTerms = {
                    navController.navigate(NavRoutes.webView("https://audiocinemateca.com/terminos"))
                },
                onBack = { navController.navigateUp() }
            )
        }

        // 10. CATEGORY SETTINGS
        composable(
            route = NavRoutes.CATEGORY_SETTINGS,
            arguments = listOf(
                navArgument("category") {
                    type = NavType.StringType
                    defaultValue = "general"
                }
            )
        ) { backStackEntry ->
            val category = backStackEntry.arguments?.getString("category") ?: "general"
            val viewModel: SettingsViewModel = hiltViewModel()
            val playbackHistoryViewModel: PlaybackHistoryViewModel = hiltViewModel()
            var showClearHistoryDialog by remember { mutableStateOf(false) }

            when (category) {
                "general" -> GeneralSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
                "playback" -> PlaybackSettingsScreen(
                    viewModel = viewModel,
                    onClearHistory = { showClearHistoryDialog = true },
                    onBack = { navController.navigateUp() }
                )
                "community" -> CommunitySettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
                "ai" -> AiSettingsScreen(
                    viewModel = viewModel,
                    geminiRepository = geminiRepository,
                    onBack = { navController.navigateUp() }
                )
                "tts" -> TtsSettingsScreen(
                    viewModel = viewModel,
                    ttsManager = ttsManager,
                    onBack = { navController.navigateUp() }
                )
                "downloads" -> DownloadsSettingsScreen(
                    viewModel = viewModel,
                    onBack = { navController.navigateUp() }
                )
                else -> CategorySettingsScreen(
                    title = "Ajustes",
                    onBack = { navController.navigateUp() }
                ) {
                    Text("Categoría $category en desarrollo...")
                }
            }

            if (showClearHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showClearHistoryDialog = false },
                    title = { Text("Eliminar historial") },
                    text = { Text("¿Estás seguro de que deseas borrar todo el historial de reproducción?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                playbackHistoryViewModel.clearAllHistory()
                                showClearHistoryDialog = false
                            }
                        ) {
                            Text("Eliminar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearHistoryDialog = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }

        // 11. COMMUNITY
        composable(
            route = NavRoutes.COMMUNITY,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.StringType
                    defaultValue = "chat"
                }
            )
        ) { backStackEntry ->
            val tabArg = backStackEntry.arguments?.getString("tab") ?: "chat"
            val initialTabIndex = when (tabArg) {
                "announcements" -> 1
                "comments" -> 2
                else -> 0
            }

            val globalChatViewModel: GlobalChatViewModel = hiltViewModel()
            val announcementsViewModel: AnnouncementsViewModel = hiltViewModel()
            val communityViewModel: CommunityViewModel = hiltViewModel()

            val voiceSearchLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val spokenText = result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        globalChatViewModel.sendMessage(spokenText.trim())
                    }
                }
            }

            CommunityScreen(
                globalChatViewModel = globalChatViewModel,
                announcementsViewModel = announcementsViewModel,
                communityViewModel = communityViewModel,
                initialTabIndex = initialTabIndex,
                onVoiceClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje para el Chat Global...")
                        }
                        voiceSearchLauncher.launch(intent)
                    } catch (_: Exception) {}
                },
                onNavigateToPlayer = { item, partIndex, episodeIndex ->
                    navController.navigate(NavRoutes.player(item, partIndex, episodeIndex))
                },
                onNavigateToContentDetail = { contentId, contentType ->
                    navController.navigate(NavRoutes.contentDetail(contentId, contentType))
                },
                onNavigateToCommunitySettings = {
                    navController.navigate(NavRoutes.categorySettings("community"))
                }
            )
        }

        // 12. AI CHAT
        composable(NavRoutes.AI_CHAT) {
            AiChatRoute(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToAiSettings = { navController.navigate(NavRoutes.categorySettings("ai")) },
                onNavigateToDetail = { itemId, itemType ->
                    navController.navigate(NavRoutes.contentDetail(itemId, itemType))
                },
                onPlayContent = { item, seasonIndex, episodeIndex ->
                    navController.navigate(NavRoutes.player(item, seasonIndex, episodeIndex))
                }
            )
        }

        // 13. NOTIFICATIONS
        composable(NavRoutes.NOTIFICATIONS) {
            val viewModel: NotificationsViewModel = hiltViewModel()
            NotificationsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // 14. CONTENT DETAIL
        composable(
            route = NavRoutes.CONTENT_DETAIL,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val itemType = backStackEntry.arguments?.getString("itemType") ?: "peliculas"

            ContentDetailRoute(
                itemId = itemId,
                itemType = itemType,
                playbackProgressRepository = playbackProgressRepository,
                downloadManager = downloadManager,
                downloadRepository = downloadRepository,
                ttsManager = ttsManager,
                geminiRepository = geminiRepository,
                sharedPreferencesManager = sharedPreferencesManager,
                onBackClick = { navController.navigateUp() },
                onNavigateToPlayer = { item, partIndex, episodeIndex ->
                    navController.navigate(NavRoutes.player(item, partIndex, episodeIndex))
                }
            )
        }

        // 15. PLAYER
        composable(
            route = NavRoutes.PLAYER,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType },
                navArgument("itemType") { type = NavType.StringType },
                navArgument("partIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("episodeIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val itemType = backStackEntry.arguments?.getString("itemType") ?: "peliculas"
            val partIndex = backStackEntry.arguments?.getInt("partIndex") ?: 0
            val episodeIndex = backStackEntry.arguments?.getInt("episodeIndex") ?: -1

            PlayerRoute(
                itemId = itemId,
                itemType = itemType,
                initialPartIndex = partIndex,
                initialEpisodeIndex = episodeIndex,
                searchRepository = searchRepository,
                sharedPreferencesManager = sharedPreferencesManager,
                playbackProgressRepository = playbackProgressRepository,
                downloadRepository = downloadRepository,
                geminiRepository = geminiRepository,
                ttsManager = ttsManager,
                onBackClick = { navController.popBackStack() },
                onNavigateToDetail = { id, type ->
                    navController.navigate(NavRoutes.contentDetail(id, type))
                },
                onNavigateToPlaybackSettings = {
                    navController.navigate(NavRoutes.categorySettings("playback"))
                },
                onNavigateToHelp = {
                    navController.navigate(NavRoutes.DOCUMENTATION)
                }
            )
        }

        // 16. WEBVIEW
        composable(
            route = NavRoutes.WEBVIEW,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType
                    defaultValue = "https://audiocinemateca.com"
                }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: "https://audiocinemateca.com"
            WebViewScreen(
                url = url,
                authCatalogRepository = authCatalogRepository,
                onBack = { navController.navigateUp() }
            )
        }
    }
}
