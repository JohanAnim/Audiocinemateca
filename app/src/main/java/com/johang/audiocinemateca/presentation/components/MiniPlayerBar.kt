package com.johang.audiocinemateca.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johang.audiocinemateca.util.initialAccessibilityFocus

@Composable
fun MiniPlayerBar(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    onBarClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Área de Información (Clic para expandir a pantalla completa)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBarClick)
                    .clearAndSetSemantics {
                        testTag = "mini_player_header"
                        heading()
                        traversalIndex = -1f
                        contentDescription = "Reproductor minimizado. $title. $subtitle"
                        onClick(label = "Expandir a pantalla completa") {
                            onBarClick()
                            true
                        }
                    }
                    .initialAccessibilityFocus(
                        tag = "mini_player_header"
                    )
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fila de Controles (Play/Pause y Cerrar)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Botón Play / Pause
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0EA5E9),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                            role = Role.Button
                            onClick(label = if (isPlaying) "Pausar" else "Reproducir") {
                                onPlayPauseClick()
                                true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Botón Detener y Cerrar
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clearAndSetSemantics {
                            contentDescription = "Detener y cerrar reproductor"
                            role = Role.Button
                            onClick(label = "Detener y cerrar reproductor") {
                                onCloseClick()
                                true
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
