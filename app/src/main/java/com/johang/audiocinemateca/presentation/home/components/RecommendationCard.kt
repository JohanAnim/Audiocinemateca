package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

@Composable
fun RecommendationCard(
    item: CatalogItem,
    onItemClick: (CatalogItem, String) -> Unit,
    onPlayClick: (CatalogItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemType = when (item) {
        is Serie -> "serie"
        is Movie -> "pelicula"
        is Documentary -> "documental"
        is ShortFilm -> "cortometraje"
        else -> "pelicula"
    }

    val displayType = when (itemType) {
        "serie" -> "SERIE"
        "documental" -> "DOCUMENTAL"
        "cortometraje" -> "CORTOMETRAJE"
        else -> "PELÍCULA"
    }

    val dubbingText = when (item.idioma.trim()) {
        "1" -> "Doblaje Castellano"
        "2" -> "Doblaje Latino"
        else -> {
            val lower = item.idioma.lowercase()
            if (lower.contains("latino")) "Doblaje Latino"
            else if (lower.contains("españa") || lower.contains("castellano")) "Doblaje Castellano"
            else if (item.idioma.isNotBlank()) "Doblaje ${item.idioma}"
            else "Español"
        }
    }

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
            .width(210.dp)
            .height(185.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Área Clicable Principal (Detalles del contenido sin duplicidad para TalkBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        onClickLabel = "Ver detalles de ${item.title}"
                    ) { onItemClick(item, itemType) }
                    .padding(16.dp)
                    .clearAndSetSemantics {
                        contentDescription = "${item.title}, $displayType, $dubbingText"
                    },
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Surface(
                        color = Color(0xFF4338CA),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = displayType,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E7FF),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dubbingText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF38BDF8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Botón de Reproducción Rápida en la esquina inferior derecha
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Button(
                    onClick = { onPlayClick(item, itemType) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics {
                        contentDescription = "Reproducir ${item.title}"
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reproducir",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
