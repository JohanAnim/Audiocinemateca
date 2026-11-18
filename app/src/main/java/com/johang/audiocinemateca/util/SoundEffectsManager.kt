package com.johang.audiocinemateca.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundEffectsManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    @Volatile
    private var soundPool: SoundPool? = null
    private val soundsMap = ConcurrentHashMap<String, Int>()
    private var hapticGenerator: HapticGenerator? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Cargar SoundPool y archivos .ogg en segundo plano para no bloquear el arranque de MainActivity
        scope.launch {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(audioAttributes)
                .build()
            soundPool = pool

            loadSound(pool, "aura_send", "sonidos/efectos/aura/enviar_mensaje.ogg")
            loadSound(pool, "aura_receive", "sonidos/efectos/aura/resivir_mensaje.ogg")
            loadSound(pool, "aura_wait", "sonidos/efectos/aura/esperar.ogg")
            loadSound(pool, "aura_error", "sonidos/efectos/aura/alerta_error.ogg")
            loadSound(pool, "aura_voice_start", "sonidos/efectos/aura/voz_start.ogg")
            loadSound(pool, "aura_voice_end", "sonidos/efectos/aura/voz_end.ogg")
        }
    }

    private fun loadSound(pool: SoundPool, key: String, assetPath: String) {
        try {
            context.assets.openFd(assetPath).use { afd ->
                val soundId = pool.load(afd, 1)
                soundsMap[key] = soundId
                Log.d("SoundEffectsManager", "Cargando en segundo plano: $assetPath")
            }
        } catch (e: Exception) {
            Log.e("SoundEffectsManager", "Error abriendo asset: $assetPath. ¿Existe el archivo?", e)
        }
    }

    fun playSound(key: String) {
        val pool = soundPool ?: return
        val soundId = soundsMap[key]
        if (soundId != null) {
            Log.d("SoundEffectsManager", "Reproduciendo sonido: $key")
            pool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            Log.w("SoundEffectsManager", "Sonido no encontrado en el mapa: $key")
        }
    }

    fun release() {
        hapticGenerator?.release()
        soundPool?.release()
        soundPool = null
        soundsMap.clear()
    }
}
