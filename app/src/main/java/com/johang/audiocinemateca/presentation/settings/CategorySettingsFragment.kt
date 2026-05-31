package com.johang.audiocinemateca.presentation.settings

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.activityViewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.di.SharedPreferencesManagerEntryPoint
import com.johang.audiocinemateca.presentation.mylists.PlaybackHistoryViewModel
import com.johang.audiocinemateca.presentation.search.SearchViewModel
import com.johang.audiocinemateca.data.repository.GeminiRepository
import dagger.hilt.android.EntryPointAccessors
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.johang.audiocinemateca.util.CrashLogger

class CategorySettingsFragment : PreferenceFragmentCompat() {

    private val searchViewModel: SearchViewModel by activityViewModels()
    private val playbackHistoryViewModel: PlaybackHistoryViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "app_prefs"
        
        val category = arguments?.getString("category") ?: "general"
        val xmlRes = when (category) {
            "general" -> R.xml.prefs_general
            "playback" -> R.xml.prefs_playback
            "search" -> R.xml.prefs_search
            "ai" -> R.xml.prefs_ai
            "tts" -> R.xml.prefs_tts
            "downloads" -> R.xml.prefs_downloads
            "community" -> R.xml.prefs_community
            else -> R.xml.prefs_general
        }
        
        setPreferencesFromResource(xmlRes, rootKey)
        setupCategoryLogic(category)
    }

    private fun setupCategoryLogic(category: String) {
        val entryPoint = EntryPointAccessors.fromApplication(requireContext(), SharedPreferencesManagerEntryPoint::class.java)
        val sharedPreferencesManager = entryPoint.sharedPreferencesManager()
        
        val geminiRepository = try {
            EntryPointAccessors.fromApplication(requireContext().applicationContext, SettingsFragment.GeminiEntryPoint::class.java).geminiRepository()
        } catch (e: Exception) { null }

        val ttsManager = try {
            EntryPointAccessors.fromApplication(requireContext().applicationContext, SettingsFragment.GeminiEntryPoint::class.java).ttsManager()
        } catch (e: Exception) { null }

        when (category) {
            "general" -> {
                findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
                    ThemeManager.applyTheme(newValue as String)
                    true
                }
                findPreference<Preference>("share_latest_log")?.setOnPreferenceClickListener {
                    shareLatestLog()
                    true
                }
                findPreference<Preference>("pref_battery_optimization")?.setOnPreferenceClickListener {
                    openBatteryOptimizationSettings()
                    true
                }
                findPreference<Preference>("pref_deep_links")?.setOnPreferenceClickListener {
                    openDeepLinkSettings()
                    true
                }
            }
            "downloads" -> {
                setupStorageLocationPreference()
                findPreference<SwitchPreferenceCompat>("offline_mode")?.setOnPreferenceChangeListener { _, newValue ->
                    sharedPreferencesManager.saveBoolean("offline_mode", newValue as Boolean)
                    if (newValue as Boolean) {
                        Toast.makeText(requireContext(), "Modo offline activado.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }
            "playback" -> {
                findPreference<Preference>("clear_playback_history")?.setOnPreferenceClickListener {
                    showClearPlaybackHistoryConfirmationDialog()
                    true
                }
            }
            "search" -> {
                findPreference<Preference>("clear_search_history")?.setOnPreferenceClickListener {
                    showClearSearchHistoryConfirmationDialog()
                    true
                }
            }
            "ai" -> {
                geminiRepository?.let { repo ->
                    val apiKeyPref = findPreference<androidx.preference.EditTextPreference>("gemini_api_key")
                    val modelPref = findPreference<ListPreference>("gemini_model")
                    
                    updateGeminiModels(repo, modelPref)

                    apiKeyPref?.setOnPreferenceChangeListener { _, newValue ->
                        val newKey = newValue as String
                        if (newKey.isNotBlank()) {
                            lifecycleScope.launch {
                                val models = repo.fetchAvailableModels(newKey)
                                if (models.isNotEmpty()) {
                                    Toast.makeText(requireContext(), "¡Conexión establecida! API Válida.", Toast.LENGTH_SHORT).show()
                                    updateGeminiModels(repo, modelPref, newKey)
                                } else {
                                    Toast.makeText(requireContext(), "No se pudo validar la clave API. Revisa tu conexión o la clave.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        true
                    }

                    findPreference<Preference>("check_api_status")?.setOnPreferenceClickListener {
                        checkGeminiApiStatus(repo)
                        true
                    }
                    findPreference<Preference>("get_api_key")?.setOnPreferenceClickListener {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey")))
                        true
                    }
                }
            }
            "tts" -> {
                ttsManager?.let { tts ->
                    setupTtsPreferences(tts)
                }
            }
        }
    }
    
    private fun setupTtsPreferences(ttsManager: com.johang.audiocinemateca.util.TtsManager) {
        val enginePref = findPreference<ListPreference>("tts_engine")
        val langPref = findPreference<ListPreference>("tts_language")
        val voicePref = findPreference<ListPreference>("tts_voice")
        val ratePref = findPreference<androidx.preference.SeekBarPreference>("tts_rate_seek")
        val pitchPref = findPreference<androidx.preference.SeekBarPreference>("tts_pitch_seek")
        val testBtn = findPreference<Preference>("tts_test_button")
        
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        ttsManager.onInitializedListener = {
            activity?.runOnUiThread {
                updateLanguagesList(ttsManager, langPref, voicePref, ratePref, pitchPref)
            }
        }

        if (enginePref != null) {
            val engines = ttsManager.getAvailableEngines()
            val entries = mutableListOf("Sistema (Predeterminado)")
            val entryValues = mutableListOf("system_default")
            engines.forEach { entries.add(it.label); entryValues.add(it.name) }
            enginePref.entries = entries.toTypedArray(); enginePref.entryValues = entryValues.toTypedArray()
            enginePref.entry?.let { enginePref.summary = it }
            
            enginePref.setOnPreferenceChangeListener { _, newValue ->
                val index = enginePref.findIndexOfValue(newValue as String)
                if (index >= 0) enginePref.summary = enginePref.entries[index]
                langPref?.isEnabled = false; voicePref?.isEnabled = false
                langPref?.summary = "Cargando idiomas..."; voicePref?.summary = "Cargando voces..."
                true
            }
        }
        
        updateLanguagesList(ttsManager, langPref, voicePref, ratePref, pitchPref)

        langPref?.setOnPreferenceChangeListener { _, newValue ->
            val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"
            prefs.edit().putString("tts_language_$engine", newValue as String).apply()
            
            val index = langPref.findIndexOfValue(newValue)
            if (index >= 0) langPref.summary = langPref.entries[index]
            updateVoicesList(ttsManager, voicePref, newValue)
            true
        }

        voicePref?.setOnPreferenceChangeListener { _, newValue ->
            val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"
            prefs.edit().putString("tts_voice_$engine", newValue as String).apply()
            
            val index = voicePref.findIndexOfValue(newValue)
            if (index >= 0) voicePref.summary = voicePref.entries[index]
            true
        }

        ratePref?.setOnPreferenceChangeListener { _, newValue ->
            val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"
            val rate = (newValue as Int) / 10.0f
            prefs.edit().putFloat("tts_rate_$engine", rate).apply()
            true
        }

        pitchPref?.setOnPreferenceChangeListener { _, newValue ->
            val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"
            val pitch = (newValue as Int) / 10.0f
            prefs.edit().putFloat("tts_pitch_$engine", pitch).apply()
            true
        }
        
        testBtn?.setOnPreferenceClickListener {
            ttsManager.speak("Probando configuración independiente.", interrupt = true)
            true
        }
    }

    private fun updateLanguagesList(ttsManager: com.johang.audiocinemateca.util.TtsManager, langPref: ListPreference?, voicePref: ListPreference?, ratePref: androidx.preference.SeekBarPreference?, pitchPref: androidx.preference.SeekBarPreference?) {
        if (langPref == null) return
        val voices = ttsManager.getVoicesForCurrentEngine()
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"

        if (voices.isEmpty()) {
            langPref.isEnabled = false; langPref.summary = "No se detectaron idiomas."
            return
        }

        // Cargar Rate y Pitch guardados para este motor específico
        ratePref?.value = (prefs.getFloat("tts_rate_$engine", 1.0f) * 10).toInt()
        pitchPref?.value = (prefs.getFloat("tts_pitch_$engine", 1.0f) * 10).toInt()

        val languages = voices.map { it.locale }.distinctBy { it.language }.sortedBy { it.displayName }
        val entries = mutableListOf<String>(); val entryValues = mutableListOf<String>()
        languages.forEach { entries.add(it.displayName.replaceFirstChar { c -> c.uppercase() }); entryValues.add(it.language) }
        
        langPref.entries = entries.toTypedArray(); langPref.entryValues = entryValues.toTypedArray(); langPref.isEnabled = true
        
        var currentLang = prefs.getString("tts_language_$engine", "")
        if (currentLang.isNullOrBlank() || !entryValues.contains(currentLang)) {
            // Default a español si existe, si no al primero
            currentLang = if (entryValues.contains("es")) "es" else entryValues.first()
            prefs.edit().putString("tts_language_$engine", currentLang).apply()
        }
        
        langPref.value = currentLang
        val activeLang = languages.find { it.language == currentLang }
        langPref.summary = activeLang?.displayName?.replaceFirstChar { c -> c.uppercase() } ?: "Idioma"
        
        updateVoicesList(ttsManager, voicePref, currentLang)
    }

    private fun updateVoicesList(ttsManager: com.johang.audiocinemateca.util.TtsManager, voicePref: ListPreference?, language: String) {
        if (voicePref == null) return
        val voices = ttsManager.getVoicesForCurrentEngine()
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val engine = prefs.getString("tts_engine", "system_default") ?: "system_default"
        val currentVoiceName = prefs.getString("tts_voice_$engine", "")

        val filteredVoices = voices.filter { it.locale.language == language }.sortedBy { it.name }

        if (filteredVoices.isEmpty()) {
            voicePref.isEnabled = false; voicePref.summary = "No hay voces para este idioma."
        } else {
            voicePref.isEnabled = true
            val entries = mutableListOf<String>(); val entryValues = mutableListOf<String>()
            filteredVoices.forEach { entries.add("${it.locale.displayName} (${it.name})"); entryValues.add(it.name) }
            voicePref.entries = entries.toTypedArray(); voicePref.entryValues = entryValues.toTypedArray()
            
            var selectedVoice = currentVoiceName
            if (selectedVoice.isNullOrBlank() || !entryValues.contains(selectedVoice)) {
                selectedVoice = entryValues.first()
                prefs.edit().putString("tts_voice_$engine", selectedVoice).apply()
            }
            
            voicePref.value = selectedVoice
            val activeVoice = filteredVoices.find { it.name == selectedVoice }
            voicePref.summary = activeVoice?.let { "${it.locale.displayName} (${it.name})" } ?: "Voz"
        }
    }

    private fun updateGeminiModels(repository: GeminiRepository, pref: ListPreference?, providedApiKey: String? = null) {
        if (pref == null) return

        // No vaciar si ya hay algo y estamos re-validando, para evitar parpadeo
        if (pref.entries.isNullOrEmpty()) {
            pref.entries = emptyArray()
            pref.entryValues = emptyArray()
            pref.isEnabled = false
        }

        lifecycleScope.launch {
            // Comprobación rápida para validar API (usando la nueva o la guardada)
            val isValid = repository.initialize()
            if (isValid || providedApiKey != null) {
                val models = repository.fetchAvailableModels(providedApiKey)
                if (models.isNotEmpty()) {
                    pref.entries = models.map { it.second }.toTypedArray()
                    pref.entryValues = models.map { it.first }.toTypedArray()
                    
                    // Establecer predeterminado si no hay uno seleccionado o el actual ya no existe
                    if (pref.value == null || pref.value !in pref.entryValues) {
                        pref.value = "gemma-4-31b-it" 
                    }
                    
                    pref.isEnabled = true
                    val currentEntry = pref.entry ?: pref.value
                    pref.title = "Modelo de IA seleccionado: $currentEntry"
                }
            }
        }

        pref.setOnPreferenceChangeListener { _, newValue ->
            val index = pref.findIndexOfValue(newValue as String)
            if (index >= 0) {
                val entry = pref.entries[index]
                pref.title = "Modelo de IA seleccionado: $entry"
            }
            true
        }
    }

    private fun checkGeminiApiStatus(repository: GeminiRepository) {
        lifecycleScope.launch {
            if (repository.initialize()) {
                val result = repository.generateContent("[DIRECTIVA: SILENT_MODE] Responde únicamente 'OK' si me escuchas. Sin planes ni análisis.")
                if (result.isSuccess) {
                    val cleanResponse = result.getOrNull()?.trim() ?: ""
                    Toast.makeText(requireContext(), "API Válida: $cleanResponse", Toast.LENGTH_SHORT).show()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Error desconocido"
                    Toast.makeText(requireContext(), "Error de API: $error", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), "No se pudo inicializar (revisa la clave API)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearSearchHistoryConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar historial")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Eliminar") { _, _ -> searchViewModel.clearSearchHistory() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showClearPlaybackHistoryConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar historial")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Eliminar") { _, _ -> playbackHistoryViewModel.clearAllHistory() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun shareLatestLog() {
        val latestLog = CrashLogger.getLatestLogFile(requireContext())
        if (latestLog == null || !latestLog.exists()) {
            Toast.makeText(requireContext(), "No hay reportes de error disponibles.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                latestLog
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reporte de Error - Audiocinemateca")
                putExtra(Intent.EXTRA_TEXT, "Adjunto el reporte de error generado por la aplicación.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Enviar reporte por..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error al compartir el archivo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun openDeepLinkSettings() {
        try {
            val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Si falla (en versiones antiguas), abrir la info de la app
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            }
            startActivity(intent)
        }
    }

    private fun setupStorageLocationPreference() {
        val downloadLocationPref = findPreference<ListPreference>("download_location") ?: return
        val entries = mutableListOf<String>()
        val entryValues = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val volumeNames = MediaStore.getExternalVolumeNames(requireContext())
            volumeNames.forEach { volumeName ->
                val isPrimary = volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY
                entries.add(if (isPrimary) "Memoria interna" else "Almacenamiento externo")
                entryValues.add(volumeName)
            }
        }
        downloadLocationPref.entries = entries.toTypedArray()
        downloadLocationPref.entryValues = entryValues.toTypedArray()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = when (arguments?.getString("category")) {
            "general" -> "Ajustes Generales"
            "playback" -> "Ajustes de Reproducción"
            "search" -> "Ajustes de Búsqueda"
            "ai" -> "Inteligencia Artificial"
            "tts" -> "Ajustes de Texto a Voz"
            "downloads" -> "Ajustes de Descargas"
            "community" -> "Comunidad"
            else -> "Ajustes"
        }
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }
}
