package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.johang.audiocinemateca.data.remote.ai.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class AiModelOption(
    val id: String,
    val displayName: String,
    val badge: String,
    val supportsThinking: Boolean,
    val defaultThinking: Boolean = false
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

        val CURATED_GROQ_MODELS = listOf(
            AiModelOption(
                id = "openai/gpt-oss-120b",
                displayName = "GPT-OSS 120B (Recomendado • Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = true
            ),
            AiModelOption(
                id = "llama-3.3-70b-versatile",
                displayName = "Llama 3.3 70B Versatile (Estándar)",
                badge = "Estándar",
                supportsThinking = false,
                defaultThinking = false
            ),
            AiModelOption(
                id = "qwen/qwen3-32b",
                displayName = "Qwen 3 32B (Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = true
            ),
            AiModelOption(
                id = "openai/gpt-oss-20b",
                displayName = "GPT-OSS 20B (Razonamiento Ligero)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = false
            ),
            AiModelOption(
                id = "deepseek-r1-distill-llama-70b",
                displayName = "DeepSeek R1 70B (Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = true
            ),
            AiModelOption(
                id = "meta-llama/llama-4-scout-17b-16e-instruct",
                displayName = "Llama 4 Scout 17B (Estándar)",
                badge = "Estándar",
                supportsThinking = false,
                defaultThinking = false
            ),
            AiModelOption(
                id = "llama-3.1-8b-instant",
                displayName = "Llama 3.1 8B Instant (Ultra Rápido)",
                badge = "Estándar",
                supportsThinking = false,
                defaultThinking = false
            )
        )

        val CURATED_GEMINI_MODELS = listOf(
            AiModelOption(
                id = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash (Recomendado • Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = false
            ),
            AiModelOption(
                id = "gemma-4-27b-it",
                displayName = "Gemma 4 27B IT (Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = false
            ),
            AiModelOption(
                id = "gemma-4-26b-a4b-it",
                displayName = "Gemma 4 26B A4B IT (Razonamiento)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = false
            ),
            AiModelOption(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro (Razonamiento Avanzado)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = true
            ),
            AiModelOption(
                id = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash-Lite (Ligero)",
                badge = "Razonamiento",
                supportsThinking = true,
                defaultThinking = false
            ),
            AiModelOption(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash (Estándar)",
                badge = "Estándar",
                supportsThinking = false,
                defaultThinking = false
            )
        )
    }

    private var activeProvider: String = PROVIDER_GROQ
    private var selectedModel: String = DEFAULT_GROQ_MODEL
    private var apiKey: String = ""
    private var chatHistory = mutableListOf<Content>()
    private var systemInstruction: Content? = null
    private var systemInstructionText: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
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
                description = "Busca películas, series, documentales o cortometrajes en el catálogo local por título, género, reparto o sinopsis. Usa esta herramienta SIEMPRE que el usuario pida recomendaciones, busque una obra, pregunte si tienes un título o mencione interés en un género, director o actor.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf("query" to Property("string", "Término de búsqueda: puede ser un título, género, director, actor o tema")),
                    required = listOf("query")
                )
            )
        )
    )

    private fun getToolsForRequest(): List<Tool>? =
        if (supportsAdvancedFeatures()) listOf(catalogSearchTool, playContentTool) else null

    fun getActiveProvider(): String = activeProvider
    fun getSelectedModel(): String = selectedModel

    fun initialize(instructionText: String? = null): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val configuredProvider = prefs.getString("ai_provider", PROVIDER_GROQ) ?: PROVIDER_GROQ
        val groqKey = (prefs.getString("groq_api_key", "") ?: "").trim()
        val geminiKey = (prefs.getString("gemini_api_key", "") ?: "").trim()

        // Resolver proveedor: prioriza el seleccionado; si aún no tiene clave pero el otro sí, usa fallback
        activeProvider = when {
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

    suspend fun generateContentRating(title: String, description: String): String? = withContext(Dispatchers.IO) {
        if (!initialize()) return@withContext null
        if (apiKey.isBlank()) return@withContext null
        try {
            val prompt = "[DIRECTIVA: CLASIFICACION_CONTENIDO] Analiza el siguiente contenido cinematográfico/audiolibro:\nTítulo: '$title'\nSinopsis: '$description'\nGenera únicamente la clasificación de edad recomendada (ej: 'Clasificación 12+', 'Clasificación 16+', 'Apto para Todo Público') seguida de los descriptores de contenido breves (ej: 'Diálogos sugerentes, violencia moderada, lenguaje fuerte'). Responde en máximo 1 frase concisa en español."
            val res = generateContent(prompt)
            if (res.isSuccess) res.getOrNull()?.trim() else null
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
        chatHistory.addAll(history)
    }

    private fun isDeepThinkingEnabled(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("gemini_deep_thinking", true)
    }

    private fun getReasoningEffort(): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val effort = prefs.getString("ai_reasoning_effort", "medium") ?: "medium"
        return if (effort in listOf("low", "medium", "high")) effort else "medium"
    }

    private fun buildThinkingConfig(): ThinkingConfig? {
        if (!supportsAdvancedFeatures()) return null

        val model = selectedModel.lowercase()
        val deepThinking = isDeepThinkingEnabled()
        val effort = getReasoningEffort()

        return when {
            model.contains("gemma-4") || model.contains("gemma-3") || model.contains("gemini-3") -> {
                if (deepThinking) ThinkingConfig(thinkingLevel = effort)
                else ThinkingConfig(thinkingLevel = "minimal")
            }
            model.contains("gemini-2.5") && model.contains("pro") -> {
                if (deepThinking) {
                    val budget = when (effort) {
                        "high" -> 8192
                        "low" -> 2048
                        else -> 4096
                    }
                    ThinkingConfig(thinkingBudget = budget)
                } else {
                    ThinkingConfig(thinkingBudget = 512)
                }
            }
            model.contains("gemini-2.5") -> {
                if (deepThinking) {
                    val budget = when (effort) {
                        "high" -> 4096
                        "low" -> 1024
                        else -> 2048
                    }
                    ThinkingConfig(thinkingBudget = budget)
                } else {
                    ThinkingConfig(thinkingBudget = 0)
                }
            }
            else -> null
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
    // FLUJO DE CONVERSACIÓN MULTITURNO Y HERRAMIENTAS (GROQ Y GEMINI)
    // =========================================================================

    fun sendMessageStream(message: String): Flow<StreamEvent> = flow {
        initialize()
        if (apiKey.isBlank()) {
            emit(StreamEvent.Error("No hay Clave API configurada. Ve a Ajustes de IA para configurar Groq o Gemini."))
            return@flow
        }

        if (activeProvider == PROVIDER_GROQ) {
            chatHistory.add(Content(role = "user", parts = listOf(Part(text = message))))
            val groqResult = executeGroqChatTurn(withTools = true)
            groqResult.fold(
                onSuccess = { response ->
                    val parts = response.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                    if (parts.isNotEmpty()) {
                        for (part in parts) {
                            part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                            if (part.thought == true) continue
                            part.text?.takeIf { it.isNotBlank() }?.let { emit(StreamEvent.TextDelta(it)) }
                        }
                    } else {
                        rollbackLastUserMessage()
                        emit(StreamEvent.Error("El modelo no devolvió respuesta."))
                    }
                },
                onFailure = { err ->
                    rollbackLastUserMessage()
                    emit(StreamEvent.Error(err.message ?: "Error al conectar con Groq."))
                }
            )
            return@flow
        }

        // Flujo nativo para Google Gemini
        val actualMessage = if (!supportsAdvancedFeatures() && chatHistory.isEmpty() && systemInstructionText != null) {
            "[CONTEXTO]\n${systemInstructionText}\n\n[MENSAJE]\n$message"
        } else {
            message
        }

        chatHistory.add(Content(role = "user", parts = listOf(Part(text = actualMessage))))

        val request = GoogleAiRequest(
            contents = chatHistory,
            systemInstruction = getSystemInstructionForRequest(),
            tools = getToolsForRequest(),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                thinkingConfig = buildThinkingConfig()
            )
        )

        Log.d(TAG, "sendMessageStream (Gemini) → modelo=$selectedModel, historial=${chatHistory.size} turnos")

        try {
            val response = googleAiApiService.streamGenerateContent(selectedModel, apiKey, request)
            if (response.isSuccessful && response.body() != null) {
                val source = response.body()!!.source()
                val modelParts = mutableListOf<Part>()

                while (!source.exhausted()) {
                    val line = withContext(Dispatchers.IO) { source.readUtf8Line() } ?: break
                    if (line.startsWith("data: ")) {
                        val json = line.substring(6).trim()
                        if (json.isEmpty() || json == "[DONE]") continue
                        try {
                            val chunk = gson.fromJson(json, GoogleAiResponse::class.java)
                            chunk.error?.let {
                                Log.e(TAG, "Error en stream Gemini: ${it.code} - ${it.message}")
                                emit(StreamEvent.Error(it.message))
                                return@flow
                            }

                            val parts = chunk.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                            if (parts.isNotEmpty()) {
                                modelParts.addAll(parts)
                                for (part in parts) {
                                    part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                                    if (part.thought == true) continue
                                    part.text?.let { emit(StreamEvent.TextDelta(it)) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parseando chunk SSE: ${e.message}")
                            emit(StreamEvent.Error("Fallo de lectura desde la API: ${e.message}"))
                            return@flow
                        }
                    }
                }
                if (modelParts.isNotEmpty()) {
                    chatHistory.add(Content(role = "model", parts = modelParts.toList()))
                } else {
                    rollbackLastUserMessage()
                }
            } else {
                rollbackLastUserMessage()
                val errorBody = withContext(Dispatchers.IO) {
                    response.errorBody()?.string() ?: "Sin detalle"
                }
                Log.e(TAG, "Error API Gemini stream ${response.code()}: $errorBody")
                emit(StreamEvent.Error("Error de API Gemini (${response.code()})"))
            }
        } catch (e: Exception) {
            rollbackLastUserMessage()
            Log.e(TAG, "Error de conexión en sendMessageStream", e)
            emit(StreamEvent.Error("Error de conexión."))
        }
    }

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
        val resolvedId = functionCallId ?: "call_${functionName}_${UUID.randomUUID().toString().take(8)}"
        chatHistory.add(
            Content(
                role = "user",
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = functionName,
                            id = resolvedId,
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
    ): Result<GoogleAiResponse> = withContext(Dispatchers.IO) {
        val resolvedId = functionCallId ?: "call_${functionName}_${UUID.randomUUID().toString().take(8)}"

        chatHistory.add(
            Content(
                role = "user",
                parts = listOf(
                    Part(
                        functionResponse = FunctionResponse(
                            name = functionName,
                            id = resolvedId,
                            response = responseMap
                        )
                    )
                )
            )
        )

        Log.d(TAG, "sendFunctionResponse (${activeProvider.uppercase()}): name=$functionName, id=$resolvedId, historial=${chatHistory.size}")

        if (activeProvider == PROVIDER_GROQ) {
            val res = executeGroqChatTurn(withTools = true)
            if (res.isFailure) {
                if (chatHistory.isNotEmpty() && chatHistory.last().role == "user") {
                    chatHistory.removeAt(chatHistory.size - 1)
                }
            }
            return@withContext res
        }

        val request = GoogleAiRequest(
            contents = chatHistory,
            systemInstruction = getSystemInstructionForRequest(),
            tools = getToolsForRequest(),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                thinkingConfig = buildThinkingConfig()
            )
        )
        try {
            val res = googleAiApiService.generateContent(selectedModel, apiKey, request)
            if (res.isSuccessful && res.body() != null) {
                val aiResponse = res.body()!!
                aiResponse.candidates?.firstOrNull()?.content?.let {
                    chatHistory.add(it)
                }
                Result.success(aiResponse)
            } else {
                val errorBody = res.errorBody()?.string() ?: "Sin detalle"
                Log.e(TAG, "Error en sendFunctionResponse Gemini ${res.code()}: $errorBody")
                if (chatHistory.isNotEmpty() && chatHistory.last().role == "user") {
                    chatHistory.removeAt(chatHistory.size - 1)
                }
                Result.failure(Exception("Error ${res.code()}: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en sendFunctionResponse", e)
            if (chatHistory.isNotEmpty() && chatHistory.last().role == "user") {
                chatHistory.removeAt(chatHistory.size - 1)
            }
            Result.failure(e)
        }
    }

    suspend fun generateFinalResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        initialize()
        chatHistory.add(Content(role = "user", parts = listOf(Part(text = prompt))))

        if (activeProvider == PROVIDER_GROQ) {
            val res = executeGroqChatTurn(withTools = false)
            return@withContext res.fold(
                onSuccess = { aiResp ->
                    val text = aiResp.candidates?.firstOrNull()?.content?.parts
                        ?.firstOrNull { !it.text.isNullOrBlank() && it.thought != true }?.text
                    if (text != null) Result.success(text)
                    else {
                        rollbackLastUserMessage()
                        Result.failure(Exception("Respuesta vacía"))
                    }
                },
                onFailure = { err ->
                    rollbackLastUserMessage()
                    Result.failure(err)
                }
            )
        }

        val request = GoogleAiRequest(
            contents = chatHistory,
            systemInstruction = getSystemInstructionForRequest(),
            tools = getToolsForRequest(),
            generationConfig = GenerationConfig(
                temperature = 0.7f,
                thinkingConfig = buildThinkingConfig()
            )
        )
        try {
            val response = googleAiApiService.generateContent(selectedModel, apiKey, request)
            if (response.isSuccessful) {
                val content = response.body()?.candidates?.firstOrNull()?.content
                val text = content?.parts?.firstOrNull { it.text != null && it.thought != true }?.text
                if (text != null) {
                    chatHistory.add(content)
                    Result.success(text)
                } else {
                    rollbackLastUserMessage()
                    Result.failure(Exception("Respuesta vacía"))
                }
            } else {
                rollbackLastUserMessage()
                Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            rollbackLastUserMessage()
            Result.failure(e)
        }
    }

    /**
     * Genera contenido sin historial (one-shot) en el proveedor activo (Groq o Gemini).
     */
    suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        initialize()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Clave API vacía"))
        }

        if (activeProvider == PROVIDER_GROQ) {
            return@withContext executeGroqOneShot(
                systemPrompt = systemInstructionText ?: "Eres Aura, la asistente inteligente de Audiocinemateca.",
                userPrompt = prompt,
                temperature = 0.3f,
                stripReasoningParams = false
            )
        }

        val request = GoogleAiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            systemInstruction = getSystemInstructionForRequest(),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                thinkingConfig = buildThinkingConfig()
            )
        )
        try {
            val response = googleAiApiService.generateContent(selectedModel, apiKey, request)
            val text = response.body()?.candidates?.firstOrNull()?.content?.parts
                ?.firstOrNull { it.text != null && it.thought != true }?.text
            if (text != null) Result.success(stripThinkingTags(text))
            else Result.failure(Exception("Error de respuesta vacía (${response.code()})"))
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en generateContent", e)
            Result.failure(e)
        }
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

    private suspend fun executeGroqChatTurn(
        withTools: Boolean,
        stripReasoningParams: Boolean = false,
        modelOverride: String? = null
    ): Result<GoogleAiResponse> = withContext(Dispatchers.IO) {
        val effectiveModel = modelOverride ?: selectedModel
        try {
            val messagesArray = buildGroqMessagesFromHistory()
            val bodyJson = JSONObject().apply {
                put("model", effectiveModel)
                put("messages", messagesArray)
                put("temperature", 0.7)
                put("max_completion_tokens", 600)

                if (withTools && getToolsForRequest() != null) {
                    put("tools", buildGroqToolsJsonArray())
                    put("tool_choice", "auto")
                }

                if (!stripReasoningParams) {
                    applyGroqReasoningParams(this, effectiveModel)
                }
            }

            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(req).execute().use { res ->
                val rawBody = res.body.string()
                if (res.isSuccessful) {
                    val parsed = JSONObject(rawBody)
                    val messageObj = parsed.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")

                    if (messageObj == null) {
                        return@withContext Result.failure(Exception("Respuesta de Groq sin mensaje."))
                    }

                    val modelParts = mutableListOf<Part>()
                    val rawContent = if (messageObj.isNull("content")) "" else messageObj.optString("content", "")
                    val cleanedContent = stripThinkingTags(rawContent)

                    // Extraer llamadas a función si vinieron embebidas en texto XML/JSON o en tool_calls
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

                    val contentTurn = Content(role = "model", parts = modelParts)
                    if (modelParts.isNotEmpty()) {
                        chatHistory.add(contentTurn)
                    }

                    Result.success(
                        GoogleAiResponse(
                            candidates = listOf(
                                Candidate(
                                    content = contentTurn,
                                    finishReason = "STOP"
                                )
                            )
                        )
                    )
                } else {
                    // Si alcanzamos límite de tokens por minuto (HTTP 429) en el modelo actual de Groq,
                    // hacemos fallback automático inmediato a otro modelo de Groq con cuota independiente
                    if (res.code == 429 && modelOverride == null) {
                        val fallbackModel = if (effectiveModel == "llama-3.3-70b-versatile") {
                            "openai/gpt-oss-20b"
                        } else {
                            "llama-3.3-70b-versatile"
                        }
                        Log.w(TAG, "Rate limit (429) en $effectiveModel, cambiando automáticamente a $fallbackModel para este turno")
                        return@withContext executeGroqChatTurn(
                            withTools = withTools,
                            stripReasoningParams = stripReasoningParams,
                            modelOverride = fallbackModel
                        )
                    }

                    // Manejo inteligente de errores 400 de Groq:
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
                                return@withContext Result.success(
                                    GoogleAiResponse(
                                        candidates = listOf(
                                            Candidate(content = rescuedContent, finishReason = "STOP")
                                        )
                                    )
                                )
                            } else if (withTools) {
                                return@withContext executeGroqChatTurn(
                                    withTools = false,
                                    stripReasoningParams = stripReasoningParams,
                                    modelOverride = modelOverride
                                )
                            }
                        }

                        if (!stripReasoningParams) {
                            return@withContext executeGroqChatTurn(
                                withTools = withTools,
                                stripReasoningParams = true,
                                modelOverride = modelOverride
                            )
                        }
                    }

                    val errMsg = try {
                        JSONObject(rawBody).optJSONObject("error")?.optString("message") ?: "Error HTTP ${res.code}"
                    } catch (_: Exception) {
                        "Error HTTP ${res.code}"
                    }
                    Log.e(TAG, "Error en Groq (${res.code}): $rawBody")
                    Result.failure(Exception(errMsg))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en executeGroqChatTurn", e)
            Result.failure(e)
        }
    }

    private fun applyGroqReasoningParams(bodyJson: JSONObject, modelName: String = selectedModel) {
        val lower = modelName.lowercase()
        val deepThinking = isDeepThinkingEnabled()
        val effort = getReasoningEffort()

        when {
            lower.contains("gpt-oss") -> {
                bodyJson.put("reasoning_effort", if (deepThinking) effort else "low")
                bodyJson.put("include_reasoning", false)
            }
            lower.contains("qwen3") || lower.contains("qwen-3") -> {
                bodyJson.put("reasoning_effort", if (deepThinking) "default" else "none")
                bodyJson.put("reasoning_format", "hidden")
            }
            lower.contains("deepseek-r1") || lower.contains("reasoner") -> {
                bodyJson.put("reasoning_format", "hidden")
            }
        }
    }

    /**
     * Compacta respuestas de herramientas antiguas en el historial para no agotar
     * el límite de Tokens Por Minuto (TPM) de Groq en conversaciones multi-turno.
     */
    private fun compactHistoricalFunctionResponse(
        fr: FunctionResponse,
        isLatestTurn: Boolean
    ): String {
        if (isLatestTurn) {
            return gson.toJson(fr.response)
        }
        val rawList = fr.response["resultados"] as? List<*> ?: return gson.toJson(fr.response)
        val compactList = rawList.take(5).mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            mapOf(
                "id" to (map["id"] ?: ""),
                "tipo" to (map["tipo"] ?: ""),
                "titulo" to (map["titulo"] ?: ""),
                "anio" to (map["anio"] ?: "")
            )
        }
        return gson.toJson(mapOf("resultados" to compactList))
    }

    private fun buildGroqMessagesFromHistory(): JSONArray {
        val arr = JSONArray()
        systemInstructionText?.takeIf { it.isNotBlank() }?.let { sys ->
            arr.put(JSONObject().put("role", "system").put("content", sys))
        }

        // Mantener los últimos 14 turnos para evitar crecimiento infinito de tokens
        val startIndex = (chatHistory.size - 14).coerceAtLeast(0)
        val window = chatHistory.subList(startIndex, chatHistory.size)

        window.forEachIndexed { idx, turn ->
            val isLatestTurn = idx == window.lastIndex
            val role = turn.role ?: return@forEachIndexed
            val parts = turn.parts

            val functionResponses = parts.mapNotNull { it.functionResponse }
            if (functionResponses.isNotEmpty()) {
                for (fr in functionResponses) {
                    val toolMsg = JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", fr.id ?: "call_${fr.name}")
                        put("name", fr.name)
                        put("content", compactHistoricalFunctionResponse(fr, isLatestTurn))
                    }
                    arr.put(toolMsg)
                }
                return@forEachIndexed
            }

            if (role == "user") {
                val text = parts.mapNotNull { it.text }.joinToString("\n").trim()
                if (text.isNotBlank()) {
                    arr.put(JSONObject().put("role", "user").put("content", text))
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
                arr.put(assistantObj)
            }
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
        if (fnName != "search_catalog" && fnName != "play_content") return null
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

    suspend fun fetchAvailableModels(
        providedApiKey: String? = null,
        providerOverride: String? = null
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val provider = providerOverride ?: prefs.getString("ai_provider", PROVIDER_GROQ) ?: PROVIDER_GROQ

        if (provider == PROVIDER_GROQ) {
            val curatedPairs = CURATED_GROQ_MODELS.map { it.id to it.displayName }
            val key = (providedApiKey ?: prefs.getString("groq_api_key", "") ?: "").trim()
            if (key.isBlank()) return@withContext curatedPairs

            val request = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use curatedPairs
                    val json = JSONObject(response.body?.string() ?: "")
                    val dataArray = json.optJSONArray("data") ?: return@use curatedPairs

                    val curatedIds = CURATED_GROQ_MODELS.map { it.id }.toSet()
                    val merged = curatedPairs.toMutableList()

                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.getJSONObject(i)
                        val id = item.optString("id", "").trim()
                        if (id.isBlank() || id in curatedIds) continue
                        val lower = id.lowercase()
                        if (lower.contains("whisper") ||
                            lower.contains("tts") ||
                            lower.contains("guard") ||
                            lower.contains("safeguard") ||
                            lower.contains("embedding") ||
                            lower.contains("playai")
                        ) {
                            continue
                        }
                        val isReasoning = lower.contains("gpt-oss") ||
                            lower.contains("qwen3") ||
                            lower.contains("r1") ||
                            lower.contains("reason")
                        val badge = if (isReasoning) "Razonamiento" else "API en Vivo"
                        merged.add(id to "$id ($badge)")
                    }
                    merged
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar lista en vivo de Groq, usando catálogo oficial", e)
                curatedPairs
            }
        } else {
            val curatedPairs = CURATED_GEMINI_MODELS.map { it.id to it.displayName }
            val key = (providedApiKey ?: prefs.getString("gemini_api_key", "") ?: "").trim()
            if (key.isBlank()) return@withContext curatedPairs

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                .build()

            return@withContext try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use curatedPairs
                    val json = JSONObject(response.body?.string() ?: "")
                    val modelsArray = json.optJSONArray("models") ?: return@use curatedPairs

                    val curatedIds = CURATED_GEMINI_MODELS.map { it.id }.toSet()
                    val merged = curatedPairs.toMutableList()

                    for (i in 0 until modelsArray.length()) {
                        val model = modelsArray.getJSONObject(i)
                        val name = model.optString("name", "").removePrefix("models/")
                        if (name.isBlank() || name in curatedIds) continue
                        val displayName = model.optString("displayName", name)
                        val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                        var supportsGenerate = false
                        for (j in 0 until methods.length()) {
                            if (methods.getString(j) == "generateContent") {
                                supportsGenerate = true
                                break
                            }
                        }
                        if (supportsGenerate &&
                            (name.contains("gemini", true) || name.contains("gemma", true)) &&
                            !name.contains("embedding", true) &&
                            !name.contains("imagen", true) &&
                            !name.contains("veo", true)
                        ) {
                            merged.add(name to "$displayName ($name)")
                        }
                    }
                    merged
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar lista en vivo de Gemini, usando catálogo oficial", e)
                curatedPairs
            }
        }
    }
}

sealed class StreamEvent {
    data class TextDelta(val text: String) : StreamEvent()
    data class FunctionCallEvent(val call: FunctionCall) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
