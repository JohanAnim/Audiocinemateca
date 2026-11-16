package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.runtime.*
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
    
    val sleepDurationEntries = remember { listOf("30 minutos", "1 hora", "2 horas") }
    val sleepDurationValues = remember { listOf("30", "60", "120") }
    var currentSleepDuration by remember { mutableStateOf(viewModel.getString("sleep_timer_duration", "60")) }

    val rewindEntries = remember { listOf("5 segundos", "10 segundos", "15 segundos", "30 segundos") }
    val rewindValues = remember { listOf("5", "10", "15", "30") }
    var currentRewind by remember { mutableStateOf(viewModel.getString("rewind_interval", "5")) }

    val forwardEntries = remember { listOf("5 segundos", "10 segundos", "15 segundos", "30 segundos") }
    val forwardValues = remember { listOf("5", "10", "15", "30") }
    var currentForward by remember { mutableStateOf(viewModel.getString("forward_interval", "15")) }

    CategorySettingsScreen(
        title = "Ajustes de Reproducción",
        onBack = onBack
    ) {
        SettingsSwitchItem(
            title = "Reproducción automática",
            summary = "Reproducir la siguiente parte o episodio automáticamente.",
            checked = autoplay,
            isInitialFocus = true,
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

        var gesturesEnabled by remember { mutableStateOf(viewModel.getBoolean("enable_player_gestures", true)) }
        SettingsSwitchItem(
            title = "Gestos táctiles en el reproductor",
            summary = "Permite deslizar en la pantalla del reproductor para adelantar, retroceder, pausar o consultar el tiempo.",
            checked = gesturesEnabled,
            onCheckedChange = { 
                gesturesEnabled = it
                viewModel.updateBoolean("enable_player_gestures", it)
            }
        )

        var handleFocusEnabled by remember { mutableStateOf(viewModel.getBoolean("handle_audio_focus", true)) }
        SettingsSwitchItem(
            title = "Manejar foco de audio",
            summary = "Pausa la reproducción automáticamente al recibir llamadas o notificaciones y se reanuda al finalizar. Desactívalo si usas Discord o TikTok en segundo plano.",
            checked = handleFocusEnabled,
            onCheckedChange = { 
                handleFocusEnabled = it
                viewModel.updateBoolean("handle_audio_focus", it)
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
            summary = "Sincroniza la vibración con las acciones de la película en tiempo real usando un mando conectado (ej. PS5 DualSense) o en teléfonos con motor háptico avanzado (Android 12+).",
            checked = hapticEnabled,
            onCheckedChange = { 
                hapticEnabled = it
                viewModel.updateBoolean("haptic_audio_enabled", it)
                if (it) {
                    com.johang.audiocinemateca.media.CinemaHapticEngine.triggerConfirmationPulseFromSettings(context)
                }
            }
        )
    }
}
