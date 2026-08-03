package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            item {
                SettingsHeader("Configuración")
            }

            items(getSettingsCategories()) { item ->
                SettingsItem(
                    title = item.title,
                    summary = item.summary,
                    icon = item.icon,
                    onClick = { onNavigateToCategory(item.id) }
                )
            }

            item {
                SettingsHeader("Información Legal")
            }

            item {
                SettingsItem(
                    title = "Política de Privacidad",
                    summary = "Lee cómo protegemos tus datos personales.",
                    icon = Icons.Default.PrivacyTip,
                    onClick = onNavigateToPrivacy
                )
            }

            item {
                SettingsItem(
                    title = "Términos y Condiciones",
                    summary = "Normas de uso de la aplicación.",
                    icon = Icons.Default.Description,
                    onClick = onNavigateToTerms
                )
            }
        }
    }
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    icon: Any, // Puede ser ImageVector o Int (Resource)
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .audiocinematecaAccessibility(
                label = "$title. $summary",
                role = Role.Button,
                onClickAction = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                when (icon) {
                    is ImageVector -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    is Int -> Icon(
                        painter = painterResource(id = icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val icon: Int
)

fun getSettingsCategories() = listOf(
    SettingsCategory("general", "General", "Temas, pestaña de inicio y actualizaciones.", R.drawable.ic_account),
    SettingsCategory("playback", "Reproducción", "Controles, temporizador e historial.", R.drawable.ic_history),
    SettingsCategory("community", "Comunidad", "Ajustes del Chat Global y anuncios de voz.", R.drawable.ic_catalog),
    SettingsCategory("ai", "Inteligencia Artificial", "Configuración de Gemini y clave API.", R.drawable.ic_ai_chat),
    SettingsCategory("tts", "Ajustes de Texto a Voz", "Motores de voz, tono y velocidad.", R.drawable.ic_account),
    SettingsCategory("downloads", "Descargas", "Ubicación de archivos y modo offline.", R.drawable.ic_downloads)
)
