package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringArrayResource
import com.johang.audiocinemateca.R
import android.app.AlertDialog
import androidx.compose.ui.platform.LocalContext

@Composable
fun PlaybackSettingsScreen(
    viewModel: SettingsViewModel,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val autoplay by viewModel.autoplay.collectAsState()
    val sleepTimerEnabled by viewModel.sleepTimerEnabled.collectAsState()
    
    val sleepDurationEntries = stringArrayResource(id = R.array.sleep_timer_entries).toList()
    val sleepDurationValues = stringArrayResource(id = R.array.sleep_timer_values).toList()
    var currentSleepDuration by remember { mutableStateOf(viewModel.getString("sleep_timer_duration", "60")) }

    val rewindEntries = stringArrayResource(id = R.array.rewind_interval_entries).toList()
    val rewindValues = stringArrayResource(id = R.array.rewind_interval_values).toList()
    var currentRewind by remember { mutableStateOf(viewModel.getString("rewind_interval", "5")) }

    val forwardEntries = stringArrayResource(id = R.array.forward_interval_entries).toList()
    val forwardValues = stringArrayResource(id = R.array.forward_interval_values).toList()
    var currentForward by remember { mutableStateOf(viewModel.getString("forward_interval", "15")) }

    CategorySettingsScreen(
        title = "Ajustes de Reproducción",
        onBack = onBack
    ) {
        SettingsSwitchItem(
            title = "Reproducción automática",
            summary = "Reproducir la siguiente parte o episodio automáticamente.",
            checked = autoplay,
            onCheckedChange = { viewModel.updateBoolean("autoplay", it) }
        )

        SettingsSwitchItem(
            title = "Temporizador de descanso",
            summary = "Pausa la reproducción después de un tiempo de inactividad.",
            checked = sleepTimerEnabled,
            onCheckedChange = { viewModel.updateBoolean("sleep_timer_enabled", it) }
        )

        SettingsListItem(
            title = "Tiempo de inactividad",
            summary = sleepDurationEntries[sleepDurationValues.indexOf(currentSleepDuration).coerceAtLeast(0)],
            entries = sleepDurationEntries,
            entryValues = sleepDurationValues,
            currentValue = currentSleepDuration,
            onValueChange = { 
                currentSleepDuration = it
                viewModel.updateString("sleep_timer_duration", it)
            },
            enabled = sleepTimerEnabled
        )

        SettingsListItem(
            title = "Intervalo de retroceso",
            summary = rewindEntries[rewindValues.indexOf(currentRewind).coerceAtLeast(0)],
            entries = rewindEntries,
            entryValues = rewindValues,
            currentValue = currentRewind,
            onValueChange = { 
                currentRewind = it
                viewModel.updateString("rewind_interval", it)
            }
        )

        SettingsListItem(
            title = "Intervalo de avance",
            summary = forwardEntries[forwardValues.indexOf(currentForward).coerceAtLeast(0)],
            entries = forwardEntries,
            entryValues = forwardValues,
            currentValue = currentForward,
            onValueChange = { 
                currentForward = it
                viewModel.updateString("forward_interval", it)
            }
        )

        SettingsClickItem(
            title = "Eliminar historial de reproducción",
            summary = "Borra todo el contenido que has visto.",
            onClick = onClearHistory
        )

        SettingsCategoryHeader("Inmersión Sensorial")

        var hapticEnabled by remember { mutableStateOf(viewModel.getBoolean("haptic_audio_enabled", false)) }
        SettingsSwitchItem(
            title = "Vibración Háptica Avanzada",
            summary = "Sincroniza la vibración con el audio en tiempo real para una inmersión total (Android 12+).",
            checked = hapticEnabled,
            onCheckedChange = { 
                hapticEnabled = it
                viewModel.updateBoolean("haptic_audio_enabled", it)
            }
        )
    }
}
