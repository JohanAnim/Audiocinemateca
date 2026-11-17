package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.util.accessibleHeading
import com.johang.audiocinemateca.util.initialAccessibilityFocus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategory: (String) -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onBack: () -> Unit
) {
    // Recuerda la categoría enfocada: al entrar por primera vez enfoca "general" (la 1ra opción),
    // y al regresar desde cualquier subcategoría restaura el foco exactamente en esa categoría.
    var focusedItemId by rememberSaveable { mutableStateOf("general") }

    Scaffold(
        modifier = Modifier.semantics { isTraversalGroup = true },
        topBar = {
            TopAppBar(
                modifier = Modifier.semantics { traversalIndex = 1f },
                title = { Text("Ajustes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
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
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
        ) {
            item {
                SettingsHeader("Configuración")
            }

            items(
                items = getSettingsCategories(),
                key = { it.id }
            ) { item ->
                SettingsItem(
                    title = item.title,
                    summary = item.summary,
                    icon = item.icon,
                    itemTag = "settings_cat_${item.id}",
                    isInitialFocus = item.id == focusedItemId,
                    onClick = {
                        focusedItemId = item.id
                        onNavigateToCategory(item.id)
                    }
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
                    itemTag = "settings_cat_privacy",
                    isInitialFocus = focusedItemId == "privacy",
                    onClick = {
                        focusedItemId = "privacy"
                        onNavigateToPrivacy()
                    }
                )
            }

            item {
                SettingsItem(
                    title = "Términos y Condiciones",
                    summary = "Normas de uso de la aplicación.",
                    icon = Icons.Default.Description,
                    itemTag = "settings_cat_terms",
                    isInitialFocus = focusedItemId == "terms",
                    onClick = {
                        focusedItemId = "terms"
                        onNavigateToTerms()
                    }
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
            .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
            .accessibleHeading(title),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsItem(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    itemTag: String = "settings_item_$title",
    isInitialFocus: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                testTag = itemTag
                if (isInitialFocus) {
                    traversalIndex = -1f
                }
                contentDescription = if (summary.isNotBlank() && summary != title) {
                    "$title. $summary"
                } else {
                    title
                }
                onClick(label = title) {
                    onClick()
                    true
                }
            }
            .then(
                if (isInitialFocus) {
                    Modifier.initialAccessibilityFocus(tag = itemTag)
                } else {
                    Modifier
                }
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
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
    val icon: ImageVector
)

fun getSettingsCategories() = listOf(
    SettingsCategory("general", "General", "Temas, pestaña de inicio y actualizaciones.", Icons.Default.Settings),
    SettingsCategory("playback", "Reproducción", "Controles, temporizador e historial.", Icons.Default.PlayCircleOutline),
    SettingsCategory("community", "Comunidad", "Ajustes del Chat Global y anuncios de voz.", Icons.Default.People),
    SettingsCategory("ai", "Inteligencia Artificial", "Proveedores, modelos y clave de acceso.", Icons.Default.AutoAwesome),
    SettingsCategory("tts", "Ajustes de Texto a Voz", "Motores de voz, tono y velocidad.", Icons.Default.RecordVoiceOver),
    SettingsCategory("downloads", "Descargas", "Ubicación de archivos y modo offline.", Icons.Default.Download)
)
