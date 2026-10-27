package com.johang.audiocinemateca.presentation.contentdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Episode
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.repository.RatingStats
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.domain.model.DownloadProgressInfo
import com.johang.audiocinemateca.presentation.catalog.DetailToolbar
import com.johang.audiocinemateca.util.TimeFormatUtils
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.buildInteractionDescription
import com.johang.audiocinemateca.util.initialAccessibilityFocus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentDetailScreen(
    item: CatalogItem?,
    isLoading: Boolean,
    isFavorite: Boolean,
    ratingStats: RatingStats,
    viewCountText: String,
    downloadState: DownloadState,
    singleProgress: DownloadProgressInfo?,
    seasonProgress: SeasonProgressInfo?,
    episodeStates: Map<String, DownloadState>,
    selectedSeasonIndex: Int,
    playbackProgressList: List<PlaybackProgressEntity>,
    isTtsSpeaking: Boolean,
    onBackClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareClick: () -> Unit,
    onPlayClick: (partIndex: Int, episodeIndex: Int) -> Unit,
    onDownloadClick: () -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onSeasonDownloadClick: (Int) -> Unit,
    onEpisodeDownloadClick: (seasonIndex: Int, episodeIndex: Int) -> Unit,
    onOpenFilmAffinity: (String) -> Unit,
    onTtsToggle: () -> Unit,
    onSubmitRating: (Int) -> Unit
) {
    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedMetadataDetail by remember { mutableStateOf<MetadataItem?>(null) }
    var showFullTechnicalSheet by remember { mutableStateOf(false) }
    var showSeasonDropdown by remember { mutableStateOf(false) }

    if (showRatingDialog) {
        RatingDialog(
            initialRating = if (ratingStats.userRating > 0) ratingStats.userRating else 5,
            onDismissRequest = { showRatingDialog = false },
            onSubmit = { stars ->
                onSubmitRating(stars)
            }
        )
    }

    selectedMetadataDetail?.let { meta ->
        AlertDialog(
            onDismissRequest = { selectedMetadataDetail = null },
            title = { Text(text = meta.label, fontWeight = FontWeight.Bold) },
            text = { Text(text = meta.value, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { selectedMetadataDetail = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    val toolbarTitle = remember(item) {
        when (item) {
            is Movie -> "Detalles de la película"
            is Serie -> "Detalles de la serie"
            else -> "Detalles del contenido"
        }
    }

    Scaffold(
        modifier = Modifier.semantics { isTraversalGroup = true },
        topBar = {
            Box(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                }
            ) {
                DetailToolbar(
                    title = toolbarTitle,
                    isFavorite = isFavorite,
                    onBackClick = onBackClick,
                    onFavoriteClick = onFavoriteToggle,
                    onShareClick = onShareClick
                )
            }
        }
    ) { paddingValues ->
        if (isLoading && item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Contenido no encontrado",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val seasons = remember(item) { if (item is Serie) item.capitulos.keys.sorted() else emptyList() }
            val currentSeasonKey = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()
            val episodes = remember(item, currentSeasonKey) {
                if (item is Serie && currentSeasonKey != null) item.capitulos[currentSeasonKey] ?: emptyList() else emptyList()
            }

            val metadataList = remember(item) {
                val list = mutableListOf<MetadataItem>()
                if (item.anio.isNotBlank()) list.add(MetadataItem("Año", item.anio))

                val formattedGenres = if (item.genero.isNotBlank()) {
                    item.genero.split(",", "/", "|").map { it.trim() }.filter { it.isNotBlank() }.joinToString(" • ")
                } else ""
                if (formattedGenres.isNotBlank()) list.add(MetadataItem("Género", formattedGenres))

                if (item.pais.isNotBlank()) list.add(MetadataItem("País", item.pais))

                val durationMin = item.duracion.toIntOrNull() ?: 0
                if (durationMin > 0) {
                    list.add(MetadataItem("Duración", TimeFormatUtils.formatDuration(durationMin)))
                }

                val languageStr = when (item.idioma) {
                    "1" -> "Español de España"
                    "2" -> "Español Latino"
                    else -> item.idioma
                }
                if (languageStr.isNotBlank()) list.add(MetadataItem("Idioma", languageStr))

                if (item.director.isNotBlank()) list.add(MetadataItem("Dirección", item.director))
                if (item.guion.isNotBlank()) list.add(MetadataItem("Guion", item.guion))
                if (item.musica.isNotBlank()) list.add(MetadataItem("Música", item.musica))
                if (item.fotografia.isNotBlank()) list.add(MetadataItem("Fotografía", item.fotografia))
                if (item.reparto.isNotBlank()) list.add(MetadataItem("Reparto", item.reparto))
                if (item.productora.isNotBlank()) list.add(MetadataItem("Productora", item.productora))
                if (item.narracion.isNotBlank()) list.add(MetadataItem("Narración", item.narracion))

                if (item is Serie) {
                    val seasonsCount = item.capitulos.keys.size
                    if (seasonsCount > 0) {
                        list.add(MetadataItem("Temporadas", "$seasonsCount"))
                    }
                } else if (item is Movie && item.enlaces.size > 1) {
                    list.add(MetadataItem("Partes", "${item.enlaces.size}"))
                }
                list
            }

            val chunkedMetadata = remember(metadataList) { metadataList.chunked(4) }

            // Main Content Scrollable
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header & Title
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Badges principales estilo Netflix con FlowRow responsivo
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            if (item.anio.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                ) {
                                    Text(
                                        text = item.anio,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val dubbingLabel = remember(item.idioma) {
                                when (item.idioma.trim()) {
                                    "1" -> "Doblaje Español de España"
                                    "2" -> "Doblaje Latino"
                                    else -> if (item.idioma.isNotBlank()) "Doblaje ${item.idioma}" else "Doblaje Latino"
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(horizontal = 3.dp)
                            ) {
                                Text(
                                    text = dubbingLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (item.narracion.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                ) {
                                    Text(
                                        text = "Narración: ${item.narracion}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val durationMin = item.duracion.toIntOrNull() ?: 0
                            val durationLabel = when {
                                item is Serie -> if (item.capitulos.isNotEmpty()) "${item.capitulos.keys.size} temp." else "Serie"
                                durationMin > 0 -> TimeFormatUtils.formatDuration(durationMin)
                                else -> ""
                            }
                            if (durationLabel.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                ) {
                                    Text(
                                        text = durationLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Rating row
                        val ratingText = if (ratingStats.totalRatings > 0) {
                            val avg = String.format(java.util.Locale.US, "%.1f", ratingStats.averageRating)
                            val userPart = if (ratingStats.userRating > 0) " • Tu nota: ★ ${ratingStats.userRating}" else ""
                            "★ $avg / 5 (${ratingStats.totalRatings} valoraciones)$userPart"
                        } else {
                            "Sin valoraciones aún"
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clickable(onClick = { showRatingDialog = true })
                                .clearAndSetSemantics {
                                    contentDescription = "$ratingText. Calificar este contenido"
                                    onClick(label = "Calificar este contenido") {
                                        showRatingDialog = true
                                        true
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = ratingText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // View count
                        if (viewCountText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = viewCountText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Main Play & Download Action Buttons
                item {
                    val latestProgress = remember(playbackProgressList) {
                        playbackProgressList.maxByOrNull { it.lastPlayedTimestamp }
                    }
                    val isFinished = latestProgress?.isFinished == true ||
                            (latestProgress != null && latestProgress.totalDurationMs > 0 &&
                                    latestProgress.currentPositionMs >= latestProgress.totalDurationMs - 15000)

                    val playButtonLabel: String
                    val playActionPart: Int
                    val playActionEpisode: Int

                    if (latestProgress != null && !isFinished && latestProgress.currentPositionMs < latestProgress.totalDurationMs) {
                        val remaining = TimeFormatUtils.formatDuration(latestProgress.totalDurationMs - latestProgress.currentPositionMs)
                        if (item is Serie) {
                            val seasons = item.capitulos.keys.sorted()
                            val seasonKey = seasons.getOrNull(latestProgress.partIndex)
                            val ep = item.capitulos[seasonKey]?.getOrNull(latestProgress.episodeIndex)
                            playButtonLabel = ep?.let { "Continuar T${latestProgress.partIndex + 1}:E${it.capitulo} - ${it.titulo} ($remaining restantes)" }
                                ?: "Continuar escuchando ($remaining restantes)"
                        } else {
                            playButtonLabel = "Continuar escuchando ($remaining restantes)"
                        }
                        playActionPart = latestProgress.partIndex
                        playActionEpisode = latestProgress.episodeIndex
                    } else if (latestProgress != null && isFinished && item is Serie) {
                        val seasonIndex = latestProgress.partIndex.coerceAtLeast(0)
                        val episodeIndex = latestProgress.episodeIndex.coerceAtLeast(0)
                        val seasons = item.capitulos.keys.sorted()
                        val currentSeasonKey = seasons.getOrNull(seasonIndex)
                        val currentSeasonEpisodes = item.capitulos[currentSeasonKey] ?: emptyList()

                        val (nextSeason, nextEpisode, nextEpObj) = if (episodeIndex + 1 < currentSeasonEpisodes.size) {
                            Triple(seasonIndex, episodeIndex + 1, currentSeasonEpisodes[episodeIndex + 1])
                        } else if (seasonIndex + 1 < seasons.size) {
                            val nextKey = seasons[seasonIndex + 1]
                            val nextEps = item.capitulos[nextKey] ?: emptyList()
                            if (nextEps.isNotEmpty()) Triple(seasonIndex + 1, 0, nextEps[0]) else Triple(-1, -1, null)
                        } else Triple(-1, -1, null)

                        if (nextEpObj != null) {
                            playButtonLabel = "Siguiente: T${nextSeason + 1}:E${nextEpObj.capitulo} - ${nextEpObj.titulo}"
                            playActionPart = nextSeason
                            playActionEpisode = nextEpisode
                        } else {
                            playButtonLabel = "Comenzar de nuevo"
                            playActionPart = 0
                            playActionEpisode = 0
                        }
                    } else {
                        playButtonLabel = "Comenzar a oír"
                        playActionPart = 0
                        playActionEpisode = if (item is Serie) 0 else -1
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onPlayClick(playActionPart, playActionEpisode) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clearAndSetSemantics {
                                        testTag = "content_detail_play_button"
                                        traversalIndex = -1f
                                        contentDescription = playButtonLabel
                                        role = Role.Button
                                        onClick(label = playButtonLabel) {
                                            onPlayClick(playActionPart, playActionEpisode)
                                            true
                                        }
                                    }
                                    .initialAccessibilityFocus(tag = "content_detail_play_button"),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = playButtonLabel,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (item !is Serie) {
                                val downloadButtonText = when (downloadState) {
                                    is DownloadState.Downloading -> "Cancelar"
                                    is DownloadState.Downloaded -> "Eliminar"
                                    is DownloadState.Failed -> "Reintentar"
                                    else -> "Descargar"
                                }
                                val downloadAccessibilityLabel = when (downloadState) {
                                    is DownloadState.Downloading -> "Cancelar descarga de ${item.title}"
                                    is DownloadState.Downloaded -> "Eliminar descarga de ${item.title}"
                                    is DownloadState.Failed -> "Reintentar descarga de ${item.title}"
                                    else -> "Descargar ${item.title}"
                                }

                                OutlinedButton(
                                    onClick = onDownloadClick,
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = downloadAccessibilityLabel
                                        role = Role.Button
                                        onClick(label = downloadAccessibilityLabel) {
                                            onDownloadClick()
                                            true
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = when (downloadState) {
                                            is DownloadState.Downloaded, is DownloadState.Downloading -> Icons.Default.Close
                                            else -> Icons.Default.FileDownload
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = downloadButtonText)
                                }
                            }
                        }

                        // Single Download Progress (Movies/Docs/Shorts)
                        if (singleProgress != null && singleProgress.totalBytes > 0) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val speed = TimeFormatUtils.formatSpeed(singleProgress.speedBytesPerSec)
                                    val remaining = TimeFormatUtils.formatRemainingTime(singleProgress.remainingSeconds)
                                    LinearProgressIndicator(
                                        progress = { singleProgress.progress / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp),
                                    )
                                    Text(
                                        text = "${singleProgress.progress}% • $speed • Quedan $remaining",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Sinopsis Section estilo Netflix (Clic directo para lectura TTS)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTtsToggle)
                            .clearAndSetSemantics {
                                val ttsHint = if (isTtsSpeaking) "Toca para detener la lectura por voz" else "Toca para escuchar la sinopsis con síntesis de voz"
                                contentDescription = "Sinopsis: ${item.sinopsis.ifBlank { "Sin sinopsis disponible." }}. $ttsHint."
                                role = Role.Button
                                onClick(label = if (isTtsSpeaking) "Detener lectura TTS" else "Escuchar sinopsis") {
                                    onTtsToggle()
                                    true
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sinopsis",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isTtsSpeaking) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = if (isTtsSpeaking) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isTtsSpeaking) "Leyendo..." else "Toca para oír",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isTtsSpeaking) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = item.sinopsis.ifBlank { "Sin sinopsis disponible." },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 24.sp
                            )
                        }
                    }
                }

                // Resumen de Elenco y Creadores estilo Netflix
                if (item.reparto.isNotBlank() || item.director.isNotBlank() || item.guion.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (item.reparto.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Protagonistas: ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = item.reparto,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val creatorLabel = remember(item) {
                                if (item.director.isNotBlank()) "Creado por: " else if (item.guion.isNotBlank()) "Guion: " else ""
                            }
                            val creatorValue = remember(item) {
                                if (item.director.isNotBlank()) item.director else item.guion
                            }

                            if (creatorLabel.isNotBlank()) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = creatorLabel,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        text = creatorValue,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            TextButton(
                                onClick = { showFullTechnicalSheet = true },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Más detalles e información", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Movie multi-part section
                if (item is Movie && item.enlaces.size > 1) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Partes de la Película (${item.enlaces.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                item.enlaces.forEachIndexed { index, _ ->
                                    val progress = playbackProgressList.find { p -> p.partIndex == index }
                                    val partDesc = if (progress != null && progress.currentPositionMs < progress.totalDurationMs) {
                                        "Parte ${index + 1}: Continuar (${TimeFormatUtils.formatDuration(progress.totalDurationMs - progress.currentPositionMs)} restantes)"
                                    } else if (progress != null) {
                                        "Parte ${index + 1}: Completada"
                                    } else {
                                        "Parte ${index + 1}: Reproducir ahora"
                                    }

                                    Button(
                                        onClick = { onPlayClick(index, -1) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .audiocinematecaAccessibility(
                                                label = partDesc,
                                                role = Role.Button,
                                                onClickAction = { onPlayClick(index, -1) }
                                            ),
                                        colors = ButtonDefaults.filledTonalButtonColors()
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(partDesc)
                                    }
                                }
                            }
                        }
                    }
                }

                // Series: Seasons & Episodes
                if (item is Serie) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Temporadas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Selector de temporadas desplegable (Lista Desplegable)
                            if (seasons.size > 1) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showSeasonDropdown = true }
                                            .clearAndSetSemantics {
                                                role = Role.DropdownList
                                                stateDescription = "${selectedSeasonIndex + 1} de ${seasons.size}"
                                                contentDescription = "Temporada ${selectedSeasonIndex + 1}"
                                                onClick(label = "Cambiar de temporada") {
                                                    showSeasonDropdown = true
                                                    true
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Temporada ${selectedSeasonIndex + 1}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${episodes.size} episodios disponibles",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "Cambiar",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSeasonDropdown,
                                        onDismissRequest = { showSeasonDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        seasons.forEachIndexed { index, seasonKey ->
                                            val seasonEpisodes = item.capitulos[seasonKey] ?: emptyList()
                                            val isSelected = selectedSeasonIndex == index
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Temporada ${index + 1}",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = "${seasonEpisodes.size} eps",
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.outline
                                                        )
                                                    }
                                                },
                                                leadingIcon = {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = null
                                                    )
                                                },
                                                onClick = {
                                                    onSeasonSelect(index)
                                                    showSeasonDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Season download button & progress
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Temporada ${selectedSeasonIndex + 1} (${episodes.size} episodios)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedButton(
                                    onClick = { onSeasonDownloadClick(selectedSeasonIndex) },
                                    modifier = Modifier.audiocinematecaAccessibility(
                                        label = "Descargar todos los episodios de la temporada ${selectedSeasonIndex + 1}",
                                        role = Role.Button,
                                        onClickAction = { onSeasonDownloadClick(selectedSeasonIndex) }
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Descargar temporada")
                                }
                            }

                            // Season Download Progress Card
                            if (seasonProgress != null && seasonProgress.isDownloading) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val currentEpNumber = (seasonProgress.downloadedEpisodes + 1).coerceAtMost(seasonProgress.totalEpisodes)
                                        val speed = TimeFormatUtils.formatSpeed(seasonProgress.speedBytesPerSec)
                                        val remaining = TimeFormatUtils.formatRemainingTime(seasonProgress.remainingSeconds)

                                        Text(
                                            text = "Descargando Temporada ${seasonProgress.seasonIndex + 1} (Episodio $currentEpNumber de ${seasonProgress.totalEpisodes})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        LinearProgressIndicator(
                                            progress = { seasonProgress.overallProgress / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                        )
                                        Text(
                                            text = "${seasonProgress.overallProgress}% total • $speed • Quedan $remaining",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Episodes items
                    itemsIndexed(
                        items = episodes,
                        key = { index, episode -> "${selectedSeasonIndex}_${index}_${episode.capitulo}" }
                    ) { index, episode ->
                        val epState = episodeStates["${selectedSeasonIndex}_$index"]
                        EpisodeRow(
                            episode = episode,
                            seasonIndex = selectedSeasonIndex,
                            episodeIndex = index,
                            state = epState,
                            onPlay = { onPlayClick(selectedSeasonIndex, index) },
                            onDownloadAction = { onEpisodeDownloadClick(selectedSeasonIndex, index) }
                        )
                    }
                }
            }

            if (showFullTechnicalSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFullTechnicalSheet = false }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp)
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Información del título",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        metadataList.forEach { meta ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics {
                                        contentDescription = "${meta.label}: ${meta.value}."
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = meta.label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(0.4f)
                                    )
                                    Text(
                                        text = meta.value,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(0.6f),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }

                        if (item.filmaffinity.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    showFullTechnicalSheet = false
                                    onOpenFilmAffinity(item.filmaffinity)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics {
                                        contentDescription = "Abrir ficha de ${item.title} en FilmAffinity"
                                        role = Role.Button
                                        onClick(label = "Ver ficha en FilmAffinity") {
                                            showFullTechnicalSheet = false
                                            onOpenFilmAffinity(item.filmaffinity)
                                            true
                                        }
                                    }
                            ) {
                                Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ver en FilmAffinity", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class MetadataItem(val label: String, val value: String)

@Composable
private fun MetadataGridCell(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label: $value"
                onClick(label = "Ver detalle de $label") {
                    onClick()
                    true
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    seasonIndex: Int,
    episodeIndex: Int,
    state: DownloadState?,
    onPlay: () -> Unit,
    onDownloadAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .audiocinematecaAccessibility(
                label = "Episodio ${episode.capitulo}: ${episode.titulo}",
                role = Role.Button,
                onClickAction = onPlay
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Episodio ${episode.capitulo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = episode.titulo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDownloadAction,
                modifier = Modifier
                    .size(36.dp)
                    .audiocinematecaAccessibility(
                        label = when (state) {
                            is DownloadState.Downloading -> "Cancelar descarga del episodio ${episode.capitulo}"
                            is DownloadState.Downloaded -> "Eliminar de descargas el episodio ${episode.capitulo}"
                            else -> "Descargar episodio ${episode.capitulo}"
                        },
                        role = Role.Button,
                        onClickAction = onDownloadAction
                    )
            ) {
                Icon(
                    imageVector = when (state) {
                        is DownloadState.Downloaded, is DownloadState.Downloading -> Icons.Default.Close
                        else -> Icons.Default.FileDownload
                    },
                    contentDescription = null,
                    tint = if (state is DownloadState.Downloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingDialog(
    initialRating: Int,
    onDismissRequest: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var rating by remember { mutableIntStateOf(initialRating.coerceIn(1, 5)) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Calificar contenido", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Selecciona de 1 a 5 estrellas para valorar esta obra:",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { starIndex ->
                        val isFilled = starIndex <= rating
                        IconButton(
                            onClick = { rating = starIndex },
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "$starIndex ${if (starIndex == 1) "estrella" else "estrellas"}. ${if (isFilled) "Seleccionada" else ""}",
                                role = Role.RadioButton,
                                onClickAction = { rating = starIndex }
                            )
                        ) {
                            Icon(
                                imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (isFilled) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "$rating de 5 estrellas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(rating)
                    onDismissRequest()
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancelar")
            }
        }
    )
}
