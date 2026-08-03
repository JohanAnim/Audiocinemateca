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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun RecommendationsSection(
    userName: String?,
    recommendations: List<CatalogItem>,
    isLoading: Boolean,
    onNavigateToDetail: (itemId: String, itemType: String) -> Unit,
    onPlayContent: (itemId: String, itemType: String) -> Unit,
    onRefreshRecommendations: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = if (!userName.isNullOrBlank()) userName else "Johan"
    val headerTitle = "Mis recomendaciones para ti, $displayName"

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado de la Sección de Recomendaciones con Barra de Acento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(0xFF8B5CF6), shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = headerTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Creemos que esta selección podría gustarte",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 16.sp
                    )
                }
            }

            IconButton(
                onClick = onRefreshRecommendations,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = "Actualizar recomendaciones" }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Carrusel Horizontal de Recomendaciones
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
                            color = Color(0xFF8B5CF6),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Cargando tus recomendaciones...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        } else if (recommendations.isEmpty()) {
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Explora más audios para generar tus recomendaciones personalizadas.",
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
                    items = recommendations,
                    key = { index, item -> "rec_${index}_${item.id}" }
                ) { index, item ->
                    RecommendationCard(
                        item = item,
                        onItemClick = { catalogItem, itemType ->
                            onNavigateToDetail(catalogItem.id, itemType)
                        },
                        onPlayClick = { catalogItem, itemType ->
                            onPlayContent(catalogItem.id, itemType)
                        }
                    )
                }
            }
        }
    }
}
