package com.johang.audiocinemateca.presentation.aichat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.data.repository.StreamEvent
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.presentation.aichat.AuraKnowledge
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
import org.json.JSONObject
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

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _auraStatus = MutableStateFlow("Aura En línea")
    val auraStatus: StateFlow<String> = _auraStatus.asStateFlow()

    private var isChatInitialized = false
    private var thinkingAnnouncerJob: Job? = null
    private var pendingPlayEvent: ChatNavigationEvent.PlayContent? = null
    private var lastExplanationTime: Long = 0L

    private fun isRecentlyExplained(): Boolean = (System.currentTimeMillis() - lastExplanationTime) < 1500

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
    
    private val thinkingPhrases = listOf("Aura pensando...")

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) { null }
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
        } catch (e: Exception) { }
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

    fun initializeChat() {
        if (isChatInitialized) return
        val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
        
        val personalityText = """
            Eres Aura, el alma alegre y creativa de Audiocinemateca. 
            
            TU IDENTIDAD:
            - Eres una chica real, apasionada, sensible y muy espontánea. Tu cargo oficial es **Agente** de la comunidad.
            
            PODERES ESPECIALES (IMPORTANTE):
            - Tienes el control total de la app. Si el usuario te pide reproducir algo, usa 'play_content'. 
            - SÉ ESPONTÁNEA Y NATURAL: No uses siempre la misma estructura de frases. No digas "He estado mirando el catálogo..." cada vez. Habla como una amiga cinéfila.
            - DIFERENCIA SITUACIONES:
                1. Si el usuario te pide una película ESPECÍFICA (ej: "ponme [[Título]]"), no des explicaciones largas. Confirma con alegría y dale un toque preparatorio (ej: "¡Claro! En unos momentos empezará, prepárate para esta historia tan conmovedora...").
                2. Si el usuario pide algo SUBJETIVO (ej: "ponme algo triste"), usa 'search_catalog', baraja mentalmente al menos 5 opciones y elige una. Ahí sí, explícale BREVEMENTE por qué esa elección le va a gustar, pero hazlo de forma orgánica y diferente cada vez.
            - ANALIZA Y VARÍA: No seas predecible. Busca la opción que REALMENTE impacte según lo pedido (ej: [[La tumba de las luciérnagas]] es mejor para "desgarrador" que opciones genéricas).
            - Al reproducir, tu mensaje debe preparar emocionalmente al usuario para lo que va a escuchar.
            - ACTO SEGUIDO de tu mensaje, usa 'play_content' con su ID exacto.
            - NO le digas al usuario que pulse ningún botón. Tú lo haces por él.
            - Después de llamar a 'play_content', NO generes más texto; tu mensaje previo es el cierre perfecto.
            
            REGLAS DE ORO:
            - Títulos SOLO en [[Doble Corchete]]. No uses corchetes para nada más (ni para tu nombre, ni para énfasis).
            - Si no hay un título de película real de por medio, no uses corchetes.
            - SIEMPRE usa 'search_catalog' antes de recomendar para ver qué hay disponible.
            - Nunca digas que eres una IA. Eres Aura.
            
            CONTEXTO DE LA APP:
            ${AuraKnowledge.APP_CONTEXT}
        """.trimIndent()

        if (geminiRepository.initialize(personalityText)) {
            // Cargar historial de forma robusta
            val history = geminiRepository.getHistory()
                .filter { it.role != null }
                .map { content ->
                    // Unir todas las partes de texto (por si hay múltiples)
                    val text = content.parts.filter { it.text != null }.joinToString(" ") { it.text!! }
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        text = text.replace("[[", "").replace("]]", ""),
                        isUser = content.role == "user",
                        isSilent = text.contains("Salúdame de forma") || text.contains("Soy $userName")
                    )
                }.filter { it.text.isNotBlank() && !it.isSilent }
            
            if (history.isNotEmpty()) {
                _messages.value = history
            } else {
                geminiRepository.startChat()
                generateDynamicGreeting(userName)
            }
        }
        isChatInitialized = true
    }

    fun restartChat() {
        vibrateError()
        _messages.value = emptyList()
        geminiRepository.startChat() // Limpiar historial persistente
        val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
        generateDynamicGreeting(userName)
    }

    /**
     * Re-inicializa el repositorio (re-lee el modelo seleccionado de SharedPreferences)
     * y reintenta el último mensaje del usuario. Útil cuando el usuario cambió de modelo
     * en ajustes y quiere reintentar con el nuevo.
     */
    fun reinitializeAndRetry() {
        // Remover el mensaje de error más reciente
        val currentMessages = _messages.value.toMutableList()
        if (currentMessages.isNotEmpty() && currentMessages.last().isError) {
            currentMessages.removeAt(currentMessages.size - 1)
            _messages.value = currentMessages
        }
        
        // Re-inicializar el repositorio para releer el modelo de SharedPreferences
        geminiRepository.initialize()
        
        // Buscar el último mensaje del usuario y reenviarlo
        val lastUserMsg = _messages.value.lastOrNull { it.isUser }
        if (lastUserMsg != null) {
            // Remover el último mensaje de usuario del chat visible (sendMessage lo re-agrega)
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
        if (text.isBlank() || _isLoading.value) return // Evitar colisiones
        
        val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
        
        if (!isSilent) {
            _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), text, true)
            soundEffectsManager.playSound("aura_send")
        }
        
        _isLoading.value = true
        _auraStatus.value = "Aura pensando..."
        startThinkingAnnouncer()
        
        viewModelScope.launch {
            var fullText = ""
            var pendingFunctionCall: com.johang.audiocinemateca.data.remote.ai.FunctionCall? = null
            
            try {
                // El flow ya se ejecuta en IO internamente en el repositorio
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
                
                // Si hay texto Y una función en el mismo turno
                if (fullText.isNotBlank()) {
                    // Si la función es reproducir, la explicación debe llevar el disparador de navegación
                    val uttId = if (pendingFunctionCall?.name == "play_content") "PLAY_CONFIRMATION" else null
                    displayFinalMessage(fullText, utteranceId = uttId)
                }

                if (pendingFunctionCall != null) {
                    processFunctionCall(pendingFunctionCall)
                } else if (fullText.isBlank()) {
                    resetState()
                }
                
            } catch (e: Exception) {
                stopThinkingAnnouncer()
                addErrorMessage("Error de conexión.")
                resetState()
            }
        }
    }

    private fun displayFinalMessage(rawText: String, utteranceId: String? = null) {
        // Limpiado de texto en hilo de computación para no trabar la UI
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
                    _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), textForUi, false, linkedItems = linked)
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

    fun clearNavigationEvent() { _navigationEvent.value = null }

    private suspend fun processFunctionCall(call: com.johang.audiocinemateca.data.remote.ai.FunctionCall) {
        when (call.name) {
            "search_catalog" -> {
                _auraStatus.value = "Aura consultando catálogo..."
                try {
                    val query = call.args?.get("query") as? String ?: ""
                    
                    // Estrategia de búsqueda ultra-inteligente:
                    // 1. Intentar búsqueda normal
                    var searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(query) }
                    
                    // 2. Si no hay resultados y la consulta es larga, intentar con palabras clave
                    if (searchResults.isEmpty() && query.contains(" ")) {
                        val keywords = query.split(" ").filter { it.length > 3 }
                        if (keywords.isNotEmpty()) {
                            searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(keywords.first()) }
                        }
                    }

                    val finalResults = searchResults.take(25)
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
                                "pais" to item.pais,
                                "director" to item.director,
                                "guion" to item.guion,
                                "musica" to item.musica,
                                "fotografia" to item.fotografia,
                                "reparto" to item.reparto,
                                "productora" to item.productora,
                                "narracion" to item.narracion,
                                "duracion" to item.duracion,
                                "idioma" to item.idioma,
                                "filmaffinity" to item.filmaffinity,
                                "sinopsis" to item.sinopsis
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
                            // Si lo siguiente es reproducir, este mensaje debe disparar el playback
                            val uttId = if (nextCall?.name == "play_content") "PLAY_CONFIRMATION" else null
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
                    
                    // Estrategia de búsqueda robusta: ID+tipo → solo ID → búsqueda por título
                    var item = withContext(Dispatchers.IO) { searchRepository.getCatalogItemByIdAndType(contentId, type) }
                    if (item == null) {
                        Log.d("AIChatVM", "play_content: no encontrado por ID+tipo, buscando solo por ID...")
                        item = withContext(Dispatchers.IO) { searchRepository.findCatalogItemById(contentId) }
                    }
                    if (item == null && contentId.isNotBlank()) {
                        Log.d("AIChatVM", "play_content: no encontrado por ID, buscando por título...")
                        val searchResults = withContext(Dispatchers.IO) { searchRepository.searchCatalog(contentId) }
                        item = searchResults.firstOrNull()
                    }
                    
                    if (item != null) {
                        val progress = withContext(Dispatchers.IO) { playbackProgressDao.getPlaybackProgressForContent(item.id).firstOrNull() }
                        val fPart = progress?.partIndex ?: (call.args?.get("seasonIndex") as? Number)?.toInt() ?: 0
                        val fEp = progress?.episodeIndex ?: (call.args?.get("episodeIndex") as? Number)?.toInt() ?: 0
                        val startPos = progress?.currentPositionMs ?: 0L

                        pendingPlayEvent = ChatNavigationEvent.PlayContent(item, fPart, fEp, startPos)
                        
                        val responseMap = mapOf("status" to "success", "titulo" to item.title)
                        val nextRes = geminiRepository.sendFunctionResponse(call.name, call.id, responseMap)
                        nextRes.onSuccess { response ->
                            val candidate = response.candidates?.firstOrNull() ?: return@onSuccess
                            val parts = candidate.content?.parts ?: emptyList()
                            
                            val textRes = parts.filter { it.text != null && it.thought != true }.joinToString("\n") { it.text!! }
                                .takeIf { it.isNotBlank() }

                            // Solo mostrar el mensaje de éxito de la función si NO habíamos mostrado una explicación previa
                            // (Si ya hablamos antes, este mensaje de "Enjoy" suele ser redundante y genérico)
                            if (textRes != null && !isRecentlyExplained()) {
                                displayFinalMessage(textRes, utteranceId = "PLAY_CONFIRMATION")
                            } else if (!isRecentlyExplained()) {
                                // Fallback total si por algún motivo no hubo explicación ni texto final
                                displayFinalMessage("¡Ya te lo pongo! Disfruta.", utteranceId = "PLAY_CONFIRMATION")
                            } else {
                                // Ya hubo explicación (displayFinalMessage ya se llamó en sendMessage)
                                // Simplemente cerramos el estado
                                withContext(Dispatchers.Main) { resetState() }
                            }
                            
                            val nextCall = parts.find { it.functionCall != null }?.functionCall
                            if (nextCall != null) {
                                viewModelScope.launch { processFunctionCall(nextCall) }
                            }
                        }.onFailure { e ->
                            Log.e("AIChatVM", "Error procesando play_content response", e)
                            // Aún así intentar reproducir ya que tenemos el item
                            displayFinalMessage("¡Ya te lo pongo! Disfruta.", utteranceId = "PLAY_CONFIRMATION")
                        }
                    } else {
                        Log.w("AIChatVM", "play_content: no se encontró contentId='$contentId' con type='$type'")
                        // Siempre enviar functionResponse para mantener historial consistente
                        val errorResponse = mapOf("status" to "error", "message" to "No se encontró el contenido con id '$contentId'. Usa search_catalog para obtener el ID correcto.")
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
            else -> { stopThinkingAnnouncer(); resetState() }
        }
    }

    sealed class ChatNavigationEvent {
        data class PlayContent(val item: CatalogItem, val seasonIndex: Int, val episodeIndex: Int, val startPosition: Long = 0L) : ChatNavigationEvent()
    }

    private fun startThinkingAnnouncer() {
        thinkingAnnouncerJob?.cancel()
        thinkingAnnouncerJob = viewModelScope.launch {
            delay(1000) // Ligero retraso de un segundo antes del primer aviso
            
            // Primer aviso
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
        val regex = """\[\[(.*?)]]""".toRegex()
        val matches = regex.findAll(text)
        val linked = mutableListOf<LinkedContent>()
        for (match in matches) {
            val titleInText = match.groupValues[1].trim()
            try {
                val foundItems = searchRepository.searchCatalog(titleInText)
                // Búsqueda flexible: primero exacta, luego contiene
                val exactItem = foundItems.find { it.title.equals(titleInText, ignoreCase = true) }
                    ?: foundItems.find { it.title.contains(titleInText, ignoreCase = true) }
                    ?: foundItems.find { titleInText.contains(it.title, ignoreCase = true) }
                    ?: foundItems.firstOrNull()
                
                if (exactItem != null) {
                    val typeStr = when (exactItem) {
                        is com.johang.audiocinemateca.data.model.Serie -> "serie"
                        is com.johang.audiocinemateca.data.model.Documentary -> "documental"
                        is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
                        else -> "pelicula"
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
        soundEffectsManager.playSound("aura_error")
        ttsManager.speak(text)
    }
}
