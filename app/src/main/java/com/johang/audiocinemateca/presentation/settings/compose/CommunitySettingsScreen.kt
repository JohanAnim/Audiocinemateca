package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CommunitySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val announcementEntries = remember {
        listOf("Solo cuando no escucho nada (Por defecto)", "En todo momento", "Nunca")
    }
    val announcementValues = remember { listOf("idle_only", "always", "never") }
    var currentMode by remember { mutableStateOf(viewModel.getString("chat_announcement_mode", "idle_only")) }
    var showPresence by remember { mutableStateOf(viewModel.getBoolean("community_show_presence", true)) }

    CategorySettingsScreen(
        title = "Comunidad",
        onBack = onBack
    ) {
        SettingsSwitchItem(
            title = "Mostrar presencia en línea",
            summary = if (showPresence) "Apareces en la lista de usuarios conectados en el chat" else "Oculto, no aparecerás en la lista de usuarios conectados",
            checked = showPresence,
            isInitialFocus = true,
            onCheckedChange = { checked ->
                showPresence = checked
                viewModel.updateBoolean("community_show_presence", checked)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsListItem(
            title = "Anuncios de voz del Chat Global",
            summary = announcementEntries[announcementValues.indexOf(currentMode).coerceAtLeast(0)],
            entries = announcementEntries,
            entryValues = announcementValues,
            currentValue = currentMode,
            onValueChange = { 
                currentMode = it
                viewModel.updateString("chat_announcement_mode", it)
            }
        )
    }
}
