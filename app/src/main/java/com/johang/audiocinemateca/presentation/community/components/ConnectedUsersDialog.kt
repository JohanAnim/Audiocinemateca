package com.johang.audiocinemateca.presentation.community.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.johang.audiocinemateca.data.model.OnlineUser
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun ConnectedUsersDialog(
    onlineUsers: List<OnlineUser>,
    isAdmin: Boolean,
    currentUserId: String? = null,
    onReplyToUser: (name: String) -> Unit,
    onMentionUser: (name: String) -> Unit,
    onBanUser: (OnlineUser) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedUserForAction by remember { mutableStateOf<OnlineUser?>(null) }
    val adminEmail = "gutierrezjohanantonio@gmail.com"

    val auraUser = remember {
        OnlineUser(
            userId = "AURA_AI",
            displayName = "Aura (IA)",
            email = "Asistente de IA Oficial",
            online = true
        )
    }

    val fullUsersList = remember(onlineUsers) {
        listOf(auraUser) + onlineUsers.filter { it.userId != "AURA_AI" }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E293B),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF22C55E), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Usuarios Conectados (${fullUsersList.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (fullUsersList.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Text(
                            text = "No hay otros usuarios activos en este momento.",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(fullUsersList, key = { index, user -> "connected_${index}_${user.userId}" }) { index, user ->
                            val displayName = when {
                                user.displayName.isNotBlank() -> user.displayName
                                user.email.isNotBlank() -> user.email.substringBefore("@")
                                else -> "Usuario #${user.userId.takeLast(4)}"
                            }

                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedUserForAction = user.copy(displayName = displayName) }
                                    .audiocinematecaAccessibility(
                                        label = "Usuario: $displayName. Toca para ver opciones de interacción.",
                                        role = Role.Button,
                                        onClickAction = { selectedUserForAction = user.copy(displayName = displayName) }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF6366F1),
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            val initial = displayName.firstOrNull()?.uppercase() ?: "U"
                                            Text(
                                                text = initial,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (user.email.isNotBlank()) {
                                            Text(
                                                text = user.email,
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF22C55E), CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF334155),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cerrar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Sub-dialog con acciones del usuario seleccionado
    selectedUserForAction?.let { user ->
        val isSelfOrAura = (currentUserId != null && user.userId == currentUserId) || 
                           user.userId == "AURA_AI" ||
                           user.email.equals(adminEmail, ignoreCase = true)

        val mentionName = if (user.userId == "AURA_AI") "Aura" else user.displayName

        AlertDialog(
            onDismissRequest = { selectedUserForAction = null },
            title = {
                Text(
                    text = "Opciones para ${user.displayName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = {
                            onMentionUser(mentionName)
                            selectedUserForAction = null
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("💬 Mencionar a @$mentionName", fontSize = 15.sp, color = Color.White)
                    }

                    if (isAdmin && !isSelfOrAura) {
                        TextButton(
                            onClick = {
                                val target = user
                                selectedUserForAction = null
                                onBanUser(target)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🚫 Suspender / Sancionar del Chat", fontSize = 15.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedUserForAction = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
