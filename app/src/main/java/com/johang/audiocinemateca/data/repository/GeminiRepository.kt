package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*
import com.google.ai.client.generativeai.type.content
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var generativeModel: GenerativeModel? = null
    private var chatSession: Chat? = null
    private var lastSystemInstruction: Content? = null
    private val client = OkHttpClient()

    private val catalogSearchTool = Tool(
        functionDeclarations = listOf(
            FunctionDeclaration(
                name = "search_catalog",
                description = "Busca películas, series, documentales o cortometrajes en el catálogo local por título, género, reparto o sinopsis.",
                parameters = listOf(
                    Schema(
                        name = "query",
                        type = FunctionType.STRING,
                        description = "Término de búsqueda (ej: 'terror', 'Christopher Nolan', 'El Padrino')",
                        nullable = false
                    )
                ),
                requiredParameters = listOf("query")
            )
        )
    )

    suspend fun fetchAvailableModels(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isBlank()) return@withContext emptyList()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .build()

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
                    for (j in 0 until methods.length()) {
                        if (methods.getString(j) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                    if (supportsGenerate && (name.contains("gemini", ignoreCase = true) || name.contains("gemma", ignoreCase = true))) {
                        result.add(name to displayName)
                    }
                }
                result.sortedByDescending { it.first }
            }
        } catch (e: Exception) { emptyList() }
    }

    fun initialize(systemInstruction: Content? = null): Boolean {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")
        val selectedModel = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"

        if (apiKey.isNullOrEmpty()) return false

        // Guardamos la instrucción para re-usarla si es necesario
        if (systemInstruction != null) {
            lastSystemInstruction = systemInstruction
        }

        // Determinar capacidades según el modelo basándose en la documentación oficial
        val isGemini = selectedModel.contains("gemini", ignoreCase = true)
        val isGemma4 = selectedModel.contains("gemma-4", ignoreCase = true)
        val isGemma3 = selectedModel.contains("gemma-3", ignoreCase = true)
        val isOldGemma = selectedModel.contains("gemma-1", ignoreCase = true) || selectedModel.contains("gemma-2", ignoreCase = true)
        
        // Según la doc: Gemma 1, 2 y 3 NO soportan system_instruction nativo ni tools (salvo variantes function)
        // Gemma 4 y Gemini SI soportan las características avanzadas nativas
        val supportsNativeSystem = isGemini || isGemma4
        val supportsTools = isGemini || isGemma4 || selectedModel.contains("function", ignoreCase = true)

        val tools = if (supportsTools) listOf(catalogSearchTool) else null
        val sysInst = if (supportsNativeSystem) lastSystemInstruction else null

        return try {
            generativeModel = GenerativeModel(
                modelName = selectedModel,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 2048
                    // Nota: Si el SDK soporta thinking_config para Gemma 4, se podría añadir aquí
                },
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.MEDIUM_AND_ABOVE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.MEDIUM_AND_ABOVE),
                ),
                systemInstruction = sysInst,
                tools = tools
            )
            true
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error inicializando modelo $selectedModel", e)
            false
        }
    }

    fun startChat(history: List<Content> = emptyList()) {
        if (generativeModel == null) initialize()
        chatSession = generativeModel?.startChat(history)
    }

    suspend fun sendMessage(message: String): Result<GenerateContentResponse> = withContext(Dispatchers.IO) {
        if (chatSession == null) {
            if (!initialize()) return@withContext Result.failure(IllegalStateException("API Key no configurada."))
            startChat()
        }

        val chat = chatSession ?: return@withContext Result.failure(IllegalStateException("Error iniciando sesión."))

        // Para modelos que no soportan systemInstruction nativo (Gemma 1, 2, 3), 
        // inyectamos la personalidad en el primer mensaje.
        val needsManualInjection = generativeModel?.systemInstruction == null && lastSystemInstruction != null
        val finalMessage = if (needsManualInjection && chat.history.isEmpty()) {
            val instructionText = lastSystemInstruction?.parts?.filterIsInstance<TextPart>()?.joinToString(" ") { it.text } ?: ""
            "INSTRUCCIÓN DE SISTEMA: $instructionText\n\nMENSAJE DEL USUARIO: $message"
        } else {
            message
        }

        try {
            val response: GenerateContentResponse = chat.sendMessage(finalMessage)
            Result.success(response)
        } catch (e: Exception) {
            Log.e("GeminiRepo", "Error en sendMessage", e)
            Result.failure(e)
        }
    }

    suspend fun sendFunctionResponse(
        functionName: String,
        response: JSONObject
    ): Result<GenerateContentResponse> = withContext(Dispatchers.IO) {
        val chat = chatSession ?: return@withContext Result.failure(IllegalStateException("Chat no iniciado"))
        try {
            val content = content("function") {
                part(FunctionResponsePart(functionName, response))
            }
            val res = chat.sendMessage(content)
            Result.success(res)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generación de contenido puntual (usado para validar API)
     */
    suspend fun generateContent(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (generativeModel == null) {
            if (!initialize()) return@withContext Result.failure(IllegalStateException("No inicializado."))
        }
        val model = generativeModel ?: return@withContext Result.failure(IllegalStateException("Error modelo."))
        try {
            val response = model.generateContent(prompt)
            val text = response.text
            if (text != null) Result.success(text) else Result.failure(Exception("Sin respuesta."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}