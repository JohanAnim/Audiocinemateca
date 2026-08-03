package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import com.johang.audiocinemateca.data.model.CuratedCollection
import com.johang.audiocinemateca.data.model.CuratedItem
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun CuratedCollectionSection(
    collection: CuratedCollection,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    onPlayContent: (itemId: String, itemType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (collection.title.isBlank() || collection.items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // ENCABEZADO ULTRA PROFESIONAL ESTILO NETFLIX / CRUNCHYROLL
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = collection.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            if (collection.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = collection.subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CARRUSEL HORIZONTAL DE OBRAS
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(collection.items, key = { "${it.id}_${it.type}" }) { item ->
                CuratedItemCard(
                    item = item,
                    onNavigateToDetail = onNavigateToDetail,
                    onPlayContent = onPlayContent
                )
            }
        }
    }
}

@Composable
private fun CuratedItemCard(
    item: CuratedItem,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    onPlayContent: (itemId: String, itemType: String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(160.dp)
            .clickable { onNavigateToDetail(item.id, item.type) }
            .audiocinematecaAccessibility(
                label = "${item.title}. Toca para abrir detalles.",
                role = Role.Button,
                onClickAction = { onNavigateToDetail(item.id, item.type) }
            )
    ) {
        Column(
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = icon, fontSize = 20.sp)
                if (item.year.isNotBlank() && item.year != "N/A") {
                    Surface(
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.year,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (item.genre.isNotBlank()) {
                Text(
                    text = item.genre,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            TextButton(
                onClick = { onPlayContent(item.id, item.type) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .height(28.dp)
                    .audiocinematecaAccessibility(
                        label = "Reproducir ${item.title}",
                        role = Role.Button,
                        onClickAction = { onPlayContent(item.id, item.type) }
                    )
            ) {
                Text(
                    text = "▶ Reproducir",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }
        }
    }
}
