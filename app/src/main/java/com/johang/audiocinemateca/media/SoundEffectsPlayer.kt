package com.johang.audiocinemateca.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundEffectsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val soundPool: SoundPool
    private val loadedAssetSounds = mutableMapOf<String, Int>()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    fun loadAndPlaySoundAsset(assetPath: String, volume: Float = 1.0f) {
        val existingSoundId = loadedAssetSounds[assetPath]
        if (existingSoundId != null) {
            soundPool.play(existingSoundId, volume, volume, 1, 0, 1.0f)
            return
        }

        try {
            val afd = context.assets.openFd(assetPath)
            val soundId = soundPool.load(afd, 1)
            afd.close()
            loadedAssetSounds[assetPath] = soundId
            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0 && sampleId == soundId) {
                    soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
                }
            }
        } catch (e: Exception) {
            Log.e("SoundEffectsPlayer", "Error cargando asset de sonido: $assetPath", e)
        }
    }

    fun release() {
        soundPool.release()
    }
}
