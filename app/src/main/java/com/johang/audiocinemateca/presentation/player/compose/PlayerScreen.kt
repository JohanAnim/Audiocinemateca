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
            TopAppBar(
                modifier = Modifier.semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
                title = {
                    Column(
                        modifier = Modifier
                            .clickable(onClick = onTitleClick)
                            .clearAndSetSemantics {
                                contentDescription = "$title. $subtitle"
                                role = Role.Button
                                onClick(label = "Ver ficha del contenido") {
                                    onTitleClick()
                                    true
                                }
                            }
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Cerrar reproductor",
                            role = Role.Button,
                            onClickAction = onBackClick
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
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

                    // Menú de Más Opciones
                    Box {
                        IconButton(
                            onClick = { showPlayerMenu = true },
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "Más opciones de reproducción",
                                role = Role.Button,
                                onClickAction = { showPlayerMenu = true }
                            )
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = showPlayerMenu,
                            onDismissRequest = { showPlayerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Velocidad: ${playbackSpeed}x") },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                onClick = {
                                    showPlayerMenu = false
                                    showSpeedDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(sleepTimerText ?: "Temporizador de apagado") },
                                leadingIcon = { Icon(if (!sleepTimerText.isNullOrBlank()) Icons.Default.Timer else Icons.Default.Bedtime, contentDescription = null) },
                                onClick = {
                                    showPlayerMenu = false
                                    showSleepTimerDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ecualizador de audio") },
                                leadingIcon = { Icon(Icons.Default.Equalizer, contentDescription = null) },
                                onClick = {
                                    showPlayerMenu = false
                                    onEqualizerClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Autoreproducción: ${if (isAutoPlayEnabled) "Activada" else "Desactivada"}") },
                                leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null) },
                                onClick = {
                                    showPlayerMenu = false
                                    onAutoPlayToggle()
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
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
                CustomAccessibilityAction("Adelantar $forwardIntervalSec segundos") {
                    onForwardClick()
                    true
                },
                CustomAccessibilityAction("Retroceder $rewindIntervalSec segundos") {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Contenido Central Desplazable (Obra, Sinopsis, Comentarios)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 2f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Tarjeta de la Obra Activa
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
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            if (subtitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Votos y Botones de Interacción
                            if (viewModel.isUserLoggedIn) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Like Toggle
                                    val isLiked = voteStats.userVote == 1
                                    val likeLabel = remember(voteStats.likeCount, isLiked) {
                                        "Me gusta, ${voteStats.likeCount} votos${if (isLiked) ", seleccionado" else ""}"
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.onLikeClicked() },
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
                                                viewModel.onLikeClicked()
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

                                    // Dislike Toggle
                                    val isDisliked = voteStats.userVote == -1
                                    val dislikeLabel = remember(voteStats.dislikeCount, isDisliked) {
                                        "No me gusta, ${voteStats.dislikeCount} votos${if (isDisliked) ", seleccionado" else ""}"
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.onDislikeClicked() },
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
                                                viewModel.onDislikeClicked()
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

                                    // Compartir
                                    OutlinedButton(
                                        onClick = onShareClick,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
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
                                    }
                                }
                            }
                        }
                    }

                // Sinopsis Inteligente (Muestra la sinopsis disponible o la genera con IA)
                val activeContentItem by viewModel.contentItem.collectAsState()
                val synopsisToDisplay = remember(activeContentItem, chapterSynopsisState) {
                    when (chapterSynopsisState) {
                        is ChapterSynopsisState.Success -> (chapterSynopsisState as ChapterSynopsisState.Success).synopsis
                        else -> activeContentItem?.sinopsis ?: ""
                    }
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSynopsis() }
                                .clearAndSetSemantics {
                                    contentDescription = "Sinopsis"
                                    stateDescription = if (isSynopsisExpanded) "Expandido" else "Contraído"
                                    role = Role.Button
                                    onClick(label = if (isSynopsisExpanded) "Contraer sinopsis" else "Desplegar sinopsis") {
                                        viewModel.toggleSynopsis()
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

                // Vista Previa de Comentarios de la Comunidad
                if (viewModel.isUserLoggedIn) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCommentsClick(false) }
                            .clearAndSetSemantics {
                                contentDescription = "Comentarios de la comunidad, ${commentPreview.first} comentarios"
                                onClick(label = "Abrir comentarios") {
                                    onOpenCommentsClick(false)
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

            Spacer(modifier = Modifier.height(20.dp))

            // ==========================================
            // BARRA DE CONTROLES DE REPRODUCCIÓN
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slider de Tiempo / Barra de Progreso con modelo de tiempo accesible
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

                    val haptic = LocalHapticFeedback.current

                    // Bloquea toques/arrastres táctiles directos sobre el Slider para evitar que los gestos
                    // del reproductor muevan accidentalmente el tiempo de la obra, manteniendo intacto el
                    // control accesible (`setProgress`) cuando se enfoca con Jieshuo o TalkBack.
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
                            onValueChange = { fraction ->
                                isScrubbing = true
                                scrubPositionMs = (fraction * totalDurationMs).toLong()
                            },
                            onValueChangeFinished = {
                                isScrubbing = false
                                onSeekTo(scrubPositionMs)
                            },
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
                                        isScrubbing = false
                                        scrubPositionMs = targetMs
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

                Spacer(modifier = Modifier.height(16.dp))

                // Fila Principal de Botones de Control
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
                    // Episodio / Capítulo Anterior
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreviousClick()
                        },
                        enabled = hasPrevious,
                        modifier = Modifier
                            .size(44.dp)
                            .audiocinematecaAccessibility(
                                label = "Anterior",
                                role = Role.Button,
                                onClickAction = if (hasPrevious) onPreviousClick else null
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = if (hasPrevious) Color.White else Color(0xFF475569),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Retroceder N segundos
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRewindClick()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showRewindIntervalDialog = true
                                }
                            )
                            .clearAndSetSemantics {
                                contentDescription = "Retroceder $rewindIntervalSec segundos"
                                onClick(label = "Retroceder $rewindIntervalSec segundos") {
                                    onRewindClick()
                                    true
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("${rewindIntervalSec}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }
                    }

                    // Botón Principal Play / Pause ("Pausar" / "Reproducir" con efecto de vibración)
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
                            .initialAccessibilityFocus(
                                tag = "player_play_pause_button"
                            )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Adelantar N segundos
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onForwardClick()
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showForwardIntervalDialog = true
                                }
                            )
                            .clearAndSetSemantics {
                                contentDescription = "Adelantar $forwardIntervalSec segundos"
                                onClick(label = "Adelantar $forwardIntervalSec segundos") {
                                    onForwardClick()
                                    true
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("${forwardIntervalSec}s", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            }
                        }
                    }

                    // Episodio / Capítulo Siguiente
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNextClick()
                        },
                        enabled = hasNext,
                        modifier = Modifier
                            .size(44.dp)
                            .audiocinematecaAccessibility(
                                label = "Siguiente",
                                role = Role.Button,
                                onClickAction = if (hasNext) onNextClick else null
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = if (hasNext) Color.White else Color(0xFF475569),
                            modifier = Modifier.size(28.dp)
                        )
                    }
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

    // Diálogo de Intervalo de Retroceso
    if (showRewindIntervalDialog) {
        val intervals = listOf(5, 10, 15, 30, 60)
        AlertDialog(
            onDismissRequest = { showRewindIntervalDialog = false },
            title = { Text("Intervalo de retroceso", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    intervals.forEach { sec ->
                        val isSelected = rewindIntervalSec == sec
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onRewindIntervalChange(sec)
                                        showRewindIntervalDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$sec segundos", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRewindIntervalDialog = false }) { Text("Cancelar") } }
        )
    }

    // Diálogo de Intervalo de Avance
    if (showForwardIntervalDialog) {
        val intervals = listOf(5, 10, 15, 30, 60)
        AlertDialog(
            onDismissRequest = { showForwardIntervalDialog = false },
            title = { Text("Intervalo de avance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    intervals.forEach { sec ->
                        val isSelected = forwardIntervalSec == sec
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onForwardIntervalChange(sec)
                                        showForwardIntervalDialog = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("$sec segundos", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showForwardIntervalDialog = false }) { Text("Cancelar") } }
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
}
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
