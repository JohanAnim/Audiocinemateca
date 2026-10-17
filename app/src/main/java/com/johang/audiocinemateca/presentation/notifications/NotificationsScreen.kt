package com.johang.audiocinemateca.presentation.notifications

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.local.entities.NotificationEntity
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val notifications by viewModel.notificationsFlow.collectAsState(initial = emptyList())
    var notificationToDelete by remember { mutableStateOf<NotificationEntity?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notificaciones",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
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
                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.audiocinematecaAccessibility(
                                label = "Limpiar todo el historial de notificaciones",
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (notifications.isEmpty()) {
                EmptyNotificationsView(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = notifications,
                        key = { it.id }
                    ) { notification ->
                        NotificationCard(
                            notification = notification,
                            onLongClick = { notificationToDelete = notification },
                            onDeleteClick = { notificationToDelete = notification },
                            onNavigate = { destination, url ->
                                handleNotificationNavigation(context, destination, url)
                            }
                        )
                    }
                }
            }
        }
    }

    // Diálogo para eliminar una notificación individual
    notificationToDelete?.let { notif ->
        AlertDialog(
            onDismissRequest = { notificationToDelete = null },
            title = { Text(text = "Eliminar notificación") },
            text = { Text(text = "¿Deseas eliminar esta notificación del historial?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNotification(notif)
                        notificationToDelete = null
                        Toast.makeText(context, "Notificación eliminada", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { notificationToDelete = null }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }

    // Diálogo para limpiar todo el historial
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(text = "Limpiar historial") },
            text = { Text(text = "¿Estás seguro de que quieres borrar todas las notificaciones? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearNotifications()
                        showClearAllDialog = false
                        Toast.makeText(context, "Historial de notificaciones limpiado", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "Borrar todo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = "Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationCard(
    notification: NotificationEntity,
    onLongClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNavigate: (destination: String?, url: String?) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateStr = remember(notification.timestamp) { dateFormat.format(Date(notification.timestamp)) }
    
    val hasAction = !notification.linkUrl.isNullOrBlank() || !notification.destination.isNullOrBlank()
    val isDonation = notification.destination == "donation" ||
            notification.linkUrl?.contains("donar", ignoreCase = true) == true ||
            notification.linkUrl?.contains("paypal", ignoreCase = true) == true

    val actionLabel = remember(notification.destination, notification.linkUrl) {
        when {
            isDonation -> "Donar ahora"
            notification.destination == "announcements" -> "Ver Anuncios"
            notification.destination == "content_detail" -> "Ver contenido"
            notification.destination == "global_chat" -> "Ir al Chat"
            notification.destination == "comments" -> "Ver comentarios"
            notification.linkUrl?.contains("audiocinemateca.com") == true -> "Ver contenido"
            else -> "Abrir enlace"
        }
    }

    val accessibilityDescription = buildString {
        append(notification.title)
        append(". ")
        append(notification.body)
        append(". Recibido el ")
        append(dateStr)
        append(". Mantén pulsado para eliminar.")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* No-op o abrir detalle si aplica */ },
                onLongClick = onLongClick
            )
            .audiocinematecaAccessibility(
                label = accessibilityDescription,
                role = Role.Button
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = notification.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(32.dp)
                        .audiocinematecaAccessibility(
                            label = "Eliminar notificación: ${notification.title}",
                            role = Role.Button
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification.body,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recibido: $dateStr",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                if (hasAction) {
                    FilledTonalButton(
                        onClick = { onNavigate(notification.destination, notification.linkUrl) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "$actionLabel para ${notification.title}",
                            role = Role.Button
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyNotificationsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.NotificationsNone,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tienes notificaciones",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Aquí recibirás avisos de nuevos estrenos, comunicados oficiales, menciones y novedades del catálogo.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

private fun handleNotificationNavigation(context: Context, destination: String?, url: String?) {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("navigate_to", destination)
        putExtra("url", url)
    }
    context.startActivity(intent)
}
