package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun Top10RankingSection(
    top10List: List<Pair<Int, CatalogItem>>,
    isLoading: Boolean,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    onPlayContent: (itemId: String, itemType: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Encabezado del Top 10 con Barra de Acento Dorado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(Color(0xFFF59E0B), shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Top 10 de la Semana",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Lo más escuchado y mejor valorado por la comunidad esta semana",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 16.sp
                )
            }
        }

        // Estado de Carga o Carrusel
        if (isLoading) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Calculando el Top 10 semanal...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        } else if (top10List.isEmpty()) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No se pudieron obtener los datos del ranking en este momento.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = top10List,
                    key = { index, pair -> "top10_${index}_${pair.first}_${pair.second.id}" }
                ) { index, pair ->
                    val rankPosition = pair.first
                    val item = pair.second

                    Top10RankingCard(
                        rankPosition = rankPosition,
                        item = item,
                        onItemClick = onItemClick@{ catalogItem, itemType ->
                            onNavigateToDetail(catalogItem.id, itemType)
                        },
                        onPlayClick = onPlayClick@{ catalogItem, itemType ->
                            onPlayContent(catalogItem.id, itemType)
                        }
                    )
                }
            }
        }
    }
}
