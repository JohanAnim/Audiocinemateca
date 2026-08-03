package com.johang.audiocinemateca.presentation.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailToolbar(
    title: String,
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onShareClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Volver",
                    role = Role.Button,
                    onClickAction = onBackClick
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        actions = {
            com.johang.audiocinemateca.presentation.cast.CastButton()
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = if (isFavorite) "Eliminar de favoritos" else "Añadir a favoritos",
                    role = Role.Button,
                    onClickAction = onFavoriteClick
                )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFE57373) else MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Compartir contenido",
                    role = Role.Button,
                    onClickAction = onShareClick
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    )
}
