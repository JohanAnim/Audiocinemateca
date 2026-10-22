package com.johang.audiocinemateca.presentation.community.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.johang.audiocinemateca.data.model.Announcement
import com.johang.audiocinemateca.presentation.community.AnnouncementsViewModel
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import kotlinx.coroutines.tasks.await

@Composable
fun AnnouncementsTab(
    viewModel: AnnouncementsViewModel
) {
    val announcements by viewModel.announcements.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val currentUser = FirebaseAuth.getInstance().currentUser
    val currentUserId = currentUser?.uid ?: ""
    var isAdmin by remember { mutableStateOf(false) }

    var announcementToDelete by remember { mutableStateOf<String?>(null) }
    var announcementToReact by remember { mutableStateOf<Announcement?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        val isSuperAdmin = currentUser?.email?.lowercase() == "gutierrezjohanantonio@gmail.com"
        var isAdminRole = false
        if (currentUserId.isNotBlank()) {
            try {
                val userDoc = FirebaseFirestore.getInstance()
                    .collection("users").document(currentUserId).get().await()
                isAdminRole = userDoc.getString("role") == "admin"
            } catch (_: Exception) {}
        }
        isAdmin = isSuperAdmin || isAdminRole
    }

    Scaffold(
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nuevo anuncio", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading && announcements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (announcements.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No hay anuncios oficiales",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aquí aparecerán las noticias, actualizaciones y comunicados oficiales del equipo de Audiocinemateca.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = announcements,
                        key = { it.id }
                    ) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            currentUserId = currentUserId,
                            isAdmin = isAdmin,
                            onReactClick = { announcementToReact = announcement },
                            onDeleteClick = { announcementToDelete = announcement.id }
                        )
                    }
                }
            }
        }
    }

    // Diálogo para crear anuncio
    if (showCreateDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Nuevo Anuncio Oficial", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Escribe el mensaje del anuncio...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            viewModel.postAnnouncement(text.trim())
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Publicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Selector de reacciones
    announcementToReact?.let { announcement ->
        val emojis = listOf("❤️", "🔥", "👏", "📢", "🤔")
        AlertDialog(
            onDismissRequest = { announcementToReact = null },
            title = { Text("Reaccionar al anuncio", fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    emojis.forEach { emoji ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable {
                                    viewModel.toggleReaction(announcement.id, emoji)
                                    announcementToReact = null
                                }
                                .padding(8.dp)
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { announcementToReact = null }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Confirmación de eliminación
    announcementToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { announcementToDelete = null },
            title = { Text("¿Eliminar anuncio?") },
            text = { Text("Esta acción es permanente y eliminará el anuncio para todos los usuarios.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAnnouncement(id)
                        announcementToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { announcementToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    currentUserId: String,
    isAdmin: Boolean,
    onReactClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val reactionCounts = remember(announcement.reactions) {
        announcement.reactions.values.groupingBy { it }.eachCount()
    }
    val userReaction = announcement.reactions[currentUserId]

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${announcement.adminName} (Administrador)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isAdmin) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Eliminar anuncio",
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = announcement.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Reacciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (reactionCounts.isNotEmpty()) {
                        reactionCounts.forEach { (emoji, count) ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "$emoji $count",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Sin reacciones aún",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                TextButton(
                    onClick = onReactClick
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = if (userReaction != null) "Mi reacción ($userReaction)" else "Reaccionar")
                }
            }
        }
    }
}
