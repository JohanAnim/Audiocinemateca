package com.johang.audiocinemateca.media

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var mediaPlayer: MediaPlayer? = null

    fun playVoiceAsset(assetPath: String, onCompletion: (() -> Unit)? = null) {
        stopVoice()
        try {
            val afd = context.assets.openFd(assetPath)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
                setOnCompletionListener {
                    onCompletion?.invoke()
                    release()
                    mediaPlayer = null
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error reproduciendo voz desde asset: $assetPath", e)
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
