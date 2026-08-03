package com.johang.audiocinemateca.presentation.community.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.presentation.aichat.LinkedContent
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun AllRecommendationsDialog(
    items: List<LinkedContent>,
    onDismissRequest: () -> Unit,
    onItemSelected: (LinkedContent) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF0F172A),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎬 Recomendaciones (${items.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Selecciona una obra para ver su ficha técnica",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Cerrar diálogo de recomendaciones",
                        role = Role.Button,
                        onClickAction = onDismissRequest
                    )
                ) {
                    Text(text = "❌", fontSize = 16.sp)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemSelected(item) }
                            .audiocinematecaAccessibility(
                                label = "Ficha de ${item.title}. Toca para abrir.",
                                role = Role.Button,
                                onClickAction = { onItemSelected(item) }
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            val icon = when (item.type.lowercase()) {
                                "series" -> "📺"
                                "documentales" -> "📽️"
                                "cortometrajes" -> "🎞️"
                                else -> "🎬"
                            }
                            Text(text = icon, fontSize = 22.sp)

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Abrir ficha técnica ➔",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Cerrar",
                    role = Role.Button,
                    onClickAction = onDismissRequest
                )
            ) {
                Text(
                    text = "Cerrar",
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
