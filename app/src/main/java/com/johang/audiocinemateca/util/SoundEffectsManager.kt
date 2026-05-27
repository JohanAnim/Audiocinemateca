package com.johang.audiocinemateca.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundEffectsManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    private var soundPool: SoundPool
    private val soundsMap = mutableMapOf<String, Int>()
    private var hapticGenerator: HapticGenerator? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        // Cargar sonidos de Aura (ahora estándar de la app)
        loadSound("aura_send", "sonidos/efectos/aura/enviar_mensaje.ogg")
        loadSound("aura_receive", "sonidos/efectos/aura/resivir_mensaje.ogg")
        loadSound("aura_wait", "sonidos/efectos/aura/esperar.ogg")
        loadSound("aura_error", "sonidos/efectos/aura/alerta_error.ogg")
        loadSound("aura_voice_start", "sonidos/efectos/aura/voz_start.ogg")
        loadSound("aura_voice_end", "sonidos/efectos/aura/voz_end.ogg")
    }

    private fun loadSound(key: String, assetPath: String) {
        try {
            val assetFileDescriptor = context.assets.openFd(assetPath)
            val soundId = soundPool.load(assetFileDescriptor, 1)
            soundsMap[key] = soundId
            Log.d("SoundEffectsManager", "Intentando cargar: $assetPath")
        } catch (e: Exception) {
            Log.e("SoundEffectsManager", "Error abriendo asset: $assetPath. ¿Existe el archivo?", e)
        }
    }

    fun playSound(key: String) {
        val soundId = soundsMap[key]
        if (soundId != null) {
            Log.d("SoundEffectsManager", "Reproduciendo sonido: $key")
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            Log.w("SoundEffectsManager", "Sonido no encontrado en el mapa: $key")
        }
    }

    fun release() {
        hapticGenerator?.release()
        soundPool.release()
        soundsMap.clear()
    }
}
