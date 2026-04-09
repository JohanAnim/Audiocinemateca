package com.johang.audiocinemateca.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    
    // Listeners para la UI
    var onInitializedListener: (() -> Unit)? = null
    var onSpeechFinished: ((String?) -> Unit)? = null

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
        recreateTts()
    }

    private fun recreateTts() {
        val engine = getSelectedEngine()
        if (tts != null) {
            try {
                tts?.stop()
                tts?.shutdown()
            } catch (e: Exception) {
                Log.e("TtsManager", "Error shutting down old TTS: ${e.message}")
            }
        }
        isInitialized = false
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
            }

            override fun onDone(utteranceId: String?) {
                Log.d("TtsManager", "Speech finished: $utteranceId")
                // Usamos post para que se ejecute en el hilo principal por seguridad
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onSpeechFinished?.invoke(utteranceId)
                }
            }

            override fun onError(utteranceId: String?) {
                Log.e("TtsManager", "Speech error: $utteranceId")
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            Log.d("TtsManager", "TTS initialization SUCCESS")
            
            // Forzar español como idioma base si no hay nada configurado
            tts?.language = Locale("es", "ES")
            
            applySettings()
            onInitializedListener?.invoke()
        } else {
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

    fun speak(text: String, interrupt: Boolean = true) {
        if (!isInitialized || tts == null) {
            Log.w("TtsManager", "Cannot speak, TTS not initialized yet. Re-initializing...")
            recreateTts()
            return
        }
        
        val cleanText = text.replace("[[", "").replace("]]", "")
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        
        // Parámetros extras para asegurar que se use el stream de música (opcional)
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        
        tts?.speak(cleanText, queueMode, params, "TTS_REQUEST_${System.currentTimeMillis()}")
    }

    fun stop() {
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
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
        isInitialized = false
    }
}
