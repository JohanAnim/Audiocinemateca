package com.johang.audiocinemateca.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundEffectsPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val soundPool: SoundPool
    private val loadedSounds = mutableMapOf<Int, Int>()
    private val loadingSounds = mutableSetOf<Int>()
    private var onLoadCompleted: ((Int) -> Unit)? = null

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                val resId = loadedSounds.entries.find { it.value == sampleId }?.key
                resId?.let {
                    loadingSounds.remove(it)
                    onLoadCompleted?.invoke(it)
                }
            }
        }
    }

    fun loadSound(resId: Int, onLoaded: (Int) -> Unit) {
        if (loadedSounds.containsKey(resId)) {
            onLoaded(resId)
            return
        }
        if (loadingSounds.contains(resId)) {
            this.onLoadCompleted = onLoaded
            return
        }

        loadingSounds.add(resId)
        val soundId = soundPool.load(context, resId, 1)
        loadedSounds[resId] = soundId
        this.onLoadCompleted = onLoaded
    }

    fun playSound(resId: Int, volume: Float = 1.0f) {
        val soundId = loadedSounds[resId]
        soundId?.let {
            soundPool.play(it, volume, volume, 1, 0, 1.0f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
