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

        when (category) {
            "general" -> {
                findPreference<ListPreference>("theme")?.setOnPreferenceChangeListener { _, newValue ->
                    ThemeManager.applyTheme(newValue as String)
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
                    val modelPref = findPreference<ListPreference>("gemini_model")
                    updateGeminiModels(repo, modelPref)
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
        }
    }

    private fun updateGeminiModels(repository: GeminiRepository, pref: ListPreference?) {
        if (pref == null) return

        // Actualizar título inicialmente con lo que ya tenga
        pref.entry?.let {
            pref.title = "Modelo de IA seleccionado: $it"
        }

        lifecycleScope.launch {
            val models = repository.fetchAvailableModels()
            if (models.isNotEmpty()) {
                pref.entries = models.map { it.second }.toTypedArray()
                pref.entryValues = models.map { it.first }.toTypedArray()
                if (pref.value == null || pref.value !in pref.entryValues) {
                    pref.value = pref.entryValues.firstOrNull()?.toString()
                }
                
                // Actualizar título de nuevo tras cargar modelos
                pref.entry?.let {
                    pref.title = "Modelo de IA seleccionado: $it"
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
                val result = repository.generateContent("Di 'OK' para verificar la API")
                if (result.isSuccess) {
                    Toast.makeText(requireContext(), "API Válida (${result.getOrNull()})", Toast.LENGTH_SHORT).show()
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
            "downloads" -> "Ajustes de Descargas"
            "community" -> "Comunidad"
            else -> "Ajustes"
        }
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }
}
