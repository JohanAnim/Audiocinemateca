package com.johang.audiocinemateca.presentation.player.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.johang.audiocinemateca.presentation.player.ChapterSynopsisState
import com.johang.audiocinemateca.presentation.player.PlayerViewModel
import com.johang.audiocinemateca.util.TimeFormatUtils
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.initialAccessibilityFocus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    currentPositionMs: Long,
    totalDurationMs: Long,
    playbackSpeed: Float,
    rewindIntervalSec: Int,
    forwardIntervalSec: Int,
    sleepTimerText: String?,
    isAutoPlayEnabled: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    fullscreenLayoutMode: String = "default",
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onRewindIntervalChange: (Int) -> Unit,
    onForwardIntervalChange: (Int) -> Unit,
    onSleepTimerSelect: (minutes: Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onAutoPlayToggle: () -> Unit,
    onEqualizerClick: () -> Unit,
    onOpenCommentsClick: (showKeyboard: Boolean) -> Unit,
    onShareClick: () -> Unit,
    onTitleClick: () -> Unit,
    onNavigateToPlaybackSettings: () -> Unit,
    onNavigateToHelp: () -> Unit = {},
    onToggleFullscreenLayout: () -> Unit = {},
    onAnnounceStatus: (String) -> Unit = {}
) {
    val voteStats by viewModel.voteStats.collectAsState()
    val commentPreview by viewModel.commentPreview.collectAsState()
    val chapterSynopsisState by viewModel.chapterSynopsisState.collectAsState()
    val isSynopsisExpanded by viewModel.isSynopsisExpanded.collectAsState()

    val haptic = LocalHapticFeedback.current

    var showPlayerMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showRewindIntervalDialog by remember { mutableStateOf(false) }
    var showForwardIntervalDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    val activePositionMs = if (isScrubbing) scrubPositionMs else currentPositionMs
    val progressFraction = remember(activePositionMs, totalDurationMs) {
        if (totalDurationMs > 0) (activePositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
    }

    Scaffold(
        modifier = Modifier.semantics {
            isTraversalGroup = true
        },
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "Cerrar reproductor",
                                role = Role.Button,
                                onClickAction = onBackClick
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Widget Nativo de Transmitir (Chromecast)
                            AndroidView(
                                factory = { ctx ->
                                    MediaRouteButton(ctx).apply {
                                        try {
                                            CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .audiocinematecaAccessibility(
                                        label = "Transmitir audio por Chromecast o dispositivo inteligente",
                                        role = Role.Button
                                    )
                            )

                            // Menú de Más opciones
                            Box {
                                IconButton(
                                    onClick = { showPlayerMenu = true },
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = "Más opciones"
                                        role = Role.Button
                                        onClick(label = "Más opciones") {
                                            showPlayerMenu = true
                                            true
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showPlayerMenu,
                                    onDismissRequest = { showPlayerMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (fullscreenLayoutMode == "youtube_top") {
                                                    "Diseño: Estilo YouTube"
                                                } else {
                                                    "Diseño: Predeterminado"
                                                }
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            onToggleFullscreenLayout()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Retroceso: ${formatIntervalSpoken(rewindIntervalSec)}") },
                                        leadingIcon = { Icon(rewindIconForInterval(rewindIntervalSec), contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            showRewindIntervalDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Avance: ${formatIntervalSpoken(forwardIntervalSec)}") },
                                        leadingIcon = { Icon(forwardIconForInterval(forwardIntervalSec), contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            showForwardIntervalDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ver ficha de la obra") },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            onTitleClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ayuda del reproductor") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            showHelpDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ajustes de reproducción") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = {
                                            showPlayerMenu = false
                                            onNavigateToPlaybackSettings()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Título y Subtítulo centrados a ancho completo sin cortes ni Role.Button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            lineHeight = 24.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onTitleClick)
                                .clearAndSetSemantics {
                                    contentDescription = title
                                    onClick(label = "Ver ficha del contenido") {
                                        onTitleClick()
                                        true
                                    }
                                }
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF38BDF8),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clearAndSetSemantics {
                                        contentDescription = subtitle
                                    }
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val context = LocalContext.current
        val enablePlayerGestures = remember(context) {
            com.johang.audiocinemateca.data.local.SharedPreferencesManager(context).getBoolean("enable_player_gestures", true)
        }

        val currentStatusSpeech = remember(isPlaying, activePositionMs, totalDurationMs) {
            val stateStr = if (isPlaying) "Reproduciendo" else "Pausado"
            val currentFormatted = TimeFormatUtils.formatDuration(activePositionMs)
            val totalFormatted = TimeFormatUtils.formatDuration(totalDurationMs)
            "$stateStr. $currentFormatted transcurridos de $totalFormatted."
        }

        val gestureCustomActions = remember(forwardIntervalSec, rewindIntervalSec, currentStatusSpeech) {
            listOf(
                CustomAccessibilityAction("Consultar tiempo y estado") {
                    onAnnounceStatus(currentStatusSpeech)
                    true
                },
                CustomAccessibilityAction("Adelantar ${formatIntervalSpoken(forwardIntervalSec)}") {
                    onForwardClick()
                    true
                },
                CustomAccessibilityAction("Retroceder ${formatIntervalSpoken(rewindIntervalSec)}") {
                    onRewindClick()
                    true
                }
            )
        }

        val gestureModifier = if (enablePlayerGestures) {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y
                        var totalX = 0f
                        var totalY = 0f
                        var gestureHandled = false

                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                totalX = change.position.x - startX
                                totalY = change.position.y - startY
                                val absX = kotlin.math.abs(totalX)
                                val absY = kotlin.math.abs(totalY)

                                if (!gestureHandled && (absX > 50f || absY > 50f)) {
                                    gestureHandled = true
                                    change.consume()
                                    if (absX > absY) {
                                        if (totalX > 0f) {
                                            // Deslizar a la derecha -> Adelantar
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onForwardClick()
                                        } else {
                                            // Deslizar a la izquierda -> Retroceder
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onRewindClick()
                                        }
                                    } else {
                                        if (totalY < 0f) {
                                            // Deslizar hacia arriba -> Play / Pause
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onPlayPauseClick()
                                        } else {
                                            // Deslizar hacia abajo -> Anunciar tiempo y estado por TTS
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onAnnounceStatus(currentStatusSpeech)
                                        }
                                    }
                                }
                            } else {
                                break
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0F172A))
                .then(gestureModifier)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                    customActions = gestureCustomActions
                }
        ) {
            val isYoutubeTopLayout = fullscreenLayoutMode == "youtube_top"
            val activeContentItem by viewModel.contentItem.collectAsState()
            val synopsisToDisplay = remember(activeContentItem, chapterSynopsisState) {
                when (chapterSynopsisState) {
                    is ChapterSynopsisState.Success -> (chapterSynopsisState as ChapterSynopsisState.Success).synopsis
                    else -> activeContentItem?.sinopsis ?: ""
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isYoutubeTopLayout) {
                    // Diseño alternativo (estilo YouTube): Controles arriba y paneles sociales/info abajo
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF162032),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                            PlayerMediaControlsPanel(
                                isPlaying = isPlaying,
                                activePositionMs = activePositionMs,
                                totalDurationMs = totalDurationMs,
                                progressFraction = progressFraction,
                                playbackSpeed = playbackSpeed,
                                sleepTimerText = sleepTimerText,
                                isAutoPlayEnabled = isAutoPlayEnabled,
                                rewindIntervalSec = rewindIntervalSec,
                                forwardIntervalSec = forwardIntervalSec,
                                hasPrevious = hasPrevious,
                                hasNext = hasNext,
                                onPlayPauseClick = onPlayPauseClick,
                                onRewindClick = onRewindClick,
                                onForwardClick = onForwardClick,
                                onPreviousClick = onPreviousClick,
                                onNextClick = onNextClick,
                                onSeekTo = { pos ->
                                    isScrubbing = false
                                    scrubPositionMs = pos
                                    onSeekTo(pos)
                                },
                                onScrubFraction = { fraction ->
                                    isScrubbing = true
                                    scrubPositionMs = (fraction * totalDurationMs).toLong()
                                },
                                onScrubFinished = {
                                    isScrubbing = false
                                    onSeekTo(scrubPositionMs)
                                },
                                onOpenRewindDialog = { showRewindIntervalDialog = true },
                                onOpenForwardDialog = { showForwardIntervalDialog = true },
                                onOpenSpeedDialog = { showSpeedDialog = true },
                                onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                                onEqualizerClick = onEqualizerClick,
                                onAutoPlayToggle = onAutoPlayToggle
                            )
                        }
                    }

                    PlayerSocialAndInfoSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isUserLoggedIn = viewModel.isUserLoggedIn,
                        voteStats = voteStats,
                        commentPreview = commentPreview,
                        synopsisToDisplay = synopsisToDisplay,
                        chapterSynopsisState = chapterSynopsisState,
                        isSynopsisExpanded = isSynopsisExpanded,
                        onLikeClicked = { viewModel.onLikeClicked() },
                        onDislikeClicked = { viewModel.onDislikeClicked() },
                        onShareClick = onShareClick,
                        onToggleSynopsis = { viewModel.toggleSynopsis() },
                        onOpenCommentsClick = { onOpenCommentsClick(false) }
                    )
                } else {
                    // Diseño predeterminado: Paneles sociales y de obra arriba, controles de reproducción abajo
                    PlayerSocialAndInfoSection(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isUserLoggedIn = viewModel.isUserLoggedIn,
                        voteStats = voteStats,
                        commentPreview = commentPreview,
                        synopsisToDisplay = synopsisToDisplay,
                        chapterSynopsisState = chapterSynopsisState,
                        isSynopsisExpanded = isSynopsisExpanded,
                        onLikeClicked = { viewModel.onLikeClicked() },
                        onDislikeClicked = { viewModel.onDislikeClicked() },
                        onShareClick = onShareClick,
                        onToggleSynopsis = { viewModel.toggleSynopsis() },
                        onOpenCommentsClick = { onOpenCommentsClick(false) }
                    )

                    PlayerMediaControlsPanel(
                        isPlaying = isPlaying,
                        activePositionMs = activePositionMs,
                        totalDurationMs = totalDurationMs,
                        progressFraction = progressFraction,
                        playbackSpeed = playbackSpeed,
                        sleepTimerText = sleepTimerText,
                        isAutoPlayEnabled = isAutoPlayEnabled,
                        rewindIntervalSec = rewindIntervalSec,
                        forwardIntervalSec = forwardIntervalSec,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        onPlayPauseClick = onPlayPauseClick,
                        onRewindClick = onRewindClick,
                        onForwardClick = onForwardClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onSeekTo = { pos ->
                            isScrubbing = false
                            scrubPositionMs = pos
                            onSeekTo(pos)
                        },
                        onScrubFraction = { fraction ->
                            isScrubbing = true
                            scrubPositionMs = (fraction * totalDurationMs).toLong()
                        },
                        onScrubFinished = {
                            isScrubbing = false
                            onSeekTo(scrubPositionMs)
                        },
                        onOpenRewindDialog = { showRewindIntervalDialog = true },
                        onOpenForwardDialog = { showForwardIntervalDialog = true },
                        onOpenSpeedDialog = { showSpeedDialog = true },
                        onOpenSleepTimerDialog = { showSleepTimerDialog = true },
                        onEqualizerClick = onEqualizerClick,
                        onAutoPlayToggle = onAutoPlayToggle
                    )
                }
            }
        }
    }

    // Diálogo de Velocidad de Reproducción
    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Velocidad de reproducción", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    speeds.forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onPlaybackSpeedChange(speed)
                                        showSpeedDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("${speed}x", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showRewindIntervalDialog) {
        SeekIntervalDialog(
            title = "Intervalo de retroceso",
            selectedSec = rewindIntervalSec,
            onSelect = { sec ->
                onRewindIntervalChange(sec)
                showRewindIntervalDialog = false
            },
            onDismiss = { showRewindIntervalDialog = false }
        )
    }

    if (showForwardIntervalDialog) {
        SeekIntervalDialog(
            title = "Intervalo de avance",
            selectedSec = forwardIntervalSec,
            onSelect = { sec ->
                onForwardIntervalChange(sec)
                showForwardIntervalDialog = false
            },
            onDismiss = { showForwardIntervalDialog = false }
        )
    }

    // Diálogo de Temporizador de Apagado
    if (showSleepTimerDialog) {
        val timerOptions = listOf("Desactivado", "15 minutos", "30 minutos", "45 minutos", "60 minutos", "90 minutos", "120 minutos")
        val timerValues = listOf(0L, 15L, 30L, 45L, 60L, 90L, 120L)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Temporizador de apagado", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    timerOptions.forEachIndexed { index, option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = false,
                                    onClick = {
                                        val min = timerValues[index]
                                        if (min == 0L) onCancelSleepTimer() else onSleepTimerSelect(min)
                                        showSleepTimerDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = false, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepTimerDialog = false }) { Text("Cancelar") } }
        )
    }

    // Diálogo de Ayuda del Reproductor
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Ayuda del reproductor", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Gestos táctiles rápidos (si están activados en Ajustes):",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Deslizar a la derecha: Adelantar ${formatIntervalSpoken(forwardIntervalSec)}.")
                    Text("• Deslizar a la izquierda: Retroceder ${formatIntervalSpoken(rewindIntervalSec)}.")
                    Text("• Deslizar hacia arriba: Reproducir o pausar.")
                    Text("• Deslizar hacia abajo: Consultar por voz el tiempo y estado actual.")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Atajos de botones:",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("• Mantén pulsado Retroceder o Adelantar para cambiar su intervalo (desde 5 segundos hasta 1 minuto).")
                    Text("• En la tira inferior de controles tienes acceso directo a Velocidad, Temporizador, Ecualizador y Autoreproducción.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHelpDialog = false
                        onNavigateToHelp()
                    }
                ) {
                    Text("Abrir guía completa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun PlayerSocialAndInfoSection(
    modifier: Modifier,
    isUserLoggedIn: Boolean,
    voteStats: com.johang.audiocinemateca.data.repository.VoteStats,
    commentPreview: Pair<Long, com.johang.audiocinemateca.data.model.Comment?>,
    synopsisToDisplay: String,
    chapterSynopsisState: ChapterSynopsisState,
    isSynopsisExpanded: Boolean,
    onLikeClicked: () -> Unit,
    onDislikeClicked: () -> Unit,
    onShareClick: () -> Unit,
    onToggleSynopsis: () -> Unit,
    onOpenCommentsClick: () -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .semantics {
                isTraversalGroup = true
                traversalIndex = 2f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tarjeta de la Obra Activa y Botones Sociales
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0EA5E9).copy(alpha = 0.18f),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.55f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                if (isUserLoggedIn) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isLiked = voteStats.userVote == 1
                        val likeLabel = remember(voteStats.likeCount, isLiked) {
                            "Me gusta, ${voteStats.likeCount} votos${if (isLiked) ", seleccionado" else ""}"
                        }

                        OutlinedButton(
                            onClick = onLikeClicked,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isLiked) Color(0xFF22C55E).copy(alpha = 0.25f) else Color.Transparent,
                                contentColor = if (isLiked) Color(0xFF22C55E) else Color.White
                            ),
                            border = BorderStroke(1.dp, if (isLiked) Color(0xFF22C55E) else Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = likeLabel
                                role = Role.Checkbox
                                selected = isLiked
                                onClick(label = if (isLiked) "Quitar Me gusta" else "Dar Me gusta") {
                                    onLikeClicked()
                                    true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${voteStats.likeCount}", fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Normal)
                        }

                        val isDisliked = voteStats.userVote == -1
                        val dislikeLabel = remember(voteStats.dislikeCount, isDisliked) {
                            "No me gusta, ${voteStats.dislikeCount} votos${if (isDisliked) ", seleccionado" else ""}"
                        }

                        OutlinedButton(
                            onClick = onDislikeClicked,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDisliked) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.Transparent,
                                contentColor = if (isDisliked) Color(0xFFEF4444) else Color.White
                            ),
                            border = BorderStroke(1.dp, if (isDisliked) Color(0xFFEF4444) else Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = dislikeLabel
                                role = Role.Checkbox
                                selected = isDisliked
                                onClick(label = if (isDisliked) "Quitar No me gusta" else "Dar No me gusta") {
                                    onDislikeClicked()
                                    true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isDisliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${voteStats.dislikeCount}", fontWeight = if (isDisliked) FontWeight.Bold else FontWeight.Normal)
                        }

                        OutlinedButton(
                            onClick = onShareClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "Compartir esta obra"
                                role = Role.Button
                                onClick(label = "Compartir obra") {
                                    onShareClick()
                                    true
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Sinopsis Expandible
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggleSynopsis)
                        .clearAndSetSemantics {
                            contentDescription = "Sinopsis"
                            stateDescription = if (isSynopsisExpanded) "Expandido" else "Contraído"
                            role = Role.Button
                            onClick(label = if (isSynopsisExpanded) "Contraer sinopsis" else "Desplegar sinopsis") {
                                onToggleSynopsis()
                                true
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sinopsis", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    }
                    Icon(
                        imageVector = if (isSynopsisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                if (isSynopsisExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (chapterSynopsisState is ChapterSynopsisState.Loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Cargando sinopsis con IA...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    } else {
                        Text(
                            text = synopsisToDisplay.ifBlank { "Sin sinopsis disponible para este contenido." },
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Vista Previa de Comentarios
        if (isUserLoggedIn) {
            val previewDesc = remember(commentPreview.first, commentPreview.second) {
                val countStr = if (commentPreview.first == 1L) "1 comentario" else "${commentPreview.first} comentarios"
                val latestStr = commentPreview.second?.let { ". Último de ${it.userName}: ${it.commentText}" } ?: ""
                "Comentarios de la comunidad, $countStr$latestStr"
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCommentsClick)
                    .clearAndSetSemantics {
                        testTag = "player_comments_preview_card"
                        role = Role.Button
                        contentDescription = previewDesc
                        onClick(label = "Abrir comentarios") {
                            onOpenCommentsClick()
                            true
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Comentarios (${commentPreview.first})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Text("Ver todos", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }

                    commentPreview.second?.let { latest ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${latest.userName}: ${latest.commentText}",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerMediaControlsPanel(
    isPlaying: Boolean,
    activePositionMs: Long,
    totalDurationMs: Long,
    progressFraction: Float,
    playbackSpeed: Float,
    sleepTimerText: String?,
    isAutoPlayEnabled: Boolean,
    rewindIntervalSec: Int,
    forwardIntervalSec: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPauseClick: () -> Unit,
    onRewindClick: () -> Unit,
    onForwardClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onScrubFraction: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onOpenRewindDialog: () -> Unit,
    onOpenForwardDialog: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenSleepTimerDialog: () -> Unit,
    onEqualizerClick: () -> Unit,
    onAutoPlayToggle: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slider de Tiempo / Barra de Progreso
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                }
        ) {
            val timeStateText = remember(activePositionMs, totalDurationMs) {
                "${TimeFormatUtils.formatDuration(activePositionMs)} de ${TimeFormatUtils.formatDuration(totalDurationMs)}"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                Slider(
                    value = progressFraction,
                    onValueChange = onScrubFraction,
                    onValueChangeFinished = onScrubFinished,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8),
                        inactiveTrackColor = Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Progreso de reproducción"
                            stateDescription = timeStateText
                            setProgress { targetValue ->
                                val clamped = targetValue.coerceIn(0f, 1f)
                                val targetMs = (clamped * totalDurationMs).toLong()
                                onSeekTo(targetMs)
                                true
                            }
                        }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDigitalTime(activePositionMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = formatDigitalTime(totalDurationMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Fila Principal de Botones de Transporte con sus logotipos claros
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Anterior
            Surface(
                shape = CircleShape,
                color = if (hasPrevious) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.45f),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = hasPrevious) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPreviousClick()
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Anterior"
                        role = Role.Button
                        if (hasPrevious) {
                            onClick(label = "Anterior") {
                                onPreviousClick()
                                true
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = null,
                        tint = if (hasPrevious) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Retroceder N segundos / 1 minuto
            val rewindSpoken = remember(rewindIntervalSec) { "Retroceder ${formatIntervalSpoken(rewindIntervalSec)}" }
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRewindClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenRewindDialog()
                        }
                    )
                    .clearAndSetSemantics {
                        contentDescription = rewindSpoken
                        role = Role.Button
                        onClick(label = rewindSpoken) {
                            onRewindClick()
                            true
                        }
                        customActions = listOf(
                            CustomAccessibilityAction("Cambiar tiempo de retroceso") {
                                onOpenRewindDialog()
                                true
                            }
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = rewindIconForInterval(rewindIntervalSec),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = formatIntervalShort(rewindIntervalSec),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Botón Principal Play / Pause
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0EA5E9))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPauseClick()
                    }
                    .clearAndSetSemantics {
                        testTag = "player_play_pause_button"
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                        role = Role.Button
                        traversalIndex = -1f
                        onClick(label = if (isPlaying) "Pausar" else "Reproducir") {
                            onPlayPauseClick()
                            true
                        }
                    }
                    .initialAccessibilityFocus(tag = "player_play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Adelantar N segundos / 1 minuto
            val forwardSpoken = remember(forwardIntervalSec) { "Adelantar ${formatIntervalSpoken(forwardIntervalSec)}" }
            Surface(
                shape = CircleShape,
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onForwardClick()
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenForwardDialog()
                        }
                    )
                    .clearAndSetSemantics {
                        contentDescription = forwardSpoken
                        role = Role.Button
                        onClick(label = forwardSpoken) {
                            onForwardClick()
                            true
                        }
                        customActions = listOf(
                            CustomAccessibilityAction("Cambiar tiempo de avance") {
                                onOpenForwardDialog()
                                true
                            }
                        )
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(54.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = forwardIconForInterval(forwardIntervalSec),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = formatIntervalShort(forwardIntervalSec),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Siguiente
            Surface(
                shape = CircleShape,
                color = if (hasNext) Color(0xFF1E293B) else Color(0xFF1E293B).copy(alpha = 0.45f),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = hasNext) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNextClick()
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Siguiente"
                        role = Role.Button
                        if (hasNext) {
                            onClick(label = "Siguiente") {
                                onNextClick()
                                true
                            }
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = if (hasNext) Color.White else Color(0xFF475569),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tira rápida de controles con sus propios logotipos visuales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlayerQuickControlPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "${playbackSpeed}x",
                accessibilityLabel = "Velocidad de reproducción: ${playbackSpeed}x",
                isActive = playbackSpeed != 1.0f,
                onClick = onOpenSpeedDialog
            )
            PlayerQuickControlPill(
                modifier = Modifier.weight(1f),
                icon = if (!sleepTimerText.isNullOrBlank()) Icons.Default.Timer else Icons.Default.Bedtime,
                label = sleepTimerText?.substringAfter(": ")?.ifBlank { "Temporizador" } ?: "Apagado",
                accessibilityLabel = sleepTimerText ?: "Temporizador de apagado: desactivado",
                isActive = !sleepTimerText.isNullOrBlank(),
                onClick = onOpenSleepTimerDialog
            )
            PlayerQuickControlPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Equalizer,
                label = "Audio",
                accessibilityLabel = "Ecualizador de audio",
                isActive = false,
                onClick = onEqualizerClick
            )
            PlayerQuickControlPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Autorenew,
                label = "Auto",
                accessibilityLabel = "Autoreproducción: ${if (isAutoPlayEnabled) "Activada" else "Desactivada"}",
                isActive = isAutoPlayEnabled,
                onClick = onAutoPlayToggle
            )
        }
    }
}

@Composable
private fun PlayerQuickControlPill(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accessibilityLabel: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) Color(0xFF0EA5E9).copy(alpha = 0.22f) else Color(0xFF1E293B),
        border = BorderStroke(
            1.dp,
            if (isActive) Color(0xFF38BDF8) else Color(0xFF334155)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = accessibilityLabel
                role = Role.Button
                onClick(label = accessibilityLabel) {
                    onClick()
                    true
                }
            }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF38BDF8) else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeekIntervalDialog(
    title: String,
    selectedSec: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val intervals = listOf(5, 10, 15, 30, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                intervals.forEach { sec ->
                    val isSelected = selectedSec == sec
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(sec) },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatIntervalSpoken(sec),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun formatIntervalShort(sec: Int): String = if (sec >= 60) "${sec / 60}m" else "${sec}s"

private fun formatIntervalSpoken(sec: Int): String = if (sec == 60) "1 minuto" else "$sec segundos"

private fun rewindIconForInterval(sec: Int): androidx.compose.ui.graphics.vector.ImageVector = when (sec) {
    5 -> Icons.Default.Replay5
    10 -> Icons.Default.Replay10
    30 -> Icons.Default.Replay30
    else -> Icons.Default.Replay
}

private fun forwardIconForInterval(sec: Int): androidx.compose.ui.graphics.vector.ImageVector = when (sec) {
    5 -> Icons.Default.Forward5
    10 -> Icons.Default.Forward10
    30 -> Icons.Default.Forward30
    else -> Icons.Default.FastForward
}

private fun formatDigitalTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
