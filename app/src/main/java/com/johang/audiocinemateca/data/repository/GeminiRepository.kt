package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.remote.ai.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiSpeechAudio(
    val bytes: ByteArray,
    val fileExtension: String,
    val mimeType: String,
    val modelUsed: String,
    val voiceUsed: String
)

enum class AiConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class AiConnectionState(
    val provider: String,
    val status: AiConnectionStatus,
    val message: String,
    val modelName: String = "",
    val modelsCount: Int = 0,
    val updatedAt: Long = 0L
)

data class AiModelsSyncResult(
    val models: List<Pair<String, String>>,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

@Singleton
class GeminiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAiApiService: GoogleAiApiService
) {
    companion object {
        private const val TAG = "GeminiRepository"

        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_GEMINI = "gemini"

        const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-120b"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"

        const val AURA_VOICE_MODE_DEVICE_TTS = "device_tts"
        const val AURA_VOICE_MODE_GEMINI = "gemini_expressive"
        const val DEFAULT_GEMINI_TTS_MODEL = "gemini-3.8-flash-tts"
        const val DEFAULT_GEMINI_TTS_VOICE = "Aoede"

        val GEMINI_TTS_VOICES: List<Pair<String, String>> = listOf(
            "Aoede" to "Aoede (Cálida, relajada y melodiosa - Predeterminada)",
            "Kore" to "Kore (Firme y expresiva)",
            "Leda" to "Leda (Dulce y juvenil)",
            "Zephyr" to "Zephyr (Brillante, clara y alegre)",
            "Sulafat" to "Sulafat (Cálida y acogedora)",
            "Despina" to "Despina (Suave y gentil)",
            "Callirrhoe" to "Callirrhoe (Amable y tranquila)",
            "Autonoe" to "Autonoe (Luminosa y entusiasta)",
            "Erinome" to "Erinome (Clara y articulada)",
            "Pulcherrima" to "Pulcherrima (Directa y segura)"
        )

        private val GEMINI_TTS_PRIORITY_ORDER = listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.8-flash-lite-tts",
            "gemini-2.5-flash-preview-tts",
            "gemini-2.5-pro-preview-tts",
            "gemini-3.1-flash-tts-preview"
        )

        private val GEMINI_PRIORITY_ORDER = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-4-hydrogen",
            "gemini-4-argon",
            "gemini-4-carbon",
            "gemini-2.5-pro",
            "gemma-4-31b-it",
            "gemma-4-27b-it",
            "gemma-4-26b-a4b-it",
            "gemini-2.0-flash"
        )
    }

    private var activeProvider: String = PROVIDER_GROQ
    private var selectedModel: String = DEFAULT_GROQ_MODEL
    private var apiKey: String = ""
    private var chatHistory = mutableListOf<Content>()
    private var systemInstruction: Content? = null
    private var systemInstructionText: String? = null
    private val groqTotalCharLimitByModel = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private val _connectionStates = MutableStateFlow(loadInitialConnectionStates())
    val connectionStates: StateFlow<Map<String, AiConnectionState>> = _connectionStates.asStateFlow()

    private val _liveModelsByProvider = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    val liveModelsByProvider: StateFlow<Map<String, List<Pair<String, String>>>> = _liveModelsByProvider.asStateFlow()

    private val _liveTtsModels = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val liveTtsModels: StateFlow<List<Pair<String, String>>> = _liveTtsModels.asStateFlow()

    private fun loadInitialConnectionStates(): Map<String, AiConnectionState> {
        return listOf(PROVIDER_GROQ, PROVIDER_GEMINI).associateWith { provider ->
            readPersistedConnectionState(provider)
        }
    }

    private fun readPersistedConnectionState(provider: String): AiConnectionState {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val keyPref = if (provider == PROVIDER_GROQ) "groq_api_key" else "gemini_api_key"
        val storedKey = (prefs.getString(keyPref, "") ?: "").trim()
        if (storedKey.isBlank()) {
            return AiConnectionState(
                provider = provider,
                status = AiConnectionStatus.DISCONNECTED,
                message = "Aún no has configurado una Clave API."
            )
        }

        val rawStatus = prefs.getString("ai_last_status_$provider", null)
        val status = rawStatus?.let {
            runCatching { AiConnectionStatus.valueOf(it) }.getOrNull()
        }?.takeIf { it != AiConnectionStatus.CONNECTING } ?: AiConnectionStatus.DISCONNECTED

        val message = prefs.getString("ai_last_msg_$provider", null)
            ?.takeIf { it.isNotBlank() }
            ?: "Clave API guardada. Lista para comprobar conexión."

        return AiConnectionState(
            provider = provider,
            status = status,
            message = message,
            modelName = prefs.getString("ai_last_model_$provider", "") ?: "",
            modelsCount = prefs.getInt("ai_last_count_$provider", 0),
            updatedAt = prefs.getLong("ai_last_time_$provider", 0L)
        )
    }

    fun getConnectionState(provider: String): AiConnectionState {
        return _connectionStates.value[provider] ?: readPersistedConnectionState(provider)
    }

    fun getCachedLiveModels(provider: String): List<Pair<String, String>> {
        return _liveModelsByProvider.value[provider].orEmpty()
    }

    fun recordConnectionState(
        provider: String,
        status: AiConnectionStatus,
        message: String,
        modelName: String = selectedModel,
        modelsCount: Int? = null
    ) {
        val prevCount = _connectionStates.value[provider]?.modelsCount ?: 0
        val resolvedCount = modelsCount ?: prevCount
        val now = System.currentTimeMillis()
        val updatedState = AiConnectionState(
            provider = provider,
            status = status,
            message = message,
            modelName = modelName,
            modelsCount = resolvedCount,
            updatedAt = now
        )
        _connectionStates.value = _connectionStates.value.toMutableMap().apply {
            put(provider, updatedState)
        }

        if (status != AiConnectionStatus.CONNECTING) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("ai_last_status_$provider", status.name)
                .putString("ai_last_msg_$provider", message)
                .putString("ai_last_model_$provider", modelName)
                .putInt("ai_last_count_$provider", resolvedCount)
                .putLong("ai_last_time_$provider", now)
                .apply()
        }
    }

    fun clearProviderCache(provider: String) {
        _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
            remove(provider)
        }
        if (provider == PROVIDER_GEMINI) {
            _liveTtsModels.value = emptyList()
        }
        recordConnectionState(
            provider = provider,
            status = AiConnectionStatus.DISCONNECTED,
            message = "Aún no has configurado una Clave API.",
            modelName = "",
            modelsCount = 0
        )
    }

    fun hasGeminiApiKey(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return !(prefs.getString("gemini_api_key", "") ?: "").trim().isBlank()
    }

    fun getAuraVoiceMode(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("aura_voice_mode", AURA_VOICE_MODE_DEVICE_TTS) ?: AURA_VOICE_MODE_DEVICE_TTS
        return if (raw == AURA_VOICE_MODE_GEMINI && hasGeminiApiKey()) {
            AURA_VOICE_MODE_GEMINI
        } else {
            AURA_VOICE_MODE_DEVICE_TTS
        }
    }

    fun isExpressiveGeminiVoiceActive(): Boolean =
        getAuraVoiceMode() == AURA_VOICE_MODE_GEMINI

    fun is38PlusTtsModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("gemini-3.8") || lower.contains("gemini-4")
    }

    fun supportsExpressive38TtsTags(): Boolean =
        isExpressiveGeminiVoiceActive() && is38PlusTtsModel(getSelectedGeminiTtsModel())

    fun getSelectedGeminiTtsModel(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userChosen = prefs.getBoolean("gemini_tts_model_user_chosen", false)
        val saved = (prefs.getString("gemini_tts_model", DEFAULT_GEMINI_TTS_MODEL) ?: DEFAULT_GEMINI_TTS_MODEL)
            .trim()
            .removePrefix("models/")
        if (!userChosen && saved == "gemini-2.5-flash-preview-tts") {
            return DEFAULT_GEMINI_TTS_MODEL
        }
        return saved.ifBlank { DEFAULT_GEMINI_TTS_MODEL }
    }

    fun getSelectedGeminiTtsVoice(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val saved = (prefs.getString("gemini_tts_voice", DEFAULT_GEMINI_TTS_VOICE) ?: DEFAULT_GEMINI_TTS_VOICE).trim()
        val validIds = GEMINI_TTS_VOICES.map { it.first }
        return if (saved in validIds) saved else DEFAULT_GEMINI_TTS_VOICE
    }

    fun pickPreferredTtsModelId(liveModels: List<Pair<String, String>>, currentModel: String?): String {
        val ids = liveModels.map { it.first }
        if (ids.isEmpty()) return currentModel?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_TTS_MODEL
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val userChosen = prefs.getBoolean("gemini_tts_model_user_chosen", false)
        if (!userChosen && DEFAULT_GEMINI_TTS_MODEL in ids) {
            return DEFAULT_GEMINI_TTS_MODEL
        }
        if (!currentModel.isNullOrBlank() && currentModel in ids) return currentModel
        return GEMINI_TTS_PRIORITY_ORDER.firstOrNull { it in ids } ?: ids.first()
    }

    fun defaultThinkingForModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return lower.contains("gpt-oss-120b") ||
            lower.contains("qwen3") ||
            lower.contains("r1") ||
            lower.contains("2.5-pro")
    }

    fun pickPreferredModelId(provider: String, liveModels: List<Pair<String, String>>, currentModel: String?): String {
        val ids = liveModels.map { it.first }
        if (ids.isEmpty()) return currentModel?.takeIf { it.isNotBlank() } ?: ""
        if (!currentModel.isNullOrBlank() && currentModel in ids) return currentModel

        val preferredOrder = if (provider == PROVIDER_GROQ) {
            listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "qwen/qwen3-32b", "openai/gpt-oss-20b")
        } else {
            GEMINI_PRIORITY_ORDER
        }
        return preferredOrder.firstOrNull { it in ids } ?: ids.first()
    }

    private fun parseFriendlyHttpError(httpCode: Int, rawBody: String, provider: String): String {
        val providerName = if (provider == PROVIDER_GROQ) "Groq" else "Google Gemini"
        val apiMsg = runCatching {
            JSONObject(rawBody).optJSONObject("error")?.optString("message")
        }.getOrNull()?.trim().orEmpty()

        return when (httpCode) {
            400 -> when {
                apiMsg.contains("API key", true) || apiMsg.contains("API_KEY_INVALID", true) ->
                    "La Clave API de $providerName no es válida."
                apiMsg.isNotBlank() -> "Solicitud rechazada ($providerName 400): $apiMsg"
                else -> "Error en la solicitud a $providerName (400)."
            }
            401 -> "Clave API de $providerName inválida o revocada (401)."
            403 -> "Acceso denegado por $providerName (403). Verifica los permisos de tu Clave API."
            404 -> "El modelo seleccionado ya no está disponible en $providerName (404)."
            429 -> "Límite temporal de peticiones alcanzado en $providerName (429). Intenta de nuevo en unos segundos."
            503 -> "El modelo de $providerName está saturado temporalmente (Error 503 por alta demanda). Reintenta en unos segundos o cambia temporalmente a otro modelo."
            in 500..599 -> if (apiMsg.isNotBlank()) "Error temporal en $providerName ($httpCode): $apiMsg" else "Error temporal en los servidores de $providerName ($httpCode)."
            else -> if (apiMsg.isNotBlank()) "Error de $providerName ($httpCode): $apiMsg" else "Error de conexión con $providerName ($httpCode)."
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val gson = GsonBuilder().create()

    private fun isGemmaModel(): Boolean = selectedModel.lowercase().contains("gemma")
    private fun isGemma4(): Boolean = selectedModel.lowercase().contains("gemma-4")
    private fun supportsAdvancedFeatures(): Boolean =
        activeProvider == PROVIDER_GROQ || !isGemmaModel() || isGemma4()

    private fun getSystemInstructionForRequest(): Content? =
        if (supportsAdvancedFeatures()) systemInstruction else null

    private val playContentTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "play_content",
                description = "Inicia el reproductor con una obra del catálogo ÚNICAMENTE cuando el usuario ordene de forma explícita reproducirla o ponerla ahora mismo (ej: 'reprodúcela', 'dale play', 'pon a reproducir...', 'quiero escucharla ya', 'sí, ponla'). PROHIBIDO usar esta herramienta cuando el usuario solo busque una obra, pregunte si está disponible o pida recomendaciones. Debes haber usado search_catalog antes para obtener el 'id' exacto.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf(
                        "contentId" to Property("string", "El valor EXACTO del campo 'id' devuelto por search_catalog. NO uses el título."),
                        "type" to Property("string", "El valor EXACTO del campo 'tipo' devuelto por search_catalog: pelicula, serie, documental o cortometraje"),
                        "seasonIndex" to Property("integer", "Índice de la temporada (empezando en 0, opcional)"),
                        "episodeIndex" to Property("integer", "Índice del episodio (empezando en 0, opcional)")
                    ),
                    required = listOf("contentId", "type")
                )
            )
        )
    )

    private val catalogSearchTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "search_catalog",
                description = "Busca películas, series, documentales o cortometrajes en el catálogo local por texto libre (título, director, actor o tema). IMPORTANTE: Si el usuario pide géneros exactos, combinaciones de géneros (ej. 'mangas de romance', 'anime de acción', 'terror psicológico'), países, años o categorías específicas, usa SIEMPRE 'query_catalog_sql' en lugar o como complemento de esta herramienta para evitar falsos positivos por título.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf("query" to Property("string", "Término de búsqueda libre: título, director, actor o tema")),
                    required = listOf("query")
                )
            )
        )
    )

    private val catalogSqlTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "query_catalog_sql",
                description = "Ejecuta una consulta SQL SELECT de solo lectura directamente sobre SQLite en las tablas 'catalog_items' o 'catalog_items_fts'. Úsala SIEMPRE para búsquedas por género exacto o combinado (ej. 'mangas de romance' -> LOWER(genero) LIKE '%manga%' AND LOWER(genero) LIKE '%romance%'), conteos exactos (COUNT), últimos estrenos/agregados, filtros combinados por categoría ('peliculas', 'series', 'documentales', 'cortometrajes'), año (anio), país (pais), director, idioma ('1'=España, '2'=Latino) o FTS4 MATCH. Máximo 20 filas.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf(
                        "sql" to Property(
                            "string",
                            "Consulta SQLite SELECT válida. Columnas de catalog_items: id, category, title, titleNorm, anio, genero, pais, director, guion, musica, fotografia, reparto, productora, narracion, duracion, idioma, partes, filmaffinity, sinopsis, sortOrder. REGLAS IMPORTANTES: 1) 'genero' almacena géneros separados por punto o coma (ej: 'Animación. Manga. Romance', 'Drama. Thriller'). Usa singular con LOWER(genero) LIKE '%termino%' combinando varios AND para filtros multi-género; recuerda que el anime suele figurar con genero LIKE '%manga%' y/o '%animaci%' y pais LIKE '%jap%'. Si dudas de los nombres exactos de género disponibles, puedes consultar primero SELECT DISTINCT genero FROM catalog_items WHERE LOWER(genero) LIKE '%...%' LIMIT 20. 2) 'anio' es texto de 4 dígitos (usa anio GLOB '[0-9][0-9][0-9][0-9]' AND CAST(anio AS INTEGER) al ordenar por año de estreno); 'sortOrder' es entero donde los números MÁS ALTOS (ORDER BY sortOrder DESC) son las obras MÁS RECIENTEMENTE AGREGADAS al catálogo. Para estrenos recientes usa ORDER BY CAST(anio AS INTEGER) DESC, sortOrder DESC LIMIT 5. Tabla FTS4: catalog_items_fts(titleNorm, searchTextNorm)."
                        )
                    ),
                    required = listOf("sql")
                )
            )
        )
    )

    private val readAppDocsTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "read_app_docs",
                description = "Lee la documentación oficial interna de Audiocinemateca para responder con exactitud sobre funciones de la app, controles del reproductor, Chromecast, DualSense, listas, descargas, cómo activar la IA y crear Claves API (Groq / Google Gemini), accesibilidad, TalkBack/Jieshuo, ajustes o novedades del proyecto.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf(
                        "topic" to Property(
                            "string",
                            "Tema a consultar: 'primeros_pasos', 'reproductor_dualsense', 'listas_descargas', 'aura_comunidad', 'configurar_ia', 'accesibilidad_ajustes', 'novedades_proyecto' o 'indice'."
                        )
                    ),
                    required = listOf("topic")
                )
            )
        )
    )

    private val userLibraryTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "get_user_library",
                description = "Consulta la biblioteca personal del usuario en la app: su historial reciente de reproducción (qué fue lo último que vio o escuchó, su progreso exacto y estadísticas totales), sus favoritos guardados (y total) o sus descargas sin conexión (incluyendo peso total en MB/GB, cantidad de archivos, títulos distintos y descargas en curso).",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf(
                        "section" to Property(
                            "string",
                            "Sección a consultar: 'history' (historial reciente y última obra vista), 'favorites' (favoritos), 'downloads' (descargas) o 'all' (resumen completo)."
                        ),
                        "limit" to Property(
                            "integer",
                            "Cantidad máxima de elementos a devolver por sección (por defecto 10)."
                        )
                    ),
                    required = listOf("section")
                )
            )
        )
    )

    private fun getToolsForRequest(): List<Tool>? =
        if (supportsAdvancedFeatures()) listOf(
            catalogSearchTool,
            catalogSqlTool,
            playContentTool,
            readAppDocsTool,
            userLibraryTool
        ) else null

    fun getActiveProvider(): String = activeProvider
    fun getSelectedModel(): String = selectedModel

    fun initialize(
        instructionText: String? = null,
        providerOverride: String? = null
    ): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val configuredProvider = prefs.getString("ai_provider", PROVIDER_GROQ) ?: PROVIDER_GROQ
        val groqKey = (prefs.getString("groq_api_key", "") ?: "").trim()
        val geminiKey = (prefs.getString("gemini_api_key", "") ?: "").trim()

        activeProvider = providerOverride ?: when {
            configuredProvider == PROVIDER_GROQ && groqKey.isNotBlank() -> PROVIDER_GROQ
            configuredProvider == PROVIDER_GEMINI && geminiKey.isNotBlank() -> PROVIDER_GEMINI
            groqKey.isNotBlank() -> PROVIDER_GROQ
            geminiKey.isNotBlank() -> PROVIDER_GEMINI
            else -> configuredProvider
        }

        if (activeProvider == PROVIDER_GROQ) {
            apiKey = groqKey
            selectedModel = (prefs.getString("groq_model", DEFAULT_GROQ_MODEL) ?: DEFAULT_GROQ_MODEL).trim()
            if (selectedModel.isBlank()) selectedModel = DEFAULT_GROQ_MODEL
        } else {
            apiKey = geminiKey
            selectedModel = (prefs.getString("gemini_model", DEFAULT_GEMINI_MODEL) ?: DEFAULT_GEMINI_MODEL)
                .trim()
                .removePrefix("models/")
            if (selectedModel.isBlank()) selectedModel = DEFAULT_GEMINI_MODEL
        }

        if (instructionText != null) {
            systemInstructionText = instructionText
            systemInstruction = Content(role = null, parts = listOf(Part(text = instructionText)))
        }

        if (apiKey.isBlank()) return false

        Log.d(TAG, "Inicializado con proveedor=${activeProvider.uppercase()}, modelo=$selectedModel")
        return true
    }

    suspend fun generateContentRating(
        title: String,
        description: String,
        category: String = "",
        genre: String = "",
        anio: String = ""
    ): String? = withContext(Dispatchers.IO) {
        if (!initialize()) return@withContext null
        if (apiKey.isBlank()) return@withContext null
        try {
            val metaParts = listOfNotNull(
                category.takeIf { it.isNotBlank() }?.let { "Tipo: $it" },
                anio.takeIf { it.isNotBlank() }?.let { "Año: $it" },
                genre.takeIf { it.isNotBlank() }?.let { "Género: $it" }
            ).joinToString(" | ")

            val prompt = """
                [DIRECTIVA: CLASIFICACION_CONTENIDO]
                Eres un clasificador profesional de contenidos audiovisuales (estándares internacionales MPAA / PEGI / TV Parental Guidelines).
                Analiza esta obra:
                - Título: '$title'
                ${if (metaParts.isNotBlank()) "- Datos: $metaParts\n" else ""}- Sinopsis: '$description'

                REGLAS ESTRICTAS DE FORMATO Y VOCABULARIO:
                1. Responde ÚNICAMENTE en una sola línea breve con este formato exacto:
                   <Edad> • <Descriptores profesionales>
                2. Para <Edad>, elige SOLAMENTE una opción exacta de esta lista:
                   Apto para todo público | 7+ | 12+ | 16+ | 18+
                3. Para <Descriptores profesionales>, usa EXCLUSIVAMENTE términos técnicos reales de advertencia de contenido cuando correspondan (separados por coma):
                   - Si es apta sin elementos sensibles: Sin contenido sensible
                   - Si contiene elementos sensibles, usa solo descriptores reales como: Temas para adultos, Violencia, Violencia moderada, Violencia intensa, Consumo de drogas, Alcohol y tabaco, Palabras ofensivas, Lenguaje inapropiado, Desnudos, Contenido sexual, Terror y suspenso intenso, Escenas perturbadoras.
                4. PROHIBIDO usar descripciones amateur, géneros o tonos (NUNCA escribas cosas como "contenido de aventura", "contenido de dibujos", "animación familiar", "escenas divertidas" o "diálogos de comedia").
                5. Sin comillas, sin asteriscos, sin explicaciones extra.

                Ejemplos válidos:
                Apto para todo público • Sin contenido sensible
                12+ • Violencia moderada, lenguaje inapropiado
                16+ • Violencia, palabras ofensivas, consumo de drogas
                18+ • Temas para adultos, violencia intensa, desnudos
            """.trimIndent()

            val res = generateContent(prompt, skipSystemInstruction = true, effortOverride = "low")
            res.getOrNull()
                ?.replace("**", "")
                ?.replace("\"", "")
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.replaceFirst(Regex("""^Clasificaci[oó]n\s*:?\s*""", RegexOption.IGNORE_CASE), "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateChapterSynopsis(
        title: String,
        chapterTitle: String,
        generalDescription: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!initialize()) return@withContext null
        if (apiKey.isBlank()) return@withContext null
        try {
            val contextInfo = if (seasonNumber != null && episodeNumber != null) {
                "Temporada $seasonNumber, Episodio $episodeNumber ('$chapterTitle') de la serie '$title'"
            } else {
                "Obra: '$title' ($chapterTitle)"
            }
            val prompt = """
                [DIRECTIVA: SINOPSIS_CAPITULO]
                Eres un redactor y crítico cinematográfico profesional para la plataforma accesible Audiocinemateca.
                Genera una sinopsis precisa, atractiva, elegante y profesional (de 2 a 4 oraciones) para el siguiente capítulo u obra:
                - Obra y Capítulo: $contextInfo
                - Sinopsis general de la obra: '$generalDescription'
                
                REGLAS FUNDAMENTALES:
                1. CERO SPOILERS: No reveles desenlaces, muertes, giros sorpresa ni resoluciones clave de la trama.
                2. Enfoque profesional y atmósfera cautivadora: Plantea el conflicto principal y el contexto del capítulo sin arruinar la experiencia auditiva.
                3. Responde ÚNICAMENTE con el texto de la sinopsis en español neutro, sin títulos extras, sin comillas envolventes ni introducciones.
            """.trimIndent()
            val res = generateContent(prompt)
            if (res.isSuccess) res.getOrNull()?.trim() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun generateMotivationalShareMessage(
        title: String,
        itemType: String,
        sinopsis: String
    ): String? = withContext(Dispatchers.IO) {
        if (!initialize()) return@withContext null
        if (apiKey.isBlank()) return@withContext null
        try {
            val prompt = """
                [DIRECTIVA: COMPARTIR_CONTENIDO_MOTIVACIONAL]
                Eres Aura, la asistente cinematográfica apasionada y entusiasta de Audiocinemateca.
                Un usuario está a punto de compartir la obra '$title' (tipo: $itemType) con sus amigos o redes.
                Genera un mensaje de recomendación cálido, entusiasta, cinematográfico y persuasivo (máximo 2 oraciones) invitando a escuchar esta obra en la app Audiocinemateca.
                
                Contexto:
                - Título: '$title'
                - Tipo: $itemType
                - Sinopsis: '$sinopsis'
                
                REGLAS ESTRICTAS:
                1. CERO SPOILERS: No cuentes finales ni giros clave. Destaca la emoción, intriga o belleza de la obra.
                2. NO agregues enlaces ni URLs en tu respuesta (el enlace se agregará automáticamente al final).
                3. Responde ÚNICAMENTE con el texto de la recomendación en español cálido y natural, sin comillas envolventes.
            """.trimIndent()
            val res = generateContent(prompt)
            if (res.isSuccess) res.getOrNull()?.trim() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Genera de forma inteligente un título breve, descriptivo y atractivo (2 a 5 palabras)
     * para la conversación actual basándose en el mensaje del usuario y la respuesta de Aura,
     * sin usar literalmente el texto del usuario.
     */
    suspend fun generateSmartChatTitle(
        userMessage: String,
        auraReply: String
    ): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            initialize()
        }
        if (apiKey.isBlank()) return@withContext null

        val systemPrompt = "Eres Aura, asistente de cine accesible. Tu única tarea es crear un título corto, inteligente y temático (de 2 a 5 palabras en español) que resuma de qué trata esta conversación."
        val userPrompt = """
            Mensaje del usuario: "${userMessage.take(220)}"
            Respuesta de Aura: "${auraReply.take(280)}"
            
            REGLAS ESTRICTAS:
            1. NO repitas ni copies literalmente el mensaje del usuario. Sintetiza la idea o el tema cinematográfico en un título elegante de 2 a 5 palabras (ejemplos: "Recomendaciones de Terror Psicológico", "Saga de Cars en orden", "Cine de Ciencia Ficción", "Detalles de Interestelar", "Comedias para el fin de semana").
            2. Responde ÚNICAMENTE con el título, sin comillas, sin punto final y sin explicaciones adicionales.
        """.trimIndent()

        try {
            val rawTitle = if (activeProvider == PROVIDER_GROQ) {
                // Usamos un modelo instantáneo y ligero en Groq para no consumir el TPM del modelo principal
                val fastTitleResult = executeGroqTitleRequest(systemPrompt, userPrompt, "llama-3.1-8b-instant")
                    .recoverCatching {
                        executeGroqTitleRequest(systemPrompt, userPrompt, "llama-3.3-70b-versatile").getOrThrow()
                    }
                    .recoverCatching {
                        executeGroqOneShot(systemPrompt, userPrompt, 0.3f, stripReasoningParams = true).getOrThrow()
                    }
                fastTitleResult.getOrNull()
            } else {
                generateContent("$systemPrompt\n\n$userPrompt").getOrNull()
            }

            rawTitle
                ?.let { stripThinkingTags(it) }
                ?.lineSequence()
                ?.firstOrNull { it.isNotBlank() }
                ?.replace(Regex("""^(título|titulo|tema)\s*:\s*""", RegexOption.IGNORE_CASE), "")
                ?.trim('"', '\'', '«', '»', '.', '!', '¡', '?', '¿', '-', '*', ' ')
                ?.takeIf { it.length in 3..60 }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo generar título inteligente con IA", e)
            null
        }
    }

    private fun executeGroqTitleRequest(
        systemPrompt: String,
        userPrompt: String,
        modelName: String
    ): Result<String> {
        return try {
            val bodyJson = JSONObject().apply {
                put("model", modelName)
                put("temperature", 0.3)
                put("max_completion_tokens", 30)
                val messagesArr = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
                put("messages", messagesArr)
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val rawBody = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    val parsed = JSONObject(rawBody)
                    val content = parsed.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "") ?: ""
                    val clean = stripThinkingTags(content)
                    if (clean.isNotBlank()) Result.success(clean)
                    else Result.failure(Exception("Título vacío"))
                } else {
                    Result.failure(Exception("HTTP ${res.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startChat() {
        chatHistory.clear()
    }

    fun getHistory(): List<Content> = chatHistory.toList()

    fun setHistory(history: List<Content>) {
        chatHistory.clear()
        chatHistory.addAll(sanitizeChatHistoryList(history))
    }

    /**
     * Limpia del historial cualquier turno con llamadas a función huérfanas o desbalanceadas
     * para evitar errores HTTP 400 permanentes en Groq y Gemini.
     */
    private fun sanitizeChatHistoryList(source: List<Content>): MutableList<Content> {
        val cleaned = mutableListOf<Content>()
        var i = 0
        while (i < source.size) {
            val turn = source[i]
            val fnCalls = turn.parts.mapNotNull { it.functionCall }
            if (turn.role == "model" && fnCalls.isNotEmpty()) {
                val nextTurn = source.getOrNull(i + 1)
                val fnResponses = nextTurn?.parts?.mapNotNull { it.functionResponse }.orEmpty()
                val callIds = fnCalls.mapNotNull { it.id }.toSet()
                val respIds = fnResponses.mapNotNull { it.id }.toSet()
                val isBalanced = nextTurn?.role == "user" &&
                    fnResponses.size == fnCalls.size &&
                    (callIds.isEmpty() || callIds == respIds)
                if (isBalanced) {
                    cleaned.add(turn)
                    cleaned.add(nextTurn!!)
                    i += 2
                    continue
                } else {
                    // Descartar par huérfano/corrupto para que no rompa los siguientes turnos
                    i += if (nextTurn?.role == "user" && fnResponses.isNotEmpty()) 2 else 1
                    continue
                }
            }
            if (turn.role == "user" && turn.parts.any { it.functionResponse != null }) {
                // Respuesta de función suelta sin su functionCall previo
                i++
                continue
            }
            cleaned.add(turn)
            i++
        }
        return cleaned
    }

    private fun sanitizeActiveHistory() {
        chatHistory = sanitizeChatHistoryList(chatHistory)
    }

    private fun supportsAdvancedFeaturesForModel(modelName: String): Boolean {
        val lower = modelName.lowercase()
        if (activeProvider == PROVIDER_GROQ) {
            return !lower.contains("compound")
        }
        val isGemma = lower.contains("gemma")
        val isGemma4 = lower.contains("gemma-4")
        return !isGemma || isGemma4
    }

    private fun getToolsForModel(modelName: String): List<Tool>? =
        if (supportsAdvancedFeaturesForModel(modelName)) listOf(
            catalogSearchTool,
            catalogSqlTool,
            playContentTool,
            readAppDocsTool,
            userLibraryTool
        ) else null

    private fun getSystemInstructionForModel(modelName: String): Content? =
        if (supportsAdvancedFeaturesForModel(modelName)) systemInstruction else null

    private fun getReasoningEffort(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val rawEffort = prefs.getString("ai_reasoning_effort", "medium")?.trim().orEmpty()
        return if (rawEffort in listOf("low", "medium", "high", "extra")) rawEffort else "medium"
    }

    private fun buildThinkingConfig(
        modelName: String = selectedModel,
        includeThoughts: Boolean = false,
        effortOverride: String? = null
    ): ThinkingConfig? {
        if (!supportsAdvancedFeaturesForModel(modelName)) return null

        val model = modelName.lowercase()
        val effort = effortOverride ?: getReasoningEffort()
        val emitThoughts = if (includeThoughts) true else null

        return when {
            model.contains("gemini-4") -> {
                val level = when (effort) {
                    "low" -> "low"
                    "high" -> "high"
                    "extra" -> "xhigh"
                    else -> "medium"
                }
                ThinkingConfig(includeThoughts = emitThoughts, thinkingLevel = level)
            }
            // Gemma 4 solo acepta thinkingLevel = "minimal" o "high" en la Gemini API;
            // enviar "low" o "medium" provoca HTTP 400 INVALID_ARGUMENT.
            model.contains("gemma-4") -> {
                val level = if (effort == "low") "minimal" else "high"
                ThinkingConfig(includeThoughts = emitThoughts, thinkingLevel = level)
            }
            model.contains("gemini-3") -> {
                val level = when (effort) {
                    "low" -> "low"
                    "high", "extra" -> "high"
                    else -> "medium"
                }
                ThinkingConfig(includeThoughts = emitThoughts, thinkingLevel = level)
            }
            model.contains("gemini-2.5") && model.contains("pro") -> {
                val budget = when (effort) {
                    "low" -> 1024
                    "high" -> 8192
                    "extra" -> 16384
                    else -> 4096
                }
                ThinkingConfig(includeThoughts = emitThoughts, thinkingBudget = budget)
            }
            model.contains("gemini-2.5") -> {
                val budget = when (effort) {
                    "low" -> 1024
                    "high" -> 4096
                    "extra" -> 12288
                    else -> 2048
                }
                ThinkingConfig(includeThoughts = emitThoughts, thinkingBudget = budget)
            }
            else -> null
        }
    }

    private fun buildGeminiGenerationConfig(
        modelName: String,
        defaultTemp: Float,
        includeThoughts: Boolean = false,
        effortOverride: String? = null,
        maxOutputTokens: Int? = null,
        disableThinkingConfig: Boolean = false
    ): GenerationConfig {
        val lower = modelName.lowercase()
        // En Gemini 4 y Gemini 3 temperature está deprecado/ignorado y los niveles de razonamiento controlan la generación
        val omitTemperature = lower.contains("gemini-4") || lower.contains("gemini-3")
        return GenerationConfig(
            temperature = if (omitTemperature) null else defaultTemp,
            maxOutputTokens = maxOutputTokens,
            thinkingConfig = if (disableThinkingConfig) {
                null
            } else {
                buildThinkingConfig(
                    modelName = modelName,
                    includeThoughts = includeThoughts,
                    effortOverride = effortOverride
                )
            }
        )
    }

    /**
     * Backoff exponencial con jitter aleatorio siguiendo las guías oficiales para mitigar 503 y 429.
     */
    private fun computeRetryDelayMs(attempt: Int): Long {
        val expStep = (attempt - 2).coerceIn(0, 4)
        val base = 750L * (1 shl expStep)
        return base + Random.nextLong(150L, 550L)
    }

    /**
     * Conserva todo el historial del chat activo intacto por defecto (`maxTurns == null`) para dar
     * memoria completa a Aura y mantener un prefijo estable que aproveche el Implicit Context Caching
     * de Gemini y el Prompt Caching automático de Groq. Solo recorta de forma segura si un modelo
     * reporta desbordamiento real de ventana de contexto (`maxTurns != null`).
     */
    private fun sliceSafeHistoryWindow(maxTurns: Int? = null): List<Content> {
        if (maxTurns == null || chatHistory.size <= maxTurns) {
            return chatHistory.toList()
        }
        var startIdx = (chatHistory.size - maxTurns).coerceAtLeast(0)
        // Asegurar iniciar siempre en un turno humano real ("user" sin functionResponse)
        while (startIdx < chatHistory.size - 1) {
            val turn = chatHistory[startIdx]
            val isPlainUserTurn = turn.role == "user" && turn.parts.none { it.functionResponse != null }
            if (isPlainUserTurn) break
            startIdx++
        }
        return chatHistory.subList(startIdx, chatHistory.size).toList()
    }

    private fun buildGeminiContentsForRequest(maxTurnsOverride: Int? = null): List<Content> {
        return sliceSafeHistoryWindow(maxTurnsOverride)
    }

    private fun isContextOverflowError(httpCode: Int, rawBody: String): Boolean {
        if (httpCode == 413) return true
        if (httpCode != 400) return false
        val lower = rawBody.lowercase()
        return lower.contains("context_length_exceeded") ||
            lower.contains("maximum number of tokens") ||
            lower.contains("exceeds the maximum") ||
            lower.contains("reduce the length") ||
            lower.contains("request too large") ||
            lower.contains("prompt is too long")
    }

    private fun logGeminiCacheUsageIfPresent(
        modelName: String,
        usage: com.johang.audiocinemateca.data.remote.ai.UsageMetadata?
    ) {
        val cached = usage?.cachedContentTokenCount ?: 0
        if (cached > 0) {
            Log.d(TAG, "Gemini [$modelName] implicit context cache hit: $cached/${usage?.promptTokenCount ?: 0} tokens cacheados")
        }
    }

    private fun stripThinkingTags(raw: String): String {
        return raw
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<thought>[\\s\\S]*?</thought>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<reasoning>[\\s\\S]*?</reasoning>", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    // =========================================================================
    // FLUJO DE CONVERSACIÓN MULTITURNO, REINTENTOS Y FALLBACK INTELIGENTE
    // =========================================================================

    private fun isAuthHttpError(code: Int): Boolean = code == 401 || code == 403

    private suspend fun getVerifiedFallbackCandidates(
        provider: String,
        excludedModel: String
    ): List<String> {
        var liveList = _liveModelsByProvider.value[provider].orEmpty()
        if (liveList.isEmpty()) {
            liveList = fetchAvailableModelsLive(providerOverride = provider).models
        }
        val availableIds = liveList.map { it.first }.filter { it.isNotBlank() && !it.equals(excludedModel, ignoreCase = true) }
        if (availableIds.isEmpty()) return emptyList()

        val priority = if (provider == PROVIDER_GROQ) {
            listOf(
                "openai/gpt-oss-120b",
                "llama-3.3-70b-versatile",
                "qwen/qwen3-32b",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "openai/gpt-oss-20b",
                "llama-3.1-8b-instant"
            )
        } else {
            GEMINI_PRIORITY_ORDER
        }

        val ranked = availableIds.sortedWith(
            compareBy<String> { id ->
                val lower = id.lowercase()
                val priIdx = priority.indexOfFirst { it.equals(id, ignoreCase = true) }
                when {
                    priIdx >= 0 -> priIdx
                    lower.contains("compound") -> 10_000
                    provider == PROVIDER_GEMINI && lower.contains("gemma-3") -> 9_000
                    provider == PROVIDER_GEMINI && lower.contains("gemma") -> 500
                    else -> 200
                }
            }.thenBy { it }
        )
        return ranked.take(3)
    }

    private suspend fun executeGroqTurnWithRetryAndFallback(
        withTools: Boolean,
        onProgressStatus: (suspend (String) -> Unit)? = null
    ): Result<GoogleAiResponse> {
        val primaryModel = selectedModel
        var lastErrorMsg = "Error desconocido con Groq."
        var sawAuthError = false

        // 1. Reintentar varias veces con el mismo modelo seleccionado
        for (attempt in 1..3) {
            if (attempt > 1) {
                onProgressStatus?.invoke("Reintentando con $primaryModel ($attempt/3)...")
                delay(if (attempt == 2) 650L else 1300L)
            }
            val attemptRes = executeGroqSingleAttempt(
                modelName = primaryModel,
                withTools = withTools,
                allowFallbackToolStrip = true
            )
            if (attemptRes.response != null) {
                return Result.success(attemptRes.response)
            }
            lastErrorMsg = attemptRes.errorMessage
            if (attemptRes.isAuthError) {
                sawAuthError = true
                break
            }
            if (attemptRes.httpCode == 404) {
                // Modelo no existe / retirado: pasar directo a alternativa real
                break
            }
        }

        // 2. Fallback inteligente usando modelos reales existentes en la API de Groq
        if (!sawAuthError) {
            val candidates = getVerifiedFallbackCandidates(PROVIDER_GROQ, primaryModel)
            for (altModel in candidates) {
                for (attempt in 1..2) {
                    onProgressStatus?.invoke("Probando modelo alternativo $altModel...")
                    if (attempt > 1) delay(700L)
                    val altRes = executeGroqSingleAttempt(
                        modelName = altModel,
                        withTools = withTools && supportsAdvancedFeaturesForModel(altModel),
                        allowFallbackToolStrip = true
                    )
                    if (altRes.response != null) {
                        Log.i(TAG, "Groq respondió correctamente tras cambiar a modelo alternativo '$altModel'")
                        recordConnectionState(
                            provider = PROVIDER_GROQ,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado con $altModel (respaldo automático tras fallo en $primaryModel).",
                            modelName = altModel
                        )
                        return Result.success(altRes.response)
                    }
                    lastErrorMsg = altRes.errorMessage
                    if (altRes.isAuthError) {
                        sawAuthError = true
                        break
                    }
                    if (altRes.httpCode == 404) break
                }
                if (sawAuthError) break
            }
        }

        recordConnectionState(PROVIDER_GROQ, AiConnectionStatus.ERROR, lastErrorMsg, modelName = primaryModel)
        return Result.failure(Exception(lastErrorMsg))
    }

    private suspend fun executeGeminiNonStreamWithRetryAndFallback(
        onProgressStatus: (suspend (String) -> Unit)? = null
    ): Result<GoogleAiResponse> {
        val primaryModel = selectedModel
        var lastErrorMsg = "Error desconocido con Google Gemini."
        var sawAuthError = false

        suspend fun callOnce(
            targetModel: String,
            useTools: Boolean,
            maxTurnsOverride: Int? = null,
            disableThinkingConfig: Boolean = false
        ): GroqOrGeminiAttemptResult {
            val request = GoogleAiRequest(
                contents = buildGeminiContentsForRequest(maxTurnsOverride),
                systemInstruction = getSystemInstructionForModel(targetModel),
                tools = if (useTools) getToolsForModel(targetModel) else null,
                generationConfig = buildGeminiGenerationConfig(
                    modelName = targetModel,
                    defaultTemp = 0.7f,
                    includeThoughts = false,
                    disableThinkingConfig = disableThinkingConfig
                )
            )
            return try {
                val res = googleAiApiService.generateContent(targetModel, apiKey, request)
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    logGeminiCacheUsageIfPresent(targetModel, body.usageMetadata)
                    val content = body.candidates?.firstOrNull()?.content
                    if (content != null && content.parts.isNotEmpty()) {
                        chatHistory.add(content)
                        GroqOrGeminiAttemptResult(response = body)
                    } else {
                        GroqOrGeminiAttemptResult(httpCode = 200, errorMessage = "Google Gemini ($targetModel) devolvió una respuesta vacía.")
                    }
                } else {
                    val rawErr = res.errorBody()?.string() ?: ""
                    Log.w(TAG, "Gemini/Gemma HTTP ${res.code()} [$targetModel]: $rawErr")
                    if (maxTurnsOverride == null && isContextOverflowError(res.code(), rawErr) && chatHistory.size > 16) {
                        val safeLimit = (chatHistory.size / 2).coerceIn(16, 80)
                        Log.w(TAG, "Gemini [$targetModel] alcanzó límite de contexto; reintentando con últimos $safeLimit turnos")
                        return callOnce(targetModel, useTools, maxTurnsOverride = safeLimit, disableThinkingConfig = disableThinkingConfig)
                    }
                    if (res.code() == 400 && !disableThinkingConfig && request.generationConfig?.thinkingConfig != null) {
                        Log.w(TAG, "Gemini/Gemma [$targetModel] rechazó parámetros (400); reintentando en el mismo modelo sin thinkingConfig")
                        return callOnce(targetModel, useTools, maxTurnsOverride = maxTurnsOverride, disableThinkingConfig = true)
                    }
                    val friendly = parseFriendlyHttpError(res.code(), rawErr, PROVIDER_GEMINI)
                    GroqOrGeminiAttemptResult(
                        httpCode = res.code(),
                        isAuthError = isAuthHttpError(res.code()),
                        errorMessage = friendly
                    )
                }
            } catch (e: Exception) {
                GroqOrGeminiAttemptResult(
                    errorMessage = "Error de red con Google Gemini ($targetModel): ${e.message ?: "tiempo de espera agotado"}"
                )
            }
        }

        for (attempt in 1..3) {
            if (attempt > 1) {
                onProgressStatus?.invoke("Reintentando con $primaryModel ($attempt/3)...")
                delay(computeRetryDelayMs(attempt))
            }
            val attemptRes = callOnce(primaryModel, useTools = true)
            if (attemptRes.response != null) {
                recordConnectionState(
                    provider = PROVIDER_GEMINI,
                    status = AiConnectionStatus.CONNECTED,
                    message = "Conectado correctamente con Google Gemini ($primaryModel).",
                    modelName = primaryModel
                )
                return Result.success(attemptRes.response)
            }
            lastErrorMsg = attemptRes.errorMessage
            if (attemptRes.isAuthError) {
                sawAuthError = true
                break
            }
            if (attemptRes.httpCode in listOf(400, 404)) break
        }

        if (!sawAuthError) {
            val candidates = getVerifiedFallbackCandidates(PROVIDER_GEMINI, primaryModel)
            for (altModel in candidates) {
                for (attempt in 1..2) {
                    onProgressStatus?.invoke("Probando modelo alternativo $altModel...")
                    if (attempt > 1) delay(computeRetryDelayMs(attempt))
                    val altRes = callOnce(altModel, useTools = supportsAdvancedFeaturesForModel(altModel))
                    if (altRes.response != null) {
                        Log.i(TAG, "Gemini respondió correctamente tras cambiar a modelo alternativo '$altModel'")
                        recordConnectionState(
                            provider = PROVIDER_GEMINI,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado con $altModel (respaldo automático tras fallo en $primaryModel).",
                            modelName = altModel
                        )
                        return Result.success(altRes.response)
                    }
                    lastErrorMsg = altRes.errorMessage
                    if (altRes.isAuthError) {
                        sawAuthError = true
                        break
                    }
                    if (altRes.httpCode == 404) break
                }
                if (sawAuthError) break
            }
        }

        recordConnectionState(PROVIDER_GEMINI, AiConnectionStatus.ERROR, lastErrorMsg, modelName = primaryModel)
        return Result.failure(Exception(lastErrorMsg))
    }

    private data class GroqOrGeminiAttemptResult(
        val response: GoogleAiResponse? = null,
        val httpCode: Int = 0,
        val isAuthError: Boolean = false,
        val errorMessage: String = ""
    )

    fun sendMessageStream(message: String): Flow<StreamEvent> = flow {
        initialize()
        if (apiKey.isBlank()) {
            emit(StreamEvent.Error("No hay Clave API configurada. Ve a Ajustes de IA para configurar Groq o Gemini."))
            return@flow
        }

        sanitizeActiveHistory()

        if (activeProvider == PROVIDER_GROQ) {
            chatHistory.add(Content(role = "user", parts = listOf(Part(text = message))))
            val groqResult = executeGroqTurnWithRetryAndFallback(
                withTools = true,
                onProgressStatus = { statusText -> emit(StreamEvent.StatusUpdate(statusText)) }
            )
            groqResult.fold(
                onSuccess = { response ->
                    val parts = response.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                    if (parts.isNotEmpty()) {
                        recordConnectionState(
                            provider = PROVIDER_GROQ,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado correctamente con Groq ($selectedModel).",
                            modelName = selectedModel
                        )
                        for (part in parts) {
                            part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                            if (part.thought == true) continue
                            part.text?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.TextDelta(it)) }
                        }
                    } else {
                        rollbackLastUserMessage()
                        val msg = "El modelo no devolvió respuesta tras varios intentos."
                        recordConnectionState(PROVIDER_GROQ, AiConnectionStatus.ERROR, msg, modelName = selectedModel)
                        emit(StreamEvent.Error(msg))
                    }
                },
                onFailure = { err ->
                    rollbackLastUserMessage()
                    val msg = err.message ?: "Error al conectar con Groq."
                    emit(StreamEvent.Error(msg))
                }
            )
            return@flow
        }

        // Flujo para Google Gemini con reintentos en el mismo modelo y fallback inteligente a modelos reales
        val actualMessage = if (!supportsAdvancedFeaturesForModel(selectedModel) && chatHistory.isEmpty() && systemInstructionText != null) {
            "[CONTEXTO]\n${systemInstructionText}\n\n[MENSAJE]\n$message"
        } else {
            message
        }
        chatHistory.add(Content(role = "user", parts = listOf(Part(text = actualMessage))))

        val primaryModel = selectedModel
        var lastErrorMsg = "Error de conexión con Google Gemini."
        var sawAuthError = false
        var streamedSuccess = false
        var geminiOverflowMaxTurns: Int? = null

        for (attempt in 1..3) {
            if (attempt > 1) {
                emit(StreamEvent.StatusUpdate("Reintentando con $primaryModel ($attempt/3)..."))
                delay(computeRetryDelayMs(attempt))
            }

            // En los intentos 1-2 usamos SSE con includeThoughts=true (mantiene vivo el socket mientras razona);
            // en el 3er intento usamos generateContent directo por si el streaming falla en la red/proxy.
            if (attempt <= 2) {
                val request = GoogleAiRequest(
                    contents = buildGeminiContentsForRequest(geminiOverflowMaxTurns),
                    systemInstruction = getSystemInstructionForModel(primaryModel),
                    tools = getToolsForModel(primaryModel),
                    generationConfig = buildGeminiGenerationConfig(primaryModel, 0.7f, includeThoughts = true)
                )
                try {
                    val response = googleAiApiService.streamGenerateContent(primaryModel, apiKey, request)
                    if (response.isSuccessful && response.body() != null) {
                        val source = response.body()!!.source()
                        val modelParts = mutableListOf<Part>()
                        var streamChunkError: String? = null
                        var lastUsage: com.johang.audiocinemateca.data.remote.ai.UsageMetadata? = null

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) {
                                val json = line.substring(6).trim()
                                if (json.isEmpty() || json == "[DONE]") continue
                                val chunk = runCatching { gson.fromJson(json, GoogleAiResponse::class.java) }.getOrNull()
                                if (chunk?.usageMetadata != null) {
                                    lastUsage = chunk.usageMetadata
                                }
                                if (chunk?.error != null) {
                                    streamChunkError = chunk.error.message
                                    break
                                }
                                val parts = chunk?.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                                if (parts.isNotEmpty()) {
                                    modelParts.addAll(parts)
                                    for (part in parts) {
                                        part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                                        if (part.thought == true) continue
                                        part.text?.let { emit(StreamEvent.TextDelta(it)) }
                                    }
                                }
                            }
                        }

                        logGeminiCacheUsageIfPresent(primaryModel, lastUsage)
                        val hasActionableOutput = modelParts.any {
                            it.functionCall != null || (it.thought != true && !it.text.isNullOrBlank())
                        }
                        if (hasActionableOutput) {
                            chatHistory.add(Content(role = "model", parts = modelParts.toList()))
                            recordConnectionState(
                                provider = PROVIDER_GEMINI,
                                status = AiConnectionStatus.CONNECTED,
                                message = "Conectado correctamente con Google Gemini ($primaryModel).",
                                modelName = primaryModel
                            )
                            streamedSuccess = true
                            break
                        } else {
                            lastErrorMsg = streamChunkError ?: "Google Gemini ($primaryModel) devolvió respuesta vacía."
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: ""
                        Log.w(TAG, "Gemini/Gemma stream HTTP ${response.code()} [$primaryModel]: $errorBody")
                        if (geminiOverflowMaxTurns == null && isContextOverflowError(response.code(), errorBody) && chatHistory.size > 16) {
                            geminiOverflowMaxTurns = (chatHistory.size / 2).coerceIn(16, 80)
                            Log.w(TAG, "Gemini stream [$primaryModel] superó contexto; reintentando con últimos $geminiOverflowMaxTurns turnos")
                        } else if (response.code() == 400 && request.generationConfig?.thinkingConfig != null) {
                            // Recuperación silenciosa inmediata en el mismo modelo sin thinkingConfig si rechazó parámetros de razonamiento
                            val recoveryReq = GoogleAiRequest(
                                contents = buildGeminiContentsForRequest(geminiOverflowMaxTurns),
                                systemInstruction = getSystemInstructionForModel(primaryModel),
                                tools = getToolsForModel(primaryModel),
                                generationConfig = buildGeminiGenerationConfig(
                                    modelName = primaryModel,
                                    defaultTemp = 0.7f,
                                    includeThoughts = false,
                                    disableThinkingConfig = true
                                )
                            )
                            val recRes = runCatching { googleAiApiService.generateContent(primaryModel, apiKey, recoveryReq) }.getOrNull()
                            val recBody = if (recRes?.isSuccessful == true) recRes.body() else null
                            val recContent = recBody?.candidates?.firstOrNull()?.content
                            if (recContent != null && recContent.parts.any { it.functionCall != null || (it.thought != true && !it.text.isNullOrBlank()) }) {
                                logGeminiCacheUsageIfPresent(primaryModel, recBody.usageMetadata)
                                chatHistory.add(recContent)
                                recordConnectionState(
                                    provider = PROVIDER_GEMINI,
                                    status = AiConnectionStatus.CONNECTED,
                                    message = "Conectado correctamente con Google Gemini ($primaryModel).",
                                    modelName = primaryModel
                                )
                                for (part in recContent.parts) {
                                    part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                                    if (part.thought == true) continue
                                    part.text?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.TextDelta(it)) }
                                }
                                streamedSuccess = true
                                break
                            }
                            lastErrorMsg = parseFriendlyHttpError(response.code(), errorBody, PROVIDER_GEMINI)
                            break
                        }
                        lastErrorMsg = parseFriendlyHttpError(response.code(), errorBody, PROVIDER_GEMINI)
                        if (isAuthHttpError(response.code())) {
                            sawAuthError = true
                            break
                        }
                        if (response.code() in listOf(400, 404)) break
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Error de conexión con Google Gemini ($primaryModel): ${e.message ?: "tiempo de espera agotado"}"
                }
            } else {
                val request = GoogleAiRequest(
                    contents = buildGeminiContentsForRequest(geminiOverflowMaxTurns),
                    systemInstruction = getSystemInstructionForModel(primaryModel),
                    tools = getToolsForModel(primaryModel),
                    generationConfig = buildGeminiGenerationConfig(primaryModel, 0.7f, includeThoughts = false)
                )
                try {
                    val res = googleAiApiService.generateContent(primaryModel, apiKey, request)
                    if (res.isSuccessful && res.body() != null) {
                        logGeminiCacheUsageIfPresent(primaryModel, res.body()!!.usageMetadata)
                        val content = res.body()!!.candidates?.firstOrNull()?.content
                        val hasActionable = content != null && content.parts.any {
                            it.functionCall != null || (it.thought != true && !it.text.isNullOrBlank())
                        }
                        if (hasActionable && content != null) {
                            chatHistory.add(content)
                            recordConnectionState(
                                provider = PROVIDER_GEMINI,
                                status = AiConnectionStatus.CONNECTED,
                                message = "Conectado correctamente con Google Gemini ($primaryModel).",
                                modelName = primaryModel
                            )
                            for (part in content.parts) {
                                part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                                if (part.thought == true) continue
                                part.text?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.TextDelta(it)) }
                            }
                            streamedSuccess = true
                            break
                        } else {
                            lastErrorMsg = "Google Gemini ($primaryModel) devolvió respuesta vacía."
                        }
                    } else {
                        val errorBody = res.errorBody()?.string() ?: ""
                        Log.w(TAG, "Gemini/Gemma HTTP ${res.code()} [$primaryModel]: $errorBody")
                        lastErrorMsg = parseFriendlyHttpError(res.code(), errorBody, PROVIDER_GEMINI)
                        if (isAuthHttpError(res.code())) {
                            sawAuthError = true
                            break
                        }
                        if (res.code() in listOf(400, 404)) break
                    }
                } catch (e: Exception) {
                    lastErrorMsg = "Error de conexión con Google Gemini ($primaryModel): ${e.message ?: "tiempo de espera agotado"}"
                }
            }
        }

        if (streamedSuccess) return@flow

        // Fallback inteligente a otro modelo real de Gemini si el seleccionado falló tras reintentar
        if (!sawAuthError) {
            val fallbackResult = run {
                val candidates = getVerifiedFallbackCandidates(PROVIDER_GEMINI, primaryModel)
                var chosenRes: Result<GoogleAiResponse>? = null
                for (altModel in candidates) {
                    emit(StreamEvent.StatusUpdate("Probando modelo alternativo $altModel..."))
                    for (attempt in 1..2) {
                        if (attempt > 1) delay(computeRetryDelayMs(attempt))
                        val request = GoogleAiRequest(
                            contents = buildGeminiContentsForRequest(),
                            systemInstruction = getSystemInstructionForModel(altModel),
                            tools = getToolsForModel(altModel),
                            generationConfig = buildGeminiGenerationConfig(altModel, 0.7f, includeThoughts = false)
                        )
                        try {
                            val res = googleAiApiService.generateContent(altModel, apiKey, request)
                            if (res.isSuccessful && res.body() != null) {
                                val aiResp = res.body()!!
                                val content = aiResp.candidates?.firstOrNull()?.content
                                if (content != null && content.parts.isNotEmpty()) {
                                    chatHistory.add(content)
                                    recordConnectionState(
                                        provider = PROVIDER_GEMINI,
                                        status = AiConnectionStatus.CONNECTED,
                                        message = "Conectado con $altModel (respaldo automático tras fallo en $primaryModel).",
                                        modelName = altModel
                                    )
                                    chosenRes = Result.success(aiResp)
                                    break
                                }
                            } else {
                                val rawErr = res.errorBody()?.string() ?: ""
                                lastErrorMsg = parseFriendlyHttpError(res.code(), rawErr, PROVIDER_GEMINI)
                                if (isAuthHttpError(res.code())) {
                                    sawAuthError = true
                                    break
                                }
                                if (res.code() == 404) break
                            }
                        } catch (e: Exception) {
                            lastErrorMsg = "Error de conexión con $altModel: ${e.message ?: "error de red"}"
                        }
                    }
                    if (chosenRes != null || sawAuthError) break
                }
                chosenRes
            }

            if (fallbackResult != null && fallbackResult.isSuccess) {
                val parts = fallbackResult.getOrNull()?.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                for (part in parts) {
                    part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                    if (part.thought == true) continue
                    part.text?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.TextDelta(it)) }
                }
                return@flow
            }
        }

        rollbackLastUserMessage()
        recordConnectionState(PROVIDER_GEMINI, AiConnectionStatus.ERROR, lastErrorMsg, modelName = primaryModel)
        emit(StreamEvent.Error(lastErrorMsg))
    }.flowOn(Dispatchers.IO)

    private fun rollbackLastUserMessage() {
        if (chatHistory.isNotEmpty() && chatHistory.last().role == "user") {
            chatHistory.removeAt(chatHistory.size - 1)
        }
    }

    fun rollbackLastTurn() {
        if (chatHistory.isNotEmpty() && chatHistory.last().role == "model") {
            chatHistory.removeAt(chatHistory.size - 1)
        }
    }

    /**
     * Registra localmente el resultado de una herramienta terminal (como play_content)
     * y el cierre del asistente en el historial sin gastar otra llamada HTTP ni tokens adicionales.
     */
    fun recordLocalFunctionCompletion(
        functionName: String,
        functionCallId: String?,
        responseMap: Map<String, Any>,
        assistantClosingText: String? = null
    ) {
        chatHistory.add(
            Content(
                role = "user",
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = functionName,
                            id = functionCallId,
                            response = responseMap
                        )
                    )
                )
            )
        )
        if (!assistantClosingText.isNullOrBlank()) {
            chatHistory.add(
                Content(
                    role = "model",
                    parts = listOf(Part(text = assistantClosingText))
                )
            )
        }
    }

    suspend fun sendFunctionResponse(
        functionName: String,
        functionCallId: String?,
        responseMap: Map<String, Any>
    ): Result<GoogleAiResponse> {
        return sendFunctionResponses(
            listOf(
                FunctionResponse(
                    name = functionName,
                    id = functionCallId,
                    response = responseMap
                )
            )
        )
    }

    suspend fun sendFunctionResponses(
        responses: List<FunctionResponse>
    ): Result<GoogleAiResponse> = withContext(Dispatchers.IO) {
        if (responses.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No hay respuestas de herramientas para enviar."))
        }

        chatHistory.add(
            Content(
                role = "user",
                parts = responses.map { Part(functionResponse = it) }
            )
        )

        Log.d(TAG, "sendFunctionResponses (${activeProvider.uppercase()}): count=${responses.size}, names=${responses.joinToString { it.name }}, historial=${chatHistory.size}")

        val res = if (activeProvider == PROVIDER_GROQ) {
            executeGroqTurnWithRetryAndFallback(withTools = true)
        } else {
            executeGeminiNonStreamWithRetryAndFallback()
        }

        if (res.isFailure) {
            rollbackLastUserMessage()
        }
        res
    }

    suspend fun generateFinalResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        initialize()
        sanitizeActiveHistory()
        chatHistory.add(Content(role = "user", parts = listOf(Part(text = prompt))))

        val res = if (activeProvider == PROVIDER_GROQ) {
            executeGroqTurnWithRetryAndFallback(withTools = false)
        } else {
            executeGeminiNonStreamWithRetryAndFallback()
        }

        res.fold(
            onSuccess = { aiResp ->
                val text = aiResp.candidates?.firstOrNull()?.content?.parts
                    ?.firstOrNull { !it.text.isNullOrBlank() && it.thought != true }?.text
                if (text != null) Result.success(text)
                else {
                    rollbackLastUserMessage()
                    Result.failure(Exception("El modelo devolvió una respuesta vacía."))
                }
            },
            onFailure = { err ->
                rollbackLastUserMessage()
                Result.failure(err)
            }
        )
    }

    /**
     * Genera contenido sin historial (one-shot) en el proveedor activo (Groq o Gemini).
     */
    suspend fun generateContent(
        prompt: String,
        providerOverride: String? = null,
        skipSystemInstruction: Boolean = false,
        effortOverride: String? = null,
        maxOutputTokens: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        initialize(providerOverride = providerOverride)
        if (apiKey.isBlank()) {
            val provName = if (activeProvider == PROVIDER_GROQ) "Groq" else "Google Gemini"
            val msg = "Aún no has configurado una Clave API para $provName."
            recordConnectionState(activeProvider, AiConnectionStatus.DISCONNECTED, msg, modelsCount = 0)
            return@withContext Result.failure(Exception(msg))
        }

        if (activeProvider == PROVIDER_GROQ) {
            val sysPrompt = if (skipSystemInstruction) {
                "Responde con precisión y brevedad."
            } else {
                systemInstructionText ?: "Eres Aura, la asistente inteligente de Audiocinemateca."
            }
            val groqRes = executeGroqOneShot(
                systemPrompt = sysPrompt,
                userPrompt = prompt,
                temperature = 0.3f,
                stripReasoningParams = false
            )
            groqRes.onSuccess {
                recordConnectionState(
                    provider = PROVIDER_GROQ,
                    status = AiConnectionStatus.CONNECTED,
                    message = "Conectado correctamente con Groq ($selectedModel).",
                    modelName = selectedModel
                )
            }.onFailure { err ->
                recordConnectionState(
                    provider = PROVIDER_GROQ,
                    status = AiConnectionStatus.ERROR,
                    message = err.message ?: "Error al conectar con Groq.",
                    modelName = selectedModel
                )
            }
            return@withContext groqRes
        }

        val request = GoogleAiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            systemInstruction = if (skipSystemInstruction) null else getSystemInstructionForModel(selectedModel),
            generationConfig = buildGeminiGenerationConfig(
                modelName = selectedModel,
                defaultTemp = 0.2f,
                includeThoughts = false,
                effortOverride = effortOverride,
                maxOutputTokens = maxOutputTokens
            )
        )
        var lastErrorMsg = "Error de conexión con Google Gemini."
        for (attempt in 1..2) {
            if (attempt > 1) delay(computeRetryDelayMs(attempt))
            try {
                val response = googleAiApiService.generateContent(selectedModel, apiKey, request)
                if (response.isSuccessful) {
                    val text = response.body()?.candidates?.firstOrNull()?.content?.parts
                        ?.firstOrNull { it.text != null && it.thought != true }?.text
                    if (text != null) {
                        recordConnectionState(
                            provider = PROVIDER_GEMINI,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado correctamente con Google Gemini ($selectedModel).",
                            modelName = selectedModel
                        )
                        return@withContext Result.success(stripThinkingTags(text))
                    } else {
                        lastErrorMsg = "Google Gemini devolvió una respuesta vacía."
                    }
                } else {
                    val rawErr = response.errorBody()?.string() ?: ""
                    lastErrorMsg = parseFriendlyHttpError(response.code(), rawErr, PROVIDER_GEMINI)
                    // No reintentar si el error es definitivo (clave inválida, parámetro o modelo inexistente)
                    if (response.code() in listOf(400, 401, 403, 404)) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excepción en generateContent (intento $attempt)", e)
                lastErrorMsg = "Error de conexión con Google Gemini: ${e.message ?: "sin detalle"}"
            }
        }
        recordConnectionState(PROVIDER_GEMINI, AiConnectionStatus.ERROR, lastErrorMsg, modelName = selectedModel)
        return@withContext Result.failure(Exception(lastErrorMsg))
    }

    // =========================================================================
    // CLIENTE GROQ OPENAI-COMPATIBLE CON SOPORTE DE TOOLS Y RAZONAMIENTO
    // =========================================================================

    private suspend fun executeGroqOneShot(
        systemPrompt: String,
        userPrompt: String,
        temperature: Float,
        stripReasoningParams: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bodyJson = JSONObject().apply {
                put("model", selectedModel)
                put("temperature", temperature.toDouble())
                put("max_completion_tokens", 1024)
                val messagesArr = JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                }
                put("messages", messagesArr)
                if (!stripReasoningParams) {
                    applyGroqReasoningParams(this)
                }
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val rawBody = res.body?.string() ?: ""
                if (res.isSuccessful) {
                    val parsed = JSONObject(rawBody)
                    val content = parsed.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content", "") ?: ""
                    val clean = stripThinkingTags(content)
                    if (clean.isNotBlank()) {
                        Result.success(clean)
                    } else {
                        Result.failure(Exception("Respuesta vacía de Groq"))
                    }
                } else if (res.code == 400 && !stripReasoningParams) {
                    executeGroqOneShot(systemPrompt, userPrompt, temperature, stripReasoningParams = true)
                } else {
                    val errMsg = try {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message") ?: "HTTP ${res.code}"
                    } catch (_: Exception) {
                        "HTTP ${res.code}"
                    }
                    Result.failure(Exception(errMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGroqLimitAndRequestedTokens(rawBody: String): Pair<Int?, Int?> {
        val match = Regex("""Limit\s+(\d+)\s*,\s*Requested\s+(\d+)""", RegexOption.IGNORE_CASE).find(rawBody)
        if (match != null) {
            return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
        }
        val limitOnly = Regex("""Limit\s+(\d+)""", RegexOption.IGNORE_CASE).find(rawBody)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()
        return limitOnly to null
    }

    private suspend fun executeGroqSingleAttempt(
        modelName: String,
        withTools: Boolean,
        stripReasoningParams: Boolean = false,
        allowFallbackToolStrip: Boolean = true,
        charBudgetOverride: Int? = null,
        retryBudgetStep: Int = 0
    ): GroqOrGeminiAttemptResult = withContext(Dispatchers.IO) {
        try {
            val toolsArray = if (withTools && getToolsForModel(modelName) != null) {
                buildGroqToolsJsonArray()
            } else {
                null
            }
            val systemChars = systemInstructionText?.length ?: 0
            val toolsChars = toolsArray?.toString()?.length ?: 0
            val effectiveCharBudget = charBudgetOverride ?: groqTotalCharLimitByModel[modelName]
            val messageBudget = effectiveCharBudget?.let { totalBudget ->
                (totalBudget - systemChars - toolsChars).coerceAtLeast(1800)
            }

            val messagesArray = buildGroqMessagesFromHistory(messageCharBudget = messageBudget)
            val bodyJson = JSONObject().apply {
                put("model", modelName)
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_completion_tokens", 2048)

                if (toolsArray != null) {
                    put("tools", toolsArray)
                    put("tool_choice", "auto")
                }

                if (!stripReasoningParams) {
                    applyGroqReasoningParams(this, modelName)
                }
            }

            val bodyString = bodyJson.toString()
            val bodyChars = bodyString.length

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyString.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val rawBody = res.body.string()
                if (res.isSuccessful) {
                    val parsed = JSONObject(rawBody)
                    val cachedTokens = parsed.optJSONObject("usage")
                        ?.optJSONObject("prompt_tokens_details")
                        ?.optInt("cached_tokens", 0) ?: 0
                    if (cachedTokens > 0) {
                        Log.d(TAG, "Groq [$modelName] prompt cache hit: $cachedTokens tokens cacheados")
                    }
                    val messageObj = parsed.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")

                    if (messageObj == null) {
                        return@withContext GroqOrGeminiAttemptResult(
                            httpCode = res.code,
                            errorMessage = "Groq ($modelName) devolvió una respuesta sin contenido."
                        )
                    }

                    val modelParts = mutableListOf<Part>()
                    val rawContent = if (messageObj.isNull("content")) "" else messageObj.optString("content", "")
                    val cleanedContent = stripThinkingTags(rawContent)

                    val inlineToolCall = extractInlineFunctionCallIfPresent(cleanedContent)
                    val visibleText = if (inlineToolCall != null) {
                        removeInlineFunctionTag(cleanedContent)
                    } else {
                        cleanedContent
                    }

                    if (visibleText.isNotBlank()) {
                        modelParts.add(Part(text = visibleText))
                    }

                    val toolCallsArr = messageObj.optJSONArray("tool_calls")
                    if (toolCallsArr != null && toolCallsArr.length() > 0) {
                        for (i in 0 until toolCallsArr.length()) {
                            val tc = toolCallsArr.getJSONObject(i)
                            val tcId = tc.optString("id").ifBlank { "call_${UUID.randomUUID().toString().take(8)}" }
                            val fnObj = tc.optJSONObject("function") ?: continue
                            val fnName = fnObj.optString("name")
                            val rawArgs = fnObj.optString("arguments", "{}")
                            val argsMap = parseJsonArgsToMap(rawArgs)
                            modelParts.add(
                                Part(
                                    functionCall = FunctionCall(
                                        name = fnName,
                                        id = tcId,
                                        args = argsMap
                                    )
                                )
                            )
                        }
                    } else if (inlineToolCall != null) {
                        modelParts.add(Part(functionCall = inlineToolCall))
                    }

                    if (modelParts.isEmpty()) {
                        return@withContext GroqOrGeminiAttemptResult(
                            httpCode = res.code,
                            errorMessage = "Groq ($modelName) no generó texto ni acciones."
                        )
                    }

                    val contentTurn = Content(role = "model", parts = modelParts)
                    chatHistory.add(contentTurn)

                    GroqOrGeminiAttemptResult(
                        response = GoogleAiResponse(
                            candidates = listOf(
                                Candidate(
                                    content = contentTurn,
                                    finishReason = "STOP"
                                )
                            )
                        )
                    )
                } else {
                    if (res.code == 400) {
                        val errorObj = try { JSONObject(rawBody).optJSONObject("error") } catch (_: Exception) { null }
                        val errCode = errorObj?.optString("code") ?: ""
                        val failedGen = errorObj?.optString("failed_generation") ?: ""

                        if (errCode == "tool_use_failed" && failedGen.isNotBlank()) {
                            val rescuedCall = extractInlineFunctionCallIfPresent(failedGen)
                            if (rescuedCall != null) {
                                Log.d(TAG, "Rescatado tool_call desde failed_generation de Groq: ${rescuedCall.name}")
                                val rescuedContent = Content(
                                    role = "model",
                                    parts = listOf(Part(functionCall = rescuedCall))
                                )
                                chatHistory.add(rescuedContent)
                                return@withContext GroqOrGeminiAttemptResult(
                                    response = GoogleAiResponse(
                                        candidates = listOf(
                                            Candidate(content = rescuedContent, finishReason = "STOP")
                                        )
                                    )
                                )
                            }
                        }

                        if (!stripReasoningParams) {
                            return@withContext executeGroqSingleAttempt(
                                modelName = modelName,
                                withTools = withTools,
                                stripReasoningParams = true,
                                allowFallbackToolStrip = allowFallbackToolStrip,
                                charBudgetOverride = charBudgetOverride,
                                retryBudgetStep = retryBudgetStep
                            )
                        }

                        if (withTools && allowFallbackToolStrip && errCode == "tool_use_failed") {
                            return@withContext executeGroqSingleAttempt(
                                modelName = modelName,
                                withTools = false,
                                stripReasoningParams = true,
                                allowFallbackToolStrip = false,
                                charBudgetOverride = charBudgetOverride,
                                retryBudgetStep = retryBudgetStep
                            )
                        }
                    }

                    if (isContextOverflowError(res.code, rawBody) && retryBudgetStep < 3) {
                        val (limitTokens, requestedTokens) = parseGroqLimitAndRequestedTokens(rawBody)
                        val computedBudget = when {
                            limitTokens != null && requestedTokens != null && requestedTokens > 0 ->
                                ((bodyChars.toDouble() * limitTokens * 0.80) / requestedTokens).toInt()
                            limitTokens != null -> (limitTokens * 2.2).toInt()
                            else -> (bodyChars * 0.55).toInt()
                        }
                        val steppedBudget = if (charBudgetOverride != null) {
                            minOf(computedBudget, (charBudgetOverride * 0.72).toInt())
                        } else {
                            computedBudget
                        }.coerceAtLeast(6000)

                        groqTotalCharLimitByModel[modelName] = steppedBudget
                        Log.w(
                            TAG,
                            "Groq [$modelName] alcanzó límite (${res.code}, limit=$limitTokens, req=$requestedTokens, chars=$bodyChars); reintentando con presupuesto=$steppedBudget chars (paso ${retryBudgetStep + 1}/3)"
                        )
                        return@withContext executeGroqSingleAttempt(
                            modelName = modelName,
                            withTools = withTools,
                            stripReasoningParams = stripReasoningParams,
                            allowFallbackToolStrip = allowFallbackToolStrip,
                            charBudgetOverride = steppedBudget,
                            retryBudgetStep = retryBudgetStep + 1
                        )
                    }

                    val friendlyErr = parseFriendlyHttpError(res.code, rawBody, PROVIDER_GROQ)
                    Log.e(TAG, "Error en Groq [$modelName] (${res.code}): $rawBody")
                    GroqOrGeminiAttemptResult(
                        httpCode = res.code,
                        isAuthError = isAuthHttpError(res.code),
                        errorMessage = friendlyErr
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en executeGroqSingleAttempt [$modelName]", e)
            GroqOrGeminiAttemptResult(
                errorMessage = "Error de conexión con Groq ($modelName): ${e.message ?: "error de red"}"
            )
        }
    }

    private fun applyGroqReasoningParams(bodyJson: JSONObject, modelName: String = selectedModel) {
        val lower = modelName.lowercase()
        val effort = getReasoningEffort()

        when {
            lower.contains("gpt-oss") -> {
                val mapped = when (effort) {
                    "low" -> "low"
                    "high", "extra" -> "high"
                    else -> "medium"
                }
                bodyJson.put("reasoning_effort", mapped)
                bodyJson.put("include_reasoning", false)
            }
            lower.contains("qwen3") || lower.contains("qwen-3") -> {
                bodyJson.put("reasoning_effort", "default")
                bodyJson.put("reasoning_format", "hidden")
            }
            lower.contains("deepseek-r1") || lower.contains("reasoner") -> {
                bodyJson.put("reasoning_format", "hidden")
            }
        }
    }

    private fun compactToolResponseMap(fr: FunctionResponse, maxJsonChars: Int? = null): String {
        val resp = fr.response
        val compacted: Map<String, Any> = when {
            resp.containsKey("contenido") -> mapOf(
                "tema" to (resp["tema"] ?: ""),
                "resumen" to "Guía de ayuda consultada previamente en este chat."
            )
            resp["resultados"] is List<*> -> {
                val list = (resp["resultados"] as List<*>).take(4).mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    mapOf(
                        "id" to (map["id"] ?: ""),
                        "tipo" to (map["tipo"] ?: ""),
                        "titulo" to (map["titulo"] ?: ""),
                        "anio" to (map["anio"] ?: "")
                    )
                }
                mapOf("resultados" to list)
            }
            resp["filas"] is List<*> -> {
                val list = (resp["filas"] as List<*>).take(5).mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    map.filterKeys { it != "sinopsis" }
                }
                mapOf("total_devuelto" to (resp["total_devuelto"] ?: list.size), "filas" to list)
            }
            else -> resp
        }
        val raw = gson.toJson(compacted)
        if (maxJsonChars == null || raw.length <= maxJsonChars) return raw

        // Si un payload puntual sigue superando el presupuesto, acotarlo manteniendo JSON válido
        val slimmed: Map<String, Any> = when {
            resp["contenido"] is String -> mapOf(
                "tema" to (resp["tema"] ?: ""),
                "contenido" to (resp["contenido"] as String).take((maxJsonChars - 120).coerceAtLeast(300))
            )
            resp["filas"] is List<*> -> mapOf(
                "total_devuelto" to (resp["total_devuelto"] ?: 0),
                "filas" to (resp["filas"] as List<*>).take(4).mapNotNull { entry ->
                    (entry as? Map<*, *>)?.filterKeys { it != "sinopsis" && it != "reparto" }
                }
            )
            resp["resultados"] is List<*> -> mapOf(
                "resultados" to (resp["resultados"] as List<*>).take(4).mapNotNull { entry ->
                    val map = entry as? Map<*, *> ?: return@mapNotNull null
                    mapOf("id" to (map["id"] ?: ""), "tipo" to (map["tipo"] ?: ""), "titulo" to (map["titulo"] ?: ""), "anio" to (map["anio"] ?: ""))
                }
            )
            else -> mapOf("resumen" to raw.take((maxJsonChars - 40).coerceAtLeast(200)))
        }
        return gson.toJson(slimmed)
    }

    private fun turnToGroqMessages(
        turn: Content,
        compactTools: Boolean,
        maxToolPayloadChars: Int?
    ): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        val role = turn.role ?: return out
        val parts = turn.parts

        val functionResponses = parts.mapNotNull { it.functionResponse }
        if (functionResponses.isNotEmpty()) {
            for (fr in functionResponses) {
                val payload = if (compactTools || maxToolPayloadChars != null) {
                    val fullJson = if (compactTools) compactToolResponseMap(fr, maxToolPayloadChars) else gson.toJson(fr.response)
                    if (maxToolPayloadChars != null && fullJson.length > maxToolPayloadChars) {
                        compactToolResponseMap(fr, maxToolPayloadChars)
                    } else {
                        fullJson
                    }
                } else {
                    gson.toJson(fr.response)
                }
                out.add(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", fr.id ?: "call_${fr.name}")
                        put("name", fr.name)
                        put("content", payload)
                    }
                )
            }
            return out
        }

        if (role == "user") {
            val text = parts.mapNotNull { it.text }.joinToString("\n").trim()
            if (text.isNotBlank()) {
                out.add(JSONObject().put("role", "user").put("content", text))
            }
        } else if (role == "model") {
            val text = parts.filter { it.thought != true }.mapNotNull { it.text }.joinToString("\n").trim()
            val functionCalls = parts.mapNotNull { it.functionCall }

            val assistantObj = JSONObject().apply {
                put("role", "assistant")
                if (text.isNotBlank()) {
                    put("content", text)
                } else if (functionCalls.isNotEmpty()) {
                    put("content", JSONObject.NULL)
                } else {
                    put("content", "")
                }

                if (functionCalls.isNotEmpty()) {
                    val tcArray = JSONArray()
                    for (fc in functionCalls) {
                        val tcId = fc.id ?: "call_${fc.name}"
                        val fnObj = JSONObject().apply {
                            put("name", fc.name)
                            put("arguments", gson.toJson(fc.args ?: emptyMap<String, Any>()))
                        }
                        val tcItem = JSONObject().apply {
                            put("id", tcId)
                            put("type", "function")
                            put("function", fnObj)
                        }
                        tcArray.put(tcItem)
                    }
                    put("tool_calls", tcArray)
                }
            }
            out.add(assistantObj)
        }
        return out
    }

    private fun buildAtomicChatUnits(source: List<Content>): List<List<Content>> {
        val units = mutableListOf<List<Content>>()
        var i = 0
        while (i < source.size) {
            val turn = source[i]
            val hasFnCall = turn.role == "model" && turn.parts.any { it.functionCall != null }
            val next = source.getOrNull(i + 1)
            val nextIsFnResp = next?.role == "user" && next.parts.any { it.functionResponse != null }
            if (hasFnCall && nextIsFnResp && next != null) {
                units.add(listOf(turn, next))
                i += 2
            } else {
                units.add(listOf(turn))
                i++
            }
        }
        return units
    }

    private fun buildConversationDigest(droppedUnits: List<List<Content>>, maxChars: Int = 650): String {
        val lines = mutableListOf<String>()
        var total = 0
        for (unit in droppedUnits) {
            for (turn in unit) {
                val text = turn.parts.filter { it.thought != true }.mapNotNull { it.text }.joinToString(" ").trim()
                if (text.isBlank()) continue
                val prefix = if (turn.role == "user") "Usuario" else "Aura"
                val entry = "- $prefix: ${text.replace("\n", " ").take(110)}"
                if (total + entry.length + 1 > maxChars) break
                lines.add(entry)
                total += entry.length + 1
            }
            if (total >= maxChars) break
        }
        return if (lines.isEmpty()) "" else "\n\n[MEMORIA PREVIA DE ESTE CHAT (resumen de mensajes anteriores):\n${lines.joinToString("\n")}]"
    }

    private fun buildGroqMessagesFromHistory(messageCharBudget: Int? = null): JSONArray {
        val arr = JSONArray()

        // Ruta rápida por defecto: enviar todo el historial completo sin mutaciones
        // para preservar el prefijo exacto y aprovechar Prompt Caching automático de Groq.
        if (messageCharBudget == null) {
            systemInstructionText?.takeIf { it.isNotBlank() }?.let { sys ->
                arr.put(JSONObject().put("role", "system").put("content", sys))
            }
            for (turn in chatHistory) {
                turnToGroqMessages(turn, compactTools = false, maxToolPayloadChars = null).forEach { arr.put(it) }
            }
            return arr
        }

        // Ruta adaptativa cuando el modelo de Groq (tier on_demand) impone un techo estricto de ITPM (ej. 7000 tokens).
        val units = buildAtomicChatUnits(chatHistory)
        val unitMsgsFirstPass = units.mapIndexed { idx, unit ->
            val isOlderThanRecentLoop = idx < units.size - 2
            unit.flatMap { turnToGroqMessages(it, compactTools = isOlderThanRecentLoop, maxToolPayloadChars = null) }
        }
        val firstPassChars = unitMsgsFirstPass.sumOf { list -> list.sumOf { it.toString().length } }
        if (firstPassChars <= messageCharBudget) {
            systemInstructionText?.takeIf { it.isNotBlank() }?.let { sys ->
                arr.put(JSONObject().put("role", "system").put("content", sys))
            }
            unitMsgsFirstPass.flatten().forEach { arr.put(it) }
            return arr
        }

        // Segundo paso: compactar herramientas antiguas y acotar resultados activos gigantes si fuera necesario.
        val activeToolCap = (messageCharBudget * 0.55).toInt().coerceAtLeast(1100)
        val unitMsgs = units.mapIndexed { idx, unit ->
            val isOlder = idx < units.size - 1
            unit.flatMap { turn ->
                turnToGroqMessages(
                    turn = turn,
                    compactTools = isOlder,
                    maxToolPayloadChars = if (isOlder) 500 else activeToolCap
                )
            }
        }

        val reserveForDigest = 700
        val usableBudget = (messageCharBudget - reserveForDigest).coerceAtLeast(900)
        var accChars = 0
        var firstKeptIdx = units.size
        for (idx in units.indices.reversed()) {
            val unitLen = unitMsgs[idx].sumOf { it.toString().length }
            if (idx == units.lastIndex || accChars + unitLen <= usableBudget) {
                accChars += unitLen
                firstKeptIdx = idx
            } else {
                break
            }
        }

        val digest = if (firstKeptIdx > 0) buildConversationDigest(units.subList(0, firstKeptIdx)) else ""
        val combinedSystem = (systemInstructionText?.takeIf { it.isNotBlank() } ?: "") + digest
        if (combinedSystem.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", combinedSystem))
        }
        for (idx in firstKeptIdx until unitMsgs.size) {
            unitMsgs[idx].forEach { arr.put(it) }
        }
        return arr
    }

    private fun buildGroqToolsJsonArray(): JSONArray {
        val toolsArray = JSONArray()
        val declarations = getToolsForRequest()?.flatMap { it.functionDeclarations } ?: emptyList()

        for (decl in declarations) {
            val propsObj = JSONObject()
            for ((propName, propVal) in decl.parameters.properties) {
                propsObj.put(
                    propName,
                    JSONObject().apply {
                        put("type", propVal.type)
                        put("description", propVal.description)
                    }
                )
            }

            val reqArr = JSONArray()
            decl.parameters.required.forEach { reqArr.put(it) }

            val paramsObj = JSONObject().apply {
                put("type", "object")
                put("properties", propsObj)
                put("required", reqArr)
            }

            val fnObj = JSONObject().apply {
                put("name", decl.name)
                put("description", decl.description)
                put("parameters", paramsObj)
            }

            toolsArray.put(
                JSONObject().apply {
                    put("type", "function")
                    put("function", fnObj)
                }
            )
        }
        return toolsArray
    }

    private fun parseJsonArgsToMap(rawJson: String): Map<String, Any> {
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            gson.fromJson<Map<String, Any>>(rawJson, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Rescata llamadas a funciones si algún modelo de código abierto emite formatos tipo:
     * <function=search_catalog>{"query": "terror"}</function> o <function=search_catalog{"query": "terror"}>
     */
    private fun extractInlineFunctionCallIfPresent(text: String): FunctionCall? {
        val regex = Regex("""<function=([a-zA-Z0-9_]+)\s*>?\s*(\{[\s\S]*?\})\s*(?:</function>|>)?""")
        val match = regex.find(text) ?: return null
        val fnName = match.groupValues[1].trim()
        val jsonArgs = match.groupValues[2].trim()
        if (fnName != "search_catalog" &&
            fnName != "query_catalog_sql" &&
            fnName != "play_content" &&
            fnName != "read_app_docs" &&
            fnName != "get_user_library"
        ) return null
        val argsMap = parseJsonArgsToMap(jsonArgs)
        return FunctionCall(
            name = fnName,
            id = "call_${fnName}_${UUID.randomUUID().toString().take(8)}",
            args = argsMap
        )
    }

    private fun removeInlineFunctionTag(text: String): String {
        return text
            .replace(Regex("""<function=[a-zA-Z0-9_]+[\s\S]*?(?:</function>|>)"""), "")
            .trim()
    }

    // =========================================================================
    // SINCRONIZACIÓN DE MODELOS EN VIVO POR PROVEEDOR (GROQ Y GEMINI)
    // =========================================================================

    private fun formatGroqDisplayName(id: String): String {
        val friendly = when (id) {
            "openai/gpt-oss-120b" -> "GPT-OSS 120B (Razonamiento)"
            "openai/gpt-oss-20b" -> "GPT-OSS 20B (Razonamiento Ligero)"
            "llama-3.3-70b-versatile" -> "Llama 3.3 70B Versatile"
            "llama-3.1-8b-instant" -> "Llama 3.1 8B Instant (Ultra Rápido)"
            "qwen/qwen3-32b" -> "Qwen 3 32B (Razonamiento)"
            else -> id
        }
        return if (friendly == id) id else "$friendly ($id)"
    }

    private fun sortGroqModels(list: List<Pair<String, String>>): List<Pair<String, String>> {
        val priority = listOf(
            "openai/gpt-oss-120b",
            "llama-3.3-70b-versatile",
            "qwen/qwen3-32b",
            "openai/gpt-oss-20b",
            "llama-3.1-8b-instant"
        )
        return list.sortedWith(compareBy<Pair<String, String>> { (id, _) ->
            val idx = priority.indexOf(id)
            if (idx >= 0) idx else Int.MAX_VALUE
        }.thenBy { it.first })
    }

    private fun sortGeminiModels(list: List<Pair<String, String>>): List<Pair<String, String>> {
        return list.sortedWith(compareBy<Pair<String, String>> { (id, _) ->
            val idx = GEMINI_PRIORITY_ORDER.indexOf(id)
            if (idx >= 0) idx else Int.MAX_VALUE
        }.thenBy { it.first })
    }

    suspend fun fetchAvailableModelsLive(
        providedApiKey: String? = null,
        providerOverride: String? = null
    ): AiModelsSyncResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val provider = providerOverride ?: prefs.getString("ai_provider", PROVIDER_GROQ) ?: PROVIDER_GROQ
        val keyPref = if (provider == PROVIDER_GROQ) "groq_api_key" else "gemini_api_key"
        val key = (providedApiKey ?: prefs.getString(keyPref, "") ?: "").trim()

        if (key.isBlank()) {
            _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
                remove(provider)
            }
            recordConnectionState(
                provider = provider,
                status = AiConnectionStatus.DISCONNECTED,
                message = "Aún no has configurado una Clave API.",
                modelName = "",
                modelsCount = 0
            )
            return@withContext AiModelsSyncResult(
                models = emptyList(),
                isSuccess = false,
                errorMessage = null
            )
        }

        val currentStatus = getConnectionState(provider)
        recordConnectionState(
            provider = provider,
            status = AiConnectionStatus.CONNECTING,
            message = "Verificando clave y cargando modelos disponibles...",
            modelName = currentStatus.modelName,
            modelsCount = currentStatus.modelsCount
        )

        if (provider == PROVIDER_GROQ) {
            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    val rawBody = response.body.string()
                    if (!response.isSuccessful) {
                        val errMsg = parseFriendlyHttpError(response.code, rawBody, PROVIDER_GROQ)
                        _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
                            remove(PROVIDER_GROQ)
                        }
                        recordConnectionState(
                            provider = PROVIDER_GROQ,
                            status = AiConnectionStatus.ERROR,
                            message = errMsg,
                            modelsCount = 0
                        )
                        return@use AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = errMsg)
                    }

                    val json = JSONObject(rawBody)
                    val dataArray = json.optJSONArray("data") ?: JSONArray()
                    val parsed = mutableListOf<Pair<String, String>>()
                    val seen = HashSet<String>()

                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val id = item.optString("id", "").trim()
                        val isActive = item.optBoolean("active", true)
                        if (id.isBlank() || !isActive || !seen.add(id)) continue
                        val lower = id.lowercase()
                        if (lower.contains("whisper") ||
                            lower.contains("tts") ||
                            lower.contains("guard") ||
                            lower.contains("safeguard") ||
                            lower.contains("embedding") ||
                            lower.contains("playai") ||
                            lower.contains("orpheus")
                        ) {
                            continue
                        }
                        parsed.add(id to formatGroqDisplayName(id))
                    }

                    val sorted = sortGroqModels(parsed)
                    _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
                        put(PROVIDER_GROQ, sorted)
                    }
                    if (sorted.isEmpty()) {
                        val err = "Groq no devolvió modelos de chat disponibles para esta clave."
                        recordConnectionState(PROVIDER_GROQ, AiConnectionStatus.ERROR, err, modelsCount = 0)
                        AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = err)
                    } else {
                        val activeSaved = prefs.getString("groq_model", "")?.trim().orEmpty()
                        val chosen = pickPreferredModelId(PROVIDER_GROQ, sorted, activeSaved)
                        if (chosen.isNotBlank() && chosen != activeSaved) {
                            prefs.edit().putString("groq_model", chosen).apply()
                        }
                        recordConnectionState(
                            provider = PROVIDER_GROQ,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado con Groq • ${sorted.size} modelos disponibles en vivo.",
                            modelName = chosen,
                            modelsCount = sorted.size
                        )
                        AiModelsSyncResult(sorted, isSuccess = true)
                    }
                }
            } catch (e: Exception) {
                val errMsg = "No se pudo conectar con Groq: ${e.message ?: "error de red"}"
                Log.w(TAG, "Error cargando modelos en vivo de Groq", e)
                recordConnectionState(PROVIDER_GROQ, AiConnectionStatus.ERROR, errMsg, modelsCount = 0)
                AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = errMsg)
            }
        } else {
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                .build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    val rawBody = response.body.string()
                    if (!response.isSuccessful) {
                        val errMsg = parseFriendlyHttpError(response.code, rawBody, PROVIDER_GEMINI)
                        _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
                            remove(PROVIDER_GEMINI)
                        }
                        recordConnectionState(
                            provider = PROVIDER_GEMINI,
                            status = AiConnectionStatus.ERROR,
                            message = errMsg,
                            modelsCount = 0
                        )
                        return@use AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = errMsg)
                    }

                    val json = JSONObject(rawBody)
                    val modelsArray = json.optJSONArray("models") ?: JSONArray()
                    val parsed = mutableListOf<Pair<String, String>>()
                    val parsedTts = mutableListOf<Pair<String, String>>()
                    val seen = HashSet<String>()

                    for (i in 0 until modelsArray.length()) {
                        val model = modelsArray.getJSONObject(i)
                        val name = model.optString("name", "").removePrefix("models/").trim()
                        if (name.isBlank() || !seen.add(name)) continue
                        val displayName = model.optString("displayName", name).trim()
                        val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                        var supportsGenerate = false
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                        if (!supportsGenerate) continue

                        if (name.contains("tts", true) &&
                            (name.contains("gemini", true) || name.contains("gemma", true))
                        ) {
                            parsedTts.add(name to "$displayName ($name)")
                        } else if ((name.contains("gemini", true) || name.contains("gemma", true)) &&
                            !name.contains("embedding", true) &&
                            !name.contains("imagen", true) &&
                            !name.contains("veo", true) &&
                            !name.contains("aqa", true) &&
                            !name.contains("robotics", true) &&
                            !name.contains("computer-use", true) &&
                            !name.contains("native-audio", true)
                        ) {
                            parsed.add(name to "$displayName ($name)")
                        }
                    }

                    val sortedTts = parsedTts.sortedWith(compareBy<Pair<String, String>> { (id, _) ->
                        val idx = GEMINI_TTS_PRIORITY_ORDER.indexOf(id)
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }.thenBy { it.first })
                    _liveTtsModels.value = sortedTts
                    if (sortedTts.isNotEmpty()) {
                        val savedTts = prefs.getString("gemini_tts_model", "")?.trim()?.removePrefix("models/").orEmpty()
                        val chosenTts = pickPreferredTtsModelId(sortedTts, savedTts)
                        if (chosenTts.isNotBlank() && chosenTts != savedTts) {
                            prefs.edit().putString("gemini_tts_model", chosenTts).apply()
                        }
                    }

                    val sorted = sortGeminiModels(parsed)
                    _liveModelsByProvider.value = _liveModelsByProvider.value.toMutableMap().apply {
                        put(PROVIDER_GEMINI, sorted)
                    }
                    if (sorted.isEmpty()) {
                        val err = "Google Gemini no devolvió modelos compatibles para esta clave."
                        recordConnectionState(PROVIDER_GEMINI, AiConnectionStatus.ERROR, err, modelsCount = 0)
                        AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = err)
                    } else {
                        val activeSaved = prefs.getString("gemini_model", "")?.trim()?.removePrefix("models/").orEmpty()
                        val chosen = pickPreferredModelId(PROVIDER_GEMINI, sorted, activeSaved)
                        if (chosen.isNotBlank() && chosen != activeSaved) {
                            prefs.edit().putString("gemini_model", chosen).apply()
                        }
                        recordConnectionState(
                            provider = PROVIDER_GEMINI,
                            status = AiConnectionStatus.CONNECTED,
                            message = "Conectado con Google Gemini • ${sorted.size} modelos disponibles en vivo.",
                            modelName = chosen,
                            modelsCount = sorted.size
                        )
                        AiModelsSyncResult(sorted, isSuccess = true)
                    }
                }
            } catch (e: Exception) {
                val errMsg = "No se pudo conectar con Google Gemini: ${e.message ?: "error de red"}"
                Log.w(TAG, "Error cargando modelos en vivo de Gemini", e)
                recordConnectionState(PROVIDER_GEMINI, AiConnectionStatus.ERROR, errMsg, modelsCount = 0)
                AiModelsSyncResult(emptyList(), isSuccess = false, errorMessage = errMsg)
            }
        }
    }

    suspend fun fetchAvailableModels(
        providedApiKey: String? = null,
        providerOverride: String? = null
    ): List<Pair<String, String>> = fetchAvailableModelsLive(providedApiKey, providerOverride).models

    suspend fun testLiveConnection(
        providerOverride: String,
        refreshModels: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val keyPref = if (providerOverride == PROVIDER_GROQ) "groq_api_key" else "gemini_api_key"
        val key = (prefs.getString(keyPref, "") ?: "").trim()
        if (key.isBlank()) {
            val provName = if (providerOverride == PROVIDER_GROQ) "Groq" else "Google Gemini"
            val msg = "Aún no has configurado una Clave API para $provName."
            recordConnectionState(providerOverride, AiConnectionStatus.DISCONNECTED, msg, modelsCount = 0)
            return@withContext Result.failure(Exception(msg))
        }

        val cached = getCachedLiveModels(providerOverride)
        val modelsList: List<Pair<String, String>> = if (refreshModels || cached.isEmpty()) {
            val syncRes = fetchAvailableModelsLive(providedApiKey = key, providerOverride = providerOverride)
            if (!syncRes.isSuccess || syncRes.models.isEmpty()) {
                return@withContext Result.failure(Exception(syncRes.errorMessage ?: "No se pudieron obtener los modelos."))
            }
            syncRes.models
        } else {
            cached
        }

        initialize(providerOverride = providerOverride)
        recordConnectionState(
            provider = providerOverride,
            status = AiConnectionStatus.CONNECTING,
            message = "Verificando respuesta de $selectedModel...",
            modelName = selectedModel,
            modelsCount = modelsList.size
        )

        val result = withTimeoutOrNull(35_000L) {
            generateContent(
                prompt = "Responde únicamente con: OK",
                providerOverride = providerOverride,
                skipSystemInstruction = true
            )
        } ?: Result.failure(
            Exception("Tiempo de espera agotado (35s) probando $selectedModel. Posible saturación (503) en los servidores del proveedor.")
        )
        return@withContext result.fold(
            onSuccess = { reply ->
                recordConnectionState(
                    provider = providerOverride,
                    status = AiConnectionStatus.CONNECTED,
                    message = "Conectado correctamente • Modelo activo: $selectedModel (${modelsList.size} disponibles).",
                    modelName = selectedModel,
                    modelsCount = modelsList.size
                )
                Result.success("$reply ($selectedModel)")
            },
            onFailure = { err ->
                val msg = err.message ?: "Error al probar el modelo seleccionado."
                recordConnectionState(
                    provider = providerOverride,
                    status = AiConnectionStatus.ERROR,
                    message = msg,
                    modelName = selectedModel,
                    modelsCount = modelsList.size
                )
                Result.failure(Exception(msg))
            }
        )
    }

    suspend fun synthesizeGeminiSpeech(
        text: String,
        modelOverride: String? = null,
        voiceOverride: String? = null,
        styleOverride: String? = null
    ): Result<GeminiSpeechAudio> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Texto vacío para síntesis de voz."))
        }

        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val geminiKey = (prefs.getString("gemini_api_key", "") ?: "").trim()
        if (geminiKey.isBlank()) {
            return@withContext Result.failure(Exception("No hay una Clave API de Google Gemini configurada para la voz expresiva."))
        }

        val primaryModel = (modelOverride ?: getSelectedGeminiTtsModel()).trim().ifBlank { DEFAULT_GEMINI_TTS_MODEL }
        val voiceName = (voiceOverride ?: getSelectedGeminiTtsVoice()).trim().ifBlank { DEFAULT_GEMINI_TTS_VOICE }
        val stylePrompt = styleOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: "Amable, cercana, conversacional y natural hablando en español"

        val candidateModels = LinkedHashSet<String>().apply {
            add(primaryModel)
            _liveTtsModels.value.map { it.first }.forEach { add(it) }
            if (isEmpty() || size == 1) {
                GEMINI_TTS_PRIORITY_ORDER.forEach { add(it) }
            }
        }.take(3)

        var lastError = "No se pudo sintetizar la voz expresiva con Google Gemini."

        for ((modelIdx, modelId) in candidateModels.withIndex()) {
            val usesSpeechMetadata = is38PlusTtsModel(modelId)
            val textForModel = if (usesSpeechMetadata) {
                cleanText
            } else {
                // Los modelos 2.5/3.1 no soportan etiquetas <...> de 3.8; limpiarlas si caen en respaldo
                cleanText
                    .replace(Regex("""<\s*(short pause|long pause)\s*>""", RegexOption.IGNORE_CASE), "... ")
                    .replace(Regex("""<\s*(laugh|chuckle|sigh|gasp|breath|cough|throat-clearing)\s*>""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""[ \t]{2,}"""), " ")
                    .trim()
            }
            val part = Part(
                text = textForModel,
                speechMetadata = if (usesSpeechMetadata) {
                    SpeechMetadata(style = stylePrompt)
                } else {
                    null
                }
            )
            val request = GoogleAiRequest(
                contents = listOf(Content(role = "user", parts = listOf(part))),
                generationConfig = GenerationConfig(
                    responseModalities = listOf("AUDIO"),
                    speechConfig = SpeechConfig(
                        voiceConfig = VoiceConfig(
                            prebuiltVoiceConfig = PrebuiltVoiceConfig(voiceName = voiceName)
                        )
                    )
                )
            )

            val maxAttemptsForModel = if (modelIdx == 0) 2 else 1
            for (attempt in 1..maxAttemptsForModel) {
                if (attempt > 1) delay(computeRetryDelayMs(attempt))
                try {
                    val response = withTimeoutOrNull(25_000L) {
                        googleAiApiService.generateContent(modelId, geminiKey, request)
                    }
                    if (response == null) {
                        lastError = "Tiempo de espera agotado sintetizando voz con $modelId."
                        continue
                    }
                    if (response.isSuccessful) {
                        val inline = response.body()?.candidates?.firstOrNull()?.content?.parts
                            ?.firstOrNull { !it.inlineData?.data.isNullOrBlank() }?.inlineData
                        val b64 = inline?.data
                        if (!b64.isNullOrBlank()) {
                            val rawBytes = Base64.decode(b64, Base64.DEFAULT)
                            val mime = inline.mimeType?.lowercase().orEmpty()
                            val (finalBytes, ext, outMime) = normalizeGeminiAudioBytes(rawBytes, mime)
                            return@withContext Result.success(
                                GeminiSpeechAudio(
                                    bytes = finalBytes,
                                    fileExtension = ext,
                                    mimeType = outMime,
                                    modelUsed = modelId,
                                    voiceUsed = voiceName
                                )
                            )
                        } else {
                            lastError = "Google Gemini ($modelId) no devolvió datos de audio."
                        }
                    } else {
                        val rawErr = response.errorBody()?.string() ?: ""
                        lastError = parseFriendlyHttpError(response.code(), rawErr, PROVIDER_GEMINI)
                        if (response.code() in listOf(401, 403)) {
                            return@withContext Result.failure(Exception(lastError))
                        }
                        if (response.code() in listOf(400, 404)) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error en synthesizeGeminiSpeech ($modelId intento $attempt)", e)
                    lastError = "Error al generar voz con $modelId: ${e.message ?: "error de red"}"
                }
            }
        }

        return@withContext Result.failure(Exception(lastError))
    }

    private fun normalizeGeminiAudioBytes(rawBytes: ByteArray, mimeLower: String): Triple<ByteArray, String, String> {
        return when {
            mimeLower.contains("mpeg") || mimeLower.contains("mp3") ->
                Triple(rawBytes, "mp3", "audio/mpeg")
            mimeLower.contains("ogg") || mimeLower.contains("opus") ->
                Triple(rawBytes, "ogg", "audio/ogg")
            mimeLower.contains("wav") && rawBytes.size >= 4 &&
                rawBytes[0] == 'R'.code.toByte() && rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() && rawBytes[3] == 'F'.code.toByte() ->
                Triple(rawBytes, "wav", "audio/wav")
            else -> {
                val sampleRate = parseSampleRateFromMime(mimeLower)
                Triple(wrapPcm16MonoAsWav(rawBytes, sampleRate), "wav", "audio/wav")
            }
        }
    }

    private fun parseSampleRateFromMime(mimeLower: String): Int {
        val match = Regex("""rate=(\d+)""").find(mimeLower)
        val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull()
        return if (parsed != null && parsed in 8000..48000) parsed else 24000
    }

    private fun wrapPcm16MonoAsWav(pcmBytes: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmBytes.size
        val chunkSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // Subchunk1Size para PCM
            putShort(1.toShort()) // AudioFormat = 1 (PCM)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()
        out.write(header)
        out.write(pcmBytes)
        return out.toByteArray()
    }
}

sealed class StreamEvent {
    data class TextDelta(val text: String) : StreamEvent()
    data class FunctionCallEvent(val call: FunctionCall) : StreamEvent()
    data class StatusUpdate(val statusText: String) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
