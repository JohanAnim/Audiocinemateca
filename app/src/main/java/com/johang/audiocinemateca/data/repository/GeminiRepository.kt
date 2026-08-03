package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.johang.audiocinemateca.data.remote.ai.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAiApiService: GoogleAiApiService
) {
    companion object {
        private const val TAG = "GeminiRepository"
    }

    private var selectedModel: String = "gemma-4-27b-it"
    private var apiKey: String = ""
    private var chatHistory = mutableListOf<Content>()
    private var systemInstruction: Content? = null
    private var systemInstructionText: String? = null
    private val client = OkHttpClient()
    private val gson = GsonBuilder().create()

    // Gemma 3 y anteriores NO soportan systemInstruction, tools ni thinkingConfig.
    // Gemma 4 y todos los Gemini SÍ.
    private fun isGemmaModel(): Boolean = selectedModel.lowercase().contains("gemma")
    private fun isGemma4(): Boolean = selectedModel.lowercase().contains("gemma-4")
    private fun supportsAdvancedFeatures(): Boolean = !isGemmaModel() || isGemma4()
    private fun getSystemInstructionForRequest(): Content? = if (supportsAdvancedFeatures()) systemInstruction else null
    private val playContentTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "play_content",
                description = "Reproduce inmediatamente un título del catálogo. Úsalo cuando el usuario diga 'reprodúcelo', 'dale play', 'ponme...', etc. IMPORTANTE: Debes haber usado search_catalog antes para obtener el 'id' exacto del contenido. Si es una serie, puedes especificar temporada y capítulo.",
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

    private fun getToolsForRequest(): List<Tool>? = if (supportsAdvancedFeatures()) listOf(catalogSearchTool, playContentTool) else null

    private val catalogSearchTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "search_catalog",
                description = "Busca películas, series, documentales o cortometrajes en el catálogo local por título, género, reparto o sinopsis. Usa esta herramienta SIEMPRE que el usuario pida recomendaciones, busque algo, o mencione interés en un género, director o actor.",
                parameters = Parameters(
                    type = "object",
                    properties = mapOf("query" to Property("string", "Término de búsqueda: puede ser un título, género, director, actor o tema")),
                    required = listOf("query")
                )
            )
        )
    )

    fun initialize(instructionText: String? = null): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        apiKey = prefs.getString("gemini_api_key", "") ?: ""
        selectedModel = prefs.getString("gemini_model", "gemma-4-27b-it") ?: "gemma-4-27b-it"
        if (selectedModel.startsWith("models/")) selectedModel = selectedModel.replace("models/", "")

        if (apiKey.isBlank()) return false
        instructionText?.let {
            systemInstructionText = it
            // systemInstruction NO lleva role según la API oficial de Gemini
            systemInstruction = Content(role = null, parts = listOf(Part(text = it)))
        }
        Log.d(TAG, "Inicializado con modelo: $selectedModel")
        return true
    }

    suspend fun generateContentRating(title: String, description: String): String? = withContext(Dispatchers.IO) {
        if (!initialize()) return@withContext null
        if (apiKey.isBlank()) return@withContext null
        try {
            val prompt = "[DIRECTIVA: CLASIFICACION_CONTENIDO] Analiza el siguiente contenido cinematográfico/audiolibro:\nTítulo: '$title'\nSinopsis: '$description'\nGenera únicamente la clasificación de edad recomendada (ej: 'Clasificación 12+', 'Clasificación 16+', 'Apto para Todo Público') seguida de los descriptores de contenido breves (ej: 'Diálogos sugerentes, violencia moderada, lenguaje fuerte'). Responde en máximo 1 frase concisa en español."
            val res = generateContent(prompt)
            if (res.isSuccess) {
                res.getOrNull()?.trim()
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun startChat() { chatHistory.clear() }

    fun getHistory(): List<Content> = chatHistory.toList()

    /**
     * Lee la preferencia del usuario para pensamiento profundo.
     */
    private fun isDeepThinkingEnabled(): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("gemini_deep_thinking", false)
    }

    private fun buildThinkingConfig(): ThinkingConfig? {
        if (!supportsAdvancedFeatures()) return null
        
        val model = selectedModel.lowercase()
        val deepThinking = isDeepThinkingEnabled()

        return when {
            model.contains("gemma-4") || model.contains("gemma-3") || model.contains("gemini-3") -> {
                if (deepThinking) ThinkingConfig(thinkingLevel = "high") 
                else ThinkingConfig(thinkingLevel = "minimal")
            }
            model.contains("gemini-2.5") && model.contains("pro") -> {
                if (deepThinking) ThinkingConfig(thinkingBudget = 8192)
                else ThinkingConfig(thinkingBudget = 1024)
            }
            model.contains("gemini-2.5") -> {
                if (deepThinking) ThinkingConfig(thinkingBudget = 4096)
                else ThinkingConfig(thinkingBudget = 512) // Un pequeño budget es más seguro que 0
            }
            else -> null
        }
    }

    /**
     * Envía un mensaje con streaming. Filtra los parts de pensamiento del output visible
     * pero los preserva en el historial (incluyendo thoughtSignature) para que Gemini 3/Gemma 4
     * pueda mantener el contexto entre turnos.
     */
    fun sendMessageStream(message: String): Flow<StreamEvent> = flow {
        if (apiKey.isBlank()) initialize()

        // Para Gemma 3: inyectar system instruction en el primer mensaje (no soporta systemInstruction)
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

        Log.d(TAG, "sendMessageStream → modelo=$selectedModel, historial=${chatHistory.size} turnos")

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
                                Log.e(TAG, "Error en stream: ${it.code} - ${it.message}")
                                emit(StreamEvent.Error(it.message))
                                return@flow
                            }

                            val parts = chunk.candidates?.firstOrNull()?.content?.parts ?: emptyList()
                            if (parts.isNotEmpty()) {
                                // Guardar TODOS los parts (incluyendo thought y thoughtSignature)
                                modelParts.addAll(parts)
                                for (part in parts) {
                                    // Función call NUNCA se salta, incluso en thought parts
                                    part.functionCall?.let { emit(StreamEvent.FunctionCallEvent(it)) }
                                    // Solo filtrar TEXTO de pensamientos, no funciones
                                    if (part.thought == true) continue
                                    part.text?.let { emit(StreamEvent.TextDelta(it)) }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parseando chunk SSE: ${e.message}")
                            emit(StreamEvent.Error("Fallo de Parseo JSON desde API: ${e.message}"))
                            return@flow
                        }
                    }
                }
                if (modelParts.isNotEmpty()) {
                    // Preservar todos los parts (incluido thoughtSignature)
                    chatHistory.add(Content(role = "model", parts = modelParts.toList()))
                    Log.d(TAG, "Stream completado: ${modelParts.size} parts guardados")
                } else {
                    rollbackLastUserMessage()
                    Log.w(TAG, "Stream completado sin parts del modelo (Se quitó el turno del usuario)")
                }
            } else {
                rollbackLastUserMessage()
                val errorBody = withContext(Dispatchers.IO) {
                    response.errorBody()?.string() ?: "Sin detalle"
                }
                Log.e(TAG, "Error API stream ${response.code()}: $errorBody")
                emit(StreamEvent.Error("Error de API: ${response.code()}"))
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
     * Envía la respuesta de una función al modelo. Usa role = "user" con functionResponse
     * según la especificación oficial de la API (NO usar role "function").
     * Incluye el ID del functionCall para mapeo correcto (requerido en Gemma 4 / Gemini 3).
     */
    suspend fun sendFunctionResponse(
        functionName: String, 
        functionCallId: String?,
        responseMap: Map<String, Any>
    ): Result<GoogleAiResponse> = withContext(Dispatchers.IO) {
        // Role DEBE ser "user" con functionResponse. ID debe coincidir con el del functionCall.
        chatHistory.add(Content(
            role = "user",
            parts = listOf(Part(functionResponse = FunctionResponse(
                name = functionName,
                id = functionCallId,
                response = responseMap
            )))
        ))

        Log.d(TAG, "sendFunctionResponse: name=$functionName, id=$functionCallId, historial=${chatHistory.size}")

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
                // Guardamos la respuesta del modelo en el historial (preservando thoughtSignature)
                aiResponse.candidates?.firstOrNull()?.content?.let { 
                    chatHistory.add(it)
                    Log.d(TAG, "Respuesta de función guardada: ${it.parts.size} parts")
                }
                Result.success(aiResponse)
            } else {
                val errorBody = res.errorBody()?.string() ?: "Sin detalle"
                Log.e(TAG, "Error en sendFunctionResponse ${res.code()}: $errorBody")
                // Rollback: quitar el functionResponse que no fue procesado
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

    /**
     * Genera una respuesta final manteniendo el historial sincronizado.
     * Útil como fallback cuando el modelo no genera texto después de un function call.
     */
    suspend fun generateFinalResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        chatHistory.add(Content(role = "user", parts = listOf(Part(text = prompt))))
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
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "generateFinalResponse error ${response.code()}: $errorBody")
                Result.failure(Exception("Error ${response.code()}"))
            }
        } catch (e: Exception) {
            rollbackLastUserMessage()
            Log.e(TAG, "Excepción en generateFinalResponse", e)
            Result.failure(e)
        }
    }

    /**
     * Genera contenido sin historial (one-shot).
     */
    suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val request = GoogleAiRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = prompt)))),
            systemInstruction = getSystemInstructionForRequest(),
            generationConfig = GenerationConfig(
                temperature = 0.0f,
                thinkingConfig = buildThinkingConfig()
            )
        )
        try {
            val response = googleAiApiService.generateContent(selectedModel, apiKey, request)
            val text = response.body()?.candidates?.firstOrNull()?.content?.parts
                ?.firstOrNull { it.text != null && it.thought != true }?.text
            if (text != null) Result.success(text)
            else Result.failure(Exception("Error de respuesta vacía"))
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en generateContent", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAvailableModels(providedApiKey: String? = null): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val key = providedApiKey ?: prefs.getString("gemini_api_key", "") ?: ""
        if (key.isBlank()) return@withContext emptyList()
        val request = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models?key=$key").build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(response.body?.string() ?: "")
                val modelsArray = json.getJSONArray("models")
                val result = mutableListOf<Pair<String, String>>()
                for (i in 0 until modelsArray.length()) {
                    val model = modelsArray.getJSONObject(i)
                    val name = model.getString("name").replace("models/", "")
                    val displayName = model.getString("displayName")
                    val methods = model.getJSONArray("supportedGenerationMethods")
                    var supportsGenerate = false
                    for (j in 0 until methods.length()) { if (methods.getString(j) == "generateContent") { supportsGenerate = true; break } }
                    if (supportsGenerate && (name.contains("gemini", true) || name.contains("gemma", true))) {
                        result.add(name to displayName)
                    }
                }
                result.sortedByDescending { it.first }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetchAvailableModels", e)
            emptyList()
        }
    }
}

sealed class StreamEvent {
    data class TextDelta(val text: String) : StreamEvent()
    data class FunctionCallEvent(val call: FunctionCall) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
}
