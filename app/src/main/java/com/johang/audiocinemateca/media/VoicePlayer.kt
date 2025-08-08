package com.johang.audiocinemateca.media

import android.content.Context
import android.media.MediaPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaPlayer: MediaPlayer? = null

    fun playVoice(resId: Int, onCompletion: (() -> Unit)? = null) {
        // Detener y liberar cualquier reproducción anterior
        stopVoice()

        mediaPlayer = MediaPlayer.create(context, resId).apply {
            setOnCompletionListener {
                onCompletion?.invoke()
                release()
                mediaPlayer = null
            }
            start()
        }
    }

    fun stopVoice() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
    }
}
