package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.johang.audiocinemateca.R

@Composable
fun CommunitySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val announcementEntries = stringArrayResource(id = R.array.chat_announcement_entries).toList()
    val announcementValues = stringArrayResource(id = R.array.chat_announcement_values).toList()
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
