package com.johang.audiocinemateca

import android.app.Application
import androidx.preference.PreferenceManager
import com.johang.audiocinemateca.presentation.settings.ThemeManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AudiocinematecaApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Apply the saved theme on startup
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val theme = sharedPreferences.getString("theme", "system")
        ThemeManager.applyTheme(theme ?: "system")
    }
}