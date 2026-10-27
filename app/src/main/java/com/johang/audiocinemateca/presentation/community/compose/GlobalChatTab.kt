package com.johang.audiocinemateca.presentation.community.compose

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.community.ChatListItem
import com.johang.audiocinemateca.presentation.community.GlobalChatViewModel
import com.johang.audiocinemateca.presentation.community.components.*
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.initialAccessibilityFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalChatTab(
    viewModel: GlobalChatViewModel,
    onVoiceClick: () -> Unit = {},
    onNavigateToPlayer: (CatalogItem, Int, Int) -> Unit = { _, _, _ -> },
    onNavigateToCommunitySettings: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val chatItems by viewModel.chatItems.collectAsState()
    val isOpen by viewModel.isOpen.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val editingMessage by viewModel.editingMessage.collectAsState()
    val onlineCount by viewModel.onlineCount.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()
    val banStatusMessage by viewModel.banStatusMessage.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var selectedMessageForOptions by remember { mutableStateOf<ChatMessage?>(null) }

    // Dialogs & Menus
    var showHeaderMenu by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showConnectedUsersDialog by remember { mutableStateOf(false) }
    var showBannedUsersDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var isListeningVoice by remember { mutableStateOf(false) }
    var inAppRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopInAppVoice() {
        isListeningVoice = false
        inAppRecognizer?.stopListening()
    }

    fun startInAppVoice() {
        val recognizer = inAppRecognizer
        if (recognizer == null) {
            Toast.makeText(context, "El dictado por voz no está disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }
        isListeningVoice = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        recognizer.startListening(intent)
    }

    val requestRecordAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startInAppVoice()
        } else {
            Toast.makeText(context, "Se necesita permiso de micrófono para dictar por voz.", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListeningVoice = false
                }
                override fun onError(error: Int) {
                    isListeningVoice = false
                }
                override fun onResults(results: Bundle?) {
                    isListeningVoice = false
                    val spokenText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spokenText.isNullOrBlank()) {
                        messageText = if (messageText.isBlank()) {
                            spokenText
                        } else {
                            "${messageText.trim()} $spokenText"
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            inAppRecognizer = recognizer
        }

        onDispose {
            inAppRecognizer?.destroy()
            inAppRecognizer = null
        }
    }



    val listState = rememberLazyListState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid

    val unreadPosition by viewModel.unreadPosition.collectAsState()
    var initialScrollDone by remember { mutableStateOf(false) }

    // Sincronizar texto cuando se edita un mensaje
    LaunchedEffect(editingMessage) {
        editingMessage?.let {
            messageText = it.text
        }
    }

    // Control inteligente de posición de scroll inicial (mensajes sin leer o final directo)
    LaunchedEffect(chatItems) {
        if (chatItems.isNotEmpty()) {
            if (!initialScrollDone) {
                val targetIndex = unreadPosition ?: (chatItems.size - 1)
                listState.scrollToItem(targetIndex.coerceIn(0, chatItems.size - 1))
                initialScrollDone = true
            } else {
                // Al recibir un mensaje nuevo durante la conversación, hacer scroll suave al final
                listState.animateScrollToItem(chatItems.size - 1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                isTraversalGroup = true
            }
    ) {
        // Encabezado del Chat con Menú Contextual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 1f
                }
        ) {
            GlobalChatHeader(
                onlineCount = onlineCount,
                isAdmin = viewModel.isAdmin,
                onHeaderClick = { showConnectedUsersDialog = true },
                onOptionsClick = { showHeaderMenu = true }
            )

            DropdownMenu(
                expanded = showHeaderMenu,
                onDismissRequest = { showHeaderMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reglas de la comunidad") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                    onClick = {
                        showHeaderMenu = false
                        showRulesDialog = true
                    }
                )

                if (viewModel.isAdmin) {
                    DropdownMenuItem(
                        text = { Text("Usuarios sancionados") },
                        leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                        onClick = {
                            showHeaderMenu = false
                            showBannedUsersDialog = true
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Ajustes de la comunidad") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showHeaderMenu = false
                        onNavigateToCommunitySettings()
                    }
                )
            }
        }

        // Banner de Chat Cerrado
        if (!isOpen) {
            Surface(
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "El chat global ha sido cerrado por el administrador.",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // Mensajes del Chat
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = 2f
                }
        ) {
            if (chatItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "¡Aún no hay mensajes!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sé el primero en saludar a la comunidad.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = chatItems,
                        key = {
                            when (it) {
                                is ChatListItem.Header -> "header_${it.date}"
                                is ChatListItem.UnreadHeader -> "unread_${it.count}"
                                is ChatListItem.Message -> "msg_${it.message.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is ChatListItem.Header -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        tonalElevation = 2.dp,
                                        modifier = Modifier.clearAndSetSemantics {
                                            heading()
                                            stateDescription = "Encabezado"
                                            contentDescription = item.date
                                        }
                                    ) {
                                        Text(
                                            text = item.date,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            is ChatListItem.UnreadHeader -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        tonalElevation = 3.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MarkAsUnread,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${item.count} mensajes no leídos",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            is ChatListItem.Message -> {
                                ChatMessageCard(
                                    message = item.message,
                                    currentUserId = currentUserId,
                                    isAdmin = viewModel.isAdmin,
                                    onReply = { viewModel.setReplyingTo(item.message) },
                                    onMention = { messageText = "@${item.message.senderName} " },
                                    onEdit = { viewModel.setEditingMessage(item.message) },
                                    onDelete = { viewModel.deleteMessage(item.message) },
                                    onReactionToggle = { emoji -> viewModel.toggleReaction(item.message, emoji) },
                                    onScrollToMessage = { targetId ->
                                        val targetIndex = chatItems.indexOfFirst {
                                            it is ChatListItem.Message && it.message.id == targetId
                                        }
                                        if (targetIndex >= 0) {
                                            scope.launch {
                                                listState.animateScrollToItem(targetIndex)
                                            }
                                        }
                                    },
                                    onItemClick = {
                                        val textToSpeak = if (item.message.isDeleted) {
                                            "Mensaje eliminado"
                                        } else {
                                            "${item.message.senderName} dice: ${item.message.text}"
                                        }
                                        viewModel.speakText(textToSpeak)
                                    },
                                    onLongClick = { selectedMessageForOptions = item.message }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Previsualización de respuesta o edición
        AnimatedVisibility(visible = replyingTo != null || editingMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(36.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editingMessage != null) "Editando mensaje" else "Respondiendo a ${replyingTo?.senderName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = editingMessage?.text ?: replyingTo?.text ?: "",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val cancelActionLabel = if (editingMessage != null) "Cancelar edición" else "Cancelar respuesta"
                    IconButton(
                        onClick = {
                            viewModel.setReplyingTo(null)
                            viewModel.setEditingMessage(null)
                            messageText = ""
                        },
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = cancelActionLabel
                            role = Role.Button
                            onClick(label = cancelActionLabel) {
                                viewModel.setReplyingTo(null)
                                viewModel.setEditingMessage(null)
                                messageText = ""
                                true
                            }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
        }

        // Selector de Emojis
        AnimatedVisibility(visible = showEmojiPicker) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val emojiList = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "🙏", "🔥", "🎉", "👋", "👏", "🤔", "🎬", "🍿", "🎶", "⭐")
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    emojiList.forEach { emoji ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clickable {
                                    messageText += emoji
                                }
                                .padding(4.dp)
                                .clearAndSetSemantics {
                                    contentDescription = "Emoji $emoji"
                                    role = Role.Button
                                    onClick(label = "Insertar emoji $emoji") {
                                        messageText += emoji
                                        true
                                    }
                                }
                        ) {
                            Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }

        // Barra de Entrada de Mensaje
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics {
                        isTraversalGroup = true
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emojiToggleLabel = if (showEmojiPicker) "Ocultar emojis" else "Emojis"
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker },
                    enabled = isOpen,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = emojiToggleLabel
                        role = Role.Button
                        onClick(label = emojiToggleLabel) {
                            if (isOpen) {
                                showEmojiPicker = !showEmojiPicker
                            }
                            true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mood,
                        contentDescription = null,
                        tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                var isPointerDownVoice by remember { mutableStateOf(false) }
                val isPttActive = isListeningVoice || isPointerDownVoice

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text(if (isPttActive) "Escuchando voz... (suelta para terminar)" else "Escribe un mensaje...")
                    },
                    enabled = isOpen,
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            traversalIndex = -1f
                            if (isPttActive) {
                                contentDescription = "Escuchando voz. Suelta el botón de micrófono para terminar el dictado"
                            }
                        }
                        .initialAccessibilityFocus(
                            tag = "global_chat_message_input",
                            requestInputFocus = false
                        ),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )

                Surface(
                    shape = CircleShape,
                    color = if (isPttActive) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else Color.Transparent,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .pointerInput(isOpen) {
                            if (!isOpen) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                isPointerDownVoice = true
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    startInAppVoice()
                                } else {
                                    requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                waitForUpOrCancellation()
                                isPointerDownVoice = false
                                stopInAppVoice()
                            }
                        }
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = if (isPttActive) {
                                "Escuchando voz. Suelta para terminar el dictado, o toca dos veces para detener"
                            } else {
                                "Dictar mensaje por voz. Mantén presionado para hablar y suelta para terminar el dictado, o toca dos veces para iniciar"
                            }
                            onClick(label = if (isPttActive) "Terminar dictado por voz" else "Dictar mensaje por voz") {
                                if (isOpen) {
                                    if (isPttActive) {
                                        isPointerDownVoice = false
                                        stopInAppVoice()
                                    } else {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            startInAppVoice()
                                        } else {
                                            requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                }
                                true
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = if (isPttActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                val isSendEnabled = isOpen && messageText.isNotBlank()
                val sendActionLabel = if (editingMessage != null) "Guardar" else "Enviar"
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            if (viewModel.sendMessage(messageText.trim())) {
                                messageText = ""
                                showEmojiPicker = false
                            }
                        }
                    },
                    enabled = isSendEnabled,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = sendActionLabel
                        role = Role.Button
                        if (!isSendEnabled) {
                            disabled()
                        } else {
                            onClick(label = sendActionLabel) {
                                if (isOpen && messageText.isNotBlank()) {
                                    if (viewModel.sendMessage(messageText.trim())) {
                                        messageText = ""
                                        showEmojiPicker = false
                                    }
                                }
                                true
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (isSendEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }

    // Modal Sheet de Opciones de Mensaje
    selectedMessageForOptions?.let { msg ->
        val isMe = currentUserId != null && msg.senderId == currentUserId
        val canDelete = (isMe || viewModel.isAdmin) && !msg.isDeleted

        val popularEmojis = listOf("👋", "❤️", "👍", "👎", "🔥", "😂", "😮", "😢", "🙏", "🎉", "👏", "🤔", "🎬", "🍿", "🎶", "⭐")
        val sortedEmojis = remember(msg.reactions) {
            val counts = msg.reactions.mapValues { it.value.size }
            popularEmojis.sortedWith(
                compareByDescending<String> { emoji -> counts[emoji] ?: 0 }
                    .thenBy { emoji -> popularEmojis.indexOf(emoji) }
            )
        }

        ModalBottomSheet(
            onDismissRequest = { selectedMessageForOptions = null },
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Opciones para el mensaje de ${msg.senderName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .semantics {
                            traversalIndex = 1f
                        }
                )

                // Carrusel horizontal de reacciones ordenado inteligentemente por uso
                Text(
                    text = "Reaccionar rápidamente:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .semantics {
                            traversalIndex = 1f
                        }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = -1f
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sortedEmojis.forEachIndexed { index, emoji ->
                        val count = msg.reactions[emoji]?.size ?: 0
                        val isFirst = index == 0
                        val reactionTag = "chat_first_reaction_${msg.id}"
                        val emojiDescription = remember(emoji, count) {
                            if (count > 0) "$emoji, $count reacciones" else emoji
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    viewModel.toggleReaction(msg, emoji)
                                    selectedMessageForOptions = null
                                }
                                .clearAndSetSemantics {
                                    if (isFirst) {
                                        testTag = reactionTag
                                        traversalIndex = -1f
                                    }
                                    contentDescription = emojiDescription
                                    role = Role.Button
                                    onClick(label = "Reaccionar con $emoji") {
                                        viewModel.toggleReaction(msg, emoji)
                                        selectedMessageForOptions = null
                                        true
                                    }
                                }
                                .then(
                                    if (isFirst) {
                                        Modifier.initialAccessibilityFocus(
                                            tag = reactionTag,
                                            reassertAfterTransition = true
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                                if (count > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$count",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                ListItem(
                    headlineContent = { Text("Responder", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        viewModel.setReplyingTo(msg)
                        selectedMessageForOptions = null
                    }
                )

                ListItem(
                    headlineContent = { Text("Mencionar a @${msg.senderName}", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        messageText = "@${msg.senderName} "
                        selectedMessageForOptions = null
                    }
                )

                if (isMe && !msg.isDeleted) {
                    ListItem(
                        headlineContent = { Text("Editar", fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            viewModel.setEditingMessage(msg)
                            selectedMessageForOptions = null
                        }
                    )
                }

                if (canDelete) {
                    ListItem(
                        headlineContent = { Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            viewModel.deleteMessage(msg)
                            selectedMessageForOptions = null
                        }
                    )
                }
            }
        }
    }

    // Diálogos auxiliares
    if (showRulesDialog) {
        ChatRulesDialog(
            onDismiss = {
                viewModel.markRulesAsSeen()
                showRulesDialog = false
            }
        )
    }

    if (showConnectedUsersDialog) {
        ConnectedUsersDialog(
            onlineUsers = onlineUsers,
            isAdmin = viewModel.isAdmin,
            currentUserId = currentUserId,
            onReplyToUser = { name ->
                messageText = "@$name "
            },
            onMentionUser = { name ->
                messageText = "@$name "
            },
            onBanUser = { _ ->
                showConnectedUsersDialog = false
            },
            onDismiss = { showConnectedUsersDialog = false }
        )
    }

    if (showBannedUsersDialog) {
        val bannedList by viewModel.bannedUsers.collectAsState()
        BannedUsersDialog(
            bannedUsers = bannedList,
            onUnbanUser = { userId -> viewModel.unbanUser(userId) },
            onDismiss = { showBannedUsersDialog = false }
        )
    }

    // Mensajes de Sanción / Estado
    banStatusMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearBanStatusMessage() },
            title = { Text("Aviso de Sanción", fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearBanStatusMessage() }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
fun ChatMessageCard(
    message: ChatMessage,
    currentUserId: String?,
    isAdmin: Boolean = false,
    onReply: () -> Unit = {},
    onMention: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReactionToggle: (String) -> Unit = {},
    onScrollToMessage: (String) -> Unit = {},
    onItemClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isMe = currentUserId != null && message.senderId == currentUserId
    val canDelete = (isMe || isAdmin) && !message.isDeleted

    val timeStr = remember(message.timestamp) {
        val date = Date(message.timestamp.seconds * 1000)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    val reactionCounts = remember(message.reactions) {
        message.reactions.mapValues { it.value.size }.filterValues { it > 0 }
    }

    val replyText = remember(message.replyToSenderName, message.replyToText) {
        if (!message.replyToText.isNullOrBlank()) {
            "Respondiendo a ${message.replyToSenderName ?: "usuario"}: ${message.replyToText}"
        } else ""
    }

    val fullDescription = remember(message.senderName, message.text, timeStr, reactionCounts) {
        val reactionsText = if (reactionCounts.isNotEmpty()) {
            "Reacciones: " + reactionCounts.entries.joinToString(", ") { "${it.key} ${it.value}" }
        } else ""
        listOf("${message.senderName}: ${message.text}", reactionsText, "Enviado a las $timeStr")
            .filter { it.isNotBlank() }
            .joinToString(". ")
    }

    val customActionsList = remember(isMe, canDelete, message.isDeleted) {
        val list = mutableListOf<CustomAccessibilityAction>()
        list.add(CustomAccessibilityAction("Abrir más opciones") {
            onLongClick()
            true
        })
        list.add(CustomAccessibilityAction("Responder") {
            onReply()
            true
        })
        list.add(CustomAccessibilityAction("Mencionar a @${message.senderName}") {
            onMention()
            true
        })
        if (isMe && !message.isDeleted) {
            list.add(CustomAccessibilityAction("Editar") {
                onEdit()
                true
            })
        }
        if (canDelete) {
            list.add(CustomAccessibilityAction("Eliminar") {
                onDelete()
                true
            })
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Ítem principal del mensaje (A la izquierda)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = onItemClick,
                            onLongClick = onLongClick
                        )
                        .clearAndSetSemantics {
                            contentDescription = fullDescription
                            customActions = customActionsList
                        }
                ) {
                    if (!isMe) {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    Text(
                        text = if (message.isDeleted) "Este mensaje ha sido eliminado" else message.text,
                        fontSize = 14.sp,
                        color = if (message.isDeleted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                    )

                    // Fila de reacciones (WhatsApp / Telegram style)
                    if (reactionCounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            reactionCounts.forEach { (emoji, count) ->
                                val userHasReacted = currentUserId != null && message.reactions[emoji]?.contains(currentUserId) == true
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (userHasReacted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .clickable { onReactionToggle(emoji) }
                                        .clearAndSetSemantics {
                                            contentDescription = "Reacción $emoji, $count"
                                            role = Role.Button
                                            onClick(label = "Reaccionar $emoji") {
                                                onReactionToggle(emoji)
                                                true
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = emoji, fontSize = 13.sp)
                                        if (count > 1) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "$count",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (userHasReacted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // 2. Sub-ítem independiente para la cita de respuesta (A la derecha en la misma fila)
                if (!message.replyToText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0EA5E9).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.3f)),
                        modifier = Modifier
                            .weight(0.8f)
                            .clickable {
                                message.replyToMessageId?.let { targetId ->
                                    onScrollToMessage(targetId)
                                }
                            }
                            .clearAndSetSemantics {
                                contentDescription = "En respuesta al mensaje de ${message.replyToSenderName ?: "usuario"}: ${message.replyToText}"
                                onClick(label = "Ir al mensaje original de ${message.replyToSenderName ?: "usuario"}") {
                                    message.replyToMessageId?.let { onScrollToMessage(it) }
                                    true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Surface(
                                color = Color(0xFF0EA5E9),
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                            ) {}

                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    text = message.replyToSenderName ?: "Usuario",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = message.replyToText,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
