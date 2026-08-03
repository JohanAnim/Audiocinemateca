package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ContinueListeningSection(
    itemsList: List<ContinueListeningItem>,
    onResumeContent: (itemId: String, itemType: String, partIndex: Int, episodeIndex: Int) -> Unit,
    onMarkAsWatched: (ContinueListeningItem) -> Unit,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itemsList.isEmpty()) return

    val listState = rememberLazyListState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Encabezado con barra de acento visual
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(Color(0xFF6366F1), shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Continuar escuchando",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Reanuda rápidamente hasta donde lo dejaste",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }
        }

        // Carrusel Horizontal
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = itemsList,
                key = { item -> "cont_${item.catalogItem.id}_${item.progress.partIndex}_${item.progress.episodeIndex}" }
            ) { item ->
                ContinueListeningCard(
                    item = item,
                    onResumeClick = { catalogItem, itemType ->
                        onResumeContent(catalogItem.id, itemType, item.progress.partIndex, item.progress.episodeIndex)
                    },
                    onMarkAsWatched = { onMarkAsWatched(item) },
                    onNavigateToDetail = onNavigateToDetail
                )
            }
        }
    }
}
