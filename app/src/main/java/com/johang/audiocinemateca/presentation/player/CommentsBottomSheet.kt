package com.johang.audiocinemateca.presentation.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    var commentToReport by remember { mutableStateOf<Comment?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null
    ) {
        CommentsSheetContent(
            title = if (detailedTitle.isNotBlank()) "Comentarios de $detailedTitle" else "Comentarios de la comunidad",
            comments = commentsList,
            activeSort = activeSort,
            currentUserId = currentUserId,
            onSortSelect = { option -> viewModel.setSortOption(option) },
            onLikeClick = { commentId -> viewModel.onCommentLikeClicked(commentId) },
            onSendComment = { text ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val lastDate = sharedPreferencesManager.getString("last_comment_date", "")
                var count = sharedPreferencesManager.getInt("daily_comment_count", 0)

                if (today != lastDate) {
                    count = 0
                    sharedPreferencesManager.saveString("last_comment_date", today)
                }

                if (count >= 5) {
                    Toast.makeText(context, "Límite diario de comentarios alcanzado.", Toast.LENGTH_LONG).show()
                } else {
                    viewModel.onAddCommentClicked(text)
                    sharedPreferencesManager.saveInt("daily_comment_count", count + 1)
                }
            },
            onDeleteComment = { comment -> commentToDelete = comment },
            onReportComment = { comment -> commentToReport = comment },
            onDismiss = onDismiss
        )
    }

    commentToDelete?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text("Eliminar comentario") },
            text = { Text("¿Estás seguro de que deseas borrar este comentario?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteCommentClicked(comment.id)
                        commentToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    commentToReport?.let { comment ->
        AlertDialog(
            onDismissRequest = { commentToReport = null },
            title = { Text("Reportar comentario") },
            text = { Text("¿Deseas enviar un reporte sobre este comentario a los administradores?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onReportCommentClicked(comment)
                        commentToReport = null
                    }
                ) {
                    Text("Reportar")
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToReport = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CommentsSheetContent(
    title: String,
    comments: List<Comment>,
    activeSort: SortOption,
    currentUserId: String?,
    onSortSelect: (SortOption) -> Unit,
    onLikeClick: (String) -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Comment) -> Unit,
    onReportComment: (Comment) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Comment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Cerrar comentarios"
                        role = Role.Button
                        onClick(label = "Cerrar") {
                            onDismiss()
                            true
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeSort == SortOption.RECENT,
                    onClick = { onSortSelect(SortOption.RECENT) },
                    label = { Text("Recientes") }
                )
                FilterChip(
                    selected = activeSort == SortOption.POPULAR,
                    onClick = { onSortSelect(SortOption.POPULAR) },
                    label = { Text("Más votados") }
                )
                FilterChip(
                    selected = activeSort == SortOption.OLDEST,
                    onClick = { onSortSelect(SortOption.OLDEST) },
                    label = { Text("Antiguos") }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (comments.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Aún no hay comentarios. ¡Sé el primero en participar!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val isMyComment = currentUserId == comment.userId
                        val isLiked = currentUserId != null && comment.likes.contains(currentUserId)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = comment.userName.take(1).uppercase(Locale.ROOT),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = comment.userName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (comment.isPending) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("(Enviando...)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }

                                    var showMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones del comentario", modifier = Modifier.size(18.dp))
                                        }

                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Copiar texto") },
                                                onClick = {
                                                    showMenu = false
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Comentario", comment.commentText))
                                                    Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Responder") },
                                                onClick = {
                                                    showMenu = false
                                                    inputText = "@${comment.userName} "
                                                }
                                            )
                                            if (isMyComment) {
                                                DropdownMenuItem(
                                                    text = { Text("Eliminar comentario", color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        showMenu = false
                                                        onDeleteComment(comment)
                                                    }
                                                )
                                            } else {
                                                DropdownMenuItem(
                                                    text = { Text("Reportar comentario") },
                                                    onClick = {
                                                        showMenu = false
                                                        onReportComment(comment)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = comment.commentText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { onLikeClick(comment.id) }
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                            contentDescription = null,
                                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${comment.likes.size}",
                                            fontSize = 12.sp,
                                            color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    TextButton(
                                        onClick = { inputText = "@${comment.userName} " },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Responder", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 4.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Escribe un comentario...") },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendComment(inputText.trim())
                                inputText = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.clearAndSetSemantics {
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                }
            }
        }
    }
}
