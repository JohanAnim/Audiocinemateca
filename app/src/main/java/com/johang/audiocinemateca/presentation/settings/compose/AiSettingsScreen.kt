package com.johang.audiocinemateca.presentation.settings.compose

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.data.repository.GeminiRepository
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    geminiRepository: GeminiRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var apiKey by remember { mutableStateOf(viewModel.getString("gemini_api_key", "")) }
    var deepThinking by remember { mutableStateOf(viewModel.getBoolean("gemini_deep_thinking", false)) }
    
    // Gemini models logic
    val modelEntries = remember { mutableStateListOf<String>() }
    val modelValues = remember { mutableStateListOf<String>() }
    var currentModel by remember { mutableStateOf(viewModel.getString("gemini_model", "gemma-4-27b-it")) }
    var isModelEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank()) {
            val models = geminiRepository.fetchAvailableModels(apiKey)
            if (models.isNotEmpty()) {
                modelEntries.clear()
                modelValues.clear()
                modelEntries.addAll(models.map { it.second })
                modelValues.addAll(models.map { it.first })
                isModelEnabled = true
                if (currentModel !in modelValues) {
                    currentModel = modelValues.firstOrNull() ?: "gemma-4-27b-it"
                }
            }
        }
    }

    var aiContentRating by remember { mutableStateOf(viewModel.getBoolean("ai_content_rating_enabled", true)) }

    CategorySettingsScreen(
        title = "Inteligencia Artificial",
        onBack = onBack
    ) {
        SettingsEditTextItem(
            title = "Clave API de Gemini",
            summary = if (apiKey.isEmpty()) "Introduce tu clave para activar funciones de IA." else "Clave configurada.",
            value = apiKey,
            placeholder = "Pega aquí tu clave (ej. AIzaSy...)",
            onValueChange = { 
                apiKey = it
                viewModel.updateString("gemini_api_key", it)
                scope.launch {
                    if (it.isNotBlank()) {
                        val models = geminiRepository.fetchAvailableModels(it)
                        if (models.isNotEmpty()) {
                            Toast.makeText(context, "¡Conexión establecida! API Válida.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )

        if (isModelEnabled) {
            SettingsListItem(
                title = "Modelo de Inteligencia Artificial",
                summary = modelEntries[modelValues.indexOf(currentModel).coerceAtLeast(0)],
                entries = modelEntries,
                entryValues = modelValues,
                currentValue = currentModel,
                onValueChange = { 
                    currentModel = it
                    viewModel.updateString("gemini_model", it)
                }
            )
        }

        SettingsSwitchItem(
            title = "Pensamiento profundo",
            summary = "Permite que la IA razone internamente antes de responder (Gemma 4+).",
            checked = deepThinking,
            onCheckedChange = { 
                deepThinking = it
                viewModel.updateBoolean("gemini_deep_thinking", it)
            }
        )

        SettingsSwitchItem(
            title = "Clasificación de contenido por IA",
            summary = "Muestra una advertencia de edad y descriptores de contenido al iniciar la reproducción.",
            checked = aiContentRating,
            onCheckedChange = { 
                aiContentRating = it
                viewModel.updateBoolean("ai_content_rating_enabled", it)
            }
        )

        SettingsClickItem(
            title = "Comprobar estado de la API",
            summary = "Verifica si la clave API introducida es válida.",
            onClick = {
                scope.launch {
                    if (geminiRepository.initialize()) {
                        val result = geminiRepository.generateContent("[DIRECTIVA: SILENT_MODE] Responde únicamente 'OK' si me escuchas.")
                        if (result.isSuccess) {
                            Toast.makeText(context, "API Válida: ${result.getOrNull()}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Error de API: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "No se pudo inicializar (revisa la clave)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        SettingsClickItem(
            title = "¿No tienes una API Key?",
            summary = "Pulsa aquí para conseguir una gratis en Google AI Studio",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
            }
        )
    }
}
