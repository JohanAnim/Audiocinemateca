package com.johang.audiocinemateca.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isInitializing = false
    private var pendingSpeech: Triple<String, Boolean, String?>? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var activeUtteranceId: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()
    
    // Listeners para la UI y coordinación con voces externas
    var onInitializedListener: (() -> Unit)? = null
    var onSpeechStarted: ((String?) -> Unit)? = null
    var onSpeechFinished: ((String?) -> Unit)? = null
    var onSpeechError: ((String?) -> Unit)? = null
    @Volatile
    var onExternalStopRequested: (() -> Unit)? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        
        when {
            key == "tts_engine" -> {
                Log.d("TtsManager", "TTS Engine changed. Recreating...")
                recreateTts()
            }
            key.startsWith("tts_voice") || key.startsWith("tts_pitch") || key.startsWith("tts_rate") || key.startsWith("tts_language") -> {
                Log.d("TtsManager", "TTS Settings changed for key: $key. Applying...")
                applySettings()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        // Diferir el enlace Binder con el servicio TTS hasta después del primer frame
        mainHandler.post {
            if (!isInitialized && !isInitializing && tts == null) {
                recreateTts()
            }
        }
    }

    private fun recreateTts() {
        val engine = getSelectedEngine()
        activeUtteranceId = null
        _isSpeaking.value = false
        if (tts != null) {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e("TtsManager", "Error shutting down old TTS: ${e.message}")
            }
        }
        isInitialized = false
        isInitializing = true
        Log.d("TtsManager", "Initializing TTS with engine: $engine")
        
        tts = if (engine.isNullOrBlank() || engine == "system_default") {
            TextToSpeech(context, this)
        } else {
            // Intentar inicializar con el motor seleccionado, si falla, volver al predeterminado
            try {
                TextToSpeech(context, this, engine)
            } catch (e: Exception) {
                Log.e("TtsManager", "Failed to init with engine $engine, falling back.")
                TextToSpeech(context, this)
            }
        }
        setupProgressListener()
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("TtsManager", "Speech started: $utteranceId")
                activeUtteranceId = utteranceId
                _isSpeaking.value = true
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSpeechStarted?.invoke(utteranceId)
                }
            }

            override fun onDone(utteranceId: String?) {
                Log.d("TtsManager", "Speech finished: $utteranceId")
                if (utteranceId == null || utteranceId == activeUtteranceId) {
                    activeUtteranceId = null
                    _isSpeaking.value = false
                }
                // Usamos post para que se ejecute en el hilo principal por seguridad
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSpeechFinished?.invoke(utteranceId)
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == null || utteranceId == activeUtteranceId) {
                    activeUtteranceId = null
                    _isSpeaking.value = false
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e("TtsManager", "Speech error: $utteranceId")
                if (utteranceId == null || utteranceId == activeUtteranceId) {
                    activeUtteranceId = null
                    _isSpeaking.value = false
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSpeechError?.invoke(utteranceId)
                }
            }
        })
    }

    override fun onInit(status: Int) {
        isInitializing = false
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d("TtsManager", "TTS initialization SUCCESS")
            
            // Forzar español como idioma base si no hay nada configurado
            tts?.language = Locale("es", "ES")
            
            applySettings()
            onInitializedListener?.invoke()

            val queued = pendingSpeech
            if (queued != null) {
                pendingSpeech = null
                speak(queued.first, queued.second, queued.third)
            }
        } else {
            pendingSpeech = null
            Log.e("TtsManager", "TTS initialization FAILED")
        }
    }

    private fun getSelectedEngine(): String? {
        return prefs.getString("tts_engine", "system_default")
    }

    fun applySettings() {
        if (!isInitialized || tts == null) return
        
        try {
            val engine = getSelectedEngine() ?: "system_default"
            
            // Pitch (Tono) independiente por motor
            var pitch = prefs.getFloat("tts_pitch_$engine", 1.0f)
            if (pitch <= 0.1f) pitch = 1.0f
            tts?.setPitch(pitch)

            // Rate (Velocidad) independiente por motor
            var rate = prefs.getFloat("tts_rate_$engine", 1.0f)
            if (rate <= 0.1f) rate = 1.0f
            tts?.setSpeechRate(rate)

            // Idioma y Voz independiente por motor
            val selectedLang = prefs.getString("tts_language_$engine", "es")
            val selectedVoiceName = prefs.getString("tts_voice_$engine", "")
            
            if (!selectedVoiceName.isNullOrBlank()) {
                val voices = tts?.voices
                val matchingVoice = voices?.find { it.name == selectedVoiceName }
                if (matchingVoice != null) {
                    tts?.voice = matchingVoice
                } else {
                    tts?.language = Locale(selectedLang ?: "es")
                }
            } else {
                tts?.language = Locale(selectedLang ?: "es")
            }
            
            Log.d("TtsManager", "Settings applied for $engine: Pitch=$pitch, Rate=$rate, Voice=$selectedVoiceName")
        } catch (e: Exception) {
            Log.e("TtsManager", "Error applying settings: ${e.message}")
        }
    }

    fun speak(text: String, interrupt: Boolean = true, utteranceId: String? = null) {
        if (!isInitialized || tts == null) {
            pendingSpeech = Triple(text, interrupt, utteranceId)
            if (!isInitializing) {
                Log.w("TtsManager", "TTS not initialized yet. Starting initialization and queueing speech...")
                recreateTts()
            }
            return
        }
        
        if (interrupt) {
            onExternalStopRequested?.invoke()
            try {
                tts?.stop()
            } catch (e: Exception) {
                Log.e("TtsManager", "Error stopping TTS before speak: ${e.message}")
            }
        }

        val cleanText = text.replace("[[", "").replace("]]", "")
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        
        val finalUtteranceId = utteranceId ?: "TTS_REQUEST_${System.currentTimeMillis()}"
        activeUtteranceId = finalUtteranceId
        tts?.speak(cleanText, queueMode, params, finalUtteranceId)
    }

    fun stop(notifyExternal: Boolean = true) {
        if (notifyExternal) {
            onExternalStopRequested?.invoke()
        }
        activeUtteranceId = null
        _isSpeaking.value = false
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun getAvailableEngines(): List<TextToSpeech.EngineInfo> {
        return tts?.engines ?: emptyList()
    }

    fun getVoicesForCurrentEngine(): Set<Voice> {
         return try {
             tts?.voices ?: emptySet()
         } catch (e: Exception) {
             Log.w("TtsManager", "Could not get voices: ${e.message}")
             emptySet()
         }
    }

    fun shutdown() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        activeUtteranceId = null
        _isSpeaking.value = false
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
        isInitialized = false
    }
}
