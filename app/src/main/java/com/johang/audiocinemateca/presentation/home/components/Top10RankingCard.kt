package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem
import java.util.Locale

@Composable
fun Top10RankingCard(
    rankPosition: Int,
    item: CatalogItem,
    onItemClick: (CatalogItem, String) -> Unit,
    onPlayClick: (CatalogItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemType = getItemTypeString(item)
    val itemTypeLabel = getItemTypeBadgeLabel(itemType)
    val dubbingLabel = getDubbingLabel(item.idioma)

    val badgeBgColor = when (rankPosition) {
        1 -> Color(0xFFF59E0B) // Dorado
        2 -> Color(0xFF94A3B8) // Plata
        3 -> Color(0xFFD97706) // Bronce
        else -> Color(0xFF6366F1) // Índigo
    }

    val badgeTextColor = if (rankPosition in 1..3) Color.Black else Color.White

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
            .width(220.dp)
            .height(190.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Área Clicable Principal (sin duplicidad de texto para lectores de pantalla)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClickLabel = "Ver detalles del puesto $rankPosition"
                    ) { onItemClick(item, itemType) }
                    .padding(16.dp)
                    .clearAndSetSemantics {
                        contentDescription = "Puesto $rankPosition: ${item.title}, $itemTypeLabel, $dubbingLabel"
                    },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Insignias de Posición y Tipo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(color = badgeBgColor, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$rankPosition",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = badgeTextColor
                        )
                    }

                    Surface(
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = itemTypeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Título
                Text(
                    text = item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // Doblaje
                Text(
                    text = dubbingLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
            }

            // Botón de Reproducción en la esquina inferior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                IconButton(
                    onClick = { onPlayClick(item, itemType) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF6366F1), CircleShape)
                        .clearAndSetSemantics {
                            contentDescription = "Reproducir ${item.title}"
                        }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun getItemTypeString(item: CatalogItem): String {
    return when (item) {
        is Serie -> "serie"
        is Movie -> "pelicula"
        is Documentary -> "documental"
        is ShortFilm -> "cortometraje"
        else -> "pelicula"
    }
}

private fun getItemTypeBadgeLabel(type: String): String {
    return when (type) {
        "serie" -> "SERIE"
        "pelicula" -> "PELÍCULA"
        "documental" -> "DOCUMENTAL"
        "cortometraje" -> "CORTOMETRAJE"
        else -> "PELÍCULA"
    }
}

private fun getDubbingLabel(rawIdioma: String): String {
    val trimmed = rawIdioma.trim()
    if (trimmed == "2") return "Doblaje Latino"
    if (trimmed == "1") return "Doblaje Castellano"
    val lower = rawIdioma.lowercase(Locale.ROOT)
    if (lower.contains("latino")) return "Doblaje Latino"
    if (lower.contains("españa") || lower.contains("castellano")) return "Doblaje Castellano"
    return "Doblaje Latino"
}
