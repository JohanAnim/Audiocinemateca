package com.johang.audiocinemateca.presentation.mylists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.util.buildInteractionDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.downloads.DownloadFilter
import com.johang.audiocinemateca.presentation.downloads.DownloadsUiState
import com.johang.audiocinemateca.presentation.downloads.DownloadsViewModel
import com.johang.audiocinemateca.presentation.downloads.GroupedDownload
import com.johang.audiocinemateca.util.TimeFormatUtils
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListsScreen(
    playbackHistoryViewModel: PlaybackHistoryViewModel,
    favoritosViewModel: FavoritosViewModel,
    downloadsViewModel: DownloadsViewModel,
    onNavigateToPlayer: (catalogItem: CatalogItem, partIndex: Int, episodeIndex: Int, startPosition: Long) -> Unit,
    onNavigateToContentDetail: (contentId: String, contentType: String) -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabTitles = listOf("Historial", "Favoritos", "Descargas")
    val tabIcons = listOf(Icons.Default.History, Icons.Default.Favorite, Icons.Default.Download)

    val historyItems by playbackHistoryViewModel.historyItems.collectAsState()
    val favorites by favoritosViewModel.favorites.collectAsState()
    val downloadsState by downloadsViewModel.uiState.collectAsState()
    val totalSizeMb by downloadsViewModel.totalSizeMb.collectAsState()
    val totalDurationMs by downloadsViewModel.totalDurationMs.collectAsState()
    val currentFilter by downloadsViewModel.currentFilter.collectAsState()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearFavoritesDialog by remember { mutableStateOf(false) }
    var showTabMenu by remember { mutableStateOf(false) }
    var historyItemToDelete by remember { mutableStateOf<PlaybackProgressEntity?>(null) }
    var historyItemForOptionsMenu by remember { mutableStateOf<HistoryListItem.Item?>(null) }
    var favoriteToDelete by remember { mutableStateOf<FavoriteEntity?>(null) }
    var favoriteForOptionsMenu by remember { mutableStateOf<FavoriteEntity?>(null) }
    var showDeleteAllDownloadsDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var downloadItemToDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mis Listas",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showTabMenu = true },
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "Más opciones",
                                role = Role.Button,
                                onClickAction = { showTabMenu = true }
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null
                            )
                        }

                        DropdownMenu(
                            expanded = showTabMenu,
                            onDismissRequest = { showTabMenu = false }
                        ) {
                            when (selectedTabIndex) {
                                0 -> {
                                    DropdownMenuItem(
                                        text = { Text("Vaciar todo el historial") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteSweep,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showTabMenu = false
                                            showClearHistoryDialog = true
                                        },
                                        enabled = historyItems.any { it is HistoryListItem.Item }
                                    )
                                }
                                1 -> {
                                    DropdownMenuItem(
                                        text = { Text("Vaciar todos los favoritos") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showTabMenu = false
                                            showClearFavoritesDialog = true
                                        },
                                        enabled = favorites.isNotEmpty()
                                    )
                                }
                                2 -> {
                                    DropdownMenuItem(
                                        text = { Text("Filtrar descargas") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.FilterList,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            showTabMenu = false
                                            showFilterDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Eliminar todas las descargas") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showTabMenu = false
                                            showDeleteAllDownloadsDialog = true
                                        },
                                        enabled = totalSizeMb > 0
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabTitles.size)
                }
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = { Text(text = title, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(imageVector = tabIcons[index], contentDescription = null) },
                        modifier = Modifier.clearAndSetSemantics {
                            role = Role.Tab
                            selected = isSelected
                            stateDescription = "${index + 1} de ${tabTitles.size}"
                            collectionItemInfo = CollectionItemInfo(
                                rowIndex = 0,
                                rowSpan = 1,
                                columnIndex = index,
                                columnSpan = 1
                            )
                            contentDescription = title
                            onClick {
                                selectedTabIndex = index
                                true
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTabIndex) {
                    0 -> HistoryTabContent(
                        historyItems = historyItems,
                        onItemClick = { progress, item ->
                            if (item != null) {
                                onNavigateToPlayer(item, progress.partIndex, progress.episodeIndex, progress.currentPositionMs)
                            }
                        },
                        onOptionsClick = { item -> historyItemForOptionsMenu = item },
                        onDeleteClick = { progress -> historyItemToDelete = progress },
                        onViewDetail = { contentId, contentType -> onNavigateToContentDetail(contentId, contentType) }
                    )
                    1 -> FavoritesTabContent(
                        favorites = favorites,
                        onItemClick = { fav ->
                            val typeForNav = when (fav.contentType.lowercase()) {
                                "movie", "peliculas" -> "peliculas"
                                "serie", "series" -> "series"
                                "documentary", "documentales" -> "documentales"
                                "shortfilm", "short", "cortometrajes" -> "cortometrajes"
                                else -> "unknown"
                            }
                            onNavigateToContentDetail(fav.contentId, typeForNav)
                        },
                        onOptionsClick = { fav -> favoriteForOptionsMenu = fav },
                        onPlayClick = { fav -> favoritosViewModel.playFavorite(fav) },
                        onDeleteClick = { fav -> favoriteToDelete = fav }
                    )
                    2 -> DownloadsTabContent(
                        uiState = downloadsState,
                        totalSizeMb = totalSizeMb,
                        totalDurationMs = totalDurationMs,
                        currentFilter = currentFilter,
                        onFilterClick = { showFilterDialog = true },
                        onToggleSeries = { contentId -> downloadsViewModel.onSeriesGroupToggled(contentId) },
                        onPlayDownload = { entity -> downloadsViewModel.onPlayItem(entity) },
                        onDeleteDownload = { entity -> downloadItemToDelete = entity },
                        onNavigateToContentDetail = onNavigateToContentDetail
                    )
                }
            }
        }
    }

    // Diálogos de confirmación
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(text = "Olvidar todo el historial") },
            text = { Text(text = "¿Estás seguro de que deseas olvidar todo el historial de reproducción? Esta acción es irreversible y eliminará todos tus puntos de guardado.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playbackHistoryViewModel.clearAllHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(text = "Sí, olvidar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    historyItemToDelete?.let { progress ->
        AlertDialog(
            onDismissRequest = { historyItemToDelete = null },
            title = { Text(text = "Olvidar título del historial") },
            text = { Text(text = "¿Deseas eliminar este título de tu historial de reproducción?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        playbackHistoryViewModel.deleteHistoryItem(progress)
                        historyItemToDelete = null
                    }
                ) {
                    Text(text = "Olvidar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { historyItemToDelete = null }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    favoriteToDelete?.let { fav ->
        AlertDialog(
            onDismissRequest = { favoriteToDelete = null },
            title = { Text(text = "Eliminar de favoritos") },
            text = { Text(text = "¿Estás seguro de que quieres eliminar '${fav.title}' de tus favoritos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        favoritosViewModel.removeFavorite(fav)
                        favoriteToDelete = null
                    }
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { favoriteToDelete = null }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    if (showClearFavoritesDialog) {
        AlertDialog(
            onDismissRequest = { showClearFavoritesDialog = false },
            title = { Text(text = "Vaciar todos los favoritos") },
            text = { Text(text = "¿Estás seguro de que deseas eliminar todas las obras guardadas en tus favoritos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        favoritosViewModel.removeAllFavorites()
                        showClearFavoritesDialog = false
                    }
                ) {
                    Text(text = "Eliminar todos", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFavoritesDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Menú de opciones de favorito (abierto por mantener presionado o acciones de accesibilidad)
    favoriteForOptionsMenu?.let { fav ->
        ModalBottomSheet(
            onDismissRequest = { favoriteForOptionsMenu = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = fav.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                ListItem(
                    headlineContent = { Text("Ver detalles de la obra", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            val typeForNav = when (fav.contentType.lowercase()) {
                                "movie", "peliculas" -> "peliculas"
                                "serie", "series" -> "series"
                                "documentary", "documentales" -> "documentales"
                                "shortfilm", "short", "cortometrajes" -> "cortometrajes"
                                else -> "unknown"
                            }
                            favoriteForOptionsMenu = null
                            onNavigateToContentDetail(fav.contentId, typeForNav)
                        }
                )

                ListItem(
                    headlineContent = { Text("Reproducir ahora", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            favoriteForOptionsMenu = null
                            favoritosViewModel.playFavorite(fav)
                        }
                )

                ListItem(
                    headlineContent = { Text("Eliminar de favoritos", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier
                        .clickable {
                            favoriteForOptionsMenu = null
                            favoriteToDelete = fav
                        }
                )
            }
        }
    }

    // Menú de opciones de historial (abierto por mantener presionado o acciones de accesibilidad)
    historyItemForOptionsMenu?.let { displayItem ->
        val progress = displayItem.playbackProgress
        val title = displayItem.catalogItem?.title ?: progress.contentId
        val typeForNav = when (progress.contentType.lowercase()) {
            "movie", "peliculas", "pelicula" -> "peliculas"
            "serie", "series" -> "series"
            "documentary", "documentales", "documental" -> "documentales"
            "shortfilm", "short", "cortometrajes", "cortometraje" -> "cortometrajes"
            else -> "unknown"
        }

        ModalBottomSheet(
            onDismissRequest = { historyItemForOptionsMenu = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                ListItem(
                    headlineContent = { Text("Continuar escuchando", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            val item = displayItem.catalogItem
                            historyItemForOptionsMenu = null
                            if (item != null) {
                                onNavigateToPlayer(item, progress.partIndex, progress.episodeIndex, progress.currentPositionMs)
                            }
                        }
                )

                ListItem(
                    headlineContent = { Text("Ver detalles de la obra", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier
                        .clickable {
                            historyItemForOptionsMenu = null
                            onNavigateToContentDetail(progress.contentId, typeForNav)
                        }
                )

                ListItem(
                    headlineContent = { Text("Olvidar del historial", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier
                        .clickable {
                            historyItemForOptionsMenu = null
                            historyItemToDelete = progress
                        }
                )
            }
        }
    }

    if (showDeleteAllDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDownloadsDialog = false },
            title = { Text(text = "Eliminar Todas las Descargas") },
            text = { Text(text = "¿Estás seguro de que quieres eliminar TODAS tus descargas? Esta acción borrará todos los archivos de audio y liberará espacio en el dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.deleteAllDownloads()
                        showDeleteAllDownloadsDialog = false
                    }
                ) {
                    Text(text = "Liberar espacio", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDownloadsDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    downloadItemToDelete?.let { entity ->
        AlertDialog(
            onDismissRequest = { downloadItemToDelete = null },
            title = { Text(text = "Eliminar descarga") },
            text = { Text(text = "¿Deseas eliminar '${entity.title}' de tu dispositivo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadsViewModel.confirmDeleteItem(entity.contentId, entity.partIndex, entity.episodeIndex, false)
                        downloadItemToDelete = null
                    }
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadItemToDelete = null }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    if (showFilterDialog) {
        val filterOptions = listOf(
            DownloadFilter.All,
            DownloadFilter.Completed,
            DownloadFilter.InProgress,
            DownloadFilter.Queued,
            DownloadFilter.Failed
        )
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text(text = "Filtrar descargas", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filterOptions.forEach { filter ->
                        val isSelected = currentFilter == filter
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        downloadsViewModel.setFilter(filter)
                                        showFilterDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = filter.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) {
                    Text(text = "Cerrar")
                }
            }
        )
    }
}

// ==========================================
// PESTAÑA 1: HISTORIAL DE REPRODUCCIÓN
// ==========================================
@Composable
fun HistoryTabContent(
    historyItems: List<HistoryListItem>,
    onItemClick: (PlaybackProgressEntity, CatalogItem?) -> Unit,
    onOptionsClick: (HistoryListItem.Item) -> Unit,
    onDeleteClick: (PlaybackProgressEntity) -> Unit,
    onViewDetail: (String, String) -> Unit
) {
    val itemsOnly = remember(historyItems) { historyItems.filterIsInstance<HistoryListItem.Item>() }

    if (itemsOnly.isEmpty()) {
        EmptyListView(
            icon = Icons.Default.History,
            title = "No tienes historial de reproducción",
            description = "Los títulos que comiences a escuchar aparecerán aquí con su punto de guardado exacto para que continúes cuando quieras."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = historyItems,
                key = { item ->
                    when (item) {
                        is HistoryListItem.Header -> "header_${item.date}"
                        is HistoryListItem.Item -> "${item.playbackProgress.contentId}_${item.playbackProgress.partIndex}_${item.playbackProgress.episodeIndex}"
                    }
                }
            ) { listItem ->
                when (listItem) {
                    is HistoryListItem.Header -> {
                        Text(
                            text = listItem.date,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clearAndSetSemantics {
                                    heading()
                                    stateDescription = "Encabezado"
                                    contentDescription = listItem.date
                                }
                        )
                    }
                    is HistoryListItem.Item -> {
                        val progress = listItem.playbackProgress
                        val typeForNav = when (progress.contentType.lowercase()) {
                            "movie", "peliculas", "pelicula" -> "peliculas"
                            "serie", "series" -> "series"
                            "documentary", "documentales", "documental" -> "documentales"
                            "shortfilm", "short", "cortometrajes", "cortometraje" -> "cortometrajes"
                            else -> "unknown"
                        }
                        HistoryItemCard(
                            displayItem = listItem,
                            onClick = { onItemClick(listItem.playbackProgress, listItem.catalogItem) },
                            onLongClick = { onOptionsClick(listItem) },
                            onViewDetail = { onViewDetail(progress.contentId, typeForNav) },
                            onDeleteClick = { onDeleteClick(listItem.playbackProgress) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatRemainingTimeSpanish(remainingMs: Long): String {
    if (remainingMs <= 0L) return "Completado"

    val totalSeconds = remainingMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0 -> {
            if (minutes > 0) {
                "$hours ${if (hours == 1L) "hora" else "horas"} y $minutes ${if (minutes == 1L) "minuto" else "minutos"} restantes"
            } else {
                "$hours ${if (hours == 1L) "hora" else "horas"} restantes"
            }
        }
        minutes > 0 -> {
            if (seconds > 0) {
                "$minutes ${if (minutes == 1L) "minuto" else "minutos"} y $seconds ${if (seconds == 1L) "segundo" else "segundos"} restantes"
            } else {
                "$minutes ${if (minutes == 1L) "minuto" else "minutos"} restantes"
            }
        }
        else -> {
            "$seconds ${if (seconds == 1L) "segundo" else "segundos"} restantes"
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    displayItem: HistoryListItem.Item,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onViewDetail: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = displayItem.playbackProgress
    val title = displayItem.catalogItem?.title ?: progress.contentId

    val lastSeenLabel = remember(displayItem.catalogItem, progress.contentType, progress.partIndex, progress.episodeIndex) {
        val seasonNum = progress.partIndex + 1
        val episodeNum = progress.episodeIndex + 1
        when (progress.contentType.lowercase()) {
            "serie", "series" -> {
                val serie = displayItem.catalogItem as? com.johang.audiocinemateca.data.model.Serie
                val episodeTitle = serie?.capitulos?.get(seasonNum.toString())?.getOrNull(progress.episodeIndex)?.titulo
                    ?: serie?.capitulos?.get("temporada $seasonNum")?.getOrNull(progress.episodeIndex)?.titulo
                if (!episodeTitle.isNullOrBlank()) {
                    "T$seasonNum:E$episodeNum • $episodeTitle"
                } else {
                    "T$seasonNum:E$episodeNum"
                }
            }
            else -> {
                if (progress.partIndex > 0) {
                    "Parte ${progress.partIndex + 1}"
                } else {
                    ""
                }
            }
        }
    }

    val progressFraction = remember(progress.currentPositionMs, progress.totalDurationMs) {
        if (progress.totalDurationMs > 0) {
            (progress.currentPositionMs.toFloat() / progress.totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    val remainingMs = remember(progress.currentPositionMs, progress.totalDurationMs) {
        (progress.totalDurationMs - progress.currentPositionMs).coerceAtLeast(0L)
    }

    val remainingText = remember(remainingMs) {
        formatRemainingTimeSpanish(remainingMs)
    }

    val context = LocalContext.current
    val showAccessibilityHints = remember(context) {
        SharedPreferencesManager(context).getBoolean("enable_accessibility_hints", true)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                if (showAccessibilityHints) {
                    stateDescription = "Mantén presionado para opciones del historial"
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (lastSeenLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = lastSeenLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = remainingText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Barra de progreso real de Compose con ProgressBarRangeInfo
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .width(48.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

// ==========================================
// PESTAÑA 2: FAVORITOS
// ==========================================
@Composable
fun FavoritesTabContent(
    favorites: List<FavoriteEntity>,
    onItemClick: (FavoriteEntity) -> Unit,
    onOptionsClick: (FavoriteEntity) -> Unit,
    onPlayClick: (FavoriteEntity) -> Unit,
    onDeleteClick: (FavoriteEntity) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyListView(
            icon = Icons.Default.FavoriteBorder,
            title = "No tienes favoritos guardados",
            description = "Marca tus películas, series y documentales favoritos con el corazón para tenerlos reunidos en este lugar."
        )
    } else {
        val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = favorites,
                key = { it.contentId }
            ) { favorite ->
                FavoriteGridCard(
                    favorite = favorite,
                    dateFormat = dateFormat,
                    onItemClick = { onItemClick(favorite) },
                    onOptionsClick = { onOptionsClick(favorite) },
                    onPlayClick = { onPlayClick(favorite) },
                    onDeleteClick = { onDeleteClick(favorite) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteGridCard(
    favorite: FavoriteEntity,
    dateFormat: SimpleDateFormat,
    onItemClick: () -> Unit,
    onOptionsClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateStr = remember(favorite.addedAt) { dateFormat.format(Date(favorite.addedAt)) }
    val typeBadge = remember(favorite.contentType) {
        when (favorite.contentType.lowercase()) {
            "movie", "peliculas" -> "Película"
            "serie", "series" -> "Serie"
            "documentary", "documentales" -> "Documental"
            "shortfilm", "cortometraje" -> "Corto"
            else -> favorite.contentType.replaceFirstChar { it.uppercase() }
        }
    }

    val context = LocalContext.current
    val showAccessibilityHints = remember(context) {
        SharedPreferencesManager(context).getBoolean("enable_accessibility_hints", true)
    }

    val fullContentDescription = remember(favorite.title, typeBadge, showAccessibilityHints) {
        buildInteractionDescription(
            mainContent = "${favorite.title}. $typeBadge",
            hintText = "Mantén presionado para opciones de favoritos",
            showHint = showAccessibilityHints
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onOptionsClick
            )
            .clearAndSetSemantics {
                contentDescription = fullContentDescription
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (favorite.contentType.lowercase()) {
                                "serie", "series" -> Icons.Default.VideoLibrary
                                else -> Icons.Default.Favorite
                            },
                            contentDescription = null,
                            tint = if (favorite.contentType.lowercase().contains("serie")) MaterialTheme.colorScheme.primary else Color(0xFFE57373),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = favorite.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "$typeBadge • $dateStr",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DownloadsTabContent(
    uiState: DownloadsUiState,
    totalSizeMb: Double,
    totalDurationMs: Long,
    currentFilter: DownloadFilter,
    onFilterClick: () -> Unit = {},
    onToggleSeries: (String) -> Unit,
    onPlayDownload: (DownloadEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
    onNavigateToContentDetail: (contentId: String, contentType: String) -> Unit
) {
    when (uiState) {
        is DownloadsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DownloadsUiState.Success -> {
            if (uiState.downloads.isEmpty()) {
                EmptyListView(
                    icon = Icons.Default.Download,
                    title = "No tienes descargas",
                    description = "Descarga películas, capítulos de series y documentales para disfrutarlos cuando no tengas conexión a internet."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        StorageSummaryCard(
                            totalSizeMb = totalSizeMb,
                            totalDurationMs = totalDurationMs,
                            currentFilter = currentFilter,
                            onFilterClick = onFilterClick
                        )
                    }

                    items(
                        items = uiState.downloads,
                        key = { it.contentId }
                    ) { item ->
                        if (item.contentType == "serie") {
                            SeriesDownloadGroupCard(
                                group = item,
                                onToggle = { onToggleSeries(item.contentId) },
                                onPlayEpisode = onPlayDownload,
                                onDeleteEpisode = onDeleteDownload,
                                onViewDetail = { onNavigateToContentDetail(item.contentId, item.contentType) }
                            )
                        } else {
                            SingleDownloadCard(
                                download = item,
                                onPlay = {
                                    item.episodes.firstOrNull()?.let { onPlayDownload(it) }
                                },
                                onViewDetail = {
                                    onNavigateToContentDetail(item.contentId, item.contentType)
                                },
                                onDelete = {
                                    item.episodes.firstOrNull()?.let { onDeleteDownload(it) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StorageSummaryCard(
    totalSizeMb: Double,
    totalDurationMs: Long,
    currentFilter: DownloadFilter,
    onFilterClick: () -> Unit = {}
) {
    val sizeText = remember(totalSizeMb) {
        if (totalSizeMb >= 1024) {
            String.format(Locale.getDefault(), "%.1f GB", totalSizeMb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", totalSizeMb)
        }
    }

    val durationText = remember(totalDurationMs) {
        TimeFormatUtils.formatDuration(totalDurationMs)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { }
                ) {
                    Text(
                        text = "Almacenamiento ocupado: $sizeText",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Duración total: $durationText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                if (currentFilter != DownloadFilter.All) {
                    SuggestionChip(
                        onClick = onFilterClick,
                        label = { Text(text = currentFilter.displayName, fontSize = 11.sp) },
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Filtro activo: ${currentFilter.displayName}. Toca para cambiar filtro",
                            role = Role.Button,
                            onClickAction = onFilterClick
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SingleDownloadCard(
    download: GroupedDownload,
    onPlay: () -> Unit,
    onViewDetail: () -> Unit,
    onDelete: () -> Unit
) {
    val entity = download.episodes.firstOrNull() ?: return
    val sizeText = String.format(Locale.getDefault(), "%.1f MB", download.totalSizeMb)
    val statusText = when (entity.downloadStatus) {
        "COMPLETE" -> "Descargado"
        "DOWNLOADING" -> "Descargando..."
        "QUEUED" -> "En cola"
        else -> "Error en descarga"
    }

    var showOptionsSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val showAccessibilityHints = remember(context) {
        SharedPreferencesManager(context).getBoolean("enable_accessibility_hints", true)
    }

    val fullContentDescription = remember(download.title, statusText, sizeText, showAccessibilityHints) {
        buildInteractionDescription(
            mainContent = "${download.title}. $statusText. $sizeText",
            hintText = "Mantén presionado para opciones de descarga",
            showHint = showAccessibilityHints
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { showOptionsSheet = true }
            )
            .clearAndSetSemantics {
                contentDescription = fullContentDescription
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = download.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$statusText • $sizeText",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.semantics {
                    contentDescription = "Reproducir ${download.title}"
                    role = Role.Button
                }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = { showOptionsSheet = true },
                modifier = Modifier.semantics {
                    contentDescription = "Opciones para ${download.title}"
                    role = Role.Button
                }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showOptionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                ListItem(
                    headlineContent = { Text("Reproducir contenido", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showOptionsSheet = false
                        onPlay()
                    }
                )

                ListItem(
                    headlineContent = { Text("Ver detalles de la obra", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        showOptionsSheet = false
                        onViewDetail()
                    }
                )

                ListItem(
                    headlineContent = { Text("Eliminar de descargas", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        showOptionsSheet = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
fun SeriesDownloadGroupCard(
    group: GroupedDownload,
    onToggle: () -> Unit,
    onPlayEpisode: (DownloadEntity) -> Unit,
    onDeleteEpisode: (DownloadEntity) -> Unit,
    onViewDetail: () -> Unit = {}
) {
    val sizeText = String.format(Locale.getDefault(), "%.1f MB", group.totalSizeMb)
    val countText = "${group.episodes.size} episodios descargados"

    val context = LocalContext.current
    val showAccessibilityHints = remember(context) {
        SharedPreferencesManager(context).getBoolean("enable_accessibility_hints", true)
    }

    val fullContentDescription = remember(group.title, countText, sizeText, group.isExpanded, showAccessibilityHints) {
        val stateText = if (group.isExpanded) "Expandido" else "Contraído"
        val hintText = if (group.isExpanded) "Toca para contraer" else "Toca para expandir"
        buildInteractionDescription(
            mainContent = "${group.title}. $countText. $sizeText. $stateText",
            hintText = hintText,
            showHint = showAccessibilityHints
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics {
                        contentDescription = fullContentDescription
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = group.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$countText • $sizeText",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Icon(
                    imageVector = if (group.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = group.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    group.episodes.forEach { episode ->
                        val epSize = String.format(Locale.getDefault(), "%.1f MB", episode.totalSizeMb)
                        val epDescription = remember(episode.title, epSize, showAccessibilityHints) {
                            buildInteractionDescription(
                                mainContent = "${episode.title}. $epSize",
                                showHint = showAccessibilityHints
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onPlayEpisode(episode) })
                                .clearAndSetSemantics {
                                    contentDescription = epDescription
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = episode.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = epSize,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                IconButton(
                                    onClick = { onPlayEpisode(episode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Reproducir ${episode.title}"
                                        role = Role.Button
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteEpisode(episode) },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Eliminar ${episode.title}"
                                        role = Role.Button
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyListView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
