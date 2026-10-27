package com.johang.audiocinemateca.media

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.johang.audiocinemateca.util.GamepadNavDirection
import com.johang.audiocinemateca.util.TtsManager
import com.johang.audiocinemateca.util.moveComposeAccessibilityFocusInPanel
import com.johang.audiocinemateca.util.performClickOnAccessibilityFocusedNode
import com.johang.audiocinemateca.util.performLongClickOnAccessibilityFocusedNode
import com.johang.audiocinemateca.util.switchComposeAccessibilityPanel

/**
 * Contrato de acciones rápidas del reproductor para el mando DualSense / Gamepad.
 */
interface PlayerGamepadActions {
    fun onRewind()          // L1: Retroceder tiempo
    fun onForward()         // R1: Adelantar tiempo
    fun onPreviousChapter() // L2: Capítulo / Episodio / Parte anterior
    fun onNextChapter()     // R2: Capítulo / Episodio / Parte siguiente
    fun onAnnounceInfo()    // Cuadrado: Anunciar qué se reproduce y el tiempo actual
    fun onTogglePlayPause() // Triángulo: Pausar / Reproducir directamente
}

/**
 * Gestor integral de mandos PS5 DualSense y Gamepads para Audiocinemateca:
 * 1. Detecta conexión y desconexión en tiempo real anunciándolo por TTS.
 * 2. Gestiona la navegación 2D real por paneles (Arriba, Abajo, Izquierda, Derecha) con Cruceta y Joystick Izquierdo,
 *    respetando la orientación horizontal/vertical de cada barra o lista y vibrando al chocar con los límites.
 * 3. Permite saltar entre Paneles reales (Barra Superior <-> Pestañas <-> Lista <-> MiniReproductor <-> Navegación Inferior)
 *    moviendo el Joystick Derecho a la izquierda/derecha (o arriba/abajo).
 * 4. Ejecuta atajos directos en el reproductor (L1, R1, L2, R2, Cuadrado, Triángulo, X, Círculo).
 */
class DualSenseControllerManager(
    context: Context,
    private val ttsManager: TtsManager,
    private val onBackAction: () -> Unit,
    private val onToggleDrawerAction: () -> Unit,
    private val isDrawerOpenProvider: () -> Boolean,
    private val isOnPlayerScreenProvider: () -> Boolean,
    private val fallbackPlayPauseAction: () -> Unit,
    private val fallbackPreviousAction: () -> Unit,
    private val fallbackNextAction: () -> Unit,
    private val fallbackAnnounceMiniPlayerAction: () -> Boolean
) : InputManager.InputDeviceListener {

    companion object {
        private const val VENDOR_SONY = 0x054C
        private const val PRODUCT_DUALSENSE = 0x0CE6
        private const val PRODUCT_DUALSENSE_EDGE = 0x0DF2

        @Volatile
        var activePlayerActions: PlayerGamepadActions? = null
    }

    private enum class DualSenseButton {
        DPAD_UP,
        DPAD_DOWN,
        DPAD_LEFT,
        DPAD_RIGHT,
        CROSS,     // X (Abajo): Seleccionar / Clic
        CIRCLE,    // Círculo (Derecha): Atrás
        SQUARE,    // Cuadrado (Izquierda): Info de reproducción / Opciones contextuales
        TRIANGLE,  // Triángulo (Arriba): Play/Pause rápido en reproductor
        L1,        // Retroceder segundos
        R1,        // Adelantar segundos
        L2,        // Capítulo anterior
        R2,        // Capítulo siguiente
        OPTIONS    // Abrir/cerrar menú lateral
    }

    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val accessibilityManager = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val mainHandler = Handler(Looper.getMainLooper())

    // Mapa de descriptor físico -> nombre legible para evitar anuncios duplicados por sub-dispositivos
    private val connectedControllers = mutableMapOf<String, String>()

    // Estado de ejes analógicos (Cruceta HAT, Joystick Izquierdo, Joystick Derecho y Gatillos L2/R2)
    private var lastDpadAxisDirX = 0
    private var lastDpadAxisDirY = 0
    private var lastLeftStickDirX = 0
    private var lastLeftStickDirY = 0
    private var lastRightStickDirX = 0
    private var lastRightStickDirY = 0
    private var l2TriggerLatched = false
    private var r2TriggerLatched = false
    private var lastL2TriggerTimeMs = 0L
    private var lastR2TriggerTimeMs = 0L
    private var lastFocusMoveTimeMs = 0L

    init {
        scanInitialControllers()
        try {
            inputManager?.registerInputDeviceListener(this, mainHandler)
        } catch (_: Exception) {}
    }

    private fun scanInitialControllers() {
        connectedControllers.clear()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (isExternalGamepad(device)) {
                val key = physicalControllerKey(device)
                connectedControllers[key] = resolveSpokenControllerName(device)
            }
        }
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return
        if (!isExternalGamepad(device)) return

        val key = physicalControllerKey(device)
        if (!connectedControllers.containsKey(key)) {
            val spokenName = resolveSpokenControllerName(device)
            connectedControllers[key] = spokenName
            playWelcomePulseOnDevice(device)
            ttsManager.speak("$spokenName conectado", interrupt = false)
        }
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val remainingKeys = mutableSetOf<String>()
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (isExternalGamepad(dev)) {
                remainingKeys.add(physicalControllerKey(dev))
            }
        }

        val removedEntries = connectedControllers.entries.filter { it.key !in remainingKeys }
        if (removedEntries.isNotEmpty()) {
            val firstRemovedName = removedEntries.first().value
            for (entry in removedEntries) {
                connectedControllers.remove(entry.key)
            }
            ttsManager.speak("$firstRemovedName desconectado", interrupt = false)
        }
    }

    override fun onInputDeviceChanged(deviceId: Int) {}

    private fun isExternalGamepad(device: InputDevice): Boolean {
        if (device.isVirtual) return false
        val sources = device.sources
        val hasGamepad = (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val hasJoystick = (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        if (!hasGamepad && !hasJoystick) return false

        val nameLower = device.name.lowercase()
        if (nameLower.contains("touchscreen") || nameLower.contains("gpio") || nameLower.contains("uinput")) {
            return false
        }
        return true
    }

    private fun physicalControllerKey(device: InputDevice): String {
        val desc = device.descriptor
        return if (!desc.isNullOrBlank()) {
            desc
        } else {
            "${device.vendorId}_${device.productId}"
        }
    }

    private fun resolveSpokenControllerName(device: InputDevice): String {
        val nameLower = device.name.lowercase()
        return when {
            device.vendorId == VENDOR_SONY &&
                (device.productId == PRODUCT_DUALSENSE || device.productId == PRODUCT_DUALSENSE_EDGE || nameLower.contains("dualsense")) -> {
                "Mando DualSense"
            }
            device.vendorId == VENDOR_SONY || nameLower.contains("dualshock") || nameLower.contains("wireless controller") -> {
                "Mando DualSense"
            }
            nameLower.contains("xbox") -> "Mando Xbox"
            else -> "Mando inalámbrico"
        }
    }

    fun isFromGamepad(event: KeyEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        ) {
            return true
        }
        val dev = event.device ?: return false
        return isExternalGamepad(dev)
    }

    fun isFromGamepad(event: MotionEvent): Boolean {
        val src = event.source
        if ((src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
            (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
            (src and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
        ) {
            val dev = event.device ?: return true
            return isExternalGamepad(dev)
        }
        return false
    }

    private fun mapKeyEventToButton(event: KeyEvent): DualSenseButton? {
        val device = event.device
        val isSonyGenericLayout = device != null &&
            device.vendorId == VENDOR_SONY &&
            runCatching { device.hasKeys(KeyEvent.KEYCODE_BUTTON_Z)[0] }.getOrDefault(false)

        if (isSonyGenericLayout) {
            when (event.scanCode) {
                304 -> return DualSenseButton.SQUARE
                305 -> return DualSenseButton.CROSS
                306 -> return DualSenseButton.CIRCLE
                307 -> return DualSenseButton.TRIANGLE
                308 -> return DualSenseButton.L1
                309 -> return DualSenseButton.R1
                310 -> return DualSenseButton.L2
                311 -> return DualSenseButton.R2
                312, 313, 314, 315 -> return DualSenseButton.OPTIONS
            }
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> DualSenseButton.DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> DualSenseButton.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> DualSenseButton.DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> DualSenseButton.DPAD_RIGHT
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> DualSenseButton.CROSS
            KeyEvent.KEYCODE_BUTTON_B -> DualSenseButton.CIRCLE
            KeyEvent.KEYCODE_BUTTON_X -> DualSenseButton.SQUARE
            KeyEvent.KEYCODE_BUTTON_Y -> DualSenseButton.TRIANGLE
            KeyEvent.KEYCODE_BUTTON_L1 -> DualSenseButton.L1
            KeyEvent.KEYCODE_BUTTON_R1 -> DualSenseButton.R1
            KeyEvent.KEYCODE_BUTTON_L2 -> DualSenseButton.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> DualSenseButton.R2
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_MENU -> DualSenseButton.OPTIONS
            else -> null
        }
    }

    /**
     * Procesa eventos de botones y cruceta del mando DualSense.
     */
    fun handleKeyEvent(rootView: View, event: KeyEvent): Boolean {
        if (!isFromGamepad(event)) return false
        val button = mapKeyEventToButton(event) ?: return false

        if (event.action == KeyEvent.ACTION_UP) {
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }

        val now = SystemClock.elapsedRealtime()
        val isRepeat = event.repeatCount > 0

        when (button) {
            DualSenseButton.DPAD_UP -> {
                if (isRepeat && now - lastFocusMoveTimeMs < 115L) return true
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(rootView, GamepadNavDirection.UP)
                return true
            }
            DualSenseButton.DPAD_DOWN -> {
                if (isRepeat && now - lastFocusMoveTimeMs < 115L) return true
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(rootView, GamepadNavDirection.DOWN)
                return true
            }
            DualSenseButton.DPAD_LEFT -> {
                if (isRepeat && now - lastFocusMoveTimeMs < 115L) return true
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(rootView, GamepadNavDirection.LEFT)
                return true
            }
            DualSenseButton.DPAD_RIGHT -> {
                if (isRepeat && now - lastFocusMoveTimeMs < 115L) return true
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(rootView, GamepadNavDirection.RIGHT)
                return true
            }
            DualSenseButton.CROSS -> {
                if (isRepeat) return true
                val prefix = if (isDrawerOpenProvider()) "drawer_item_" else null
                val clicked = rootView.performClickOnAccessibilityFocusedNode(restrictToTagPrefix = prefix)
                if (clicked) {
                    playControllerPulse(leftAmp = 150, rightAmp = 150, durationMs = 24L)
                } else {
                    playBoundaryDoublePulse()
                }
                return true
            }
            DualSenseButton.CIRCLE -> {
                if (isRepeat) return true
                ttsManager.stop()
                playControllerPulse(leftAmp = 120, rightAmp = 90, durationMs = 22L)
                onBackAction()
                return true
            }
            DualSenseButton.L1 -> {
                val playerActions = activePlayerActions
                if (playerActions != null && isOnPlayerScreenProvider()) {
                    playControllerPulse(leftAmp = 190, rightAmp = 0, durationMs = 28L)
                    playerActions.onRewind()
                    return true
                }
                return true
            }
            DualSenseButton.R1 -> {
                val playerActions = activePlayerActions
                if (playerActions != null && isOnPlayerScreenProvider()) {
                    playControllerPulse(leftAmp = 0, rightAmp = 190, durationMs = 28L)
                    playerActions.onForward()
                    return true
                }
                return true
            }
            DualSenseButton.L2 -> {
                if (isRepeat || now - lastL2TriggerTimeMs < 350L) return true
                lastL2TriggerTimeMs = now
                triggerPreviousChapterAction()
                return true
            }
            DualSenseButton.R2 -> {
                if (isRepeat || now - lastR2TriggerTimeMs < 350L) return true
                lastR2TriggerTimeMs = now
                triggerNextChapterAction()
                return true
            }
            DualSenseButton.SQUARE -> {
                if (isRepeat) return true
                val playerActions = activePlayerActions
                if (playerActions != null && isOnPlayerScreenProvider()) {
                    playControllerPulse(leftAmp = 130, rightAmp = 130, durationMs = 25L)
                    playerActions.onAnnounceInfo()
                    return true
                }
                val longClicked = rootView.performLongClickOnAccessibilityFocusedNode()
                if (longClicked) {
                    playControllerPulse(leftAmp = 170, rightAmp = 170, durationMs = 30L)
                } else if (fallbackAnnounceMiniPlayerAction()) {
                    playControllerPulse(leftAmp = 130, rightAmp = 130, durationMs = 25L)
                } else {
                    playBoundaryDoublePulse()
                }
                return true
            }
            DualSenseButton.TRIANGLE -> {
                if (isRepeat) return true
                val playerActions = activePlayerActions
                if (playerActions != null && isOnPlayerScreenProvider()) {
                    playControllerPulse(leftAmp = 165, rightAmp = 165, durationMs = 26L)
                    playerActions.onTogglePlayPause()
                    return true
                }
                fallbackPlayPauseAction()
                playControllerPulse(leftAmp = 140, rightAmp = 140, durationMs = 24L)
                return true
            }
            DualSenseButton.OPTIONS -> {
                if (isRepeat) return true
                if (!isOnPlayerScreenProvider()) {
                    playControllerPulse(leftAmp = 130, rightAmp = 130, durationMs = 25L)
                    onToggleDrawerAction()
                } else {
                    activePlayerActions?.onAnnounceInfo()
                }
                return true
            }
        }
    }

    /**
     * Procesa ejes analógicos del DualSense:
     * 1. Cruceta (AXIS_HAT_X / AXIS_HAT_Y) -> Movimiento 2D dentro del panel actual
     * 2. Joystick Izquierdo (AXIS_X / AXIS_Y) -> Movimiento 2D dentro del panel actual
     * 3. Joystick Derecho (AXIS_Z / AXIS_RZ) -> Cambio entre Paneles reales de la pantalla
     * 4. Gatillos L2 y R2 (AXIS_LTRIGGER / AXIS_RTRIGGER / AXIS_BRAKE / AXIS_GAS) -> Capítulo anterior / siguiente
     */
    fun handleGenericMotionEvent(rootView: View, event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE || !isFromGamepad(event)) return false
        var handled = false
        val now = SystemClock.elapsedRealtime()

        // 1. Cruceta (HAT_X / HAT_Y) -> Dirección exacta 2D dentro del panel
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val dirHatX = when {
            hatX > 0.5f -> 1
            hatX < -0.5f -> -1
            else -> 0
        }
        val dirHatY = when {
            hatY > 0.5f -> 1
            hatY < -0.5f -> -1
            else -> 0
        }

        if (dirHatX != 0 && dirHatX != lastDpadAxisDirX) {
            lastFocusMoveTimeMs = now
            moveFocusInCurrentPanel(
                rootView,
                if (dirHatX > 0) GamepadNavDirection.RIGHT else GamepadNavDirection.LEFT
            )
            handled = true
        }
        lastDpadAxisDirX = dirHatX

        if (dirHatY != 0 && dirHatY != lastDpadAxisDirY) {
            lastFocusMoveTimeMs = now
            moveFocusInCurrentPanel(
                rootView,
                if (dirHatY > 0) GamepadNavDirection.DOWN else GamepadNavDirection.UP
            )
            handled = true
        }
        lastDpadAxisDirY = dirHatY

        // 2. Joystick Izquierdo (AXIS_X / AXIS_Y) -> Movimiento 2D dentro del panel
        val stickX = event.getAxisValue(MotionEvent.AXIS_X)
        val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        val dirLeftX = when {
            stickX > 0.65f -> 1
            stickX < -0.65f -> -1
            stickX in -0.35f..0.35f -> 0
            else -> lastLeftStickDirX
        }
        val dirLeftY = when {
            stickY > 0.65f -> 1
            stickY < -0.65f -> -1
            stickY in -0.35f..0.35f -> 0
            else -> lastLeftStickDirY
        }

        // Priorizar el eje dominante del Joystick Izquierdo para evitar diagonales involuntarias
        if (kotlin.math.abs(stickX) >= kotlin.math.abs(stickY)) {
            if (dirLeftX != 0 && dirLeftX != lastLeftStickDirX) {
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(
                    rootView,
                    if (dirLeftX > 0) GamepadNavDirection.RIGHT else GamepadNavDirection.LEFT
                )
                handled = true
            }
        } else {
            if (dirLeftY != 0 && dirLeftY != lastLeftStickDirY) {
                lastFocusMoveTimeMs = now
                moveFocusInCurrentPanel(
                    rootView,
                    if (dirLeftY > 0) GamepadNavDirection.DOWN else GamepadNavDirection.UP
                )
                handled = true
            }
        }
        lastLeftStickDirX = dirLeftX
        lastLeftStickDirY = dirLeftY

        // 3. Joystick Derecho (AXIS_Z = Horizontal, AXIS_RZ = Vertical) -> Cambiar entre Paneles reales
        val rightStickX = event.getAxisValue(MotionEvent.AXIS_Z)
        val rightStickY = event.getAxisValue(MotionEvent.AXIS_RZ)
        val dirRightX = when {
            rightStickX > 0.65f -> 1
            rightStickX < -0.65f -> -1
            rightStickX in -0.35f..0.35f -> 0
            else -> lastRightStickDirX
        }
        val dirRightY = when {
            rightStickY > 0.65f -> 1
            rightStickY < -0.65f -> -1
            rightStickY in -0.35f..0.35f -> 0
            else -> lastRightStickDirY
        }

        if (kotlin.math.abs(rightStickX) >= kotlin.math.abs(rightStickY)) {
            if (dirRightX != 0 && dirRightX != lastRightStickDirX) {
                lastFocusMoveTimeMs = now
                switchPanelAndFeedback(rootView, nextPanel = dirRightX > 0)
                handled = true
            }
        } else {
            if (dirRightY != 0 && dirRightY != lastRightStickDirY) {
                lastFocusMoveTimeMs = now
                switchPanelAndFeedback(rootView, nextPanel = dirRightY > 0)
                handled = true
            }
        }
        lastRightStickDirX = dirRightX
        lastRightStickDirY = dirRightY

        // 4. Gatillos analógicos L2 y R2 (para cambio de capítulo anterior / siguiente)
        val l2Value = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val r2Value = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )

        if (l2Value > 0.65f && !l2TriggerLatched) {
            l2TriggerLatched = true
            if (now - lastL2TriggerTimeMs >= 350L) {
                lastL2TriggerTimeMs = now
                triggerPreviousChapterAction()
            }
            handled = true
        } else if (l2Value < 0.25f) {
            l2TriggerLatched = false
        }

        if (r2Value > 0.65f && !r2TriggerLatched) {
            r2TriggerLatched = true
            if (now - lastR2TriggerTimeMs >= 350L) {
                lastR2TriggerTimeMs = now
                triggerNextChapterAction()
            }
            handled = true
        } else if (r2Value < 0.25f) {
            r2TriggerLatched = false
        }

        return handled
    }

    private fun triggerPreviousChapterAction() {
        val playerActions = activePlayerActions
        if (playerActions != null && isOnPlayerScreenProvider()) {
            playControllerPulse(leftAmp = 235, rightAmp = 60, durationMs = 42L)
            playerActions.onPreviousChapter()
        } else {
            playControllerPulse(leftAmp = 220, rightAmp = 50, durationMs = 40L)
            fallbackPreviousAction()
        }
    }

    private fun triggerNextChapterAction() {
        val playerActions = activePlayerActions
        if (playerActions != null && isOnPlayerScreenProvider()) {
            playControllerPulse(leftAmp = 60, rightAmp = 235, durationMs = 42L)
            playerActions.onNextChapter()
        } else {
            playControllerPulse(leftAmp = 50, rightAmp = 220, durationMs = 40L)
            fallbackNextAction()
        }
    }

    private fun moveFocusInCurrentPanel(rootView: View, direction: GamepadNavDirection) {
        val prefix = if (isDrawerOpenProvider()) "drawer_item_" else null
        val result = rootView.moveComposeAccessibilityFocusInPanel(
            direction = direction,
            restrictToTagPrefix = prefix
        )

        if (result.hitBoundary) {
            playBoundaryDoublePulse()
            return
        }

        if (result.moved) {
            when (direction) {
                GamepadNavDirection.RIGHT, GamepadNavDirection.DOWN -> {
                    playControllerPulse(leftAmp = 0, rightAmp = 145, durationMs = 18L)
                }
                GamepadNavDirection.LEFT, GamepadNavDirection.UP -> {
                    playControllerPulse(leftAmp = 145, rightAmp = 0, durationMs = 18L)
                }
            }
        }

        if (result.scrolledPendingFocus) {
            rootView.postDelayed({
                val afterScroll = rootView.moveComposeAccessibilityFocusInPanel(
                    direction = direction,
                    restrictToTagPrefix = prefix
                )
                speakIfScreenReaderInactive(afterScroll.focusedLabel)
            }, 40L)
        } else {
            speakIfScreenReaderInactive(result.focusedLabel)
        }
    }

    private fun switchPanelAndFeedback(rootView: View, nextPanel: Boolean) {
        val prefix = if (isDrawerOpenProvider()) "drawer_item_" else null
        val result = rootView.switchComposeAccessibilityPanel(
            nextPanel = nextPanel,
            restrictToTagPrefix = prefix
        )

        if (result.hitBoundary) {
            playBoundaryDoublePulse()
            return
        }

        if (result.moved) {
            if (nextPanel) {
                playControllerPulse(leftAmp = 90, rightAmp = 200, durationMs = 28L)
            } else {
                playControllerPulse(leftAmp = 200, rightAmp = 90, durationMs = 28L)
            }
            speakIfScreenReaderInactive(result.focusedLabel)
        }
    }

    private fun speakIfScreenReaderInactive(label: String?) {
        if (label.isNullOrBlank()) return
        val isScreenReaderActive = accessibilityManager?.isEnabled == true &&
            accessibilityManager.isTouchExplorationEnabled
        if (!isScreenReaderActive) {
            ttsManager.speak(label, interrupt = true)
        }
    }

    private fun findTargetGamepadDevice(): InputDevice? {
        var fallback: InputDevice? = null
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id) ?: continue
            if (!isExternalGamepad(dev)) continue
            if (dev.vendorId == VENDOR_SONY) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.vibratorManager.vibratorIds.isNotEmpty()) {
                    return dev
                }
                fallback = dev
            } else if (fallback == null) {
                fallback = dev
            }
        }
        return fallback
    }

    private fun playWelcomePulseOnDevice(device: InputDevice) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = device.vibratorManager
                if (vm.vibratorIds.isNotEmpty()) {
                    val effect = VibrationEffect.createWaveform(
                        longArrayOf(0L, 40L, 50L, 65L),
                        intArrayOf(0, 130, 0, 220),
                        -1
                    )
                    vm.vibrate(CombinedVibration.createParallel(effect))
                    return
                }
            }
            @Suppress("DEPRECATION")
            val vib = device.vibrator
            if (vib.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0L, 40L, 50L, 65L),
                        intArrayOf(0, 130, 0, 220),
                        -1
                    )
                )
            }
        } catch (_: Exception) {}
    }

    fun playControllerPulse(leftAmp: Int, rightAmp: Int, durationMs: Long) {
        val device = findTargetGamepadDevice() ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = device.vibratorManager
                val ids = vm.vibratorIds
                if (ids.size >= 2) {
                    val builder = CombinedVibration.startParallel()
                    var added = false
                    if (leftAmp > 0) {
                        builder.addVibrator(ids[0], VibrationEffect.createOneShot(durationMs, leftAmp.coerceIn(1, 255)))
                        added = true
                    }
                    if (rightAmp > 0) {
                        builder.addVibrator(ids[1], VibrationEffect.createOneShot(durationMs, rightAmp.coerceIn(1, 255)))
                        added = true
                    }
                    if (added) {
                        vm.vibrate(builder.combine())
                        return
                    }
                } else if (ids.size == 1) {
                    val maxAmp = maxOf(leftAmp, rightAmp).coerceIn(1, 255)
                    vm.getVibrator(ids[0]).vibrate(VibrationEffect.createOneShot(durationMs, maxAmp))
                    return
                }
            }
            @Suppress("DEPRECATION")
            val vib = device.vibrator
            if (vib.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val maxAmp = maxOf(leftAmp, rightAmp).coerceIn(1, 255)
                vib.vibrate(VibrationEffect.createOneShot(durationMs, maxAmp))
            }
        } catch (_: Exception) {}
    }

    /**
     * Doble pulso corto al chocar con los límites superior, inferior, izquierdo o derecho del panel.
     */
    fun playBoundaryDoublePulse() {
        val device = findTargetGamepadDevice() ?: return
        try {
            val timings = longArrayOf(0L, 22L, 38L, 28L)
            val amplitudes = intArrayOf(0, 175, 0, 225)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = device.vibratorManager
                if (vm.vibratorIds.isNotEmpty()) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vm.vibrate(CombinedVibration.createParallel(effect))
                    return
                }
            }
            @Suppress("DEPRECATION")
            val vib = device.vibrator
            if (vib.hasVibrator() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            inputManager?.unregisterInputDeviceListener(this)
        } catch (_: Exception) {}
    }
}
