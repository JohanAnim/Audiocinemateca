package com.johang.audiocinemateca.presentation.settings.compose

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SharedPreferencesManager,
    private val globalChatRepository: GlobalChatRepository
) : ViewModel() {

    // General
    private val _theme = MutableStateFlow(prefs.getString("theme", "system") ?: "system")
    val theme: StateFlow<String> = _theme

    private val _bootAnimationFrequency = MutableStateFlow(prefs.getString("boot_animation_frequency", "always") ?: "always")
    val bootAnimationFrequency: StateFlow<String> = _bootAnimationFrequency

    private val _startupTab = MutableStateFlow(prefs.getString("startup_tab", "home") ?: "home")
    val startupTab: StateFlow<String> = _startupTab

    private val _defaultContentTab = MutableStateFlow(prefs.getString("default_content_tab", "peliculas") ?: "peliculas")
    val defaultContentTab: StateFlow<String> = _defaultContentTab

    private val _defaultFilter = MutableStateFlow(prefs.getString("default_filter", "date_desc") ?: "date_desc")
    val defaultFilter: StateFlow<String> = _defaultFilter

    private val _autoCheckCatalog = MutableStateFlow(prefs.getBoolean("auto_check_catalog", true))
    val autoCheckCatalog: StateFlow<Boolean> = _autoCheckCatalog

    private val _autoCheckApp = MutableStateFlow(prefs.getBoolean("auto_check_app", true))
    val autoCheckApp: StateFlow<Boolean> = _autoCheckApp

    // Playback
    private val _autoplay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    val autoplay: StateFlow<Boolean> = _autoplay

    private val _sleepTimerEnabled = MutableStateFlow(prefs.getBoolean("sleep_timer_enabled", false))
    val sleepTimerEnabled: StateFlow<Boolean> = _sleepTimerEnabled

    // Helper to update and save
    fun updateString(key: String, value: String) {
        prefs.saveString(key, value)
        when(key) {
            "theme" -> _theme.value = value
            "boot_animation_frequency" -> _bootAnimationFrequency.value = value
            "startup_tab" -> _startupTab.value = value
            "default_content_tab" -> _defaultContentTab.value = value
            "default_filter" -> _defaultFilter.value = value
        }
    }

    fun updateBoolean(key: String, value: Boolean) {
        prefs.saveBoolean(key, value)
        when(key) {
            "auto_check_catalog" -> _autoCheckCatalog.value = value
            "auto_check_app" -> _autoCheckApp.value = value
            "autoplay" -> _autoplay.value = value
            "sleep_timer_enabled" -> _sleepTimerEnabled.value = value
            "community_show_presence" -> {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    globalChatRepository.setUserPresence(
                        userId = user.uid,
                        displayName = user.displayName,
                        email = user.email,
                        isOnline = value
                    )
                }
            }
        }
    }
    
    // Generic getters for simpler preferences
    fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
}
