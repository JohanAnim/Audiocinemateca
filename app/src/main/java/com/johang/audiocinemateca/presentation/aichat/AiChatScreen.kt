package com.johang.audiocinemateca.presentation.aichat

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.parseMarkdownToAnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    auraStatus: String,
    chatSessions: List<AuraChatSession> = emptyList(),
    activeSessionId: String = "",
    activeSessionTitle: String = "Nueva conversación",
    activeProvider: String = "groq",
    selectedModelId: String = "openai/gpt-oss-120b",
    availableModels: List<Pair<String, String>> = emptyList(),
    inputText: String,
    isListening: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onVoicePressStart: () -> Unit = {},
    onVoicePressEnd: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onNewChat: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
    onClearAllSessions: () -> Unit = {},
    onSelectModel: (String) -> Unit = {},
    onRestartChat: () -> Unit,
    onRetryMessage: () -> Unit,
    onLinkedContentClick: (LinkedContent) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showHistorySheet by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<AuraChatSession?>(null) }
    var renameText by remember { mutableStateOf("") }
    var recommendationsDialogItems by remember { mutableStateOf<List<LinkedContent>?>(null) }

    // Auto-scroll al último mensaje cuando cambia la cantidad de mensajes o la sesión activa
    LaunchedEffect(messages.size, activeSessionId) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AuraHeaderSelector(
                        activeTitle = activeSessionTitle,
                        auraStatus = auraStatus,
                        totalChats = chatSessions.size,
                        onClick = { showHistorySheet = true }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Regresar",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    // Selector desplegable del Modelo de IA actual (sincronizado con Ajustes Globales)
                    AuraModelDropdownSelector(
                        activeProvider = activeProvider,
                        selectedModelId = selectedModelId,
                        availableModels = availableModels,
                        onSelectModel = onSelectModel,
                        onNavigateToAiSettings = onNavigateToAiSettings
                    )

                    // Botón de Nuevo Chat en la esquina superior derecha
                    IconButton(
                        onClick = onNewChat,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Nuevo chat con Aura",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                isLoading = isLoading,
                isListening = isListening,
                onInputTextChange = onInputTextChange,
                onSendMessage = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim())
                    }
                },
                onVoicePressStart = onVoicePressStart,
                onVoicePressEnd = onVoicePressEnd
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp
            )

            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "¡Hola! Soy Aura",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Tu compañera inteligente en la Audiocinemateca. Pregúntame sobre cualquier película o serie, recomendaciones personalizadas o qué escuchar hoy.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val suggestions = listOf(
                            "¿Qué película me recomiendas hoy?",
                            "Recomiéndame una serie de suspenso",
                            "Películas con audiodescripción en español"
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onInputTextChange(suggestion) }
                                        .audiocinematecaAccessibility(
                                            label = suggestion,
                                            role = Role.Button,
                                            onClickAction = { onInputTextChange(suggestion) }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = suggestion,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom)
                ) {
                    items(
                        items = messages,
                        key = { it.id.ifEmpty { it.timestamp.toString() } }
                    ) { message ->
                        if (message.isUser) {
                            UserChatBubble(message = message)
                        } else {
                            AuraChatBubble(
                                message = message,
                                onRetryClick = onRetryMessage,
                                onShowRecommendations = { recommendationsDialogItems = it },
                                onLinkedContentClick = onLinkedContentClick
                            )
                        }
                    }
                }
            }
        }
    }

    // Panel inferior con el Historial de Conversaciones y Selector de Chats
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AuraChatHistorySheetContent(
                sessions = chatSessions,
                activeSessionId = activeSessionId,
                onNewChatClick = {
                    showHistorySheet = false
                    onNewChat()
                },
                onSelectSession = { sessionId ->
                    showHistorySheet = false
                    onSelectSession(sessionId)
                },
                onRenameSessionClick = { session ->
                    renameText = session.title
                    sessionToRename = session
                },
                onDeleteSessionClick = { sessionId ->
                    onDeleteSession(sessionId)
                },
                onClearAllClick = {
                    showHistorySheet = false
                    showClearAllDialog = true
                }
            )
        }
    }

    // Diálogo para renombrar manualmente el título de una conversación
    sessionToRename?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text(text = "Renombrar conversación") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Personaliza el título de esta conversación con Aura:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Título del chat") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameSession(session.id, trimmed)
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de confirmación para borrar todas las conversaciones guardadas
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "Borrar todo el historial") },
            text = { Text(text = "¿Estás seguro de que quieres eliminar todas las conversaciones guardadas con Aura? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllSessions()
                        showClearAllDialog = false
                    }
                ) {
                    Text(text = "Sí, eliminar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Diálogo de lista de recomendaciones
    recommendationsDialogItems?.let { items ->
        AlertDialog(
            onDismissRequest = { recommendationsDialogItems = null },
            title = { Text(text = "Recomendaciones de Aura") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { linked ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    recommendationsDialogItems = null
                                    onLinkedContentClick(linked)
                                }
                                .audiocinematecaAccessibility(
                                    label = "Ver detalles de ${linked.title}",
                                    role = Role.Button
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = linked.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { recommendationsDialogItems = null }) {
                    Text(text = "Cerrar")
                }
            }
        )
    }
}

@Composable
fun AuraHeaderSelector(
    activeTitle: String,
    auraStatus: String,
    totalChats: Int,
    onClick: () -> Unit
) {
    val indicatorColor = when {
        auraStatus.contains("pensando", ignoreCase = true) ||
                auraStatus.contains("escribiendo", ignoreCase = true) ||
                auraStatus.contains("consultando", ignoreCase = true) ||
                auraStatus.contains("redactando", ignoreCase = true) -> Color(0xFFFBC02D) // Amarillo
        auraStatus.contains("En línea", ignoreCase = true) -> Color(0xFF4CAF50) // Verde
        else -> Color(0xFFE53935) // Rojo
    }

    val accessibilityHeaderLabel = remember(activeTitle, auraStatus, totalChats) {
        val countText = if (totalChats <= 1) "1 chat guardado" else "$totalChats chats guardados"
        "$activeTitle, $auraStatus. Historial ($countText)"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = accessibilityHeaderLabel
                role = Role.Button
                onClick(label = "Abrir historial de conversaciones") {
                    onClick()
                    true
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = activeTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = auraStatus,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AuraModelDropdownSelector(
    activeProvider: String,
    selectedModelId: String,
    availableModels: List<Pair<String, String>>,
    onSelectModel: (String) -> Unit,
    onNavigateToAiSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val providerLabel = if (activeProvider.equals("groq", ignoreCase = true)) "Groq" else "Gemini"
    val fullModelDisplay = availableModels.find { it.first == selectedModelId }?.second ?: selectedModelId
    val shortModelLabel = remember(selectedModelId, fullModelDisplay) {
        fullModelDisplay
            .substringBefore("(")
            .trim()
            .ifBlank { selectedModelId.substringAfterLast("/") }
    }

    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .clearAndSetSemantics {
                    contentDescription = "Modelo actual ($providerLabel): $fullModelDisplay"
                    role = Role.DropdownList
                    onClick(label = "Cambiar modelo de inteligencia artificial") {
                        expanded = true
                        true
                    }
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = shortModelLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 88.dp)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 310.dp)
        ) {
            Text(
                text = "Modelos de $providerLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .semantics {
                        heading()
                        stateDescription = "Encabezado"
                    }
            )

            availableModels.forEach { (modelId, displayName) ->
                val isCurrent = modelId == selectedModelId
                DropdownMenuItem(
                    text = {
                        Text(
                            text = displayName,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectModel(modelId)
                    },
                    modifier = Modifier.semantics {
                        selected = isCurrent
                        contentDescription = if (isCurrent) "$displayName, Seleccionado" else displayName
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Ajustes avanzados de IA...",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onNavigateToAiSettings()
                }
            )
        }
    }
}

@Composable
private fun AuraChatHistorySheetContent(
    sessions: List<AuraChatSession>,
    activeSessionId: String,
    onNewChatClick: () -> Unit,
    onSelectSession: (String) -> Unit,
    onRenameSessionClick: (AuraChatSession) -> Unit,
    onDeleteSessionClick: (String) -> Unit,
    onClearAllClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabecera del panel de historial
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Historial de conversaciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        heading()
                        stateDescription = "Encabezado"
                    }
                )
                Text(
                    text = "Selecciona una charla para continuarla donde la dejaste",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Botones de acción rápida: Nuevo chat y Borrar todo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onNewChatClick,
                modifier = Modifier
                    .weight(1f)
                    .audiocinematecaAccessibility(
                        label = "Iniciar nueva conversación con Aura",
                        role = Role.Button,
                        onClickAction = onNewChatClick
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Nuevo chat", fontWeight = FontWeight.SemiBold)
            }

            if (sessions.size > 1 || sessions.firstOrNull()?.messages?.isNotEmpty() == true) {
                OutlinedButton(
                    onClick = onClearAllClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Borrar todo el historial de conversaciones",
                        role = Role.Button,
                        onClickAction = onClearAllClick
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Limpiar todo")
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(
                items = sessions,
                key = { it.id }
            ) { session ->
                val isSelected = session.id == activeSessionId
                val messageCount = session.messages.size

                val relativeDate = remember(session.updatedAt) {
                    DateUtils.getRelativeTimeSpanString(
                        session.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    ).toString()
                }

                val accessibilityLabel = buildString {
                    append(session.title)
                    if (isSelected) append(", Conversación activa")
                    append(". ")
                    append(if (messageCount == 1) "1 mensaje" else "$messageCount mensajes")
                    append(", $relativeDate")
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSession(session.id) }
                        .clearAndSetSemantics {
                            contentDescription = accessibilityLabel
                            role = Role.Button
                            selected = isSelected
                            onClick(label = "Continuar esta conversación") {
                                onSelectSession(session.id)
                                true
                            }
                            customActions = listOf(
                                CustomAccessibilityAction("Renombrar título de la conversación") {
                                    onRenameSessionClick(session)
                                    true
                                },
                                CustomAccessibilityAction("Eliminar conversación") {
                                    onDeleteSessionClick(session.id)
                                    true
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            text = "Activa",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "$relativeDate • ${if (messageCount == 1) "1 mensaje" else "$messageCount mensajes"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { onRenameSessionClick(session) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { onDeleteSessionClick(session.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp).copy(bottomEnd = CornerSize(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.audiocinematecaAccessibility(
                label = "Tú: ${message.text}"
            )
        ) {
            Text(
                text = message.text,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun AuraChatBubble(
    message: ChatMessage,
    onRetryClick: () -> Unit,
    onShowRecommendations: (List<LinkedContent>) -> Unit,
    onLinkedContentClick: (LinkedContent) -> Unit
) {
    val hasLinks = message.linkedItems.isNotEmpty()
    val borderColor = if (message.isError) MaterialTheme.colorScheme.error else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 40.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Surface(
                shape = RoundedCornerShape(18.dp).copy(bottomStart = CornerSize(4.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = if (message.isError) BorderStroke(1.5.dp, borderColor) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .audiocinematecaAccessibility(
                        label = "Aura dice: ${message.text}"
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val formattedText = remember(message.text) {
                        parseMarkdownToAnnotatedString(message.text)
                    }
                    Text(
                        text = formattedText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        lineHeight = 21.sp
                    )

                    // Botón para reintentar si ocurrió error
                    if (message.isError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRetryClick,
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "Reintentar respuesta de Aura",
                                role = Role.Button
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Reintentar", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    // Chips interactivos de recomendaciones directas
                    if (hasLinks && !message.isError) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = { onShowRecommendations(message.linkedItems) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.audiocinematecaAccessibility(
                                    label = "Ver todas las recomendaciones de Aura",
                                    role = Role.Button
                                )
                            ) {
                                Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Ver recomendaciones (${message.linkedItems.size})", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputText: String,
    isLoading: Boolean,
    isListening: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onVoicePressStart: () -> Unit,
    onVoicePressEnd: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                placeholder = {
                    Text(
                        text = if (isListening) "Escuchando voz... (suelta para enviar)" else "Escribe un mensaje...",
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isListening) {
                            Modifier.semantics { contentDescription = "Escuchando voz. Suelta para enviar mensaje a Aura" }
                        } else Modifier
                    ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSendMessage() }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            if (inputText.isNotBlank()) {
                FilledIconButton(
                    onClick = onSendMessage,
                    enabled = !isLoading,
                    modifier = Modifier
                        .size(48.dp)
                        .audiocinematecaAccessibility(
                            label = "Enviar mensaje",
                            role = Role.Button
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null
                    )
                }
            } else {
                var isPointerDown by remember { mutableStateOf(false) }
                val isPttActive = isListening || isPointerDown

                Surface(
                    shape = CircleShape,
                    color = if (isPttActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .pointerInput(isLoading) {
                            if (isLoading) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isPointerDown = true
                                onVoicePressStart()
                                waitForUpOrCancellation()
                                isPointerDown = false
                                onVoicePressEnd()
                            }
                        }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = if (isPttActive) {
                                "Escuchando voz. Suelta para enviar mensaje a Aura, o toca dos veces para detener y enviar"
                            } else {
                                "Dictar mensaje por voz. Mantén presionado para hablar y suelta para enviar a Aura, o toca dos veces para iniciar"
                            }
                            onClick(label = if (isPttActive) "Detener y enviar mensaje" else "Dictar mensaje a Aura") {
                                if (isPttActive) {
                                    isPointerDown = false
                                    onVoicePressEnd()
                                } else {
                                    onVoicePressStart()
                                }
                                true
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isPttActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
