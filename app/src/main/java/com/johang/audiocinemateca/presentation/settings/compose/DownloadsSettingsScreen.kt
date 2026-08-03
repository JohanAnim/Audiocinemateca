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
    val storageEntries = remember { mutableStateListOf<String>() }
    val storageValues = remember { mutableStateListOf<String>() }
    var currentStorage by remember { mutableStateOf(viewModel.getString("download_location", "")) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumeNames = MediaStore.getExternalVolumeNames(context)
            volumeNames.forEach { volumeName ->
                val isPrimary = volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY
                storageEntries.add(if (isPrimary) "Memoria interna" else "Almacenamiento externo")
                storageValues.add(volumeName)
            }
        }
        if (currentStorage.isEmpty() && storageValues.isNotEmpty()) {
            currentStorage = storageValues.first()
        }
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
            onCheckedChange = { 
                offlineMode = it
                viewModel.updateBoolean("offline_mode", it)
                if (it) Toast.makeText(context, "Modo offline activado.", Toast.LENGTH_SHORT).show()
            }
        )
    }
}
