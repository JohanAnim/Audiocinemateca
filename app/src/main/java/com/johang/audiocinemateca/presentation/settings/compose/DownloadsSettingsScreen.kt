package com.johang.audiocinemateca.presentation.settings.compose

import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.R

@Composable
fun DownloadsSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var offlineMode by remember { mutableStateOf(viewModel.getBoolean("offline_mode", false)) }
    
    // Storage location logic
    val (storageEntries, storageValues) = remember(context) {
        val entries = mutableListOf<String>()
        val values = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumeNames = MediaStore.getExternalVolumeNames(context)
            volumeNames.forEach { volumeName ->
                val isPrimary = volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY
                entries.add(if (isPrimary) "Memoria interna" else "Almacenamiento externo")
                values.add(volumeName)
            }
        }
        Pair(entries, values)
    }
    var currentStorage by remember {
        val saved = viewModel.getString("download_location", "")
        mutableStateOf(if (saved.isEmpty() && storageValues.isNotEmpty()) storageValues.first() else saved)
    }

    CategorySettingsScreen(
        title = "Ajustes de Descargas",
        onBack = onBack
    ) {
        if (storageValues.isNotEmpty()) {
            SettingsListItem(
                title = "Ubicación de almacenamiento",
                summary = if (currentStorage.isNotEmpty()) storageEntries[storageValues.indexOf(currentStorage).coerceAtLeast(0)] else "Seleccionar...",
                entries = storageEntries,
                entryValues = storageValues,
                currentValue = currentStorage,
                isInitialFocus = true,
                onValueChange = { 
                    currentStorage = it
                    viewModel.updateString("download_location", it)
                }
            )
        }

        SettingsSwitchItem(
            title = "Modo offline total",
            summary = "Activa este modo para usar la app sin conexión a internet.",
            checked = offlineMode,
            isInitialFocus = storageValues.isEmpty(),
            onCheckedChange = { 
                offlineMode = it
                viewModel.updateBoolean("offline_mode", it)
                if (it) Toast.makeText(context, "Modo offline activado.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
