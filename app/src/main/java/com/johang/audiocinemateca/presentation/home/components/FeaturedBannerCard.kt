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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.FeaturedBanner
import com.johang.audiocinemateca.presentation.components.PlayButton
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun FeaturedBannerCard(
    banner: FeaturedBanner,
    onPlayClick: (FeaturedBanner) -> Unit,
    onFavoriteToggle: (FeaturedBanner) -> Unit,
    onTitleClick: (FeaturedBanner) -> Unit,
    modifier: Modifier = Modifier,
    playText: String? = null
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1B4B)
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.5.dp, Color(0xFF6366F1).copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 390.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 390.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF312E81),
                            Color(0xFF1E1B4B),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(28.dp)
        ) {
            Column {
                // Insignia de Contenido Destacado + Tipo de Contenido
                val displayType = when (banner.itemType.lowercase()) {
                    "serie", "series" -> "SERIE"
                    "documental", "documentary" -> "DOCUMENTAL"
                    "cortometraje", "short" -> "CORTOMETRAJE"
                    else -> "PELÍCULA"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = Color(0xFF6366F1),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "★ DESTACADO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF4338CA),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = displayType,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E7FF),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Título del Banner (con evento de clic para ver detalles)
                Text(
                    text = banner.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 34.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        onClickLabel = "Ver detalles de ${banner.title}"
                    ) { onTitleClick(banner) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Fila de Calificación Accesible + Géneros en una sola línea
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Calificación Real
                    val ratingFormatted = String.format(java.util.Locale.US, "%.1f", banner.rating)
                    val ratingText = "$ratingFormatted de ${banner.maxRating} estrellas"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = ratingText
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_favorite_filled),
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$ratingFormatted / ${banner.maxRating}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    // Separador visual
                    Text(
                        text = "•",
                        fontSize = 15.sp,
                        color = Color(0xFF64748B)
                    )

                    // Géneros Reales en una sola línea separados por /
                    val genresFormatted = if (banner.genres.isNotEmpty()) {
                        banner.genres.joinToString(" / ")
                    } else "Cine"

                    Text(
                        text = genresFormatted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFC7D2FE),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Descripción
                Text(
                    text = banner.description,
                    fontSize = 15.sp,
                    color = Color(0xFFE0E7FF),
                    lineHeight = 22.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Acciones: Botón de Reproducción + Botón de Favorito
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PlayButton(
                    hasStartedListening = banner.hasStartedListening,
                    title = banner.title,
                    onClick = { onPlayClick(banner) },
                    modifier = Modifier.weight(1f),
                    customText = playText
                )

                // Botón de Agregar / Quitar de Favoritos
                val favText = if (banner.isFavorite) "Quitar de mis favoritos" else "Agregar a mis favoritos"
                IconButton(
                    onClick = { onFavoriteToggle(banner) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (banner.isFavorite) Color(0xFFDC2626) else Color(0xFF3730A3)
                        )
                        .semantics { contentDescription = favText }
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (banner.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
                        ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
