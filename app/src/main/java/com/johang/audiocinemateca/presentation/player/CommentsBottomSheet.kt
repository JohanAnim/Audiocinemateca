package com.johang.audiocinemateca.presentation.player

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics. onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Comment
import com.johang.audiocinemateca.util.initialAccessibilityFocus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SheetBgColor = Color(0xFF0F172A)
private val CardBgColor = Color(0xFF1E293B)
private val CardBorderColor = Color(0xFF334155)
private val AccentCyan = Color(0xFF38BDF8)
private val PrimaryBlue = Color(0xFF0EA5E9)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFF94A3B8)
private val LikedGreen = Color(0xFF22C55E)
private val DangerRed = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    viewModel: PlayerViewModel,
    sharedPreferencesManager: SharedPreferencesManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val commentsList by viewModel.allComments.collectAsState(initial = emptyList())
    val activeSort by viewModel.sortOption.collectAsState()
    val detailedTitle = viewModel.getDetailedTitle()
    val currentUserId = viewModel.getCurrentUserId()

    var selectedCommentForOptions by remember { mutableStateOf<Comment?>(null) }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var commentToReport by remember { mutableStateOf<Comment?>(null) }
    var replyingToComment by remember { mutableStateOf<Comment?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBgColor,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        CommentsSheetContent(
            title = if (detailedTitle.isNotBlank()) detailedTitle else "Esta obra",
            comments = commentsList,
            activeSort = activeSort,
            currentUserId = currentUserId,
            replyingTo = replyingToComment,
            onCancelReply = { replyingToComment = null },
            onReplySelect = { comment -> replyingToComment = comment },
            onSortSelect = { option -> viewModel.setSortOption(option) },
            onLikeClick = { commentId -> viewModel.onCommentLikeClicked(commentId) },
            onOpenCommentOptions = { comment -> selectedCommentForOptions = comment },
            onSendComment = { rawText ->
                val finalText = if (replyingToComment != null && !rawText.startsWith("@${replyingToComment!!.userName}")) {
                    "@${replyingToComment!!.userName} $rawText"
                } else {
                    rawText
                }

                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastDate = sharedPreferencesManager.getString("last_comment_date", "")
                var count = sharedPreferencesManager.getInt("daily_comment_count", 0)

                if (today != lastDate) {
                    count = 0
                    sharedPreferencesManager.saveString("last_comment_date", today)
                }

                if (count >= 5) {
                    Toast.makeText(context, "Límite diario de 5 comentarios alcanzado.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.onAddCommentClicked(finalText)
                    sharedPreferencesManager.saveInt("daily_comment_count", count + 1)
                    replyingToComment = null
                }
            },
            onDeleteComment = { comment -> commentToDelete = comment },
            onReportComment = { comment -> commentToReport = comment },
            onDismiss = onDismiss
        )
    }

    // Menú de Opciones del Comentario (Accesible y Cómodo)
    selectedCommentForOptions?.let { comment ->
        val isMyComment = currentUserId != null && currentUserId == comment.userId
        val isLiked = currentUserId != null && comment.likes.contains(currentUserId)

        AlertDialog(
            onDismissRequest = { selectedCommentForOptions = null },
            containerColor = CardBgColor,
            titleContentColor = TextPrimary,
            textContentColor = TextPrimary,
            title = {
                Column {
                    Text(
                        text = "Comentario de ${comment.userName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = comment.commentText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Dar / Quitar Me gusta
                    CommentOptionButton(
                        icon = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                        label = if (isLiked) "Quitar Me gusta (${comment.likes.size})" else "Dar Me gusta (${comment.likes.size})",
                        tint = if (isLiked) LikedGreen else AccentCyan,
                        modifier = Modifier.initialAccessibilityFocus("comment_option_first"),
                        testTag = "comment_option_first",
                        onClick = {
                            viewModel.onCommentLikeClicked(comment.id)
                            selectedCommentForOptions = null
                        }
                    )

                    // 2. Responder al usuario
                    CommentOptionButton(
                        icon = Icons.AutoMirrored.Filled.Reply,
                        label = "Responder a ${comment.userName}",
                        tint = AccentCyan,
                        onClick = {
                            replyingToComment = comment
                            selectedCommentForOptions = null
                        }
                    )

                    // 3. Copiar texto
                    CommentOptionButton(
                        icon = Icons.Default.ContentCopy,
                        label = "Copiar texto del comentario",
                        tint = TextPrimary,
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Comentario", comment.commentText))
                            Toast.makeText(context, "Comentario copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            selectedCommentForOptions = null
                        }
                    )

                    // 4. Eliminar (propio) o Reportar (de otro usuario)
                    if (isMyComment) {
                        CommentOptionButton(
                            icon = Icons.Default.DeleteOutline,
                            label = "Eliminar mi comentario",
                            tint = DangerRed,
                            onClick = {
                                val target = comment
                                selectedCommentForOptions = null
                                commentToDelete = target
                            }
                        )
                    } else {
                        CommentOptionButton(
                            icon = Icons.Default.Flag,
                            label = "Reportar comentario",
                            tint = Color(0xFFF59E0B),
                            onClick = {
                                val target = comment
                                selectedCommentForOptions = null
                                commentToReport = target
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCommentForOptions = null }) {
                    Text("Cerrar", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    commentToDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            containerColor = CardBgColor,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Eliminar comentario", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas borrar tu comentario de forma permanente?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteCommentClicked(comment.id)
                        commentToDelete = null
                    }
                ) {
                    Text("Eliminar", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text("Cancelar", color = AccentCyan)
                }
            }
        )
    }

    commentToReport?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            containerColor = CardBgColor,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Reportar comentario", fontWeight = FontWeight.Bold) },
            text = { Text("¿Deseas enviar un reporte sobre el comentario de ${comment.userName} a los administradores?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onReportCommentClicked(comment)
                        commentToReport = null
                    }
                ) {
                    Text("Reportar", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text("Cancelar", color = AccentCyan)
                }
            }
        )
    }
}

@Composable
private fun CommentOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SheetBgColor,
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .clearAndSetSemantics {
                if (testTag != null) {
                    this.testTag = testTag
                }
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onClick()
                    true
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (tint == DangerRed) DangerRed else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun CommentsSheetContent(
    title: String,
    comments: List<Comment>,
    activeSort: SortOption,
    currentUserId: String?,
    replyingTo: Comment?,
    onCancelReply: () -> Unit,
    onReplySelect: (Comment) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onLikeClick: (String) -> Unit,
    onOpenCommentOptions: (Comment) -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onReportComment: (Comment) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

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
            Toast.makeText(context, "Se necesita permiso de micrófono para dictar comentario.", Toast.LENGTH_SHORT).show()
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
                        inputText = if (inputText.isBlank()) {
                            spokenText
                        } else {
                            "${inputText.trim()} $spokenText"
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

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = SheetBgColor,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Indicador superior visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CardBorderColor)
                )
            }

            // ==========================================
            // CABECERA DEL PANEL DE COMENTARIOS
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -2f
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val countText = if (comments.size == 1) "1 comentario" else "${comments.size} comentarios"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clearAndSetSemantics {
                            testTag = "comments_sheet_header"
                            heading()
                            contentDescription = "Comentarios de $title. $countText"
                            stateDescription = "Encabezado"
                        }
                        .initialAccessibilityFocus("comments_sheet_header")
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryBlue.copy(alpha = 0.18f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Comment,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Comentarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "$title • $countText",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = CardBgColor,
                    border = BorderStroke(1.dp, CardBorderColor),
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { onDismiss() }
                        .clearAndSetSemantics {
                            contentDescription = "Cerrar comentarios"
                            role = Role.Button
                            onClick(label = "Cerrar comentarios") {
                                onDismiss()
                                true
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ==========================================
            // BARRA DE FILTROS ACCESIBLE (PESTAÑAS)
            // ==========================================
            val sortOptions = remember {
                listOf(
                    SortOption.RECENT to "Recientes",
                    SortOption.POPULAR to "Más votados",
                    SortOption.OLDEST to "Antiguos"
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = -1f
                        collectionInfo = CollectionInfo(rowCount = 1, columnCount = sortOptions.size)
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortOptions.forEachIndexed { index, (option, label) ->
                    val isSelected = activeSort == option
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSelected) PrimaryBlue else CardBgColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) AccentCyan else CardBorderColor
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSortSelect(option)
                            }
                            .clearAndSetSemantics {
                                role = Role.Tab
                                selected = isSelected
                                contentDescription = label
                                stateDescription = "${index + 1} de ${sortOptions.size}"
                                collectionItemInfo = CollectionItemInfo(
                                    rowIndex = 0,
                                    rowSpan = 1,
                                    columnIndex = index,
                                    columnSpan = 1
                                )
                                onClick(label = "Ordenar por $label") {
                                    onSortSelect(option)
                                    true
                                }
                            }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 6.dp),
                color = CardBorderColor.copy(alpha = 0.6f)
            )

            // ==========================================
            // LISTA DE COMENTARIOS O ESTADO VACÍO
            // ==========================================
            if (comments.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Aún no hay comentarios en esta obra. ¡Sé el primero en compartir tu opinión!"
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = CircleShape,
                            color = CardBgColor,
                            border = BorderStroke(1.dp, CardBorderColor),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Aún no hay comentarios",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "¡Sé el primero en compartir qué te pareció esta obra con la comunidad!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics {
                            isTraversalGroup = true
                            traversalIndex = 0f
                            collectionInfo = CollectionInfo(rowCount = comments.size, columnCount = 1)
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val isMyComment = currentUserId != null && currentUserId == comment.userId
                        val isLiked = currentUserId != null && comment.likes.contains(currentUserId)

                        CommentItemCard(
                            comment = comment,
                            isMyComment = isMyComment,
                            isLiked = isLiked,
                            onCardClick = { onOpenCommentOptions(comment) },
                            onLikeClick = { onLikeClick(comment.id) },
                            onReplyClick = { onReplySelect(comment) },
                            onCopyClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Comentario", comment.commentText))
                                Toast.makeText(context, "Comentario copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            onDeleteOrReportClick = {
                                if (isMyComment) onDeleteComment(comment) else onReportComment(comment)
                            }
                        )
                    }
                }
            }

            // ==========================================
            // BANNER DE RESPUESTA ACTIVA (ESTILO TELEGRAM)
            // ==========================================
            AnimatedVisibility(visible = replyingTo != null) {
                replyingTo?.let { target ->
                    Surface(
                        color = CardBgColor,
                        border = BorderStroke(1.dp, CardBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(AccentCyan)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clearAndSetSemantics {
                                        contentDescription = "Respondiendo a ${target.userName}: ${target.commentText}"
                                    }
                            ) {
                                Text(
                                    text = "Respondiendo a @${target.userName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentCyan
                                )
                                Text(
                                    text = target.commentText,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onCancelReply,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clearAndSetSemantics {
                                        contentDescription = "Cancelar respuesta a ${target.userName}"
                                        role = Role.Button
                                        onClick(label = "Cancelar respuesta") {
                                            onCancelReply()
                                            true
                                        }
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // BARRA INFERIOR DE ENTRADA DE COMENTARIO
            // ==========================================
            Surface(
                color = CardBgColor,
                border = BorderStroke(1.dp, CardBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        isTraversalGroup = true
                        traversalIndex = 1f
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val placeholderLabel = if (replyingTo != null) {
                        "Responder a @${replyingTo.userName}..."
                    } else {
                        "Escribe un comentario..."
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = if (isListeningVoice) "Escuchando voz... (suelta para terminar)" else placeholderLabel,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SheetBgColor,
                            unfocusedContainerColor = SheetBgColor,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = CardBorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank()) {
                                    onSendComment(inputText.trim())
                                    inputText = ""
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(22.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botón de Dictado por Voz (Mantener presionado para dictar, soltar para terminar)
                    var isPointerDownVoice by remember { mutableStateOf(false) }
                    val isPttActive = isListeningVoice || isPointerDownVoice

                    Surface(
                        shape = CircleShape,
                        color = if (isPttActive) DangerRed.copy(alpha = 0.25f) else SheetBgColor,
                        border = BorderStroke(1.dp, if (isPttActive) DangerRed else CardBorderColor),
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
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
                                    "Escuchando voz. Suelta para terminar el comentario, o toca dos veces para detener"
                                } else {
                                    "Dictar comentario por voz. Mantén presionado para hablar y suelta para terminar, o toca dos veces para iniciar"
                                }
                                onClick(label = if (isPttActive) "Terminar dictado de comentario" else "Dictar comentario por voz") {
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
                                    true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isPttActive) DangerRed else AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botón de Enviar Comentario
                    val canSend = inputText.isNotBlank()
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) PrimaryBlue else SheetBgColor,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (canSend) AccentCyan else CardBorderColor
                        ),
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(enabled = canSend) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSendComment(inputText.trim())
                                inputText = ""
                            }
                            .clearAndSetSemantics {
                                contentDescription = "Enviar comentario"
                                role = Role.Button
                                onClick(label = "Enviar comentario") {
                                    if (inputText.isNotBlank()) {
                                        onSendComment(inputText.trim())
                                        inputText = ""
                                    }
                                    true
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = if (canSend) Color.White else TextSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItemCard(
    comment: Comment,
    isMyComment: Boolean,
    isLiked: Boolean,
    onCardClick: () -> Unit,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onCopyClick: () -> Unit,
    onDeleteOrReportClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val relativeDateText = remember(comment.timestamp, comment.isPending) {
        if (comment.isPending) {
            "Enviando ahora"
        } else {
            formatRelativeCommentTime(comment.timestamp.toDate())
        }
    }

    val likesSpeech = remember(comment.likes.size, isLiked) {
        val count = comment.likes.size
        val base = when (count) {
            0 -> "Sin me gusta"
            1 -> "1 me gusta"
            else -> "$count me gusta"
        }
        if (isLiked) "$base, te gusta" else base
    }

    val fullSemanticDescription = remember(
        comment.userName,
        isMyComment,
        relativeDateText,
        comment.commentText,
        likesSpeech
    ) {
        val authorLabel = if (isMyComment) "${comment.userName} (Tú)" else comment.userName
        "Comentario de $authorLabel, $relativeDateText: ${comment.commentText}. $likesSpeech"
    }

    val likeActionLabel = if (isLiked) "Quitar Me gusta" else "Dar Me gusta"
    val deleteOrReportLabel = if (isMyComment) "Eliminar mi comentario" else "Reportar comentario"

    val accessibilityActions = remember(
        likeActionLabel,
        comment.userName,
        deleteOrReportLabel,
        onLikeClick,
        onReplyClick,
        onCopyClick,
        onDeleteOrReportClick,
        onCardClick
    ) {
        listOf(
            CustomAccessibilityAction(likeActionLabel) {
                onLikeClick()
                true
            },
            CustomAccessibilityAction("Responder a ${comment.userName}") {
                onReplyClick()
                true
            },
            CustomAccessibilityAction("Copiar comentario") {
                onCopyClick()
                true
            },
            CustomAccessibilityAction(deleteOrReportLabel) {
                onDeleteOrReportClick()
                true
            },
            CustomAccessibilityAction("Más opciones") {
                onCardClick()
                true
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CardBgColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isMyComment) PrimaryBlue.copy(alpha = 0.55f) else CardBorderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCardClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCardClick()
                }
            )
            .clearAndSetSemantics {
                contentDescription = fullSemanticDescription
                role = Role.Button
                customActions = accessibilityActions
                onClick(label = "Opciones del comentario de ${comment.userName}") {
                    onCardClick()
                    true
                }
                onLongClick(label = "Opciones del comentario de ${comment.userName}") {
                    onCardClick()
                    true
                }
            }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Cabecera de la tarjeta: Avatar + Nombre + Badge "Tú" + Fecha + Botón visual de opciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isMyComment) PrimaryBlue else Color(0xFF334155),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = comment.userName.take(1).uppercase(Locale.ROOT),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = comment.userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isMyComment) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = PrimaryBlue.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = "Tú",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = relativeDateText,
                            fontSize = 11.sp,
                            color = if (comment.isPending) AccentCyan else TextSecondary
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cuerpo del Comentario
            Text(
                text = comment.commentText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFF1F5F9),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Pie del Comentario: Píldora de Me gusta + Píldora de Responder
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isLiked) LikedGreen.copy(alpha = 0.18f) else SheetBgColor,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isLiked) LikedGreen.copy(alpha = 0.6f) else CardBorderColor
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                            contentDescription = null,
                            tint = if (isLiked) LikedGreen else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (comment.likes.isEmpty()) "Me gusta" else "${comment.likes.size}",
                            fontSize = 12.sp,
                            fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium,
                            color = if (isLiked) LikedGreen else TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SheetBgColor,
                    border = BorderStroke(1.dp, CardBorderColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Responder",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentCyan
                        )
                    }
                }
            }
        }
    }
}

private fun formatRelativeCommentTime(date: Date?): String {
    if (date == null) return "Hace un momento"
    val now = System.currentTimeMillis()
    val time = date.time
    val diff = now - time
    return when {
        diff < 60_000L -> "Hace un momento"
        diff < 3600_000L -> {
            val mins = (diff / 60_000L).toInt()
            if (mins == 1) "Hace 1 min" else "Hace $mins min"
        }
        diff < 86_400_000L -> {
            val hours = (diff / 3600_000L).toInt()
            if (hours == 1) "Hace 1 hora" else "Hace $hours horas"
        }
        diff < 7 * 86_400_000L -> {
            DateUtils.getRelativeTimeSpanString(
                time,
                now,
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        }
        else -> {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
        }
    }
}
