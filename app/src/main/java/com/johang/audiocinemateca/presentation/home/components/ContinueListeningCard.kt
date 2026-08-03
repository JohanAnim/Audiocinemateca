package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem
import java.util.Locale

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info

data class ContinueListeningItem(
    val catalogItem: CatalogItem,
    val progress: PlaybackProgressEntity,
    val itemType: String,
    val subtitleText: String,
    val remainingTimeText: String,
    val progressRatio: Float
)

@Composable
fun ContinueListeningCard(
    item: ContinueListeningItem,
    onResumeClick: (CatalogItem, String) -> Unit,
    onMarkAsWatched: () -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showOptions by remember { mutableStateOf(false) }

    val progressPercent = (item.progressRatio * 100).toInt().coerceIn(0, 100)

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
            .width(250.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Área Clicable Principal (sin duplicidad de texto para TalkBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {
                        contentDescription = "Continuar ${item.catalogItem.title}, ${item.subtitleText}, ${item.remainingTimeText}, $progressPercent por ciento completado"
                    }
                    .clickable(
                        onClickLabel = "Reanudar ${item.catalogItem.title}"
                    ) { onResumeClick(item.catalogItem, item.itemType) }
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Insignia de Episodio / Tipo
                Surface(
                    color = Color(0xFF312E81),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.subtitleText.uppercase(Locale.ROOT),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA5B4FC),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Título
                Text(
                    text = item.catalogItem.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // Tiempo Restante, Porcentaje y Barra de Progreso
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.remainingTimeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF818CF8)
                        )
                        Text(
                            text = "$progressPercent%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA5B4FC)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { item.progressRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF6366F1),
                        trackColor = Color(0xFF334155)
                    )
                }
            }

            // Menú de Opciones en esquina superior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                IconButton(
                    onClick = { showOptions = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "Opciones para ${item.catalogItem.title}",
                        tint = Color(0xFFCBD5E1)
                    )
                }
                DropdownMenu(
                    expanded = showOptions,
                    onDismissRequest = { showOptions = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Ver detalles") },
                        onClick = {
                            showOptions = false
                            onNavigateToDetail(item.catalogItem.id, item.itemType)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Marcar como visto") },
                        onClick = {
                            showOptions = false
                            onMarkAsWatched()
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_thumb_up),
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}
