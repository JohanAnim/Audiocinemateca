package com.johang.audiocinemateca.presentation.settings.compose

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.util.TtsManager
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp

@Composable
fun TtsSettingsScreen(
    viewModel: SettingsViewModel,
    ttsManager: TtsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    
    val engineEntries = remember { mutableStateListOf<String>() }
    val engineValues = remember { mutableStateListOf<String>() }
    var currentEngine by remember { mutableStateOf(prefs.getString("tts_engine", "system_default") ?: "system_default") }

    val langEntries = remember { mutableStateListOf<String>() }
    val langValues = remember { mutableStateListOf<String>() }
    var currentLang by remember { mutableStateOf("") }

    val voiceEntries = remember { mutableStateListOf<String>() }
    val voiceValues = remember { mutableStateListOf<String>() }
    var currentVoice by remember { mutableStateOf("") }

    var rate by remember { mutableFloatStateOf(prefs.getFloat("tts_rate_$currentEngine", 1.0f)) }
    var pitch by remember { mutableFloatStateOf(prefs.getFloat("tts_pitch_$currentEngine", 1.0f)) }

    val updateVoices = { lang: String ->
        val voices = ttsManager.getVoicesForCurrentEngine().filter { it.locale.language == lang }
        voiceEntries.clear()
        voiceValues.clear()
        voices.forEach { 
            voiceEntries.add("${it.locale.displayName} (${it.name})")
            voiceValues.add(it.name)
        }
        currentVoice = prefs.getString("tts_voice_$currentEngine", "") ?: ""
        if (currentVoice !in voiceValues && voiceValues.isNotEmpty()) {
            currentVoice = voiceValues.first()
            prefs.edit().putString("tts_voice_$currentEngine", currentVoice).apply()
        }
    }

    val updateLangs = {
        val voices = ttsManager.getVoicesForCurrentEngine()
        val languages = voices.map { it.locale }.distinctBy { it.language }.sortedBy { it.displayName }
        langEntries.clear()
        langValues.clear()
        languages.forEach { 
            langEntries.add(it.displayName.replaceFirstChar { c -> c.uppercase() })
            langValues.add(it.language)
        }
        currentLang = prefs.getString("tts_language_$currentEngine", "") ?: ""
        if (currentLang.isBlank() && langValues.isNotEmpty()) {
            currentLang = if (langValues.contains("es")) "es" else langValues.first()
            prefs.edit().putString("tts_language_$currentEngine", currentLang).apply()
        }
        updateVoices(currentLang)
    }

    LaunchedEffect(Unit) {
        val engines = ttsManager.getAvailableEngines()
        engineEntries.add("Sistema (Predeterminado)")
        engineValues.add("system_default")
        engines.forEach { engineEntries.add(it.label); engineValues.add(it.name) }
        updateLangs()
    }

    CategorySettingsScreen(
        title = "Ajustes de Texto a Voz",
        onBack = onBack
    ) {
        SettingsCategoryHeader("Motor y Voz")

        SettingsListItem(
            title = "Motor de Texto a Voz",
            summary = if (engineValues.indexOf(currentEngine) >= 0) engineEntries[engineValues.indexOf(currentEngine)] else currentEngine,
            entries = engineEntries,
            entryValues = engineValues,
            currentValue = currentEngine,
            onValueChange = { 
                currentEngine = it
                prefs.edit().putString("tts_engine", it).apply()
                // In real app, applying engine might need ttsManager.setEngine(it)
                updateLangs()
                rate = prefs.getFloat("tts_rate_$it", 1.0f)
                pitch = prefs.getFloat("tts_pitch_$it", 1.0f)
            }
        )

        if (langValues.isNotEmpty()) {
            SettingsListItem(
                title = "Idioma de la voz",
                summary = if (langValues.indexOf(currentLang) >= 0) langEntries[langValues.indexOf(currentLang)] else currentLang,
                entries = langEntries,
                entryValues = langValues,
                currentValue = currentLang,
                onValueChange = { 
                    currentLang = it
                    prefs.edit().putString("tts_language_$currentEngine", it).apply()
                    updateVoices(it)
                }
            )
        }

        if (voiceValues.isNotEmpty()) {
            SettingsListItem(
                title = "Voz",
                summary = if (voiceValues.indexOf(currentVoice) >= 0) voiceEntries[voiceValues.indexOf(currentVoice)] else currentVoice,
                entries = voiceEntries,
                entryValues = voiceValues,
                currentValue = currentVoice,
                onValueChange = { 
                    currentVoice = it
                    prefs.edit().putString("tts_voice_$currentEngine", it).apply()
                }
            )
        }

        SettingsCategoryHeader("Velocidad y Tono")

        SettingsSliderItem(
            title = "Velocidad de voz",
            value = rate,
            onValueChange = { 
                rate = it
                prefs.edit().putFloat("tts_rate_$currentEngine", it).apply()
            }
        )

        SettingsSliderItem(
            title = "Tono de voz",
            value = pitch,
            onValueChange = { 
                pitch = it
                prefs.edit().putFloat("tts_pitch_$currentEngine", it).apply()
            }
        )

        SettingsClickItem(
            title = "Hacer prueba de voz",
            summary = "Escucha una demostración de los ajustes actuales.",
            onClick = { ttsManager.speak("Probando configuración independiente.", interrupt = true) }
        )

        Text(
            text = "Nota: Puedes detener el discurso de la voz en cualquier momento deslizando un dedo hacia arriba en cualquier parte de la aplicación.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}
