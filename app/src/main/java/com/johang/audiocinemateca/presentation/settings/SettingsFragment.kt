package com.johang.audiocinemateca.presentation.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.fragment.app.activityViewModels
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.di.SharedPreferencesManagerEntryPoint
import com.johang.audiocinemateca.presentation.mylists.PlaybackHistoryViewModel
import com.johang.audiocinemateca.presentation.search.SearchViewModel
import dagger.hilt.android.EntryPointAccessors

class SettingsFragment : PreferenceFragmentCompat() {

    private val searchViewModel: SearchViewModel by activityViewModels()
    private val playbackHistoryViewModel: PlaybackHistoryViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "app_prefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val entryPoint = EntryPointAccessors.fromApplication(requireContext(), SharedPreferencesManagerEntryPoint::class.java)
        val sharedPreferencesManager = entryPoint.sharedPreferencesManager()

        val themePreference = findPreference<ListPreference>("theme")
        themePreference?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            ThemeManager.applyTheme(theme)
            true
        }

        val autoplayPref = findPreference<SwitchPreferenceCompat>("autoplay")
        autoplayPref?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferencesManager.saveBoolean("autoplay", newValue as Boolean)
            true
        }

        val sleepTimerEnabledPref = findPreference<SwitchPreferenceCompat>("sleep_timer_enabled")
        sleepTimerEnabledPref?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferencesManager.saveBoolean("sleep_timer_enabled", newValue as Boolean)
            true
        }

        val sleepTimerDurationPref = findPreference<ListPreference>("sleep_timer_duration")
        sleepTimerDurationPref?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferencesManager.saveString("sleep_timer_duration", newValue as String)
            true
        }

        val rewindIntervalPref = findPreference<ListPreference>("rewind_interval")
        rewindIntervalPref?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferencesManager.saveString("rewind_interval", newValue as String)
            true
        }

        val forwardIntervalPref = findPreference<ListPreference>("forward_interval")
        forwardIntervalPref?.setOnPreferenceChangeListener { _, newValue ->
            sharedPreferencesManager.saveString("forward_interval", newValue as String)
            true
        }

        val clearSearchHistoryPref = findPreference<Preference>("clear_search_history")
        clearSearchHistoryPref?.setOnPreferenceClickListener {
            showClearSearchHistoryConfirmationDialog()
            true
        }

        val clearPlaybackHistoryPref = findPreference<Preference>("clear_playback_history")
        clearPlaybackHistoryPref?.setOnPreferenceClickListener {
            showClearPlaybackHistoryConfirmationDialog()
            true
        }
    }

    private fun showClearSearchHistoryConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar historial de búsqueda")
            .setMessage("¿Estás seguro de que quieres eliminar todo el historial de búsqueda? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                searchViewModel.clearSearchHistory()
                Toast.makeText(requireContext(), "Historial de búsqueda eliminado.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showClearPlaybackHistoryConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar historial de reproducción")
            .setMessage("¿Estás seguro de que quieres eliminar todo el historial de reproducción? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { dialog, _ ->
                playbackHistoryViewModel.clearAllHistory()
                Toast.makeText(requireContext(), "Historial de reproducción eliminado.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = activity as? AppCompatActivity
        activity?.supportActionBar?.title = "Ajustes"
        activity?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        activity?.supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        val activity = activity as? AppCompatActivity
        activity?.supportActionBar?.title = "Ajustes"

        val entryPoint = EntryPointAccessors.fromApplication(requireContext(), SharedPreferencesManagerEntryPoint::class.java)
        val sharedPreferencesManager = entryPoint.sharedPreferencesManager()

        findPreference<SwitchPreferenceCompat>("autoplay")?.isChecked = sharedPreferencesManager.getBoolean("autoplay", true)
        findPreference<SwitchPreferenceCompat>("sleep_timer_enabled")?.isChecked = sharedPreferencesManager.getBoolean("sleep_timer_enabled", false)
        findPreference<ListPreference>("sleep_timer_duration")?.value = sharedPreferencesManager.getString("sleep_timer_duration", "60")
        findPreference<ListPreference>("rewind_interval")?.value = sharedPreferencesManager.getString("rewind_interval", "5")
        findPreference<ListPreference>("forward_interval")?.value = sharedPreferencesManager.getString("forward_interval", "15")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Limpiar la configuración de la ActionBar al salir
        val activity = activity as? AppCompatActivity
        activity?.supportActionBar?.setDisplayHomeAsUpEnabled(false)
        activity?.supportActionBar?.setDisplayShowHomeEnabled(false)
    }
}

object ThemeManager {
    fun applyTheme(theme: String) {
        when (theme) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "system" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}