package com.johang.audiocinemateca.presentation.aichat

import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import io.noties.markwon.Markwon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    auraStatus: String,
    inputText: String,
    isListening: Boolean,
    onInputTextChange: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onVoiceClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onRestartChat: () -> Unit,
    onRetryMessage: () -> Unit,
    onLinkedContentClick: (LinkedContent) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var showRestartDialog by remember { mutableStateOf(false) }
    var recommendationsDialogItems by remember { mutableStateOf<List<LinkedContent>?>(null) }
    val markwon = remember { Markwon.create(context) }

    // Auto-scroll al último mensaje cuando cambia la cantidad de mensajes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        AuraStatusPill(auraStatus = auraStatus)
                    }
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
                    IconButton(
                        onClick = onNavigateToAiSettings,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Ajustes de IA",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = { showRestartDialog = true },
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Reiniciar conversación",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
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
                onVoiceClick = onVoiceClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Bottom)
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
                                markwon = markwon,
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

    // Diálogo de confirmación para reiniciar chat
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(text = "Reiniciar Chat") },
            text = { Text(text = "¿Estás seguro de que quieres borrar la conversación actual con Aura?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestartChat()
                        showRestartDialog = false
                    }
                ) {
                    Text(text = "Sí, borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
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
fun AuraStatusPill(auraStatus: String) {
    val indicatorColor = when {
        auraStatus.contains("pensando", ignoreCase = true) ||
                auraStatus.contains("escribiendo", ignoreCase = true) ||
                auraStatus.contains("consultando", ignoreCase = true) ||
                auraStatus.contains("redactando", ignoreCase = true) -> Color(0xFFFBC02D) // Amarillo
        auraStatus.contains("En línea", ignoreCase = true) -> Color(0xFF4CAF50) // Verde
        else -> Color(0xFFE53935) // Rojo
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.audiocinematecaAccessibility(label = "Estado de Aura: $auraStatus")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = auraStatus,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
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
    markwon: Markwon,
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
                    // Renderizamos con Markwon en un AndroidView estilizado
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                setTextColor(android.graphics.Color.WHITE)
                                textSize = 15f
                                setLineSpacing(4f, 1.15f)
                            }
                        },
                        update = { textView ->
                            markwon.setMarkdown(textView, message.text)
                        }
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
    onVoiceClick: () -> Unit
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
                        text = if (isListening) "Escuchando voz..." else "Escribe un mensaje...",
                        fontSize = 15.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isListening) {
                            Modifier.semantics { contentDescription = "Escuchando voz..." }
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
                FilledIconButton(
                    onClick = onVoiceClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .audiocinematecaAccessibility(
                            label = if (isListening) "Detener dictado por voz" else "Dictar mensaje por voz",
                            role = Role.Button
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null
                    )
                }
            }
        }
    }
}
