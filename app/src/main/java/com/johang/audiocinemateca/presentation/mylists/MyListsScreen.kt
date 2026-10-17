package com.johang.audiocinemateca.presentation.mylists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
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
    var historyItemToDelete by remember { mutableStateOf<PlaybackProgressEntity?>(null) }
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
                    when (selectedTabIndex) {
                        0 -> {
                            if (historyItems.any { it is HistoryListItem.Item }) {
                                IconButton(
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.audiocinematecaAccessibility(
                                        label = "Olvidar todo el historial",
                                        role = Role.Button
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        2 -> {
                            IconButton(
                                onClick = { showFilterDialog = true },
                                modifier = Modifier.audiocinematecaAccessibility(
                                    label = "Filtrar descargas. Filtro actual: ${currentFilter.displayName}",
                                    role = Role.Button
                                )
                            ) {
                                Icon(imageVector = Icons.Default.FilterList, contentDescription = null)
                            }
                            if (totalSizeMb > 0) {
                                IconButton(
                                    onClick = { showDeleteAllDownloadsDialog = true },
                                    modifier = Modifier.audiocinematecaAccessibility(
                                        label = "Eliminar todas las descargas",
                                        role = Role.Button
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
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
                contentColor = MaterialTheme.colorScheme.primary
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
                            contentDescription = "$title, ${index + 1} de ${tabTitles.size}"
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
                        onDeleteClick = { progress -> historyItemToDelete = progress }
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
                        onToggleSeries = { contentId -> downloadsViewModel.onSeriesGroupToggled(contentId) },
                        onPlayDownload = { entity -> downloadsViewModel.onPlayItem(entity) },
                        onDeleteDownload = { entity -> downloadItemToDelete = entity }
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
                    modifier = Modifier.clickable {
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
                    modifier = Modifier.clickable {
                        favoriteForOptionsMenu = null
                        favoritosViewModel.playFavorite(fav)
                    }
                )

                ListItem(
                    headlineContent = { Text("Eliminar de favoritos", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        favoriteForOptionsMenu = null
                        favoriteToDelete = fav
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
            title = { Text(text = "Filtrar descargas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    filterOptions.forEach { filter ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    downloadsViewModel.setFilter(filter)
                                    showFilterDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = currentFilter == filter,
                                onClick = {
                                    downloadsViewModel.setFilter(filter)
                                    showFilterDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = filter.displayName, fontSize = 16.sp)
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
    onDeleteClick: (PlaybackProgressEntity) -> Unit
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = "Fecha: ${listItem.date}"
                                }
                            ) {
                                Text(
                                    text = listItem.date,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    is HistoryListItem.Item -> {
                        HistoryItemCard(
                            displayItem = listItem,
                            onClick = { onItemClick(listItem.playbackProgress, listItem.catalogItem) },
                            onDeleteClick = { onDeleteClick(listItem.playbackProgress) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    displayItem: HistoryListItem.Item,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = displayItem.playbackProgress
    val title = displayItem.catalogItem?.title ?: progress.contentId

    val typeLabel = remember(progress.contentType, progress.partIndex, progress.episodeIndex) {
        when (progress.contentType.lowercase()) {
            "serie" -> "Serie • T${progress.partIndex + 1}:E${progress.episodeIndex + 1}"
            "movie", "pelicula" -> if (progress.partIndex > 0) "Película • Parte ${progress.partIndex + 1}" else "Película"
            "documentary", "documental" -> "Documental"
            "shortfilm", "cortometraje" -> "Cortometraje"
            else -> progress.contentType.replaceFirstChar { it.uppercase() }
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
        if (remainingMs > 0) "Resta: ${TimeFormatUtils.formatDuration(remainingMs)}" else "Completado"
    }

    val percentText = (progressFraction * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDeleteClick
            )
            .clearAndSetSemantics {
                contentDescription = "$title, $typeLabel. Progreso: $percentText por ciento, $remainingText"
                onClick(label = "Continuar escuchando") {
                    onClick()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Olvidar del historial") {
                        onDeleteClick()
                        true
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = typeLabel,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$percentText% escuchado",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = remainingText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
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
            columns = GridCells.Adaptive(minSize = 155.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            "movie", "peliculas" -> "PELÍCULA"
            "serie", "series" -> "SERIE"
            "documentary", "documentales" -> "DOCUMENTAL"
            "shortfilm", "cortometraje" -> "CORTO"
            else -> favorite.contentType.uppercase()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onOptionsClick
            )
            .clearAndSetSemantics {
                contentDescription = "${favorite.title}, $typeBadge, guardado el $dateStr"
                onClick(label = "Ver detalles") {
                    onItemClick()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Opciones") {
                        onOptionsClick()
                        true
                    },
                    CustomAccessibilityAction("Reproducir ahora") {
                        onPlayClick()
                        true
                    },
                    CustomAccessibilityAction("Eliminar de favoritos") {
                        onDeleteClick()
                        true
                    }
                )
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = typeBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = favorite.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Guardado: $dateStr",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ==========================================
// PESTAÑA 3: DESCARGAS
// ==========================================
@Composable
fun DownloadsTabContent(
    uiState: DownloadsUiState,
    totalSizeMb: Double,
    totalDurationMs: Long,
    currentFilter: DownloadFilter,
    onToggleSeries: (String) -> Unit,
    onPlayDownload: (DownloadEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit
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
                            currentFilter = currentFilter
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
                                onDeleteEpisode = onDeleteDownload
                            )
                        } else {
                            SingleDownloadCard(
                                download = item,
                                onPlay = {
                                    item.episodes.firstOrNull()?.let { onPlayDownload(it) }
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
    currentFilter: DownloadFilter
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
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Almacenamiento ocupado: $sizeText, Duración total: $durationText${if (currentFilter != DownloadFilter.All) ", Filtro: ${currentFilter.displayName}" else ""}"
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
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
                        onClick = {},
                        label = { Text(text = currentFilter.displayName, fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
fun SingleDownloadCard(
    download: GroupedDownload,
    onPlay: () -> Unit,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .clearAndSetSemantics {
                contentDescription = "${download.title}, $statusText, $sizeText"
                onClick(label = "Reproducir") {
                    onPlay()
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Eliminar descarga") {
                        onDelete()
                        true
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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

            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
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
    onDeleteEpisode: (DownloadEntity) -> Unit
) {
    val sizeText = String.format(Locale.getDefault(), "%.1f MB", group.totalSizeMb)
    val countText = "${group.episodes.size} episodios descargados"

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(14.dp)
                    .clearAndSetSemantics {
                        contentDescription = "${group.title}, $countText, $sizeText, ${if (group.isExpanded) "Expandido" else "Contraído"}"
                        onClick(label = if (group.isExpanded) "Contraer" else "Expandir") {
                            onToggle()
                            true
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayEpisode(episode) }
                                .padding(vertical = 8.dp)
                                .clearAndSetSemantics {
                                    contentDescription = "${episode.title}, $epSize"
                                    onClick(label = "Reproducir episodio") {
                                        onPlayEpisode(episode)
                                        true
                                    }
                                    customActions = listOf(
                                        CustomAccessibilityAction("Eliminar episodio") {
                                            onDeleteEpisode(episode)
                                            true
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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

                            IconButton(onClick = { onPlayEpisode(episode) }) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            IconButton(onClick = { onDeleteEpisode(episode) }) {
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
