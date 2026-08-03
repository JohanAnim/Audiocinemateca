package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringArrayResource
import com.johang.audiocinemateca.R

@Composable
fun CommunitySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val announcementEntries = stringArrayResource(id = R.array.chat_announcement_entries).toList()
    val announcementValues = stringArrayResource(id = R.array.chat_announcement_values).toList()
    var currentMode by remember { mutableStateOf(viewModel.getString("chat_announcement_mode", "idle_only")) }

    CategorySettingsScreen(
        title = "Comunidad",
        onBack = onBack
    ) {
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
