package com.johang.audiocinemateca.presentation.community.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.Timestamp
import com.johang.audiocinemateca.data.model.BannedUser
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun BanUserDialog(
    targetUserId: String,
    targetDisplayName: String,
    targetEmail: String,
    onConfirmBan: (BannedUser) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("Infracción de reglas de la comunidad") }
    var selectedDurationIndex by remember { mutableIntStateOf(1) } // Default 24h

    val durationOptions = listOf(
        "1 hora" to (60 * 60L),
        "24 horas" to (24 * 60 * 60L),
        "7 días" to (7 * 24 * 60 * 60L),
        "Permanente" to null
    )

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
                Text(
                    text = "Suspender del Chat Global",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Usuario: $targetDisplayName",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Duración de la sanción:",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    durationOptions.forEachIndexed { index, (label, _) ->
                        val isSelected = index == selectedDurationIndex
                        Surface(
                            color = if (isSelected) Color(0xFFEF4444) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedDurationIndex = index }
                                .audiocinematecaAccessibility(
                                    label = label,
                                    role = Role.Button,
                                    onClickAction = { selectedDurationIndex = index }
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo de la sanción", color = Color(0xFF94A3B8)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val secondsAdd = durationOptions[selectedDurationIndex].second
                            val bannedUntil = if (secondsAdd != null) {
                                Timestamp(Timestamp.now().seconds + secondsAdd, 0)
                            } else null

                            val bannedUser = BannedUser(
                                userId = targetUserId,
                                displayName = targetDisplayName,
                                email = targetEmail,
                                reason = reason.ifBlank { "Infracción de reglas de la comunidad" },
                                bannedUntil = bannedUntil
                            )
                            onConfirmBan(bannedUser)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Suspender", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
