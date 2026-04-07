package com.johang.audiocinemateca.presentation.aichat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val searchRepository: SearchRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _auraStatus = MutableStateFlow("Aura En línea")
    val auraStatus: StateFlow<String> = _auraStatus.asStateFlow()

    private val _accessibilityAnnouncement = MutableStateFlow<String?>(null)
    val accessibilityAnnouncement: StateFlow<String?> = _accessibilityAnnouncement.asStateFlow()

    private var isChatInitialized = false

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

    fun initializeChat() {
        if (isChatInitialized) return
        val user = FirebaseAuth.getInstance().currentUser
        val userName = user?.displayName ?: "Cinéfilo"
        
        val systemInstruction = content {
            text("Eres Aura, la IA oficial de Audiocinemateca. Eres una experta cinematográfica de élite.")
            text("PERSONALIDAD: Eres natural, culta y apasionada. Sé elegante y directa.")
            text("DIRECTIVA CRÍTICA DE SALIDA: Responde DIRECTAMENTE al usuario. NO incluyas procesos de pensamiento, razonamientos internos, análisis de la petición ni listas de objetivos en tu respuesta. Tu salida debe ser exclusivamente el diálogo de Aura.")
            text("BÚSQUEDA PROFESIONAL: Tienes la herramienta 'search_catalog'. ÚSALA de forma exhaustiva para dar respuestas con autoridad.")
            text("VÍNCULOS: Usa [[Título]] únicamente para obras que realmente quieras recomendar y existan en el catálogo.")
            text(AuraKnowledge.APP_CONTEXT)
        }

        if (geminiRepository.initialize(systemInstruction)) {
            geminiRepository.startChat()
            generateDynamicGreeting(userName)
        }
        isChatInitialized = true
    }

    fun restartChat() {
        vibrateError()
        _messages.value = emptyList()
        isChatInitialized = false
        initializeChat()
    }

    private fun generateDynamicGreeting(userName: String) {
        _isLoading.value = true
        _auraStatus.value = "Aura Escribiendo..."
        viewModelScope.launch {
            try {
                val result = geminiRepository.sendMessage("Saluda de forma breve a $userName.")
                _isLoading.value = false
                result.onSuccess { response ->
                    processGeminiResponse(response)
                }.onFailure {
                    _auraStatus.value = "Aura En línea"
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _auraStatus.value = "Aura En línea"
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        vibrateShort()
        
        // 1. Limpiar errores previos y evitar duplicados de usuario
        val currentList = _messages.value.toMutableList()
        if (currentList.lastOrNull()?.isError == true) currentList.removeAt(currentList.size - 1)
        
        // Añadir mensaje de usuario solo si es nuevo
        val userMsgId = UUID.randomUUID().toString()
        currentList.add(ChatMessage(userMsgId, text, true))
        _messages.value = currentList
        
        _isLoading.value = true
        _auraStatus.value = "Aura pensando..."

        viewModelScope.launch {
            try {
                withTimeout(60000) { // 60s para permitir múltiples llamadas a funciones
                    val result = geminiRepository.sendMessage(text)
                    _isLoading.value = false
                    result.onSuccess { response ->
                        processGeminiResponse(response)
                    }.onFailure { error ->
                        vibrateError()
                        _auraStatus.value = "Aura Fuera de línea"
                        addErrorMessage("Fallo de Aura: ${error.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _auraStatus.value = "Aura Fuera de línea"
                addErrorMessage("Error: Tiempo de espera agotado.")
            }
        }
    }

    private suspend fun processGeminiResponse(response: GenerateContentResponse) {
        val functionCalls = response.functionCalls
        if (functionCalls.isNotEmpty()) {
            for (call in functionCalls) {
                if (call.name == "search_catalog") {
                    val query = call.args["query"] as? String ?: ""
                    _auraStatus.value = "Aura buscando '$query'..."
                    _accessibilityAnnouncement.value = "Aura está consultando el catálogo para '$query'"
                    
                    val searchResults = searchRepository.searchCatalog(query)
                    val jsonResponse = JSONObject()
                    val resultsArray = JSONArray()
                    
                    searchResults.take(15).forEach { item ->
                        val obj = JSONObject()
                        obj.put("titulo", item.title)
                        obj.put("anio", item.anio)
                        obj.put("genero", item.genero)
                        obj.put("pais", item.pais)
                        obj.put("director", item.director)
                        obj.put("guion", item.guion)
                        obj.put("reparto", item.reparto)
                        obj.put("productora", item.productora)
                        obj.put("duracion", item.duracion)
                        obj.put("idioma", item.idioma)
                        obj.put("sinopsis", item.sinopsis)
                        resultsArray.put(obj)
                    }
                    jsonResponse.put("resultados", resultsArray)
                    if (searchResults.isEmpty()) jsonResponse.put("mensaje", "No se encontraron coincidencias en el catálogo local.")
                    
                    _auraStatus.value = "Aura analizando resultados..."
                    val nextRes = geminiRepository.sendFunctionResponse(call.name, jsonResponse)
                    nextRes.onSuccess { processGeminiResponse(it) }
                        .onFailure { 
                            _auraStatus.value = "Aura En línea"
                            addErrorMessage("Error procesando búsqueda: ${it.localizedMessage}") 
                        }
                    return 
                }
            }
        } else {
            val rawText = response.text ?: ""
            if (rawText.isBlank()) {
                _auraStatus.value = "Aura En línea"
                return
            }

            // Limpieza profunda de bloques de razonamiento (Thinking) de Gemma 4
            var cleanText = rawText
                .replace(Regex("""<\|channel>thought[\s\S]*?<channel|>""", RegexOption.IGNORE_CASE), "") // Tags oficiales
                .replace(Regex("""^\s*\*.*?\n""", RegexOption.MULTILINE), "") // Listas de "pensamiento" (vistas en logs2.txt)
                .trim()
            
            if (cleanText.isBlank()) {
                _auraStatus.value = "Aura En línea"
                return
            }
            
            val linked = parseLinkedContentGlobally(cleanText)
            val textForUi = cleanText.replace("[[", "").replace("]]", "")
            
            _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), textForUi, false, linkedItems = linked)
            _accessibilityAnnouncement.value = "Aura dice: $textForUi"
            _auraStatus.value = "Aura En línea"
            vibrateSuccess()
        }
    }
    
    private suspend fun parseLinkedContentGlobally(text: String): List<LinkedContent> {
        val regex = """\[\[(.*?)]]""".toRegex()
        val matches = regex.findAll(text)
        val linked = mutableListOf<LinkedContent>()
        for (match in matches) {
            val titleInText = match.groupValues[1].trim()
            // Búsqueda ESTRICTA por título exacto para evitar falsos positivos
            val foundItems = searchRepository.searchCatalog(titleInText)
            if (foundItems.isNotEmpty()) {
                // Solo vinculamos si el título coincide de forma exacta o muy cercana (ignorando mayúsculas)
                val exactItem = foundItems.find { it.title.equals(titleInText, ignoreCase = true) }
                if (exactItem != null) {
                    val typeStr = when (exactItem) {
                        is com.johang.audiocinemateca.data.model.Serie -> "serie"
                        is com.johang.audiocinemateca.data.model.Documentary -> "documental"
                        is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
                        else -> "pelicula"
                    }
                    linked.add(LinkedContent(exactItem.id, exactItem.title, typeStr))
                }
            }
        }
        return linked.distinctBy { it.id }
    }

    private fun addErrorMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(UUID.randomUUID().toString(), text, false, isError = true)
    }

    fun clearAnnouncement() { _accessibilityAnnouncement.value = null }
}
