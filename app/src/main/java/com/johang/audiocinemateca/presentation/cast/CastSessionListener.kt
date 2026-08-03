package com.johang.audiocinemateca.presentation.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.johang.audiocinemateca.util.TtsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastSessionListener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ttsManager: TtsManager
) : SessionManagerListener<CastSession> {

    private var isListening = false
    private var lastCapturedPosition: Long = -1L

    fun startListening(context: Context) {
        if (isListening) return
        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.addSessionManagerListener(this, CastSession::class.java)
            isListening = true
            Log.d("CastSessionListener", "Escuchando eventos de sesión de Google Cast.")
        } catch (e: Exception) {
            Log.e("CastSessionListener", "Error al registrar listener de Cast: ${e.message}")
        }
    }

    fun stopListening(context: Context) {
        if (!isListening) return
        try {
            val castContext = CastContext.getSharedInstance(context)
            castContext.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
            isListening = false
        } catch (e: Exception) {
            Log.e("CastSessionListener", "Error al remover listener de Cast: ${e.message}")
        }
    }

    companion object {
        const val ACTION_CAST_CONNECTED = "com.johang.audiocinemateca.CAST_CONNECTED"
        const val ACTION_CAST_DISCONNECTED = "com.johang.audiocinemateca.CAST_DISCONNECTED"
        const val EXTRA_LAST_CAST_POSITION = "extra_last_cast_position"
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        val deviceName = session.castDevice?.friendlyName ?: "dispositivo externo"
        Log.d("CastSessionListener", "Sesión de Cast INICIADA en $deviceName: $sessionId")
        ttsManager.speak("Transmitiendo audio en $deviceName")
        broadcastCastEvent(ACTION_CAST_CONNECTED)
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        val deviceName = session.castDevice?.friendlyName ?: "dispositivo externo"
        Log.d("CastSessionListener", "Sesión de Cast REANUDADA en $deviceName")
        ttsManager.speak("Transmitiendo audio en $deviceName")
        broadcastCastEvent(ACTION_CAST_CONNECTED)
    }

    override fun onSessionEnding(session: CastSession) {
        val pos = try { session.remoteMediaClient?.approximateStreamPosition ?: -1L } catch (e: Exception) { -1L }
        if (pos > 0) {
            lastCapturedPosition = pos
            Log.d("CastSessionListener", "Capturada posición antes de finalizar: $pos ms")
        }
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        val pos = try { session.remoteMediaClient?.approximateStreamPosition ?: -1L } catch (e: Exception) { -1L }
        val finalPos = if (pos > 0) pos else lastCapturedPosition
        Log.d("CastSessionListener", "Sesión de Cast FINALIZADA en pos: $finalPos ms")
        ttsManager.speak("Reproduciendo en este dispositivo")
        broadcastCastEvent(ACTION_CAST_DISCONNECTED, finalPos)
        lastCapturedPosition = -1L
    }

    private fun broadcastCastEvent(action: String, lastPos: Long = -1L) {
        try {
            val intent = android.content.Intent(action).apply {
                if (lastPos >= 0) {
                    putExtra(EXTRA_LAST_CAST_POSITION, lastPos)
                }
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e("CastSessionListener", "Error al enviar broadcast de Cast: ${e.message}")
        }
    }

    override fun onSessionStarting(session: CastSession) {}
    override fun onSessionStartFailed(session: CastSession, error: Int) {}
    override fun onSessionResuming(session: CastSession, sessionId: String) {}
    override fun onSessionResumeFailed(session: CastSession, error: Int) {}
    override fun onSessionSuspended(session: CastSession, reason: Int) {}
}
