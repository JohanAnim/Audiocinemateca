package com.johang.audiocinemateca.presentation.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.repository.GeminiRepository

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        setupCategoryNavigation("pref_general", "general")
        setupCategoryNavigation("pref_playback", "playback")
        setupCategoryNavigation("pref_community", "community")
        setupCategoryNavigation("pref_ai", "ai")
        setupCategoryNavigation("pref_downloads", "downloads")

        // Lógica para Información Legal
        findPreference<Preference>("nav_privacy")?.setOnPreferenceClickListener {
            val bundle = Bundle().apply { putString("url", "https://audiocinemateca.com/privacidad") }
            findNavController().navigate(R.id.webViewFragment, bundle)
            true
        }

        findPreference<Preference>("nav_terms")?.setOnPreferenceClickListener {
            val bundle = Bundle().apply { putString("url", "https://audiocinemateca.com/terminos") }
            findNavController().navigate(R.id.webViewFragment, bundle)
            true
        }
    }

    private fun setupCategoryNavigation(prefKey: String, categoryTag: String) {
        findPreference<Preference>(prefKey)?.setOnPreferenceClickListener {
            val bundle = Bundle().apply { putString("category", categoryTag) }
            findNavController().navigate(R.id.action_settingsFragment_to_categorySettingsFragment, bundle)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Ajustes"
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface GeminiEntryPoint {
        fun geminiRepository(): GeminiRepository
    }
}
