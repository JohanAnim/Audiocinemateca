package com.johang.audiocinemateca.presentation.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogToolbar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSurpriseMeClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Audiocinemateca",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Abrir menú lateral de navegación",
                    role = Role.Button,
                    onClickAction = onMenuClick
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        actions = {
            com.johang.audiocinemateca.presentation.cast.CastButton()
            IconButton(
                onClick = onSurpriseMeClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Sorpréndeme, reproducir recomendación aleatoria",
                    role = Role.Button,
                    onClickAction = onSurpriseMeClick
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Buscar contenido",
                    role = Role.Button,
                    onClickAction = onSearchClick
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
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
