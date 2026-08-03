package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.data.model.FeaturedBanner
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun FeaturedBannerSection(
    banner: FeaturedBanner?,
    isBannerLoading: Boolean,
    playText: String,
    onPlayClick: (FeaturedBanner) -> Unit,
    onTitleClick: (FeaturedBanner) -> Unit,
    onFavoriteToggle: (FeaturedBanner) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isBannerLoading) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1B4B)
            ),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF1E1B4B))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6366F1),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cargando lo destacado...",
                        fontSize = 14.sp,
                        color = Color(0xFFC7D2FE)
                    )
                }
            }
        }
    } else if (banner != null) {
        FeaturedBannerCard(
            banner = banner,
            onPlayClick = onPlayClick,
            onFavoriteToggle = onFavoriteToggle,
            onTitleClick = onTitleClick,
            playText = playText,
            modifier = modifier
        )
    }
}
