package com.johang.audiocinemateca.presentation.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeToolbar(
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSurpriseMeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(
                text = "Audiocinemateca",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color.White
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
                    tint = Color.White
                )
            }
        },
        actions = {
            com.johang.audiocinemateca.presentation.cast.CastButton()
            IconButton(
                onClick = onSurpriseMeClick,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Sorpréndeme, recomendación aleatoria",
                    role = Role.Button,
                    onClickAction = onSurpriseMeClick
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B)
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
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF0F172A),
            titleContentColor = Color.White
        ),
        modifier = modifier
    )
}
