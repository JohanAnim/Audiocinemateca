package com.johang.audiocinemateca.media

import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import kotlin.math.max

/**
 * Motor central de Inmersión Háptica de Audiocinemateca.
 *
 * Regla de enrutamiento estricta:
 * 1. Si "Vibración Háptica Avanzada" (haptic_audio_enabled) está desactivada -> Todo apagado.
 * 2. Si está activada y hay un mando compatible conectado (ej. PS5 DualSense, Xbox, etc.) ->
 *    Envía toda la experiencia háptica estéreo en tiempo real al mando y desactiva el motor del teléfono.
 * 3. Si está activada y NO hay mando conectado, pero el teléfono soporta HapticGenerator nativo ->
 *    Activa HapticGenerator en la sesión de audio del teléfono.
 * 4. Si es un teléfono normal sin HapticGenerator y NO hay mando conectado ->
 *    No hace nada (nunca usa el vibrador estándar del teléfono).
 */
@UnstableApi
class CinemaHapticEngine(
    context: Context,
    private val sharedPreferencesManager: SharedPreferencesManager
) : InputManager.InputDeviceListener, SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "CinemaHapticEngine"
        const val PREF_HAPTIC_AUDIO_ENABLED = "haptic_audio_enabled"

        // Sony Interactive Entertainment Vendor ID (DualSense / DualShock)
        private const val VENDOR_SONY = 0x054C

        /**
         * Envía un pulso de confirmación al mando conectado al activar el ajuste desde Configuración,
         * incluso si el reproductor aún no se ha iniciado.
         */
        fun triggerConfirmationPulseFromSettings(context: Context) {
            try {
                val prefs = com.johang.audiocinemateca.data.local.SharedPreferencesManager(context.applicationContext)
                // Si el reproductor ya está reproduciendo, la instancia activa de PlayerService ya gestiona el pulso
                if (prefs.getBoolean("is_currently_playing", false)) return
                val tempEngine = CinemaHapticEngine(context.applicationContext, prefs)
                val target = tempEngine.findConnectedHapticController()
                if (target != null) {
                    tempEngine.playControllerLinkConfirmationPulse(target)
                }
                tempEngine.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error en confirmación desde ajustes: ${e.message}")
            }
        }
    }

    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentAudioSessionId: Int = 0
    private var isPlayerCurrentlyPlaying: Boolean = false
    private var phoneHapticGenerator: HapticGenerator? = null

    @Volatile
    private var activeControllerTarget: ControllerHapticTarget? = null

    @Volatile
    private var isControllerRoutingActive: Boolean = false

    @Volatile
    private var currentlyVibratingController: Boolean = false

    val audioProcessor = CinemaHapticAudioProcessor(
        onCinemaHapticEvent = ::onCinemaHapticEvent,
        isHapticAnalysisActive = { isControllerRoutingActive && isPlayerCurrentlyPlaying },
        onPlaybackFlushed = ::stopControllerVibration
    )

    private sealed class ControllerHapticTarget(val deviceId: Int, val deviceName: String) {
        @RequiresApi(Build.VERSION_CODES.S)
        class StereoVibratorManagerTarget(
            deviceId: Int,
            deviceName: String,
            val manager: VibratorManager,
            val leftVibratorId: Int,
            val rightVibratorId: Int,
            val hasAmplitudeControl: Boolean
        ) : ControllerHapticTarget(deviceId, deviceName)

        @RequiresApi(Build.VERSION_CODES.S)
        class MonoVibratorManagerTarget(
            deviceId: Int,
            deviceName: String,
            val vibrator: Vibrator,
            val hasAmplitudeControl: Boolean
        ) : ControllerHapticTarget(deviceId, deviceName)

        class LegacyVibratorTarget(
            deviceId: Int,
            deviceName: String,
            val vibrator: Vibrator,
            val hasAmplitudeControl: Boolean
        ) : ControllerHapticTarget(deviceId, deviceName)
    }

    init {
        try {
            inputManager?.registerInputDeviceListener(this, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando InputDeviceListener: ${e.message}")
        }
        try {
            sharedPreferencesManager.getPrefs().registerOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando OnSharedPreferenceChangeListener: ${e.message}")
        }
        refreshHapticRouting(sendConfirmationPulseIfControllerNew = false)
    }

    fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        currentAudioSessionId = audioSessionId
        refreshHapticRouting(sendConfirmationPulseIfControllerNew = false)
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        isPlayerCurrentlyPlaying = isPlaying
        if (!isPlaying) {
            stopControllerVibration()
        } else {
            refreshHapticRouting(sendConfirmationPulseIfControllerNew = false)
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(TAG, "InputDevice conectado (id=$deviceId), reevaluando ruta háptica...")
        refreshHapticRouting(sendConfirmationPulseIfControllerNew = true)
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(TAG, "InputDevice desconectado (id=$deviceId), reevaluando ruta háptica...")
        refreshHapticRouting(sendConfirmationPulseIfControllerNew = false)
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        refreshHapticRouting(sendConfirmationPulseIfControllerNew = false)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == PREF_HAPTIC_AUDIO_ENABLED) {
            val enabled = sharedPreferencesManager.getBoolean(PREF_HAPTIC_AUDIO_ENABLED, false)
            Log.d(TAG, "Preferencia '$PREF_HAPTIC_AUDIO_ENABLED' cambiada a: $enabled")
            refreshHapticRouting(sendConfirmationPulseIfControllerNew = enabled)
        }
    }

    /**
     * Evalúa qué dispositivo debe recibir la experiencia háptica según las reglas:
     * 1. Mando conectado (PS5 DualSense / Gamepad) -> Prioridad 1
     * 2. Teléfono con HapticGenerator nativo -> Prioridad 2 (solo si no hay mando)
     * 3. Teléfono normal sin mando -> Desactivado
     */
    @Synchronized
    fun refreshHapticRouting(sendConfirmationPulseIfControllerNew: Boolean = false) {
        val hapticEnabled = sharedPreferencesManager.getBoolean(PREF_HAPTIC_AUDIO_ENABLED, false)
        if (!hapticEnabled) {
            isControllerRoutingActive = false
            stopControllerVibration()
            activeControllerTarget = null
            disablePhoneHapticGenerator()
            return
        }

        val previousControllerId = activeControllerTarget?.deviceId
        val detectedController = findConnectedHapticController()

        if (detectedController != null) {
            // Prioridad 1: Hay un mando conectado con soporte háptico
            activeControllerTarget = detectedController
            isControllerRoutingActive = true

            // Apagar el HapticGenerator del teléfono para que la experiencia vaya exclusivamente al mando
            disablePhoneHapticGenerator()

            Log.d(
                TAG,
                "Ruta Háptica Activa -> MANDO: '${detectedController.deviceName}' (id=${detectedController.deviceId}, tipo=${detectedController.javaClass.simpleName})"
            )

            if (sendConfirmationPulseIfControllerNew && previousControllerId != detectedController.deviceId) {
                playControllerLinkConfirmationPulse(detectedController)
            }
        } else {
            // No hay mando conectado: limpiar estado del mando
            isControllerRoutingActive = false
            stopControllerVibration()
            activeControllerTarget = null

            // Prioridad 2: Verificar si el teléfono tiene HapticGenerator avanzado nativo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isPhoneHapticGeneratorSupported()) {
                enablePhoneHapticGeneratorIfPossible()
            } else {
                // Prioridad 3: Teléfono normal sin mando conectado -> no hacer nada
                disablePhoneHapticGenerator()
                Log.d(TAG, "Ruta Háptica Inactiva -> Sin mando conectado y teléfono sin HapticGenerator nativo.")
            }
        }
    }

    private fun findConnectedHapticController(): ControllerHapticTarget? {
        val deviceIds = InputDevice.getDeviceIds()
        var bestCandidate: ControllerHapticTarget? = null

        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            if (!isExternalGameController(device)) continue

            val target = buildControllerHapticTarget(device) ?: continue
            // Priorizar mandos Sony DualSense / DualShock si hay más de un dispositivo conectado
            if (device.vendorId == VENDOR_SONY) {
                return target
            }
            if (bestCandidate == null) {
                bestCandidate = target
            }
        }
        return bestCandidate
    }

    private fun isExternalGameController(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        val sources = device.sources
        val hasGamepadSource = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val hasJoystickSource = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!hasGamepadSource && !hasJoystickSource) return false

        val nameLower = device.name.lowercase()
        // Descartar sensores internos o teclados virtuales del propio teléfono
        if (nameLower.contains("touchscreen") || nameLower.contains("gpio") || nameLower.contains("uinput")) {
            return false
        }
        return true
    }

    private fun buildControllerHapticTarget(device: InputDevice): ControllerHapticTarget? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val vm = device.vibratorManager
                val allIds = vm.vibratorIds
                val validIds = allIds.filter { vibId ->
                    try {
                        vm.getVibrator(vibId).hasVibrator()
                    } catch (_: Exception) {
                        false
                    }
                }
                Log.d(
                    TAG,
                    "Inspeccionando mando '${device.name}' (vendor=0x${device.vendorId.toString(16)}, product=0x${device.productId.toString(16)}, vibratorIds=${allIds.contentToString()}, validIds=$validIds)"
                )
                if (validIds.size >= 2) {
                    val leftId = validIds[0]
                    val rightId = validIds[1]
                    val hasAmp = vm.getVibrator(leftId).hasAmplitudeControl() ||
                        vm.getVibrator(rightId).hasAmplitudeControl()
                    return ControllerHapticTarget.StereoVibratorManagerTarget(
                        deviceId = device.id,
                        deviceName = device.name,
                        manager = vm,
                        leftVibratorId = leftId,
                        rightVibratorId = rightId,
                        hasAmplitudeControl = hasAmp
                    )
                } else if (validIds.size == 1) {
                    val vib = vm.getVibrator(validIds[0])
                    return ControllerHapticTarget.MonoVibratorManagerTarget(
                        deviceId = device.id,
                        deviceName = device.name,
                        vibrator = vib,
                        hasAmplitudeControl = vib.hasAmplitudeControl()
                    )
                } else if (vm.defaultVibrator.hasVibrator()) {
                    val vib = vm.defaultVibrator
                    return ControllerHapticTarget.MonoVibratorManagerTarget(
                        deviceId = device.id,
                        deviceName = device.name,
                        vibrator = vib,
                        hasAmplitudeControl = vib.hasAmplitudeControl()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error consultando vibratorManager en '${device.name}': ${e.message}")
            }
        }

        @Suppress("DEPRECATION")
        val legacyVibrator = try {
            device.vibrator
        } catch (_: Exception) {
            null
        }
        if (legacyVibrator != null && legacyVibrator.hasVibrator()) {
            val hasAmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                legacyVibrator.hasAmplitudeControl()
            } else {
                false
            }
            return ControllerHapticTarget.LegacyVibratorTarget(
                deviceId = device.id,
                deviceName = device.name,
                vibrator = legacyVibrator,
                hasAmplitudeControl = hasAmp
            )
        }

        return null
    }

    /**
     * Envía un pulso doble suave de confirmación al mando cuando se vincula o activa la función.
     */
    private fun playControllerLinkConfirmationPulse(target: ControllerHapticTarget) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0L, 45L, 55L, 65L)
                val amplitudes = intArrayOf(0, 120, 0, 210)
                when (target) {
                    is ControllerHapticTarget.StereoVibratorManagerTarget -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val effect = if (target.hasAmplitudeControl) {
                                VibrationEffect.createWaveform(timings, amplitudes, -1)
                            } else {
                                VibrationEffect.createWaveform(timings, -1)
                            }
                            val combined = CombinedVibration.createParallel(effect)
                            target.manager.vibrate(combined)
                        }
                    }
                    is ControllerHapticTarget.MonoVibratorManagerTarget -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val effect = if (target.hasAmplitudeControl) {
                                VibrationEffect.createWaveform(timings, amplitudes, -1)
                            } else {
                                VibrationEffect.createWaveform(timings, -1)
                            }
                            target.vibrator.vibrate(effect)
                        }
                    }
                    is ControllerHapticTarget.LegacyVibratorTarget -> {
                        val effect = if (target.hasAmplitudeControl) {
                            VibrationEffect.createWaveform(timings, amplitudes, -1)
                        } else {
                            VibrationEffect.createWaveform(timings, -1)
                        }
                        target.vibrator.vibrate(effect)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enviar pulso de confirmación al mando: ${e.message}")
        }
    }

    /**
     * Recibe los eventos hápticos clasificados en tiempo real desde [CinemaHapticAudioProcessor]
     * y sintetiza el patrón físico adecuado para los motores del mando.
     */
    private fun onCinemaHapticEvent(
        effectType: CinemaHapticEffectType,
        leftAmplitude: Int,
        rightAmplitude: Int,
        durationMs: Long
    ) {
        val target = activeControllerTarget ?: return
        if (!isControllerRoutingActive || !isPlayerCurrentlyPlaying) return

        if (effectType == CinemaHapticEffectType.NONE || (leftAmplitude <= 0 && rightAmplitude <= 0) || durationMs <= 0L) {
            if (currentlyVibratingController) {
                stopControllerVibration()
            }
            return
        }

        try {
            // Para cristales rotos / vidrios astillándose: crear una micro-ráfaga granulada que cruja en las manos
            val isShatterCrackle = effectType == CinemaHapticEffectType.SHATTER_CRACKLE

            when (target) {
                is ControllerHapticTarget.StereoVibratorManagerTarget -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val builder = CombinedVibration.startParallel()
                        var hasEffect = false

                        if (leftAmplitude > 0) {
                            val ampL = leftAmplitude.coerceIn(1, 255)
                            val effectL = if (isShatterCrackle && target.hasAmplitudeControl) {
                                VibrationEffect.createWaveform(
                                    longArrayOf(0L, 9L, 6L, 11L, 6L, 8L),
                                    intArrayOf(0, ampL, 0, (ampL * 0.85f).toInt().coerceAtLeast(1), 0, (ampL * 0.65f).toInt().coerceAtLeast(1)),
                                    -1
                                )
                            } else {
                                val resolvedAmp = if (target.hasAmplitudeControl) ampL else VibrationEffect.DEFAULT_AMPLITUDE
                                VibrationEffect.createOneShot(durationMs, resolvedAmp)
                            }
                            builder.addVibrator(target.leftVibratorId, effectL)
                            hasEffect = true
                        }
                        if (rightAmplitude > 0) {
                            val ampR = rightAmplitude.coerceIn(1, 255)
                            val effectR = if (isShatterCrackle && target.hasAmplitudeControl) {
                                VibrationEffect.createWaveform(
                                    longArrayOf(0L, 11L, 5L, 12L, 5L, 10L),
                                    intArrayOf(0, ampR, 0, (ampR * 0.92f).toInt().coerceAtLeast(1), 0, (ampR * 0.75f).toInt().coerceAtLeast(1)),
                                    -1
                                )
                            } else {
                                val resolvedAmp = if (target.hasAmplitudeControl) ampR else VibrationEffect.DEFAULT_AMPLITUDE
                                VibrationEffect.createOneShot(durationMs, resolvedAmp)
                            }
                            builder.addVibrator(target.rightVibratorId, effectR)
                            hasEffect = true
                        }

                        if (hasEffect) {
                            target.manager.vibrate(builder.combine())
                            currentlyVibratingController = true
                        }
                    }
                }
                is ControllerHapticTarget.MonoVibratorManagerTarget -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val combinedAmp = max(leftAmplitude, rightAmplitude).coerceIn(1, 255)
                        val effect = if (isShatterCrackle && target.hasAmplitudeControl) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 10L, 6L, 12L, 6L, 9L),
                                intArrayOf(0, combinedAmp, 0, (combinedAmp * 0.88f).toInt().coerceAtLeast(1), 0, (combinedAmp * 0.7f).toInt().coerceAtLeast(1)),
                                -1
                            )
                        } else {
                            val amp = if (target.hasAmplitudeControl) combinedAmp else VibrationEffect.DEFAULT_AMPLITUDE
                            VibrationEffect.createOneShot(durationMs, amp)
                        }
                        target.vibrator.vibrate(effect)
                        currentlyVibratingController = true
                    }
                }
                is ControllerHapticTarget.LegacyVibratorTarget -> {
                    val combinedAmp = max(leftAmplitude, rightAmplitude).coerceIn(1, 255)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = if (isShatterCrackle && target.hasAmplitudeControl) {
                            VibrationEffect.createWaveform(
                                longArrayOf(0L, 10L, 6L, 12L, 6L, 9L),
                                intArrayOf(0, combinedAmp, 0, (combinedAmp * 0.88f).toInt().coerceAtLeast(1), 0, (combinedAmp * 0.7f).toInt().coerceAtLeast(1)),
                                -1
                            )
                        } else {
                            val amp = if (target.hasAmplitudeControl) combinedAmp else VibrationEffect.DEFAULT_AMPLITUDE
                            VibrationEffect.createOneShot(durationMs, amp)
                        }
                        target.vibrator.vibrate(effect)
                    } else {
                        @Suppress("DEPRECATION")
                        target.vibrator.vibrate(durationMs)
                    }
                    currentlyVibratingController = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando cuadro háptico al mando: ${e.message}")
        }
    }

    private fun stopControllerVibration() {
        if (!currentlyVibratingController) return
        currentlyVibratingController = false
        val target = activeControllerTarget ?: return
        try {
            when (target) {
                is ControllerHapticTarget.StereoVibratorManagerTarget -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        target.manager.cancel()
                    }
                }
                is ControllerHapticTarget.MonoVibratorManagerTarget -> {
                    target.vibrator.cancel()
                }
                is ControllerHapticTarget.LegacyVibratorTarget -> {
                    target.vibrator.cancel()
                }
            }
        } catch (_: Exception) {}
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isPhoneHapticGeneratorSupported(): Boolean {
        return try {
            HapticGenerator.isAvailable()
        } catch (_: Exception) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun enablePhoneHapticGeneratorIfPossible() {
        if (currentAudioSessionId <= 0) return
        try {
            if (!HapticGenerator.isAvailable()) return
            if (phoneHapticGenerator == null) {
                phoneHapticGenerator = HapticGenerator.create(currentAudioSessionId)
            }
            phoneHapticGenerator?.enabled = true
            Log.d(TAG, "Ruta Háptica Activa -> TELÉFONO AVANZADO (HapticGenerator nativo, sessionId=$currentAudioSessionId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error activando HapticGenerator del teléfono: ${e.message}")
        }
    }

    private fun disablePhoneHapticGenerator() {
        try {
            phoneHapticGenerator?.enabled = false
            phoneHapticGenerator?.release()
        } catch (_: Exception) {}
        phoneHapticGenerator = null
    }

    fun release() {
        isControllerRoutingActive = false
        isPlayerCurrentlyPlaying = false
        stopControllerVibration()
        disablePhoneHapticGenerator()
        try {
            inputManager?.unregisterInputDeviceListener(this)
        } catch (_: Exception) {}
        try {
            sharedPreferencesManager.getPrefs().unregisterOnSharedPreferenceChangeListener(this)
        } catch (_: Exception) {}
        activeControllerTarget = null
    }
}
