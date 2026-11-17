package com.johang.audiocinemateca.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.johang.audiocinemateca.data.repository.GeminiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedExpressiveAudio(
    val file: File,
    val generation: Long,
    val fallbackPlainText: String
)

/**
 * Coordina la lectura de los mensajes de Aura alternando entre la voz expresiva nativa de
 * Google Gemini (cuando el usuario la activa y tiene Clave API de Gemini) y el motor TTS
 * local del dispositivo como respaldo automático. Soporta reproducción, pausa y reanudación
 * por mensaje individual usando el mismo clip de audio cacheado.
 */
@Singleton
class AuraVoicePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geminiRepository: GeminiRepository,
    private val ttsManager: TtsManager
) {
    companion object {
        private const val TAG = "AuraVoicePlayer"
        private const val CACHE_DIR_NAME = "aura_tts"
        private const val MAX_CACHED_FILES = 30
        private const val MAX_GEMINI_TTS_CHARS = 2400

        private val VOICE_STYLE_DIRECTIVE_REGEX = Regex("""\[ESTILO_VOZ\s*:\s*([^\]\r\n]+)\]""", RegexOption.IGNORE_CASE)
        private val PAUSE_VOCAL_TAGS_REGEX = Regex("""<\s*(short pause|long pause)\s*>""", RegexOption.IGNORE_CASE)
        private val VOCAL_TAGS_REGEX = Regex(
            """<\s*(laugh|chuckle|sigh|gasp|breath|cough|throat-clearing|short pause|long pause)\s*>""",
            RegexOption.IGNORE_CASE
        )

        fun extractVoiceStyleDirective(rawText: String): Pair<String?, String> {
            val match = VOICE_STYLE_DIRECTIVE_REGEX.find(rawText)
            val style = match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            val cleaned = VOICE_STYLE_DIRECTIVE_REGEX.replace(rawText, "").trim()
            return style to cleaned
        }

        fun stripExpressiveVoiceTags(rawText: String): String {
            return VOICE_STYLE_DIRECTIVE_REGEX.replace(rawText, "")
                .replace(PAUSE_VOCAL_TAGS_REGEX, " ")
                .replace(VOCAL_TAGS_REGEX, "")
                .replace(Regex("""[ \t]+([,.;:!?])"""), "$1")
                .replace(Regex("""[ \t]{2,}"""), " ")
                .replace(Regex("""\n[ \t]+"""), "\n")
                .replace(Regex("""\n{3,}"""), "\n\n")
                .trim()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var activeSynthesisJob: Job? = null

    @Volatile
    private var activePlayGeneration: Long = 0L

    @Volatile
    private var activeDeviceUtteranceId: String? = null

    private val _activeMessageId = MutableStateFlow<String?>(null)
    val activeMessageId: StateFlow<String?> = _activeMessageId.asStateFlow()

    private val _isSpeakingExpressive = MutableStateFlow(false)
    val isSpeakingExpressive: StateFlow<Boolean> = _isSpeakingExpressive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _isSynthesizing = MutableStateFlow(false)
    val isSynthesizing: StateFlow<Boolean> = _isSynthesizing.asStateFlow()

    private val _isDeviceTtsSpeaking = MutableStateFlow(false)
    val isDeviceTtsSpeaking: StateFlow<Boolean> = _isDeviceTtsSpeaking.asStateFlow()

    var onUtteranceCompleted: ((String?) -> Unit)? = null

    init {
        ttsManager.onExternalStopRequested = {
            stopAllActiveTracking()
        }
        ttsManager.onSpeechStarted = { utteranceId ->
            if (utteranceId != null && utteranceId == activeDeviceUtteranceId) {
                _isDeviceTtsSpeaking.value = true
            }
        }
        ttsManager.onSpeechFinished = { utteranceId ->
            if (utteranceId != null && utteranceId == activeDeviceUtteranceId) {
                activeDeviceUtteranceId = null
                _isDeviceTtsSpeaking.value = false
                if (!_isSpeakingExpressive.value && !_isPaused.value && !_isSynthesizing.value) {
                    _activeMessageId.value = null
                }
            }
            onUtteranceCompleted?.invoke(utteranceId)
        }
        ttsManager.onSpeechError = { utteranceId ->
            if (utteranceId != null && utteranceId == activeDeviceUtteranceId) {
                activeDeviceUtteranceId = null
                _isDeviceTtsSpeaking.value = false
                if (!_isSpeakingExpressive.value && !_isPaused.value && !_isSynthesizing.value) {
                    _activeMessageId.value = null
                }
            }
        }
    }

    /**
     * Alterna entre Reproducir / Pausar / Reanudar para un mensaje concreto de Aura
     * manteniendo exactamente el mismo clip de audio cargado.
     */
    fun togglePlayPauseMessage(
        messageId: String,
        expressiveText: String,
        fallbackPlainText: String,
        styleHint: String? = null,
        scope: CoroutineScope
    ) {
        if (_activeMessageId.value == messageId) {
            val mp = mediaPlayer
            when {
                mp != null && _isSpeakingExpressive.value -> {
                    runCatching { mp.pause() }
                    _isSpeakingExpressive.value = false
                    _isPaused.value = true
                    return
                }
                mp != null && _isPaused.value -> {
                    ttsManager.stop(notifyExternal = false)
                    activeDeviceUtteranceId = null
                    _isDeviceTtsSpeaking.value = false
                    runCatching { mp.start() }
                        .onSuccess {
                            _isPaused.value = false
                            _isSpeakingExpressive.value = true
                        }
                        .onFailure {
                            speak(
                                text = expressiveText,
                                fallbackPlainText = fallbackPlainText,
                                styleHint = styleHint,
                                messageId = messageId,
                                interrupt = true,
                                scope = scope
                            )
                        }
                    return
                }
                _isSynthesizing.value || _isDeviceTtsSpeaking.value -> {
                    stop()
                    return
                }
            }
        }

        speak(
            text = expressiveText,
            fallbackPlainText = fallbackPlainText,
            styleHint = styleHint,
            messageId = messageId,
            interrupt = true,
            utteranceId = null,
            scope = scope
        )
    }

    /**
     * Sintetiza y deja listo el audio expresivo antes de mostrar/reproducir la respuesta,
     * permitiendo que texto y voz salgan perfectamente sincronizados.
     */
    suspend fun prepareExpressiveAudio(
        text: String,
        fallbackPlainText: String,
        styleHint: String? = null,
        messageId: String? = null,
        interrupt: Boolean = true
    ): PreparedExpressiveAudio? {
        if (!geminiRepository.isExpressiveGeminiVoiceActive()) return null

        val speechText = cleanTextForSpeech(
            raw = text,
            preserve38Tags = geminiRepository.supportsExpressive38TtsTags()
        ).take(MAX_GEMINI_TTS_CHARS)
        if (speechText.isBlank()) return null

        if (interrupt) {
            withContext(Dispatchers.Main) {
                stopAllActiveTracking()
                ttsManager.stop(notifyExternal = false)
            }
        }

        val generation = ++activePlayGeneration
        val modelId = geminiRepository.getSelectedGeminiTtsModel()
        val voiceName = geminiRepository.getSelectedGeminiTtsVoice()

        _activeMessageId.value = messageId
        _isSynthesizing.value = true
        return try {
            val file = resolveAudioFile(
                text = speechText,
                modelId = modelId,
                voiceName = voiceName,
                styleHint = styleHint
            )
            _isSynthesizing.value = false
            if (generation != activePlayGeneration || file == null || !file.exists() || file.length() <= 0L) {
                if (generation == activePlayGeneration && _activeMessageId.value == messageId) {
                    _activeMessageId.value = null
                }
                null
            } else {
                PreparedExpressiveAudio(
                    file = file,
                    generation = generation,
                    fallbackPlainText = fallbackPlainText
                )
            }
        } catch (ce: CancellationException) {
            _isSynthesizing.value = false
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Fallo preparando voz expresiva de Aura, se usará respaldo local: ${e.message}")
            _isSynthesizing.value = false
            null
        }
    }

    fun playPreparedAudio(
        prepared: PreparedExpressiveAudio,
        messageId: String? = null,
        utteranceId: String? = null
    ) {
        if (prepared.generation != activePlayGeneration) return
        ttsManager.stop(notifyExternal = false)
        activeDeviceUtteranceId = null
        _isDeviceTtsSpeaking.value = false
        _activeMessageId.value = messageId
        _isPaused.value = false
        playAudioFile(
            file = prepared.file,
            generation = prepared.generation,
            messageId = messageId,
            utteranceId = utteranceId,
            fallbackText = prepared.fallbackPlainText
        )
    }

    fun speak(
        text: String,
        fallbackPlainText: String = stripExpressiveVoiceTags(text),
        styleHint: String? = null,
        messageId: String? = null,
        interrupt: Boolean = true,
        utteranceId: String? = null,
        scope: CoroutineScope
    ) {
        if (!geminiRepository.isExpressiveGeminiVoiceActive()) {
            if (interrupt) stopAllActiveTracking()
            speakViaDeviceTts(fallbackPlainText, messageId, interrupt, utteranceId)
            return
        }

        val speechText = cleanTextForSpeech(
            raw = text,
            preserve38Tags = geminiRepository.supportsExpressive38TtsTags()
        ).take(MAX_GEMINI_TTS_CHARS)
        if (speechText.isBlank()) {
            speakViaDeviceTts(fallbackPlainText, messageId, interrupt, utteranceId)
            return
        }

        if (interrupt) {
            stopAllActiveTracking()
            ttsManager.stop(notifyExternal = false)
        }

        val generation = ++activePlayGeneration
        val modelId = geminiRepository.getSelectedGeminiTtsModel()
        val voiceName = geminiRepository.getSelectedGeminiTtsVoice()

        _activeMessageId.value = messageId
        activeSynthesisJob = scope.launch {
            _isSynthesizing.value = true
            try {
                val audioFile = resolveAudioFile(
                    text = speechText,
                    modelId = modelId,
                    voiceName = voiceName,
                    styleHint = styleHint
                )

                if (generation != activePlayGeneration) return@launch

                _isSynthesizing.value = false
                if (audioFile != null && audioFile.exists() && audioFile.length() > 0L) {
                    playAudioFile(
                        file = audioFile,
                        generation = generation,
                        messageId = messageId,
                        utteranceId = utteranceId,
                        fallbackText = fallbackPlainText
                    )
                } else {
                    fallbackToDeviceTts(fallbackPlainText, messageId, utteranceId, reason = "Audio de Gemini vacío")
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (generation == activePlayGeneration) {
                    _isSynthesizing.value = false
                    fallbackToDeviceTts(fallbackPlainText, messageId, utteranceId, reason = e.message ?: "Error al sintetizar voz")
                }
            }
        }
    }

    suspend fun previewExpressiveVoice(
        modelId: String?,
        voiceName: String
    ): Result<String> {
        stop()
        val generation = ++activePlayGeneration
        _isSynthesizing.value = true
        val resolvedModel = modelId?.trim()?.ifBlank { null } ?: geminiRepository.getSelectedGeminiTtsModel()
        val use38Sample = geminiRepository.is38PlusTtsModel(resolvedModel)
        val sampleText = if (use38Sample) {
            "¡Hola! <chuckle> Soy Aura. <short pause> Qué alegría acompañarte hoy en la Audiocinemateca para descubrir grandes historias juntos."
        } else {
            "¡Hola! Soy Aura. Qué alegría acompañarte hoy en la Audiocinemateca para descubrir grandes historias juntos."
        }
        val sampleStyle = "Cálida, dulce, alegre y expresiva en español"

        val synthResult = geminiRepository.synthesizeGeminiSpeech(
            text = sampleText,
            modelOverride = resolvedModel,
            voiceOverride = voiceName,
            styleOverride = sampleStyle
        )

        if (generation != activePlayGeneration) {
            _isSynthesizing.value = false
            return Result.failure(CancellationException("Vista previa cancelada."))
        }

        _isSynthesizing.value = false
        return synthResult.fold(
            onSuccess = { speechAudio ->
                val cacheFile = withContext(Dispatchers.IO) {
                    writeCachedAudioFile(
                        cacheKey = buildCacheKey(sampleText, speechAudio.modelUsed, speechAudio.voiceUsed, sampleStyle),
                        extension = speechAudio.fileExtension,
                        bytes = speechAudio.bytes
                    )
                }
                withContext(Dispatchers.Main) {
                    playAudioFile(
                        file = cacheFile,
                        generation = generation,
                        messageId = null,
                        utteranceId = null,
                        fallbackText = null
                    )
                }
                Result.success("${speechAudio.voiceUsed} • ${speechAudio.modelUsed}")
            },
            onFailure = { err ->
                Result.failure(err)
            }
        )
    }

    fun speakViaDeviceTts(
        plainText: String,
        messageId: String?,
        interrupt: Boolean = true,
        utteranceId: String? = null
    ) {
        val clean = stripExpressiveVoiceTags(plainText)
        val resolvedUttId = utteranceId ?: "AURA_DEV_TTS_${System.currentTimeMillis()}"
        activeDeviceUtteranceId = resolvedUttId
        _activeMessageId.value = messageId
        _isPaused.value = false
        _isDeviceTtsSpeaking.value = true
        ttsManager.speak(clean, interrupt = interrupt, utteranceId = resolvedUttId)
    }

    fun stop() {
        stopAllActiveTracking()
        ttsManager.stop(notifyExternal = false)
    }

    private fun stopAllActiveTracking() {
        activePlayGeneration++
        activeSynthesisJob?.cancel()
        activeSynthesisJob = null
        activeDeviceUtteranceId = null
        _isSynthesizing.value = false
        _isSpeakingExpressive.value = false
        _isPaused.value = false
        _isDeviceTtsSpeaking.value = false
        _activeMessageId.value = null

        val releasePlayer = {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) stop()
                    reset()
                    release()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error liberando MediaPlayer de Aura: ${e.message}")
            } finally {
                mediaPlayer = null
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releasePlayer()
        } else {
            mainHandler.post { releasePlayer() }
        }
    }

    private suspend fun resolveAudioFile(
        text: String,
        modelId: String,
        voiceName: String,
        styleHint: String?
    ): File? = withContext(Dispatchers.IO) {
        val key = buildCacheKey(text, modelId, voiceName, styleHint)
        val cached = findCachedAudioFile(key)
        if (cached != null) return@withContext cached

        val result = geminiRepository.synthesizeGeminiSpeech(
            text = text,
            modelOverride = modelId,
            voiceOverride = voiceName,
            styleOverride = styleHint
        )
        val audio = result.getOrElse { err ->
            throw err
        }
        writeCachedAudioFile(
            cacheKey = key,
            extension = audio.fileExtension,
            bytes = audio.bytes
        )
    }

    private fun playAudioFile(
        file: File,
        generation: Long,
        messageId: String?,
        utteranceId: String?,
        fallbackText: String?
    ) {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    if (generation != activePlayGeneration) {
                        runCatching { mp.release() }
                        return@setOnPreparedListener
                    }
                    _isPaused.value = false
                    _isSpeakingExpressive.value = true
                    _activeMessageId.value = messageId
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    _isSpeakingExpressive.value = false
                    _isPaused.value = false
                    if (generation == activePlayGeneration) {
                        if (_activeMessageId.value == messageId) {
                            _activeMessageId.value = null
                        }
                        onUtteranceCompleted?.invoke(utteranceId)
                    }
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error ($what/$extra) reproduciendo voz de Aura")
                    runCatching { mp.release() }
                    if (mediaPlayer === mp) mediaPlayer = null
                    _isSpeakingExpressive.value = false
                    _isPaused.value = false
                    if (generation == activePlayGeneration && !fallbackText.isNullOrBlank()) {
                        fallbackToDeviceTts(fallbackText, messageId, utteranceId, reason = "Error de reproducción ($what)")
                    } else if (_activeMessageId.value == messageId) {
                        _activeMessageId.value = null
                    }
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo inicializar MediaPlayer para voz de Aura", e)
            _isSpeakingExpressive.value = false
            _isPaused.value = false
            if (!fallbackText.isNullOrBlank()) {
                fallbackToDeviceTts(fallbackText, messageId, utteranceId, reason = e.message ?: "Error de audio")
            } else if (_activeMessageId.value == messageId) {
                _activeMessageId.value = null
            }
        }
    }

    private fun fallbackToDeviceTts(text: String, messageId: String?, utteranceId: String?, reason: String) {
        Log.w(TAG, "Respaldo automático al TTS del dispositivo para Aura ($reason)")
        speakViaDeviceTts(text, messageId = messageId, interrupt = true, utteranceId = utteranceId)
    }

    private fun getCacheDir(): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun findCachedAudioFile(cacheKey: String): File? {
        val dir = getCacheDir()
        return listOf("wav", "mp3", "ogg")
            .map { File(dir, "$cacheKey.$it") }
            .firstOrNull { it.exists() && it.length() > 44L }
    }

    private fun writeCachedAudioFile(cacheKey: String, extension: String, bytes: ByteArray): File {
        val dir = getCacheDir()
        trimCacheIfNeeded(dir)
        val target = File(dir, "$cacheKey.$extension")
        target.writeBytes(bytes)
        return target
    }

    private fun trimCacheIfNeeded(dir: File) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        if (files.size >= MAX_CACHED_FILES) {
            files.take(files.size - MAX_CACHED_FILES + 1).forEach { runCatching { it.delete() } }
        }
    }

    private fun buildCacheKey(text: String, modelId: String, voiceName: String, styleHint: String?): String {
        val raw = "$modelId|$voiceName|${styleHint.orEmpty()}|$text"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun cleanTextForSpeech(raw: String, preserve38Tags: Boolean): String {
        val noStyleDirective = VOICE_STYLE_DIRECTIVE_REGEX.replace(raw, "")
        val markdownClean = noStyleDirective
            .replace("[[", "")
            .replace("]]", "")
            .replace(Regex("""\[([^\]]+)]\((https?://[^)]+)\)"""), "$1")
            .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^#{1,6}\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""^\s*[-*•]\s+""", RegexOption.MULTILINE), "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()

        return if (preserve38Tags) {
            markdownClean
        } else {
            stripExpressiveVoiceTags(markdownClean)
        }
    }
}
