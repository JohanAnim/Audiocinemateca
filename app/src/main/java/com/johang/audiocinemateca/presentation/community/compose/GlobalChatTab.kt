package com.johang.audiocinemateca.presentation.community.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkAsUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.community.ChatListItem
import com.johang.audiocinemateca.presentation.community.GlobalChatViewModel
import com.johang.audiocinemateca.presentation.community.components.*
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalChatTab(
    viewModel: GlobalChatViewModel,
    onVoiceClick: () -> Unit,
    onNavigateToPlayer: (CatalogItem, Int, Int) -> Unit
) {
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

    // Dialogs
    var showRulesDialog by remember { mutableStateOf(false) }
    var showConnectedUsersDialog by remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        // Encabezado del Chat
        GlobalChatHeader(
            onlineCount = onlineCount,
            isAdmin = viewModel.isAdmin,
            onHeaderClick = { showConnectedUsersDialog = true },
            onOptionsClick = { showRulesDialog = true }
        )

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
                                        tonalElevation = 2.dp
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
                                    onEdit = { viewModel.setEditingMessage(item.message) },
                                    onDelete = { viewModel.deleteMessage(item.message) },
                                    onReactionToggle = { emoji -> viewModel.toggleReaction(item.message, emoji) },
                                    onItemClick = { selectedMessageForOptions = item.message },
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
                    IconButton(
                        onClick = {
                            viewModel.setReplyingTo(null)
                            viewModel.setEditingMessage(null)
                            messageText = ""
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar")
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
                    .height(200.dp)
            ) {
                val emojiList = listOf("❤️", "😂", "😮", "😢", "😡", "👍", "🙏", "🔥", "🎉", "👋", "👏", "🤔", "🎬", "🍿", "🎶", "⭐")
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showEmojiPicker = !showEmojiPicker }
                ) {
                    Icon(
                        imageVector = Icons.Default.Mood,
                        contentDescription = "Emojis",
                        tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Escribe un mensaje...") },
                    enabled = isOpen,
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                if (viewModel.sendMessage(messageText.trim())) {
                                    messageText = ""
                                    showEmojiPicker = false
                                }
                            }
                        }
                    )
                )

                IconButton(
                    onClick = onVoiceClick,
                    enabled = isOpen
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Dictar mensaje por voz",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            if (viewModel.sendMessage(messageText.trim())) {
                                messageText = ""
                                showEmojiPicker = false
                            }
                        }
                    },
                    enabled = isOpen && messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Modal Sheet de Opciones de Mensaje
    selectedMessageForOptions?.let { msg ->
        val isMe = currentUserId != null && msg.senderId == currentUserId
        val canDelete = (isMe || viewModel.isAdmin) && !msg.isDeleted

        val popularEmojis = listOf("❤️", "😂", "🔥", "👍", "😮", "😢", "🙏", "🎉")
        val sortedEmojis = remember(msg.reactions) {
            val counts = msg.reactions.mapValues { it.value.size }
            popularEmojis.sortedByDescending { emoji -> counts[emoji] ?: 0 }
        }

        val firstReactionFocusRequester = remember { FocusRequester() }

        LaunchedEffect(msg.id) {
            delay(300L)
            try {
                firstReactionFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }

        ModalBottomSheet(
            onDismissRequest = { selectedMessageForOptions = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Opciones para el mensaje de ${msg.senderName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // Carrusel horizontal de reacciones ordenado inteligentemente por uso
                Text(
                    text = "Reaccionar rápidamente:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sortedEmojis.forEachIndexed { index, emoji ->
                        val count = msg.reactions[emoji]?.size ?: 0
                        val isFirst = index == 0
                        val emojiDescription = remember(emoji, count) {
                            if (count > 0) "$emoji, $count reacciones" else emoji
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .then(if (isFirst) Modifier.focusRequester(firstReactionFocusRequester) else Modifier)
                                .clickable {
                                    viewModel.toggleReaction(msg, emoji)
                                    selectedMessageForOptions = null
                                }
                                .clearAndSetSemantics {
                                    contentDescription = emojiDescription
                                    role = Role.Button
                                    onClick(label = "Reaccionar con $emoji") {
                                        viewModel.toggleReaction(msg, emoji)
                                        selectedMessageForOptions = null
                                        true
                                    }
                                }
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
                    headlineContent = { Text("Responder mensaje", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        viewModel.setReplyingTo(msg)
                        selectedMessageForOptions = null
                    }
                )

                if (isMe && !msg.isDeleted) {
                    ListItem(
                        headlineContent = { Text("Editar mensaje", fontWeight = FontWeight.Medium) },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            viewModel.setEditingMessage(msg)
                            selectedMessageForOptions = null
                        }
                    )
                }

                if (canDelete) {
                    ListItem(
                        headlineContent = { Text("Eliminar mensaje", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
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
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReactionToggle: (String) -> Unit = {},
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
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onItemClick,
                    onLongClick = onLongClick
                )
                .clearAndSetSemantics {
                    contentDescription = fullDescription
                    customActions = customActionsList
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isMe) {
                    Text(
                        text = message.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (!message.replyToText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Respuesta a ${message.replyToSenderName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.replyToText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
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
        }
    }
}
