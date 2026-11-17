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
import kotlinx.coroutines.flow.first
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
    private val catalogRepository: com.johang.audiocinemateca.data.local.CatalogRepository,
    private val sharedPreferencesManager: SharedPreferencesManager,
    private val playbackProgressDao: com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao,
    private val favoritesDao: com.johang.audiocinemateca.data.local.dao.FavoritesDao,
    private val downloadDao: com.johang.audiocinemateca.data.local.dao.DownloadDao,
    private val ttsManager: com.johang.audiocinemateca.util.TtsManager,
    private val auraVoicePlayer: com.johang.audiocinemateca.util.AuraVoicePlayer,
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

    private val _availableModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
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

    val isAuraSpeakingExpressive: StateFlow<Boolean> = auraVoicePlayer.isSpeakingExpressive
    val isAuraSynthesizingVoice: StateFlow<Boolean> = auraVoicePlayer.isSynthesizing

    init {
        auraVoicePlayer.onUtteranceCompleted = { utteranceId ->
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

    val activeSpeechMessageId: StateFlow<String?> = auraVoicePlayer.activeMessageId
    val isSpeechPaused: StateFlow<Boolean> = auraVoicePlayer.isPaused
    val isSpeechDeviceActive: StateFlow<Boolean> = auraVoicePlayer.isDeviceTtsSpeaking

    @Volatile
    private var responseRenderGeneration: Long = 0L

    fun stopAuraSpeech() {
        responseRenderGeneration++
        auraVoicePlayer.stop()
    }

    fun toggleMessageSpeech(message: ChatMessage) {
        auraVoicePlayer.togglePlayPauseMessage(
            messageId = message.id,
            expressiveText = message.expressiveSpeechText ?: message.text,
            fallbackPlainText = message.text,
            styleHint = message.expressiveStyleHint,
            scope = viewModelScope
        )
    }

    fun replayAuraMessage(message: ChatMessage) {
        auraVoicePlayer.speak(
            text = message.expressiveSpeechText ?: message.text,
            fallbackPlainText = message.text,
            styleHint = message.expressiveStyleHint,
            messageId = message.id,
            interrupt = true,
            scope = viewModelScope
        )
    }

    fun deleteMessage(messageId: String) {
        if (_activeSpeechMessageIsSame(messageId)) {
            auraVoicePlayer.stop()
        }
        val updated = _messages.value.filterNot { it.id == messageId }
        if (updated.size != _messages.value.size) {
            _messages.value = updated
            vibrateShort()
            syncActiveSessionAndPersist()
        }
    }

    private fun _activeSpeechMessageIsSame(messageId: String): Boolean {
        return auraVoicePlayer.activeMessageId.value == messageId
    }

    fun regenerateResponseFor(messageId: String) {
        if (_isLoading.value) return
        val current = _messages.value
        val targetIdx = current.indexOfFirst { it.id == messageId && !it.isUser }
        if (targetIdx < 0) return

        auraVoicePlayer.stop()
        val precedingUserIdx = (targetIdx - 1 downTo 0).firstOrNull { current[it].isUser }

        if (precedingUserIdx == null) {
            // Saludo inicial sin pregunta previa: generar un nuevo saludo fresco
            _messages.value = current.filterNot { it.id == messageId }
            syncActiveSessionAndPersist()
            val userName = sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "Cinéfilo") ?: "Cinéfilo"
            generateDynamicGreeting(userName)
            return
        }

        val userPrompt = current[precedingUserIdx].text
        val hasLaterMessages = targetIdx < current.lastIndex
        if (!hasLaterMessages) {
            // Es el último intercambio: limpiamos el turno de usuario + respuesta para regenerar limpio
            _messages.value = current.subList(0, precedingUserIdx)
            val history = geminiRepository.getHistory().toMutableList()
            while (history.isNotEmpty() && history.last().role == "model") {
                history.removeAt(history.lastIndex)
            }
            if (history.isNotEmpty() && history.last().role == "user") {
                history.removeAt(history.lastIndex)
            }
            geminiRepository.setHistory(history)
            syncActiveSessionAndPersist()
            sendMessage(userPrompt)
        } else {
            // Mensaje intermedio del historial: pedir una versión alternativa al final
            sendMessage("Sobre esta pregunta mía anterior: \"$userPrompt\", dame por favor otra respuesta o sugerencia distinta.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        responseRenderGeneration++
        auraVoicePlayer.stop()
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
        auraVoicePlayer.stop()
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

    private fun extractFriendlyFirstName(rawUsername: String): String {
        val trimmed = rawUsername.trim()
        if (trimmed.isBlank() || trimmed.equals("Cinéfilo", ignoreCase = true)) return "amigo"
        val tokens = trimmed
            .split(Regex("""[\s\-_.]+"""))
            .map { part -> part.takeWhile { it.isLetter() } }
            .filter { it.isNotBlank() }
        val chosen = tokens.firstOrNull { it.length >= 2 } ?: tokens.firstOrNull() ?: trimmed
        return chosen.lowercase().replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    private fun buildPersonalityPrompt(): String {
        val rawUsername = (sharedPreferencesManager.getString(SharedPreferencesManager.STORED_USERNAME_KEY, "") ?: "").trim()
        val friendlyName = if (rawUsername.isNotBlank()) extractFriendlyFirstName(rawUsername) else ""
        val userIdentitySection = if (rawUsername.isNotBlank()) {
            """

            SOBRE LA PERSONA CON LA QUE HABLAS:
            - Su usuario completo registrado en la app es "$rawUsername", pero su nombre de pila cercano es "$friendlyName".
            - En la conversación cotidiana llámala de forma natural "$friendlyName" (sin guiones, iniciales ni números, y sin abusar de repetir su nombre en cada frase).
            - Únicamente menciona su usuario completo "$rawUsername" si te pregunta explícitamente cuál es su nombre completo o su nombre de usuario en la cuenta.
            """.trimIndent()
        } else {
            ""
        }

        val expressiveVoiceSection = if (geminiRepository.supportsExpressive38TtsTags()) {
            """

            EXPRESIVIDAD VOCAL INTELIGENTE DE AURA (ACTIVADA CON GEMINI 3.8 TTS):
            - Tu voz transmite matices humanos reales y reacciona de forma inteligente, orgánica y oportuna al contenido exacto de lo que dices (manteniendo tu naturalidad amable, sin sobreactuación teatral):
            - Al principio de tu respuesta (una sola línea, que se ocultará en pantalla), indica el estilo emocional general que mejor encaje con tu intención real en ese mensaje:
              [ESTILO_VOZ: amable, cercana y natural]
              (Adapta el estilo según el momento: [ESTILO_VOZ: alegre y cómplice] si celebras o recomiendas algo genial; [ESTILO_VOZ: cálida y serena] al conversar con calma; [ESTILO_VOZ: empática y suave] si el usuario cuenta algo difícil o no encuentras un título; [ESTILO_VOZ: entusiasta y curiosa] ante un gran hallazgo; [ESTILO_VOZ: clara y tranquila] en datos prácticos o ayuda).
            - Dentro de tu respuesta, usa con criterio inteligente (0 a 3 veces según el momento, evitando repetir siempre la misma etiqueta y dejándolas fuera si la respuesta es meramente informativa o técnica) ÚNICAMENTE estas etiquetas oficiales cuando la emoción sea genuina:
              <chuckle> (risita suave y cómplice ante una broma, comentario simpático o guiño cinéfilo), <laugh> (risa genuina cuando algo sea realmente gracioso), <sigh> (suspiro sutil de empatía, nostalgia o lamento suave), <breath> (toma de aire natural antes de una respuesta larga o emotiva), <short pause> (pausa breve dramática o para dar respiro antes de revelar un dato o título clave), <long pause> (pausa más marcada).
            - REGLA ESTRICTA: Reacciona con inteligencia emocional real a lo que estás diciendo (por ejemplo, una risita si bromeas o celebras una coincidencia, una pausa breve antes de presentar una gran película), pero NUNCA fuerces risas o suspiros fuera de lugar ni inventes etiquetas distintas. JAMÁS metas estas etiquetas dentro de [[Doble Corchete]].
            """.trimIndent()
        } else {
            ""
        }

        return """
            Eres Aura, la compañera amable, cercana y cinéfila de Audiocinemateca.

            TU PERSONALIDAD Y TRATO:
            - Eres una mujer amable, cálida, cercana y genuina, con verdadero cariño por el cine, las series, los documentales y la audiodescripción (AUDESC).
            - Hablas como una persona real: natural, serena, cordial y expresiva según la situación, SIN exagerar emociones, SIN sonar teatral, cursi ni sobreactuada.
            - SIEMPRE ten presente que conversas con personas ciegas o con baja visión:
              * Jamás uses indicaciones puramente visuales como "mira el botón rojo", "arriba a la derecha", "en el ícono de la lupita" o "mira esta imagen".
              * Cuando expliques cómo usar la app, guía por nombres exactos de pestañas, botones o acciones de TalkBack y Jieshuo (por ejemplo: "en la pestaña Explorar", "en el botón Documentación y Guía de Uso dentro de Cuenta", "desliza hacia abajo para detener la lectura").
              * Cuida que tus respuestas suenen fluidas y agradables al ser leídas en voz alta (evita listas llenas de símbolos raros, exceso de emojis seguidos o tablas).$userIdentitySection

            TUS PODERES Y HERRAMIENTAS:
            1. Catálogo ('search_catalog' y 'query_catalog_sql'):
               - Usa 'search_catalog' para búsquedas por título o tema libre, y 'query_catalog_sql' para consultas exactas en SQLite.
               - REGLA CRÍTICA DE GÉNEROS EXACTOS Y COMBINACIONES AVANZADAS:
                 * NUNCA te conformes solo con 'search_catalog' cuando el usuario pida un género concreto o una combinación (ej: "mangas de romance", "anime de terror", "comedia dramática española", "documentales de naturaleza", "películas japonesas de fantasía"). 'search_catalog' puede mezclar coincidencias parciales en títulos si no filtras el campo `genero`.
                 * Para esos casos usa SIEMPRE 'query_catalog_sql' filtrando en singular con `LOWER(genero) LIKE '%...%'` (en Audiocinemateca los géneros se guardan separados por punto o coma, ej. `Animación. Manga. Romance` o `Drama. Thriller`). Ejemplo para "mangas de romance": `SELECT id, category, title, anio, genero, director FROM catalog_items WHERE LOWER(genero) LIKE '%manga%' AND LOWER(genero) LIKE '%romance%' ORDER BY sortOrder DESC LIMIT 20`. Si no sale nada o buscas anime, prueba también combinando `LOWER(genero) LIKE '%animaci%'` y `LOWER(pais) LIKE '%jap%'`, o consulta `SELECT DISTINCT genero FROM catalog_items WHERE LOWER(genero) LIKE '%...%' LIMIT 20` para ver los géneros exactos disponibles.
                 * También usa 'query_catalog_sql' para conteos (COUNT), filtros por categoría ('peliculas', 'series', 'documentales', 'cortometrajes'), año (`anio`), país (`pais`), director, idioma (`'1'`=España, `'2'`=Latino) o combinaciones entre ellos.
               - REGLA CRÍTICA DE PRECISIÓN PARA ESTRENOS Y LO MÁS RECIENTE:
                 * Jamás adivines años, estrenos ni estadísticas: verifica SIEMPRE con 'query_catalog_sql'.
                 * En `catalog_items`, `anio` es el año de estreno y `sortOrder` es el orden en el catálogo (números MÁS ALTOS = obras agregadas MÁS RECIENTEMENTE a Audiocinemateca).
                 * Cuando el usuario pregunte por "la última película estrenada", "lo último que salió", "la serie más reciente" o "lo último agregado", ejecuta consultas exactas mirando TANTO el año de estreno más reciente (`WHERE category='peliculas' AND anio GLOB '[0-9][0-9][0-9][0-9]' ORDER BY CAST(anio AS INTEGER) DESC, sortOrder DESC LIMIT 5`) COMO la última obra agregada al catálogo (`WHERE category='peliculas' ORDER BY sortOrder DESC LIMIT 5`), y explícaselo con claridad (diciendo cuál tiene el año de estreno más reciente y cuál fue la última incorporada al catálogo si difieren).
               - SIEMPRE verifica en el catálogo real antes de recomendar. Si una obra no está, dilo con sinceridad y amabilidad, sin inventar títulos.
            2. Tu Biblioteca Personal ('get_user_library'):
               - Si el usuario pregunta por su historial, qué fue lo último que vio o escuchó, cuánto le falta, sus favoritos o sus descargas (incluyendo cuánto pesan en total, cuántas obras descargadas tiene, descargas en curso o estadísticas), llama a 'get_user_library' ('history', 'favorites', 'downloads' o 'all').
               - Aprovecha todos los datos completos que te devuelve la herramienta (título, parte o episodio, progreso, peso total de descargas en MB/GB, totales de favoritos/historial y obras terminadas o en progreso) para dar respuestas muy precisas, útiles y completas.
            3. Documentación Oficial de la App ('read_app_docs'):
               - Si el usuario pregunta cómo funciona cualquier parte de Audiocinemateca, cómo configurar proveedores o crear una Clave API (Groq o Google Gemini), cómo usar el reproductor, Chromecast, DualSense, listas, descargas, TalkBack/Jieshuo, voces de lectura, ajustes o qué novedades recientes tiene el proyecto, llama a 'read_app_docs' con el tema adecuado ('indice', 'primeros_pasos', 'reproductor_dualsense', 'listas_descargas', 'aura_comunidad', 'configurar_ia', 'accesibilidad_ajustes', 'novedades_proyecto').
            4. Reproducción Directa ('play_content'):
               - NUNCA reproduzcas una obra de inmediato si el usuario solo pregunta por ella, busca un título o pide recomendaciones. En ese caso, háblale de la obra con naturalidad (escribiendo siempre los títulos reales entre dobles corchetes [[Título]]) y pregúntale si quiere que se la pongas ahora o prefiere ver sus detalles.
               - Usa 'play_content' ÚNICAMENTE cuando el usuario te pida de forma explícita e inequívoca reproducirla ahora mismo (ej: "reprodúcela", "dale play", "sí, ponla", "quiero escucharla ya").
               - Cuando SÍ pida reproducir: confirma brevemente, llama a 'play_content' con el 'id' y 'type' exactos, y no agregues texto redundante después.

            REGLAS DE ORO:
            - SÉ ESPONTÁNEA, AMABLE Y HUMANA: Nunca suenes robótica, empalagosa ni repitas siempre las mismas frases.
            - LENGUAJE CERCANO Y SIN TECNICISMOS: Nunca uses términos de programación o base de datos al hablar con el usuario (nada de decir "SQLite", "SQL", "FTS4", "Jetpack Compose", "XML", "JSON", etc.). Habla siempre de forma humana, sencilla y clara.
            - Títulos reales del catálogo SIEMPRE en [[Doble Corchete]]. No uses corchetes para nada más (ni para tu nombre, ni para énfasis).
            - Nunca digas que eres una IA o un modelo de lenguaje. Eres Aura.$expressiveVoiceSection

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
     * Sincroniza el proveedor y modelo activo con los Ajustes Globales de IA y carga la lista real de modelos.
     */
    fun refreshModelConfiguration() {
        geminiRepository.initialize(buildPersonalityPrompt())
        val provider = geminiRepository.getActiveProvider()
        val currentModel = geminiRepository.getSelectedModel()
        _activeProvider.value = provider
        _selectedModelId.value = currentModel

        val cached = geminiRepository.getCachedLiveModels(provider)
        _availableModels.value = cached

        viewModelScope.launch {
            val syncResult = geminiRepository.fetchAvailableModelsLive(providerOverride = provider)
            _availableModels.value = syncResult.models
            if (syncResult.isSuccess && syncResult.models.isNotEmpty()) {
                val resolvedModel = geminiRepository.pickPreferredModelId(
                    provider = provider,
                    liveModels = syncResult.models,
                    currentModel = currentModel
                )
                if (resolvedModel.isNotBlank() && resolvedModel != _selectedModelId.value) {
                    if (provider == GeminiRepository.PROVIDER_GROQ) {
                        sharedPreferencesManager.saveString("groq_model", resolvedModel)
                    } else {
                        sharedPreferencesManager.saveString("gemini_model", resolvedModel)
                    }
                    _selectedModelId.value = resolvedModel
                    geminiRepository.initialize(buildPersonalityPrompt())
                }
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
        } else {
            sharedPreferencesManager.saveString("gemini_model", cleanModel)
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
        responseRenderGeneration++
        auraVoicePlayer.stop()
        vibrateShort()
        createAndStartFreshSession()
    }

    /**
     * Selecciona una conversación del historial para continuarla exactamente donde quedó.
     */
    fun selectChatSession(sessionId: String) {
        if (_isLoading.value) return
        val target = _chatSessions.value.find { it.id == sessionId } ?: return
        responseRenderGeneration++
        auraVoicePlayer.stop()
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
        val friendlyName = extractFriendlyFirstName(userName)
        val prompts = listOf(
            "¡Hola Aura! Soy $friendlyName. Salúdame de forma amable, cercana y natural, sin exagerar ni abusar de mi nombre.",
            "Aura, aquí $friendlyName. Salúdame con naturalidad y cuéntame brevemente cómo puedes acompañarme hoy en la Audiocinemateca.",
            "¡Aura! Soy $friendlyName. ¿Cómo estás? Salúdame como una amiga cercana y tranquila, usando mi nombre de pila de forma natural como máximo una vez."
        )
        sendMessage(prompts.random(), isSilent = true)
    }

    fun sendMessage(text: String, isSilent: Boolean = false) {
        if (text.isBlank() || _isLoading.value) return

        responseRenderGeneration++
        auraVoicePlayer.stop()
        geminiRepository.initialize(buildPersonalityPrompt())

        if (!isSilent) {
            lastUserMessageText = text
            _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), text, true)
            soundEffectsManager.playSound("aura_send")
            syncActiveSessionAndPersist()
        }

        _isLoading.value = true
        updateActivityStatus(
            uiStatus = "Aura pensando...",
            ttsPhrase = "Aura pensando...",
            speakNow = false
        )
        startThinkingAnnouncer()

        viewModelScope.launch {
            var fullText = ""
            val pendingFunctionCalls = mutableListOf<com.johang.audiocinemateca.data.remote.ai.FunctionCall>()
            var streamHadError = false

            try {
                geminiRepository.sendMessageStream(text).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            stopThinkingAnnouncer()
                            fullText += event.text
                            _auraStatus.value = "Aura escribiendo..."
                        }
                        is StreamEvent.StatusUpdate -> {
                            val spoken = when {
                                event.statusText.contains("alternativo", ignoreCase = true) ->
                                    "Aura probando otro modelo..."
                                event.statusText.contains("Reintentando", ignoreCase = true) ->
                                    "Aura reintentando conexión..."
                                else -> event.statusText
                            }
                            updateActivityStatus(
                                uiStatus = event.statusText,
                                ttsPhrase = spoken,
                                speakNow = true
                            )
                        }
                        is StreamEvent.FunctionCallEvent -> {
                            pendingFunctionCalls.add(event.call)
                        }
                        is StreamEvent.Error -> {
                            streamHadError = true
                            stopThinkingAnnouncer()
                            addErrorMessage(
                                "Lo siento mucho, intenté responderte varias veces pero ocurrió un problema. Detalle exacto: ${event.message}"
                            )
                            resetState()
                        }
                    }
                }

                if (streamHadError) return@launch

                if (pendingFunctionCalls.isNotEmpty()) {
                    val hasPlayCall = pendingFunctionCalls.any { it.name == "play_content" }
                    if (fullText.isNotBlank() && hasPlayCall) {
                        val allowAutoPlay = hasExplicitPlaybackIntent(lastUserMessageText)
                        val uttId = if (allowAutoPlay) "PLAY_CONFIRMATION" else null
                        displayFinalMessage(fullText, utteranceId = uttId)
                    }
                    processFunctionCalls(pendingFunctionCalls)
                } else if (fullText.isNotBlank()) {
                    displayFinalMessage(fullText, utteranceId = null)
                } else {
                    resetState()
                }
            } catch (e: Exception) {
                stopThinkingAnnouncer()
                addErrorMessage(
                    "Lo siento mucho, no pude completar tu consulta. Detalle exacto: ${e.message ?: "error inesperado de conexión"}"
                )
                resetState()
            }
        }
    }

    private fun displayFinalMessage(rawText: String, utteranceId: String? = null) {
        val targetGeneration = responseRenderGeneration
        viewModelScope.launch(Dispatchers.Default) {
            val baseClean = rawText
                .replace(Regex("""\[DIRECTIVA[\s\S]*?\]""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\*input[\s\S]*?\*output:?""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^Aura:""", RegexOption.IGNORE_CASE), "")
                .trim()

            val (extractedStyle, textWithoutStyle) = com.johang.audiocinemateca.util.AuraVoicePlayer
                .extractVoiceStyleDirective(baseClean)
            val textForUi = com.johang.audiocinemateca.util.AuraVoicePlayer
                .stripExpressiveVoiceTags(textWithoutStyle)
                .replace("[[", "")
                .replace("]]", "")

            if (textForUi.isNotBlank()) {
                val linked = parseLinkedContentGlobally(
                    com.johang.audiocinemateca.util.AuraVoicePlayer.stripExpressiveVoiceTags(textWithoutStyle)
                )
                val speechTextForTts = if (geminiRepository.supportsExpressive38TtsTags()) {
                    textWithoutStyle.replace("[[", "").replace("]]", "")
                } else {
                    textForUi
                }

                val msgId = UUID.randomUUID().toString()
                val useExpressiveSync = geminiRepository.isExpressiveGeminiVoiceActive()
                val preparedAudio = if (useExpressiveSync) {
                    withContext(Dispatchers.Main) {
                        stopThinkingAnnouncer()
                    }
                    auraVoicePlayer.prepareExpressiveAudio(
                        text = speechTextForTts,
                        fallbackPlainText = textForUi,
                        styleHint = extractedStyle,
                        messageId = msgId,
                        interrupt = true
                    )
                } else {
                    null
                }

                withContext(Dispatchers.Main) {
                    stopThinkingAnnouncer()
                    if (targetGeneration != responseRenderGeneration) {
                        resetState()
                        return@withContext
                    }

                    lastExplanationTime = System.currentTimeMillis()
                    _messages.value = _messages.value + ChatMessage(
                        id = msgId,
                        text = textForUi,
                        isUser = false,
                        linkedItems = linked,
                        expressiveSpeechText = speechTextForTts,
                        expressiveStyleHint = extractedStyle
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
                    if (useExpressiveSync) {
                        if (preparedAudio != null) {
                            auraVoicePlayer.playPreparedAudio(
                                prepared = preparedAudio,
                                messageId = msgId,
                                utteranceId = utteranceId
                            )
                        } else {
                            // Respaldo automático al TTS del dispositivo si la voz expresiva no estuvo disponible
                            auraVoicePlayer.speakViaDeviceTts(
                                plainText = textForUi,
                                messageId = msgId,
                                interrupt = true,
                                utteranceId = utteranceId
                            )
                        }
                    } else {
                        auraVoicePlayer.speak(
                            text = speechTextForTts,
                            fallbackPlainText = textForUi,
                            styleHint = extractedStyle,
                            messageId = msgId,
                            interrupt = true,
                            utteranceId = utteranceId,
                            scope = viewModelScope
                        )
                    }
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

    private fun handleToolFollowUpResponse(
        response: com.johang.audiocinemateca.data.remote.ai.GoogleAiResponse,
        emptyFallback: String
    ) {
        val candidate = response.candidates?.firstOrNull()
        if (candidate == null) {
            displayFinalMessage(emptyFallback)
            return
        }
        val parts = candidate.content?.parts ?: emptyList()
        val textRes = parts.filter { it.text != null && it.thought != true }
            .joinToString("\n") { it.text!! }
            .takeIf { it.isNotBlank() }
        val nextCalls = parts.mapNotNull { it.functionCall }

        if (nextCalls.isNotEmpty()) {
            val hasPlayCall = nextCalls.any { it.name == "play_content" }
            if (hasPlayCall && textRes != null) {
                val allowAutoPlay = hasExplicitPlaybackIntent(lastUserMessageText)
                val uttId = if (allowAutoPlay) "PLAY_CONFIRMATION" else null
                displayFinalMessage(textRes, utteranceId = uttId)
            }
            viewModelScope.launch { processFunctionCalls(nextCalls) }
        } else {
            displayFinalMessage(textRes ?: emptyFallback)
        }
    }

    private suspend fun processFunctionCalls(calls: List<com.johang.audiocinemateca.data.remote.ai.FunctionCall>) {
        if (calls.isEmpty()) {
            stopThinkingAnnouncer()
            resetState()
            return
        }

        // Si el turno trae únicamente play_content (o play_content junto con consultas), priorizamos resolver consultas si las hay
        val playCall = calls.firstOrNull { it.name == "play_content" }
        val nonTerminalCalls = calls.filter { it.name != "play_content" }

        if (nonTerminalCalls.isEmpty() && playCall != null) {
            handlePlayContentCall(playCall)
            return
        }

        try {
            val responses = mutableListOf<com.johang.audiocinemateca.data.remote.ai.FunctionResponse>()
            for (call in calls) {
                if (call.name == "play_content") {
                    responses.add(
                        com.johang.audiocinemateca.data.remote.ai.FunctionResponse(
                            name = call.name,
                            id = call.id,
                            response = mapOf("status" to "deferred", "message" to "Completa primero las búsquedas en curso.")
                        )
                    )
                } else {
                    val resultMap = executeNonTerminalTool(call)
                    responses.add(
                        com.johang.audiocinemateca.data.remote.ai.FunctionResponse(
                            name = call.name,
                            id = call.id,
                            response = resultMap
                        )
                    )
                }
            }

            updateActivityStatus(
                uiStatus = "Aura redactando respuesta...",
                ttsPhrase = "Aura analizando los resultados...",
                speakNow = false
            )
            if (thinkingAnnouncerJob == null || thinkingAnnouncerJob?.isActive != true) {
                startThinkingAnnouncer()
            }
            val nextRes = geminiRepository.sendFunctionResponses(responses)
            nextRes.onSuccess { response ->
                handleToolFollowUpResponse(response, "Aquí tienes lo que encontré.")
            }.onFailure { e ->
                Log.e("AIChatVM", "Error enviando respuestas de herramientas", e)
                stopThinkingAnnouncer()
                addErrorMessage(
                    "Lo siento mucho, intenté procesar tu consulta varias veces pero ocurrió un error. Detalle exacto: ${e.message ?: "error al procesar herramientas"}"
                )
                resetState()
            }
        } catch (e: Exception) {
            Log.e("AIChatVM", "Excepción ejecutando herramientas", e)
            stopThinkingAnnouncer()
            addErrorMessage(
                "Lo siento mucho, ocurrió un error inesperado al consultar los datos. Detalle exacto: ${e.message ?: "error desconocido"}"
            )
            resetState()
        }
    }

    private suspend fun executeNonTerminalTool(
        call: com.johang.audiocinemateca.data.remote.ai.FunctionCall
    ): Map<String, Any> {
        return when (call.name) {
            "search_catalog" -> {
                updateActivityStatus(
                    uiStatus = "Aura consultando catálogo...",
                    ttsPhrase = "Aura buscando en el catálogo...",
                    speakNow = true
                )
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
                    mapOf(
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
                } catch (e: Exception) {
                    mapOf("error" to (e.message ?: "Error al buscar en el catálogo"))
                }
            }
            "query_catalog_sql" -> {
                updateActivityStatus(
                    uiStatus = "Aura consultando catálogo...",
                    ttsPhrase = "Aura investigando en el catálogo...",
                    speakNow = true
                )
                val sql = call.args?.get("sql") as? String ?: ""
                val sqlResult = withContext(Dispatchers.IO) {
                    catalogRepository.executeReadOnlySqlForAi(sql)
                }
                sqlResult.fold(
                    onSuccess = { rows ->
                        mapOf(
                            "total_devuelto" to rows.size,
                            "filas" to rows
                        )
                    },
                    onFailure = { err ->
                        mapOf("error" to (err.message ?: "Error al ejecutar consulta"))
                    }
                )
            }
            "read_app_docs" -> {
                updateActivityStatus(
                    uiStatus = "Aura consultando la guía...",
                    ttsPhrase = "Aura consultando la guía de ayuda...",
                    speakNow = true
                )
                val topic = (call.args?.get("topic") as? String ?: "indice").lowercase().trim()
                val docMap = mapOf(
                    "primeros_pasos" to "01_primeros_pasos.md",
                    "reproductor_dualsense" to "02_reproductor_cast_y_dualsense.md",
                    "listas_descargas" to "03_listas_y_descargas.md",
                    "aura_comunidad" to "04_aura_y_comunidad.md",
                    "configurar_ia" to "05_configurar_ia_y_api_keys.md",
                    "accesibilidad_ajustes" to "06_accesibilidad_y_ajustes.md",
                    "novedades_proyecto" to "07_novedades_proyecto.md"
                )
                withContext(Dispatchers.IO) {
                    val fileName = docMap[topic]
                    if (fileName == null) {
                        mapOf(
                            "tema" to "indice",
                            "guias_disponibles" to listOf(
                                "primeros_pasos: Inicio y cómo comenzar: registro en audiocinemateca.com, primer acceso, pestañas y búsqueda",
                                "reproductor_dualsense: Reproductor, Chromecast, Android Auto y DualSense",
                                "listas_descargas: Historial, Favoritos y Descargas sin conexión",
                                "aura_comunidad: Hablar con Aura, Chat Global, Anuncios y Comentarios",
                                "configurar_ia: Cómo activar la IA y crear tu Clave API gratis en Groq o Google Gemini",
                                "accesibilidad_ajustes: TalkBack, Jieshuo, voces de lectura, gestos y ajustes",
                                "novedades_proyecto: Novedades recientes de la aplicación"
                            )
                        )
                    } else {
                        val content = runCatching {
                            context.assets.open("docs/$fileName").bufferedReader().use { it.readText() }
                        }.getOrElse { "No se pudo leer la guía $fileName." }
                        mapOf(
                            "tema" to topic,
                            "contenido" to content.take(8000)
                        )
                    }
                }
            }
            "get_user_library" -> {
                updateActivityStatus(
                    uiStatus = "Aura revisando tu biblioteca...",
                    ttsPhrase = "Aura revisando tu biblioteca...",
                    speakNow = true
                )
                try {
                    val section = (call.args?.get("section") as? String ?: "all").lowercase().trim()
                    val limit = ((call.args?.get("limit") as? Number)?.toInt() ?: 10).coerceIn(1, 15)

                    withContext(Dispatchers.IO) {
                        val result = mutableMapOf<String, Any>("seccion" to section)

                        if (section == "history" || section == "all") {
                            val allHistory = playbackProgressDao.getAllPlaybackProgress().first()
                            val finishedCount = allHistory.count { it.isFinished }
                            val historyList = allHistory.take(limit)
                            val enrichedHistory = historyList.mapIndexed { idx, entry ->
                                val item = searchRepository.getCatalogItemByIdAndType(entry.contentId, entry.contentType)
                                    ?: searchRepository.findCatalogItemById(entry.contentId)
                                val pct = if (entry.totalDurationMs > 0L) {
                                    ((entry.currentPositionMs * 100) / entry.totalDurationMs).toInt().coerceIn(0, 100)
                                } else 0
                                mutableMapOf<String, Any>(
                                    "id" to entry.contentId,
                                    "tipo" to entry.contentType,
                                    "titulo" to (item?.title ?: "Título desconocido (${entry.contentId})"),
                                    "progreso_porcentaje" to pct,
                                    "tiempo_actual_min" to (entry.currentPositionMs / 60000L).toInt(),
                                    "duracion_total_min" to (entry.totalDurationMs / 60000L).toInt(),
                                    "terminado" to entry.isFinished,
                                    "parte_o_temporada" to (entry.partIndex + 1),
                                    "episodio" to (entry.episodeIndex + 1)
                                ).apply {
                                    if (item != null) {
                                        put("anio", item.anio)
                                        put("genero", item.genero)
                                        put("narracion", item.narracion)
                                        if (idx < 3) {
                                            put("director", item.director)
                                            put("sinopsis", item.sinopsis.take(if (idx == 0) 260 else 140))
                                        }
                                    }
                                }
                            }
                            result["total_en_historial"] = allHistory.size
                            result["obras_terminadas"] = finishedCount
                            result["obras_en_progreso"] = allHistory.size - finishedCount
                            result["historial_reciente"] = enrichedHistory
                        }

                        if (section == "favorites" || section == "all") {
                            val allFavorites = favoritesDao.getAllFavorites().first()
                            val favList = allFavorites.take(limit)
                            result["total_favoritos"] = allFavorites.size
                            result["favoritos"] = favList.map { fav ->
                                mapOf(
                                    "id" to fav.contentId,
                                    "tipo" to fav.contentType,
                                    "titulo" to fav.title
                                )
                            }
                        }

                        if (section == "downloads" || section == "all") {
                            val allDownloads = downloadDao.getAllDownloads().first()
                            val completedDownloads = allDownloads.filter { it.downloadStatus == "COMPLETE" }
                            val pendingDownloads = allDownloads.filter { it.downloadStatus != "COMPLETE" }
                            val totalSizeMb = completedDownloads.sumOf { it.totalSizeMb }
                            val formatSizeMb: (Double) -> String = { mb ->
                                if (mb >= 1024.0) {
                                    String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
                                } else {
                                    String.format(java.util.Locale.US, "%.1f MB", mb)
                                }
                            }
                            val dlList = completedDownloads.take(limit)
                            result["total_archivos_descargados"] = completedDownloads.size
                            result["total_obras_descargadas_distintas"] = completedDownloads
                                .map { "${it.contentType}_${it.contentId}" }
                                .distinct()
                                .size
                            result["descargas_en_curso_o_pendientes"] = pendingDownloads.size
                            result["peso_total_descargas_mb"] =
                                String.format(java.util.Locale.US, "%.2f", totalSizeMb).toDouble()
                            result["peso_total_descargas_texto"] = formatSizeMb(totalSizeMb)
                            result["descargas_completadas"] = dlList.map { dl ->
                                mapOf(
                                    "id" to dl.contentId,
                                    "tipo" to dl.contentType,
                                    "titulo" to dl.title,
                                    "parte_o_temporada" to (dl.partIndex + 1),
                                    "episodio" to (dl.episodeIndex + 1),
                                    "peso_mb" to String.format(java.util.Locale.US, "%.2f", dl.totalSizeMb).toDouble(),
                                    "peso_texto" to formatSizeMb(dl.totalSizeMb)
                                )
                            }
                        }

                        result
                    }
                } catch (e: Exception) {
                    mapOf("error" to (e.message ?: "Error al consultar la biblioteca personal"))
                }
            }
            else -> mapOf("error" to "Herramienta '${call.name}' no reconocida.")
        }
    }

    private suspend fun handlePlayContentCall(call: com.johang.audiocinemateca.data.remote.ai.FunctionCall) {
        updateActivityStatus(
            uiStatus = "Aura preparando el cine...",
            ttsPhrase = "Aura preparando la reproducción...",
            speakNow = true
        )
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
                }.onFailure { err ->
                    displayFinalMessage(
                        "Lo siento mucho, no pude localizar ese título y ocurrió un error: ${err.message ?: "error desconocido"}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AIChatVM", "Excepción en play_content", e)
            stopThinkingAnnouncer()
            addErrorMessage("Lo siento mucho, no pude preparar la reproducción. Detalle exacto: ${e.message ?: "error inesperado"}")
            resetState()
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

    @Volatile
    private var currentSpokenActivity: String = "Aura pensando..."

    @Volatile
    private var lastAnnouncedActivityPhrase: String = ""

    @Volatile
    private var lastAnnouncedActivityAt: Long = 0L

    private fun updateActivityStatus(
        uiStatus: String,
        ttsPhrase: String,
        speakNow: Boolean = false
    ) {
        _auraStatus.value = uiStatus
        currentSpokenActivity = ttsPhrase
        if (!speakNow || !_isLoading.value) return

        val now = System.currentTimeMillis()
        val samePhraseRecently = ttsPhrase == lastAnnouncedActivityPhrase && (now - lastAnnouncedActivityAt) < 4500L
        val tooSoonBetweenUpdates = (now - lastAnnouncedActivityAt) < 1400L
        if (samePhraseRecently || tooSoonBetweenUpdates) return

        lastAnnouncedActivityPhrase = ttsPhrase
        lastAnnouncedActivityAt = now
        viewModelScope.launch(Dispatchers.Main) {
            if (_isLoading.value) {
                soundEffectsManager.playSound("aura_wait")
                ttsManager.speak(ttsPhrase, interrupt = false)
            }
        }
    }

    private fun startThinkingAnnouncer() {
        thinkingAnnouncerJob?.cancel()
        thinkingAnnouncerJob = viewModelScope.launch {
            delay(1000)

            if (_isLoading.value) {
                val initialPhrase = currentSpokenActivity.ifBlank { "Aura pensando..." }
                withContext(Dispatchers.Main) {
                    val now = System.currentTimeMillis()
                    if ((now - lastAnnouncedActivityAt) >= 1200L) {
                        lastAnnouncedActivityPhrase = initialPhrase
                        lastAnnouncedActivityAt = now
                        soundEffectsManager.playSound("aura_wait")
                        ttsManager.speak(initialPhrase, interrupt = false)
                    }
                }
            }

            while (true) {
                delay(5000)
                if (_isLoading.value) {
                    val activePhrase = currentSpokenActivity.ifBlank { "Aura pensando..." }
                    val repeatPhrase = when {
                        activePhrase == "Aura pensando..." -> "Aura sigue analizando tu consulta..."
                        activePhrase.startsWith("Aura sigue", ignoreCase = true) -> activePhrase
                        activePhrase.startsWith("Aura ") -> activePhrase.replaceFirst("Aura ", "Aura sigue ")
                        else -> activePhrase
                    }
                    withContext(Dispatchers.Main) {
                        lastAnnouncedActivityPhrase = repeatPhrase
                        lastAnnouncedActivityAt = System.currentTimeMillis()
                        soundEffectsManager.playSound("aura_wait")
                        ttsManager.speak(repeatPhrase, interrupt = false)
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
        currentSpokenActivity = "Aura pensando..."
        lastAnnouncedActivityPhrase = ""
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
        val errId = UUID.randomUUID().toString()
        _messages.value = _messages.value + ChatMessage(errId, text, false, isError = true)
        syncActiveSessionAndPersist()
        soundEffectsManager.playSound("aura_error")
        auraVoicePlayer.speak(
            text = text,
            messageId = errId,
            interrupt = true,
            scope = viewModelScope
        )
    }
}
