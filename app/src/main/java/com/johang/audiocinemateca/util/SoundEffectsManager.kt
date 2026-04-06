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
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                Log.d("SoundEffectsManager", "Sonido cargado con éxito. ID: $sampleId")
            } else {
                Log.e("SoundEffectsManager", "Error al cargar sonido. Status: $status")
            }
        }

        // Cargar sonidos
        loadSound("receive_message", "sonidos/efectos/resibir_mensajje.wav")
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
