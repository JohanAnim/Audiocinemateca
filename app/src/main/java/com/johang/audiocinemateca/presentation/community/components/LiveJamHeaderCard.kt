package com.johang.audiocinemateca.presentation.community.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.LiveJamSession
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun LiveJamHeaderCard(
    currentJam: LiveJamSession?,
    isJoined: Boolean,
    isHost: Boolean,
    onJoinJam: (LiveJamSession) -> Unit,
    onLeaveJam: () -> Unit,
    onEndJam: () -> Unit,
    onOpenJamPlayer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentJam != null && currentJam.jamId.isNotBlank(),
        enter = fadeIn() + slideInVertically(initialOffsetY = { -30 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -30 })
    ) {
        if (currentJam == null) return@AnimatedVisibility

        val subtitle = if (currentJam.episodeTitle.isNotBlank()) {
            "T${currentJam.partIndex + 1}:E${currentJam.episodeIndex + 1} - ${currentJam.episodeTitle}"
        } else {
            currentJam.contentType.uppercase()
        }

        Surface(
            onClick = { if (isHost) onOpenJamPlayer() },
            enabled = isHost,
            color = if (isJoined) Color(0xFF064E3B) else Color(0xFF1E1B4B),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (currentJam.isPlaying) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isHost) "🔴 TU JAM EN VIVO" else "🔴 JAM EN VIVO DE ${currentJam.hostName.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isJoined) Color(0xFF6EE7B7) else Color(0xFFA5B4FC)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentJam.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$subtitle • ${currentJam.activeListenersCount} escuchando",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                if (isHost) {
                    Button(
                        onClick = onEndJam,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Finalizar Jam en Vivo",
                            role = Role.Button,
                            onClickAction = onEndJam
                        )
                    ) {
                        Text(
                            text = "Finalizar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (isJoined) {
                    Button(
                        onClick = onLeaveJam,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF047857),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Escuchando en vivo. Toca para salir del Jam",
                            role = Role.Button,
                            onClickAction = onLeaveJam
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_play_arrow),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "En Vivo • Salir",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    val isPrivate = currentJam.pinCode.isNotBlank()
                    Button(
                        onClick = { onJoinJam(currentJam) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPrivate) Color(0xFF8B5CF6) else Color(0xFF6366F1),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = if (isPrivate) "Unirme al Jam privado de ${currentJam.hostName}. Requiere contraseña" else "Unirme al Jam en vivo de ${currentJam.hostName}",
                            role = Role.Button,
                            onClickAction = { onJoinJam(currentJam) }
                        )
                    ) {
                        Text(
                            text = if (isPrivate) "🔐 Privado" else "🎧 Unirme",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
