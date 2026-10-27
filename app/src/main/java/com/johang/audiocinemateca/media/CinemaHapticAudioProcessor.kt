package com.johang.audiocinemateca.media

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Tipos de texturas y efectos físicos detectados en tiempo real en la película.
 */
enum class CinemaHapticEffectType {
    NONE,
    HEAVY_RUMBLE,    // Explosiones, truenos, motores, derrumbes (subgraves profundos)
    FOLEY_IMPACT,    // Golpes secos, objetos lanzados, puñetazos, portazos, pasos fuertes
    SHATTER_CRACKLE, // Vidrios rotos, cristales astillándose, choques metálicos, disparos
    RAIN_DROPLETS,   // Lluvia, gotas de agua, chapoteo
    WIND_SWEEP       // Viento, ráfagas de aire, tormenta ambiental
}

/**
 * Procesador DSP de Cine en Tiempo Real acoplado a ExoPlayer (Pass-Through puro).
 *
 * Diseñado específicamente para:
 * 1. Ignorar la voz humana (narrador de audiodescripción y diálogos).
 * 2. Ignorar el ritmo constante de batería/bajo de la música de fondo (Music Beat Rejector).
 * 3. Detectar con alta sensibilidad efectos físicos de Foley: vidrios lanzados o rotos,
 *    golpes en objetos, puñetazos, portazos y disparos.
 * 4. Sintetizar texturas ambientales sutiles para la lluvia y el viento en los motores del DualSense.
 */
@UnstableApi
class CinemaHapticAudioProcessor(
    private val onCinemaHapticEvent: (
        effectType: CinemaHapticEffectType,
        leftAmplitude: Int,
        rightAmplitude: Int,
        durationMs: Long
    ) -> Unit,
    private val isHapticAnalysisActive: () -> Boolean,
    private val onPlaybackFlushed: () -> Unit
) : AudioProcessor {

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Coeficientes de filtros IIR
    private var lfeAlpha = 0.010f       // < 75 Hz (Explosiones / Subgraves puros LFE)
    private var foleyLowAlpha = 0.025f  // ~ 130 Hz
    private var foleyHighAlpha = 0.14f  // ~ 780 Hz (Banda de golpes físicos: objetos, puñetazos, puertas)
    private var vocalHighAlpha = 0.35f  // ~ 2,600 Hz (Techo de formantes principales de voz)

    // Estados de filtros por canal (Izquierdo y Derecho)
    private var lfe1L = 0f
    private var lfe2L = 0f
    private var lfe1R = 0f
    private var lfe2R = 0f

    private var lp130L = 0f
    private var lp780L = 0f
    private var lp130R = 0f
    private var lp780R = 0f

    private var lp2600Mono = 0f
    private var prevMonoSample = 0f

    // Acumuladores por ventana de análisis (~32 ms)
    private var accLfeSqL = 0f
    private var accLfeSqR = 0f
    private var accFoleySqL = 0f
    private var accFoleySqR = 0f
    private var accVocalSq = 0f
    private var accCrispSqL = 0f
    private var accCrispSqR = 0f

    private var peakFoleyL = 0f
    private var peakFoleyR = 0f
    private var peakCrispL = 0f
    private var peakCrispR = 0f
    private var zeroCrossings = 0
    private var accSamples = 0

    // Seguidores de envolvente multi-escala (para distinguir música constante vs impactos reales)
    private var musicFloorLfe = 0.015f
    private var slowFoleyEnv = 0.012f
    private var slowCrispEnv = 0.008f
    private var recentSpeechEnv = 0.02f

    // Confianza acumulada para texturas ambientales (Lluvia y Viento)
    private var rainConfidence = 0f
    private var windConfidence = 0f
    private var rainAlternateToggle = false

    private var lastDispatchTimeMs = 0L

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            this.inputAudioFormat = AudioFormat.NOT_SET
            this.outputAudioFormat = AudioFormat.NOT_SET
            return AudioFormat.NOT_SET
        }

        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat

        val effectiveFs = (inputAudioFormat.sampleRate / 2f).coerceAtLeast(11025f)
        val twoPi = (2.0 * Math.PI).toFloat()
        val dt = 1f / effectiveFs

        // 1. Subgraves LFE (< 75 Hz)
        val rcLfe = 1f / (twoPi * 75f)
        lfeAlpha = (dt / (rcLfe + dt)).coerceIn(0.002f, 0.15f)

        // 2. Banda de impactos físicos Foley (130 Hz - 780 Hz)
        val rc130 = 1f / (twoPi * 130f)
        foleyLowAlpha = (dt / (rc130 + dt)).coerceIn(0.005f, 0.25f)

        val rc780 = 1f / (twoPi * 780f)
        foleyHighAlpha = (dt / (rc780 + dt)).coerceIn(0.02f, 0.55f)

        // 3. Corte entre voz (300 - 2,600 Hz) y agudos cristalinos (> 2,600 Hz: vidrios, lluvia, metal)
        val rc2600 = 1f / (twoPi * 2600f)
        vocalHighAlpha = (dt / (rc2600 + dt)).coerceIn(0.08f, 0.85f)

        resetFilterState()
        return outputAudioFormat
    }

    override fun isActive(): Boolean {
        return outputAudioFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (isHapticAnalysisActive() && inputAudioFormat != AudioFormat.NOT_SET) {
            try {
                analyzePcmBuffer(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
            } catch (_: Exception) {}
        }

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        buffer.put(inputBuffer)
        buffer.flip()
        outputBuffer = buffer
    }

    private fun analyzePcmBuffer(pcm: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val encoding = inputAudioFormat.encoding
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        if (bytesPerFrame <= 0) return

        val totalFrames = pcm.remaining() / bytesPerFrame
        if (totalFrames <= 0) return

        val stepFrames = 2
        var frameIdx = 0

        while (frameIdx < totalFrames) {
            val byteOffset = pcm.position() + frameIdx * bytesPerFrame
            val leftSample: Float
            val rightSample: Float

            if (encoding == C.ENCODING_PCM_16BIT) {
                leftSample = (pcm.getShort(byteOffset) / 32768f).coerceIn(-1f, 1f)
                rightSample = if (channels >= 2) {
                    (pcm.getShort(byteOffset + 2) / 32768f).coerceIn(-1f, 1f)
                } else {
                    leftSample
                }
            } else {
                leftSample = pcm.getFloat(byteOffset).coerceIn(-1f, 1f)
                rightSample = if (channels >= 2) {
                    pcm.getFloat(byteOffset + 4).coerceIn(-1f, 1f)
                } else {
                    leftSample
                }
            }

            val mono = 0.5f * (leftSample + rightSample)

            // Conteo de cruces por cero (Zero-Crossing Rate) para detectar vidrios rotos, lluvia y viento
            if ((mono >= 0f && prevMonoSample < 0f) || (mono < 0f && prevMonoSample >= 0f)) {
                zeroCrossings++
            }
            prevMonoSample = mono

            // Banda 1: Subgraves LFE puros (< 75 Hz, 2 polos) -> Explosiones, truenos, motores pesados
            lfe1L += lfeAlpha * (leftSample - lfe1L)
            lfe2L += lfeAlpha * (lfe1L - lfe2L)
            lfe1R += lfeAlpha * (rightSample - lfe1R)
            lfe2R += lfeAlpha * (lfe1R - lfe2R)

            // Banda 2: Golpes físicos Foley (130 Hz - 780 Hz) -> Golpear puertas/mesas, puñetazos, pasos, objetos
            lp130L += foleyLowAlpha * (leftSample - lp130L)
            val hp130L = leftSample - lp130L
            lp780L += foleyHighAlpha * (hp130L - lp780L)
            val foleyL = lp780L

            lp130R += foleyLowAlpha * (rightSample - lp130R)
            val hp130R = rightSample - lp130R
            lp780R += foleyHighAlpha * (hp130R - lp780R)
            val foleyR = lp780R

            // Banda 3: Voz humana (780 Hz - 2,600 Hz) vs Banda 4: Agudos Cristalinos (> 2,600 Hz: vidrios, lluvia, metal)
            val hp780Mono = mono - 0.5f * (lp130L + lp130R + foleyL + foleyR)
            lp2600Mono += vocalHighAlpha * (hp780Mono - lp2600Mono)
            val vocalMid = lp2600Mono

            // Componente agudo estéreo (> 2,600 Hz) para vidrios rotos, escombros y gotas de lluvia
            val crispL = (leftSample - lp130L - foleyL) - vocalMid
            val crispR = (rightSample - lp130R - foleyR) - vocalMid

            val absFoleyL = abs(foleyL)
            val absFoleyR = abs(foleyR)
            if (absFoleyL > peakFoleyL) peakFoleyL = absFoleyL
            if (absFoleyR > peakFoleyR) peakFoleyR = absFoleyR

            val absCrispL = abs(crispL)
            val absCrispR = abs(crispR)
            if (absCrispL > peakCrispL) peakCrispL = absCrispL
            if (absCrispR > peakCrispR) peakCrispR = absCrispR

            accLfeSqL += lfe2L * lfe2L
            accLfeSqR += lfe2R * lfe2R
            accFoleySqL += foleyL * foleyL
            accFoleySqR += foleyR * foleyR
            accVocalSq += vocalMid * vocalMid
            accCrispSqL += crispL * crispL
            accCrispSqR += crispR * crispR
            accSamples++

            frameIdx += stepFrames
        }

        val nowMs = android.os.SystemClock.elapsedRealtime()
        if (nowMs - lastDispatchTimeMs >= 32L && accSamples > 0) {
            lastDispatchTimeMs = nowMs
            evaluateAndDispatchHaptics()
        }
    }

    private fun evaluateAndDispatchHaptics() {
        val invN = 1f / accSamples.toFloat()
        val lfeRmsL = sqrt(accLfeSqL * invN)
        val lfeRmsR = sqrt(accLfeSqR * invN)
        val lfeRms = max(lfeRmsL, lfeRmsR)

        val foleyRmsL = sqrt(accFoleySqL * invN)
        val foleyRmsR = sqrt(accFoleySqR * invN)
        val foleyRms = max(foleyRmsL, foleyRmsR)

        val vocalRms = sqrt(accVocalSq * invN)

        val crispRmsL = sqrt(accCrispSqL * invN)
        val crispRmsR = sqrt(accCrispSqR * invN)
        val crispRms = max(crispRmsL, crispRmsR)

        val foleyPeak = max(peakFoleyL, peakFoleyR)
        val crispPeak = max(peakCrispL, peakCrispR)
        val zcr = zeroCrossings.toFloat() * invN

        // Reiniciar acumuladores de la ventana
        accLfeSqL = 0f
        accLfeSqR = 0f
        accFoleySqL = 0f
        accFoleySqR = 0f
        accVocalSq = 0f
        accCrispSqL = 0f
        accCrispSqR = 0f
        peakFoleyL = 0f
        peakFoleyR = 0f
        peakCrispL = 0f
        peakCrispR = 0f
        zeroCrossings = 0
        accSamples = 0

        // Factores de cresta (Pico / RMS):
        // La voz y la música sostenida tienen cresta baja (~1.5 - 2.3).
        // Un golpe seco, objeto lanzado o vidrio rompiéndose tiene cresta alta (> 2.8) y salto súbito de envolvente.
        val foleyCrest = if (foleyRms > 0.004f) foleyPeak / foleyRms else 1f
        val crispCrest = if (crispRms > 0.003f) crispPeak / crispRms else 1f

        val foleyOnsetRatio = if (slowFoleyEnv > 0.005f) foleyRms / slowFoleyEnv else 1f
        val crispOnsetRatio = if (slowCrispEnv > 0.003f) crispRms / slowCrispEnv else 1f

        // Actualizar envolventes lentas de referencia
        slowFoleyEnv = slowFoleyEnv * 0.84f + foleyRms * 0.16f
        slowCrispEnv = slowCrispEnv * 0.82f + crispRms * 0.18f
        recentSpeechEnv = recentSpeechEnv * 0.78f + vocalRms * 0.22f

        // Seguidor de piso musical rítmico: sube lentamente cuando hay música constante de fondo
        // para filtrar los golpes repetitivos de batería musical sin bloquear explosiones reales.
        musicFloorLfe = if (lfeRms > musicFloorLfe) {
            musicFloorLfe * 0.92f + lfeRms * 0.08f
        } else {
            musicFloorLfe * 0.96f + lfeRms * 0.04f
        }

        // ¿Es habla pura (narrador o diálogo) sin acción física?
        val isLikelyPureSpeech = vocalRms > 0.018f &&
            vocalRms > foleyRms * 0.95f &&
            foleyOnsetRatio < 1.85f &&
            crispOnsetRatio < 2.0f &&
            lfeRms < musicFloorLfe * 1.6f

        // =========================================================================
        // 1. DETECTOR DE VIDRIOS ROTOS / CRISTALES / CHOQUES METÁLICOS / DISPAROS
        // =========================================================================
        // Los vidrios rotos y objetos que se parten tienen picos agudos (> 2,600 Hz) con alta cresta,
        // alto cruce por cero o salto brusco en agudos + impacto medio simultáneo.
        val isGlassOrShatter = (crispPeak > 0.055f && crispOnsetRatio > 1.75f && crispCrest > 2.45f) ||
            (crispPeak > 0.042f && foleyOnsetRatio > 1.65f && zcr > 0.16f && crispRms > vocalRms * 0.38f)

        if (isGlassOrShatter) {
            val strengthL = ((crispRmsL * 9.5f) + (peakCrispL * 1.8f) + (foleyRmsL * 2.2f)).coerceIn(0.15f, 1f)
            val strengthR = ((crispRmsR * 11.0f) + (peakCrispR * 2.2f) + (foleyRmsR * 2.5f)).coerceIn(0.20f, 1f)
            val ampL = mapAmplitude(strengthL, minAmp = 55, maxAmp = 240)
            val ampR = mapAmplitude(strengthR, minAmp = 75, maxAmp = 255)
            onCinemaHapticEvent(CinemaHapticEffectType.SHATTER_CRACKLE, ampL, ampR, 42L)
            return
        }

        // =========================================================================
        // 2. DETECTOR DE GOLPES FÍSICOS FOLEY (Golpear algo, lanzar objetos, puñetazos, portazos)
        // =========================================================================
        // Se distingue de la batería de la música y de la voz porque tiene un ataque rápido (foleyOnsetRatio)
        // y una cresta pronunciada (foleyCrest), sin requerir subgraves de 50 Hz.
        val isPhysicalFoleyHit = !isLikelyPureSpeech &&
            foleyPeak > 0.065f &&
            ((foleyOnsetRatio > 1.72f && foleyCrest > 2.35f) ||
                (foleyOnsetRatio > 2.15f && foleyPeak > 0.09f))

        if (isPhysicalFoleyHit) {
            val hitL = ((foleyRmsL * 5.8f) + (peakFoleyL * 1.4f)).coerceIn(0.15f, 1f)
            val hitR = ((foleyRmsR * 5.8f) + (peakFoleyR * 1.4f)).coerceIn(0.15f, 1f)
            val ampL = mapAmplitude(hitL, minAmp = 60, maxAmp = 245)
            val ampR = mapAmplitude(hitR, minAmp = 60, maxAmp = 245)
            onCinemaHapticEvent(CinemaHapticEffectType.FOLEY_IMPACT, ampL, ampR, 26L)
            return
        }

        // =========================================================================
        // 3. DETECTOR DE EXPLOSIONES, TRUENOS Y SUBGRAVES DE CINE (Con rechazo de batería musical)
        // =========================================================================
        // Restamos el piso musical continuo (musicFloorLfe * 1.48f) para que el bajo/batería de la música
        // no produzca vibración molesta, dejando pasar únicamente explosiones, truenos y motores fuertes.
        val lfeThreshold = max(0.028f, musicFloorLfe * 1.48f)
        val cleanLfeL = max(0f, lfeRmsL - lfeThreshold)
        val cleanLfeR = max(0f, lfeRmsR - lfeThreshold)

        if (!isLikelyPureSpeech && (cleanLfeL > 0.008f || cleanLfeR > 0.008f)) {
            val rumbleL = (cleanLfeL * 7.5f + cleanLfeR * 2.0f).coerceIn(0.1f, 1f)
            val rumbleR = (cleanLfeR * 6.5f + cleanLfeL * 1.8f).coerceIn(0.1f, 1f)
            val ampL = mapAmplitude(rumbleL, minAmp = 45, maxAmp = 255)
            val ampR = mapAmplitude(rumbleR, minAmp = 40, maxAmp = 245)
            onCinemaHapticEvent(CinemaHapticEffectType.HEAVY_RUMBLE, ampL, ampR, 50L)
            return
        }

        // =========================================================================
        // 4. DETECTOR DE TEXTURAS AMBIENTALES: LLUVIA Y VIENTO
        // =========================================================================
        // La lluvia tiene alta tasa de cruces por cero (ruido de gotas en alta frecuencia) y micro-picos,
        // mientras que la voz tiene baja tasa de cruces por cero y energía concentrada en formantes vocales.
        val isRainSignature = !isLikelyPureSpeech &&
            zcr > 0.21f &&
            crispRms > 0.007f &&
            crispCrest > 2.15f &&
            vocalRms < crispRms * 2.4f

        rainConfidence = if (isRainSignature) {
            (rainConfidence + 0.35f).coerceAtMost(1.0f)
        } else {
            (rainConfidence - 0.22f).coerceAtLeast(0f)
        }

        if (rainConfidence >= 0.45f) {
            rainAlternateToggle = !rainAlternateToggle
            val dropIntensity = ((crispRms * 12f).coerceIn(0.12f, 0.65f)) * rainConfidence
            val baseDropAmp = mapAmplitude(dropIntensity, minAmp = 18, maxAmp = 62)
            val ampL = if (rainAlternateToggle) baseDropAmp else (baseDropAmp * 0.45f).toInt()
            val ampR = if (!rainAlternateToggle) baseDropAmp else (baseDropAmp * 0.45f).toInt()
            onCinemaHapticEvent(CinemaHapticEffectType.RAIN_DROPLETS, ampL, ampR, 14L)
            return
        }

        // El viento / brisa fuerte es un murmullo continuo de banda ancha (foley + aire) con baja cresta (suave, sin picos vocales)
        val isWindSignature = !isLikelyPureSpeech &&
            zcr in 0.11f..0.30f &&
            foleyRms in 0.010f..0.095f &&
            foleyCrest < 2.25f &&
            vocalRms < foleyRms * 1.15f

        windConfidence = if (isWindSignature) {
            (windConfidence + 0.28f).coerceAtMost(1.0f)
        } else {
            (windConfidence - 0.20f).coerceAtLeast(0f)
        }

        if (windConfidence >= 0.50f) {
            val windL = (foleyRmsL * 5.5f * windConfidence).coerceIn(0.08f, 0.50f)
            val windR = (foleyRmsR * 5.5f * windConfidence).coerceIn(0.08f, 0.50f)
            val ampL = mapAmplitude(windL, minAmp = 16, maxAmp = 52)
            val ampR = mapAmplitude(windR, minAmp = 16, maxAmp = 52)
            onCinemaHapticEvent(CinemaHapticEffectType.WIND_SWEEP, ampL, ampR, 46L)
            return
        }

        // Silencio háptico (diálogo, narrador o música suave sin impactos)
        onCinemaHapticEvent(CinemaHapticEffectType.NONE, 0, 0, 0L)
    }

    private fun mapAmplitude(normalized: Float, minAmp: Int, maxAmp: Int): Int {
        if (normalized <= 0.02f) return 0
        val clamped = normalized.coerceIn(0f, 1f)
        val curved = clamped.pow(0.70f)
        return (minAmp + (curved * (maxAmp - minAmp)).toInt()).coerceIn(minAmp, maxAmp)
    }

    override fun queueEndOfStream() {
        inputEnded = true
        onPlaybackFlushed()
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean {
        return inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER
    }

    @Deprecated("Deprecated in Media3 AudioProcessor")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        resetFilterState()
        onPlaybackFlushed()
    }

    override fun reset() {
        @Suppress("DEPRECATION")
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    private fun resetFilterState() {
        lfe1L = 0f
        lfe2L = 0f
        lfe1R = 0f
        lfe2R = 0f
        lp130L = 0f
        lp780L = 0f
        lp130R = 0f
        lp780R = 0f
        lp2600Mono = 0f
        prevMonoSample = 0f
        accLfeSqL = 0f
        accLfeSqR = 0f
        accFoleySqL = 0f
        accFoleySqR = 0f
        accVocalSq = 0f
        accCrispSqL = 0f
        accCrispSqR = 0f
        peakFoleyL = 0f
        peakFoleyR = 0f
        peakCrispL = 0f
        peakCrispR = 0f
        zeroCrossings = 0
        accSamples = 0
        musicFloorLfe = 0.015f
        slowFoleyEnv = 0.012f
        slowCrispEnv = 0.008f
        recentSpeechEnv = 0.02f
        rainConfidence = 0f
        windConfidence = 0f
    }
}
