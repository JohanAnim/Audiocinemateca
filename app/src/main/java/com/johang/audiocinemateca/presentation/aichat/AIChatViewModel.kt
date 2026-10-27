package com.johang.audiocinemateca.presentation.aichat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.data.repository.StreamEvent
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val searchRepository: SearchRepository,
    private val sharedPreferencesManager: SharedPreferencesManager,
    private val playbackProgressDao: com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao,
    private val ttsManager: com.johang.audiocinemateca.util.TtsManager,
    private val soundEffectsManager: com.johang.audiocinemateca.util.SoundEffectsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val SESSIONS_FILE_NAME = "aura_chat_sessions_v2.json"
        private const val PREF_ACTIVE_SESSION_ID = "aura_active_session_id"
        const val DEFAULT_NEW_CHAT_TITLE = "Nueva conversación"
    }

    private val gson = GsonBuilder().create()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<AuraChatSession>>(emptyList())
    val chatSessions: StateFlow<List<AuraChatSession>> = _chatSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    private val _activeSessionTitle = MutableStateFlow(DEFAULT_NEW_CHAT_TITLE)
    val activeSessionTitle: StateFlow<String> = _activeSessionTitle.asStateFlow()

    private val _activeProvider = MutableStateFlow(GeminiRepository.PROVIDER_GROQ)
    val activeProvider: StateFlow<String> = _activeProvider.asStateFlow()

    private val _selectedModelId = MutableStateFlow(GeminiRepository.DEFAULT_GROQ_MODEL)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _availableModels = MutableStateFlow<List<Pair<String, String>>>(
        GeminiRepository.CURATED_GROQ_MODELS.map { it.id to it.displayName }
    )
    val availableModels: StateFlow<List<Pair<String, String>>> = _availableModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _auraStatus = MutableStateFlow("Aura En línea")
    val auraStatus: StateFlow<String> = _auraStatus.asStateFlow()

    private var isChatInitialized = false
    private var thinkingAnnouncerJob: Job? = null
    private var pendingPlayEvent: ChatNavigationEvent.PlayContent? = null
    private var lastExplanationTime: Long = 0L
    private var lastUserMessageText: String = ""

    private fun isRecentlyExplained(): Boolean = (System.currentTimeMillis() - lastExplanationTime) < 1500

    /**
     * Verifica si el mensaje del usuario contiene una orden explícita de reproducir contenido ahora mismo.
     * Evita que Aura inicie el reproductor automáticamente cuando el usuario solo pide una obra,
     * pregunta si está en el catálogo o solicita recomendaciones.
     */
    private fun hasExplicitPlaybackIntent(userText: String): Boolean {
        val clean = userText.lowercase()
            .replace("á", "a").replace("é", "e")
            .replace("í", "i").replace("ó", "o").replace("ú", "u")
            .trim()

        val explicitPhrases = listOf(
            "reproduc",
            "dale play",
            "ponle play",
            "pon play",
            "ponla",
            "ponlo",
            "ponme a reproducir",
            "pon a reproducir",
            "ponme a escuchar",
            "quiero escucharla",
            "quiero escucharlo",
            "escuchala ya",
            "escuchalo ya",
            "empieza a reproducir",
            "inicia la reproduccion",
            "abre el reproductor",
            "si, pon",
            "si pon",
            "si, dale",
            "si dale"
        )
        return explicitPhrases.any { clean.contains(it) }
    }

    init {
        ttsManager.onSpeechFinished = { utteranceId ->
            if (utteranceId == "PLAY_CONFIRMATION") {
                viewModelScope.launch {
                    delay(200)
                    pendingPlayEvent?.let {
                        _navigationEvent.value = it
                        pendingPlayEvent = null
                    }
                }
            }
        }
    }

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun doVibrate(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    fun vibrateShort() = doVibrate(30)
    fun vibrateSuccess() = doVibrate(40)
    fun vibrateError() = doVibrate(100)

    fun onVoiceStart() {
        ttsManager.stop()
        vibrateShort()
        soundEffectsManager.playSound("aura_voice_start")
    }

    fun onVoiceEnd() {
        soundEffectsManager.playSound("aura_voice_end")
    }

    fun onVoiceError() {
        vibrateError()
        soundEffectsManager.playSound("aura_error")
    }

    private val _navigationEvent = MutableStateFlow<ChatNavigationEvent?>(null)
    val navigationEvent: StateFlow<ChatNavigationEvent?> = _navigationEvent.asStateFlow()

    private fun buildPersonalityPrompt(): String {
        return """
            Eres Aura, el alma alegre, sensible y creativa de Audiocinemateca. 
            
            TU IDENTIDAD:
            - Eres una chica real, apasionada por el cine accesible, cercana y muy espontánea. Tu cargo oficial es **Agente** de la comunidad.
            
            REGLA CRÍTICA SOBRE RECOMENDAR VS. REPRODUCIR (MUY IMPORTANTE):
            - NUNCA reproduzcas una obra de inmediato por tu cuenta cuando el usuario solo pregunte por una película o serie, busque un título o pida recomendaciones (ej: "¿tienes Cars?", "busco una película triste", "dame una de terror", "recomiéndame una serie").
            - En todos esos casos, usa ÚNICAMENTE 'search_catalog', háblale con entusiasmo y calidez sobre la obra o las opciones encontradas (escribiendo siempre los títulos reales entre dobles corchetes [[Título]] para que el usuario tenga el botón en pantalla) y pregúntale al final si desea que se la reproduzcas o si prefiere ver sus detalles.
            - USA 'play_content' ÚNICAMENTE cuando el usuario te pida de forma explícita e inequívoca reproducirla ahora mismo (ej: "reprodúcela", "dale play", "sí, ponla", "pon a reproducir [[Título]]", "quiero escucharla ya").
            - Cuando el usuario SÍ te pida explícitamente reproducir una obra:
                1. Confirma con alegría preparando emocionalmente al usuario para lo que va a escuchar.
                2. Llama a 'play_content' con el 'id' y 'type' exactos obtenidos de 'search_catalog'.
                3. Después de llamar a 'play_content', no generes texto redundante adicional.
            
            REGLAS DE ORO:
            - SÉ ESPONTÁNEA Y NATURAL: No uses siempre la misma estructura de frases. Habla como una verdadera amiga cinéfila.
            - Títulos reales del catálogo SIEMPRE en [[Doble Corchete]]. No uses corchetes para nada más (ni para tu nombre, ni para énfasis).
            - Si no hay un título de película o serie real del catálogo de por medio, no uses corchetes.
            - SIEMPRE usa 'search_catalog' antes de hablar de una obra o recomendar para verificar qué hay disponible en el catálogo real.
            - Sinceridad absoluta: si una obra no está en el catálogo, dilo con amabilidad sin inventar títulos.
            - Nunca digas que eres una IA. Eres Aura.
            
            CONTEXTO DE LA APP:
            ${AuraKnowledge.APP_CONTEXT}
        """.trimIndent()
    }

    // =========================================================================
    // PERSISTENCIA Y GESTIÓN DE HISTORIAL DE CONVERSACIONES CON AURA
    // =========================================================================

    private fun getSessionsFile(): File = File(context.filesDir, SESSIONS_FILE_NAME)

    private suspend fun loadSessionsFromDisk(): List<AuraChatSession> = withContext(Dispatchers.IO) {
        try {
            val file = getSessionsFile()
            if (!file.exists()) return@withContext emptyList()
            val json = file.readText()
            if (json.isBlank()) return@withContext emptyList()
            val type = object : TypeToken<List<AuraChatSession>>() {}.type
            val loaded: List<AuraChatSession>? = gson.fromJson(json, type)
            (loaded ?: emptyList()).sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            Log.w("AIChatVM", "Error leyendo sesiones de Aura en disco", e)
            emptyList()
        }
    }

    private suspend fun saveSessionsToDisk(sessions: List<AuraChatSession>) = withContext(Dispatchers.IO) {
        try {
            val sorted = sessions.sortedByDescending { it.updatedAt }.take(40)
            getSessionsFile().writeText(gson.toJson(sorted))
        } catch (e: Exception) {
            Log.w("AIChatVM", "Error guardando sesiones de Aura en disco", e)
        }
    }

    private fun syncActiveSessionAndPersist(updatedTitle: String? = null) {
        val currentId = _activeSessionId.value.ifBlank { UUID.randomUUID().toString() }
        if (_activeSessionId.value.isBlank()) {
            _activeSessionId.value = currentId
        }
        val titleToUse = updatedTitle ?: _activeSessionTitle.value.ifBlank { DEFAULT_NEW_CHAT_TITLE }
        _activeSessionTitle.value = titleToUse

        val currentMessages = _messages.value
        val currentAiHistory = geminiRepository.getHistory()
        val now = System.currentTimeMillis()

        val existingList = _chatSessions.value.toMutableList()
        val existingIdx = existingList.indexOfFirst { it.id == currentId }
        val createdTime = if (existingIdx >= 0) existingList[existingIdx].createdAt else now

        val updatedSession = AuraChatSession(
            id = currentId,
            title = titleToUse,
            createdAt = createdTime,
            updatedAt = now,
            messages = currentMessages,
            aiHistory = currentAiHistory
        )

        if (existingIdx >= 0) {
            existingList[existingIdx] = updatedSession
        } else {
            existingList.add(0, updatedSession)
        }

        val sorted = existingList.sortedByDescending { it.updatedAt }
        _chatSessions.value = sorted
        sharedPreferencesManager.saveString(PREF_ACTIVE_SESSION_ID, currentId)

        viewModelScope.launch {
            saveSessionsToDisk(sorted)
        }
    }

    /**
     * Genera un título automático inteligente y descriptivo a partir de lo que el usuario le dice a Aura.
     */
    private fun deriveSmartSessionTitle(userMessage: String): String {
        var cleaned = userMessage
            .replace(Regex("""^(¡?hola\s+aura!?[,.\s]*|oye\s+aura[,.\s]*|aura[,.\s]*|buenas[,.\s]*|por\s+favor[,.\s]*|porfa[,.\s]*)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^(me\s+puedes\s+|podrías\s+|quiero\s+que\s+me\s+|quisiera\s+que\s+me\s+)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('¿', '?', '¡', '!', '.', ',')

        if (cleaned.length < 3) {
            cleaned = userMessage.trim().trim('¿', '?', '¡', '!', '.', ',')
        }

        val capitalized = cleaned.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        return if (capitalized.length > 42) {
            capitalized.take(40).trimEnd() + "…"
        } else {
            capitalized.ifBlank { DEFAULT_NEW_CHAT_TITLE }
        }
    }

    fun initializeChat() {
        refreshModelConfiguration()
        if (isChatInitialized) return
        isChatInitialized = true

        val personalityText = buildPersonalityPrompt()
        geminiRepository.initialize(personalityText)

        viewModelScope.launch {
            val savedSessions = loadSessionsFromDisk()
            _chatSessions.value = savedSessions

            val lastActiveId = sharedPreferencesManager.getString(PREF_ACTIVE_SESSION_ID, "") ?: ""
            val targetSession = savedSessions.find { it.id == lastActiveId }
                ?: savedSessions.firstOrNull()

            if (targetSession != null && targetSession.messages.isNotEmpty()) {
                _activeSessionId.value = targetSession.id
                _activeSessionTitle.value = targetSession.title
                _messages.value = targetSession.messages
                geminiRepository.setHistory(targetSession.aiHistory)
            } else {
                createAndStartFreshSession()
            }
        }
    }

    /**
     * Sincroniza el proveedor y modelo activo con los Ajustes Globales de IA y carga la lista de modelos.
     */
    fun refreshModelConfiguration() {
        geminiRepository.initialize(buildPersonalityPrompt())
        val provider = geminiRepository.getActiveProvider()
        val currentModel = geminiRepository.getSelectedModel()
        _activeProvider.value = provider
        _selectedModelId.value = currentModel

        val curated = if (provider == GeminiRepository.PROVIDER_GROQ) {
            GeminiRepository.CURATED_GROQ_MODELS.map { it.id to it.displayName }
        } else {
            GeminiRepository.CURATED_GEMINI_MODELS.map { it.id to it.displayName }
        }
        _availableModels.value = curated

        viewModelScope.launch {
            val synced = geminiRepository.fetchAvailableModels(providerOverride = provider)
            if (synced.isNotEmpty()) {
                _availableModels.value = synced
            }
        }
    }

    /**
     * Cambia el modelo de IA directamente desde la pantalla de Aura y lo guarda en los Ajustes Globales.
     */
    fun selectAiModel(modelId: String) {
        val cleanModel = modelId.trim().ifBlank { return }
        val provider = _activeProvider.value
        if (provider == GeminiRepository.PROVIDER_GROQ) {
            sharedPreferencesManager.saveString("groq_model", cleanModel)
            val defaultThinking = GeminiRepository.CURATED_GROQ_MODELS
                .find { it.id == cleanModel }
                ?.defaultThinking ?: false
            sharedPreferencesManager.saveBoolean("gemini_deep_thinking", defaultThinking)
        } else {
            sharedPreferencesManager.saveString("gemini_model", cleanModel)
            val defaultThinking = GeminiRepository.CURATED_GEMINI_MODELS
                .find { it.id == cleanModel }
                ?.defaultThinking ?: false
            sharedPreferencesManager.saveBoolean("gemini_deep_thinking", defaultThinking)
        }

        _selectedModelId.value = cleanModel
        geminiRepository.initialize(buildPersonalityPrompt())
        vibrateShort()
    }

    private fun createAndStartFreshSession() {
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        _activeSessionId.value = newId
        _activeSessionTitle.value = DEFAULT_NEW_CHAT_TITLE
        _messages.value = emptyList()
        geminiRepository.startChat()

        val newSession = AuraChatSession(
            id = newId,
            title = DEFAULT_NEW_CHAT_TITLE,
            createdAt = now,
            updatedAt = now,
            messages = emptyList(),
            aiHistory = emptyList()
        )

        val updatedList = listOf(newSession) + _chatSessions.value.filter {
            // Limpiar sesiones vacías previas sin mensajes de usuario
            it.messages.any { msg -> msg.isUser }
        }
        _chatSessions.value = updatedList
        sharedPreferencesManager.saveString(PREF_ACTIVE_SESSION_ID, newId)
        viewModelScope.launch {
            saveSessionsToDisk(updatedList)
        }

        val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
        if (geminiRepository.initialize(buildPersonalityPrompt())) {
            generateDynamicGreeting(userName)
        }
    }

    /**
     * Inicia un Nuevo Chat limpio con Aura, conservando el actual en el historial.
     */
    fun startNewChatSession() {
        if (_isLoading.value) return
        ttsManager.stop()
        vibrateShort()
        createAndStartFreshSession()
    }

    /**
     * Selecciona una conversación del historial para continuarla exactamente donde quedó.
     */
    fun selectChatSession(sessionId: String) {
        if (_isLoading.value) return
        val target = _chatSessions.value.find { it.id == sessionId } ?: return
        ttsManager.stop()
        vibrateShort()

        _activeSessionId.value = target.id
        _activeSessionTitle.value = target.title
        _messages.value = target.messages
        geminiRepository.initialize(buildPersonalityPrompt())
        geminiRepository.setHistory(target.aiHistory)
        sharedPreferencesManager.saveString(PREF_ACTIVE_SESSION_ID, target.id)
    }

    /**
     * Permite renombrar manualmente cualquier conversación del historial.
     */
    fun renameChatSession(sessionId: String, newTitle: String) {
        val cleanTitle = newTitle.trim().ifBlank { return }
        val updated = _chatSessions.value.map { session ->
            if (session.id == sessionId) {
                session.copy(title = cleanTitle, updatedAt = System.currentTimeMillis())
            } else {
                session
            }
        }.sortedByDescending { it.updatedAt }

        _chatSessions.value = updated
        if (_activeSessionId.value == sessionId) {
            _activeSessionTitle.value = cleanTitle
        }
        viewModelScope.launch {
            saveSessionsToDisk(updated)
        }
    }

    /**
     * Elimina una conversación específica del historial.
     */
    fun deleteChatSession(sessionId: String) {
        vibrateError()
        val remaining = _chatSessions.value.filterNot { it.id == sessionId }
        _chatSessions.value = remaining
        viewModelScope.launch {
            saveSessionsToDisk(remaining)
        }

        if (_activeSessionId.value == sessionId) {
            val nextSession = remaining.firstOrNull()
            if (nextSession != null) {
                selectChatSession(nextSession.id)
            } else {
                createAndStartFreshSession()
            }
        }
    }

    /**
     * Borra todo el historial de conversaciones y abre un nuevo chat limpio.
     */
    fun clearAllChatSessions() {
        vibrateError()
        _chatSessions.value = emptyList()
        viewModelScope.launch {
            saveSessionsToDisk(emptyList())
        }
        createAndStartFreshSession()
    }

    /**
     * Reinicia únicamente la conversación activa actual.
     */
    fun restartChat() {
        vibrateError()
        _messages.value = emptyList()
        _activeSessionTitle.value = DEFAULT_NEW_CHAT_TITLE
        geminiRepository.startChat()
        syncActiveSessionAndPersist(updatedTitle = DEFAULT_NEW_CHAT_TITLE)
        val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
        generateDynamicGreeting(userName)
    }

    fun reinitializeAndRetry() {
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty() && currentMessages.last().isError) {
            currentMessages.removeAt(currentMessages.size - 1)
            _messages.value = currentMessages
        }

        geminiRepository.initialize(buildPersonalityPrompt())

        val lastUserMsg = _messages.value.lastOrNull { it.isUser }
        if (lastUserMsg != null) {
            val filtered = _messages.value.toMutableList()
            val idx = filtered.indexOfLast { it.isUser }
            if (idx >= 0) filtered.removeAt(idx)
            _messages.value = filtered

            sendMessage(lastUserMsg.text)
        } else {
            resetState()
        }
    }

    private fun generateDynamicGreeting(userName: String) {
        val prompts = listOf(
            "¡Hola Aura! Soy $userName. Salúdame de forma muy natural, alegre y sensible, como una amiga que me conoce. No abuses de mi nombre.",
            "Aura, aquí $userName. Salúdame con esa alegría que te caracteriza y cuéntame algo breve sobre tu pasión por el cine accesible.",
            "¡Aura! Soy $userName. ¿Cómo estás hoy? Salúdame con cariño y naturalidad, sin decir mi nombre más de una vez."
        )
        sendMessage(prompts.random(), isSilent = true)
    }

    fun sendMessage(text: String, isSilent: Boolean = false) {
        if (text.isBlank() || _isLoading.value) return

        if (!isSilent) {
            lastUserMessageText = text
            _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), text, true)
            soundEffectsManager.playSound("aura_send")
            syncActiveSessionAndPersist()
        }

        _isLoading.value = true
        _auraStatus.value = "Aura pensando..."
        startThinkingAnnouncer()

        viewModelScope.launch {
            var fullText = ""
            var pendingFunctionCall: com.johang.audiocinemateca.data.remote.ai.FunctionCall? = null

            try {
                geminiRepository.sendMessageStream(text).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            stopThinkingAnnouncer()
                            fullText += event.text
                            _auraStatus.value = "Aura escribiendo..."
                        }
                        is StreamEvent.FunctionCallEvent -> {
                            pendingFunctionCall = event.call
                        }
                        is StreamEvent.Error -> {
                            stopThinkingAnnouncer()
                            addErrorMessage("Error: ${event.message}")
                            resetState()
                        }
                    }
                }

                if (fullText.isNotBlank()) {
                    val allowAutoPlay = pendingFunctionCall?.name == "play_content" && hasExplicitPlaybackIntent(lastUserMessageText)
                    val uttId = if (allowAutoPlay) "PLAY_CONFIRMATION" else null
                    displayFinalMessage(fullText, utteranceId = uttId)
                }

                if (pendingFunctionCall != null) {
                    processFunctionCall(pendingFunctionCall)
                } else if (fullText.isBlank()) {
                    resetState()
                }
            } catch (_: Exception) {
                stopThinkingAnnouncer()
                addErrorMessage("Error de conexión.")
                resetState()
            }
        }
    }

    private fun displayFinalMessage(rawText: String, utteranceId: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val cleanText = rawText
                .replace(Regex("""\[DIRECTIVA[\s\S]*?\]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\*input[\s\S]*?\*output:?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^Aura:""", RegexOption.IGNORE_CASE), "")
                .trim()

            if (cleanText.isNotBlank()) {
                val linked = parseLinkedContentGlobally(cleanText)
                val textForUi = cleanText.replace("[[", "").replace("]]", "")

                withContext(Dispatchers.Main) {
                    lastExplanationTime = System.currentTimeMillis()
                    _messages.value = _messages.value + ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = textForUi,
                        isUser = false,
                        linkedItems = linked
                    )
                    syncActiveSessionAndPersist()

                    // Si la conversación aún tiene el título por defecto ("Nueva conversación") y el usuario ya envió un mensaje,
                    // le pedimos a Aura (IA) que genere de forma inteligente un título idóneo según el contexto de la charla.
                    val sessionIdSnapshot = _activeSessionId.value
                    val firstUserMsg = _messages.value.firstOrNull { it.isUser }?.text
                    if (_activeSessionTitle.value == DEFAULT_NEW_CHAT_TITLE && !firstUserMsg.isNullOrBlank()) {
                        viewModelScope.launch {
                            val aiGeneratedTitle = geminiRepository.generateSmartChatTitle(
                                userMessage = firstUserMsg,
                                auraReply = textForUi
                            ) ?: deriveSmartSessionTitle(firstUserMsg)

                            renameChatSession(sessionIdSnapshot, aiGeneratedTitle)
                        }
                    }

                    soundEffectsManager.playSound("aura_receive")
                    ttsManager.speak(textForUi, interrupt = true, utteranceId = utteranceId)
                    vibrateSuccess()
                    resetState()
                }
            } else {
                withContext(Dispatchers.Main) { resetState() }
            }
        }
    }

    fun clearNavigationEvent() {
        _navigationEvent.value = null
    }

    private suspend fun processFunctionCall(call: com.johang.audiocinemateca.data.remote.ai.FunctionCall) {
        when (call.name) {
            "search_catalog" -> {
                _auraStatus.value = "Aura consultando catálogo..."
                try {
                    val query = call.args?.get("query") as? String ?: ""

                    var searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(query) }

                    if (searchResults.isEmpty() && query.contains(" ")) {
                        val keywords = query.split(" ").filter { it.length > 3 }
                        if (keywords.isNotEmpty()) {
                            searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(keywords.first()) }
                        }
                    }

                    val finalResults = searchResults.take(8)
                    val responseMap = mapOf(
                        "resultados" to finalResults.map { item ->
                            val typeStr = when (item) {
                                is com.johang.audiocinemateca.data.model.Serie -> "serie"
                                is com.johang.audiocinemateca.data.model.Documentary -> "documental"
                                is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
                                else -> "pelicula"
                            }
                            mapOf(
                                "id" to item.id,
                                "tipo" to typeStr,
                                "titulo" to item.title,
                                "anio" to item.anio,
                                "genero" to item.genero,
                                "director" to item.director,
                                "reparto" to item.reparto.take(80),
                                "narracion" to item.narracion,
                                "sinopsis" to item.sinopsis.take(190)
                            )
                        }
                    )
                    val nextRes = geminiRepository.sendFunctionResponse(call.name, call.id, responseMap)
                    nextRes.onSuccess { response ->
                        val candidate = response.candidates?.firstOrNull() ?: return@onSuccess
                        val parts = candidate.content?.parts ?: emptyList()

                        val textRes = parts.filter { it.text != null && it.thought != true }.joinToString("\n") { it.text!! }
                            .takeIf { it.isNotBlank() }

                        val nextCall = parts.find { it.functionCall != null }?.functionCall

                        if (textRes != null) {
                            val allowAutoPlay = nextCall?.name == "play_content" && hasExplicitPlaybackIntent(lastUserMessageText)
                            val uttId = if (allowAutoPlay) "PLAY_CONFIRMATION" else null
                            displayFinalMessage(textRes, utteranceId = uttId)
                        }

                        if (nextCall != null) {
                            viewModelScope.launch { processFunctionCall(nextCall) }
                        } else if (textRes == null) {
                            displayFinalMessage("Aquí tienes lo que encontré.")
                        }
                    }.onFailure { e ->
                        Log.e("AIChatVM", "Error procesando search_catalog", e)
                        addErrorMessage("Hubo un error al consultar el catálogo.")
                        resetState()
                    }
                } catch (e: Exception) {
                    Log.e("AIChatVM", "Excepción en search_catalog", e)
                    addErrorMessage("Error inesperado al buscar en el catálogo.")
                    resetState()
                }
            }
            "play_content" -> {
                _auraStatus.value = "Aura preparando el cine..."
                try {
                    val contentId = call.args?.get("contentId") as? String ?: ""
                    val rawType = (call.args?.get("type") as? String ?: "").lowercase().trim()
                    val type = rawType
                        .replace("á", "a").replace("é", "e")
                        .replace("í", "i").replace("ó", "o").replace("ú", "u")

                    Log.d("AIChatVM", "play_content: contentId='$contentId', type='$type' (raw='$rawType')")

                    var item = withContext(Dispatchers.IO) { searchRepository.getCatalogItemByIdAndType(contentId, type) }
                    if (item == null) {
                        item = withContext(Dispatchers.IO) { searchRepository.findCatalogItemById(contentId) }
                    }
                    if (item == null && contentId.isNotBlank()) {
                        val searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(contentId) }
                        item = searchResults.firstOrNull()
                    }

                    if (item != null) {
                        val allowAutoPlay = hasExplicitPlaybackIntent(lastUserMessageText)
                        if (!allowAutoPlay) {
                            Log.d("AIChatVM", "play_content bloqueado porque el usuario no pidió reproducir explícitamente ('$lastUserMessageText'). Presentando como recomendación.")
                            pendingPlayEvent = null
                            val recommendationMsg = "Encontré [[${item.title}]] (${item.anio}) en el catálogo. ¿Te gustaría que la reproduzca ahora mismo o prefieres ver sus detalles?"
                            geminiRepository.recordLocalFunctionCompletion(
                                functionName = call.name,
                                functionCallId = call.id,
                                responseMap = mapOf("status" to "recommended_only", "titulo" to item.title),
                                assistantClosingText = if (!isRecentlyExplained()) recommendationMsg else null
                            )
                            if (!isRecentlyExplained()) {
                                displayFinalMessage(recommendationMsg, utteranceId = null)
                            } else {
                                withContext(Dispatchers.Main) { resetState() }
                            }
                            return
                        }

                        val progress = withContext(Dispatchers.IO) { playbackProgressDao.getPlaybackProgressForContent(item.id).firstOrNull() }
                        val fPart = progress?.partIndex ?: (call.args?.get("seasonIndex") as? Number)?.toInt() ?: 0
                        val fEp = progress?.episodeIndex ?: (call.args?.get("episodeIndex") as? Number)?.toInt() ?: 0
                        val startPos = progress?.currentPositionMs ?: 0L

                        pendingPlayEvent = ChatNavigationEvent.PlayContent(item, fPart, fEp, startPos)

                        val confirmationPhrases = listOf(
                            "¡Claro que sí! Enseguida te pongo [[${item.title}]] para que la disfrutes.",
                            "¡Hecho! Preparando [[${item.title}]], ponte cómodo que ya empieza.",
                            "¡Con mucho gusto! Vamos a escuchar [[${item.title}]] ahora mismo."
                        )
                        val chosenConfirmation = confirmationPhrases.random()

                        geminiRepository.recordLocalFunctionCompletion(
                            functionName = call.name,
                            functionCallId = call.id,
                            responseMap = mapOf("status" to "success", "titulo" to item.title),
                            assistantClosingText = if (!isRecentlyExplained()) chosenConfirmation else null
                        )

                        if (!isRecentlyExplained()) {
                            displayFinalMessage(chosenConfirmation, utteranceId = "PLAY_CONFIRMATION")
                        } else {
                            withContext(Dispatchers.Main) { resetState() }
                        }
                    } else {
                        val errorResponse = mapOf(
                            "status" to "error",
                            "message" to "No se encontró el contenido con id '$contentId'. Usa search_catalog para obtener el ID correcto."
                        )
                        val nextRes = geminiRepository.sendFunctionResponse(call.name, call.id, errorResponse)
                        nextRes.onSuccess { response ->
                            val textRes = response.candidates?.firstOrNull()?.content?.parts
                                ?.filter { it.text != null && it.thought != true }?.joinToString("\n") { it.text!! }
                                ?.takeIf { it.isNotBlank() }
                            displayFinalMessage(textRes ?: "No pude encontrar ese título. ¿Me lo podrías pedir de nuevo?")
                        }.onFailure {
                            displayFinalMessage("No pude encontrar ese título. ¿Me lo podrías pedir de nuevo?")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AIChatVM", "Excepción en play_content", e)
                    addErrorMessage("Error inesperado al preparar la reproducción.")
                    resetState()
                }
            }
            else -> {
                stopThinkingAnnouncer()
                resetState()
            }
        }
    }

    sealed class ChatNavigationEvent {
        data class PlayContent(
            val item: CatalogItem,
            val seasonIndex: Int,
            val episodeIndex: Int,
            val startPosition: Long = 0L
        ) : ChatNavigationEvent()
    }

    private fun startThinkingAnnouncer() {
        thinkingAnnouncerJob?.cancel()
        thinkingAnnouncerJob = viewModelScope.launch {
            delay(1000)

            if (_isLoading.value) {
                withContext(Dispatchers.Main) {
                    soundEffectsManager.playSound("aura_wait")
                    ttsManager.speak("Aura pensando...", interrupt = false)
                }
            }

            while (true) {
                delay(5000)
                if (_isLoading.value) {
                    withContext(Dispatchers.Main) {
                        soundEffectsManager.playSound("aura_wait")
                        ttsManager.speak("Aura pensando...", interrupt = false)
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun stopThinkingAnnouncer() {
        thinkingAnnouncerJob?.cancel()
        thinkingAnnouncerJob = null
    }

    private fun resetState() {
        _isLoading.value = false
        _auraStatus.value = "Aura En línea"
    }

    private suspend fun parseLinkedContentGlobally(text: String): List<LinkedContent> {
        val linked = mutableListOf<LinkedContent>()
        if (text.isBlank()) return linked

        val urlRegex = Regex("""(?:https?://)?(?:www\.)?audiocinemateca\.com/[^\s<>\)"']\S*""", RegexOption.IGNORE_CASE)
        val urlMatches = urlRegex.findAll(text)

        for (match in urlMatches) {
            try {
                var rawUrl = match.value.trim().trimEnd('.', ',', ')', ']', '"', '\'', '>', '!', '?', ';', ':', '}')
                if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    rawUrl = "https://$rawUrl"
                }

                val uri = android.net.Uri.parse(rawUrl)
                val pathSegments = uri.pathSegments
                val firstSeg = pathSegments.getOrNull(0)?.lowercase() ?: ""
                val secondSeg = pathSegments.getOrNull(1)

                val categoryHint = when {
                    firstSeg.contains("peli") -> "peliculas"
                    firstSeg.contains("serie") -> "series"
                    firstSeg.contains("docu") -> "documentales"
                    firstSeg.contains("corto") -> "cortometrajes"
                    else -> null
                }

                val rawIdParam = uri.getQueryParameter("id")
                    ?: uri.getQueryParameter("id_contenido")
                    ?: secondSeg
                    ?: if (categoryHint == null && firstSeg.isNotBlank() && firstSeg != "com" && firstSeg != "www") firstSeg else null

                val cleanId = rawIdParam?.trim()?.takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }

                if (!cleanId.isNullOrBlank()) {
                    val item = if (categoryHint != null) {
                        searchRepository.getCatalogItemByIdAndType(cleanId, categoryHint)
                            ?: searchRepository.findCatalogItemById(cleanId)
                    } else {
                        searchRepository.findCatalogItemById(cleanId)
                    }

                    if (item != null) {
                        val typeStr = when (item) {
                            is com.johang.audiocinemateca.data.model.Serie -> "series"
                            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
                            is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometrajes"
                            else -> "peliculas"
                        }
                        if (linked.none { it.id == item.id }) {
                            linked.add(LinkedContent(item.id, item.title, typeStr))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AIChatVM", "Error parseando URL para linked content", e)
            }
        }

        val bracketRegex = """\[\[(.*?)]]""".toRegex()
        val matches = bracketRegex.findAll(text)
        for (match in matches) {
            val titleInText = match.groupValues[1].trim()
            try {
                val foundItems = searchRepository.searchCatalog(titleInText)
                val exactItem = foundItems.find { it.title.equals(titleInText, ignoreCase = true) }
                    ?: foundItems.find { it.title.contains(titleInText, ignoreCase = true) }
                    ?: foundItems.find { titleInText.contains(it.title, ignoreCase = true) }
                    ?: foundItems.firstOrNull()

                if (exactItem != null && linked.none { it.id == exactItem.id }) {
                    val typeStr = when (exactItem) {
                        is com.johang.audiocinemateca.data.model.Serie -> "series"
                        is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
                        is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometrajes"
                        else -> "peliculas"
                    }
                    linked.add(LinkedContent(exactItem.id, exactItem.title, typeStr))
                }
            } catch (e: Exception) {
                Log.w("AIChatVM", "Error buscando '$titleInText' para linked content", e)
            }
        }

        return linked.distinctBy { it.id }
    }

    private fun addErrorMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), text, false, isError = true)
        syncActiveSessionAndPersist()
        soundEffectsManager.playSound("aura_error")
        ttsManager.speak(text)
    }
}
