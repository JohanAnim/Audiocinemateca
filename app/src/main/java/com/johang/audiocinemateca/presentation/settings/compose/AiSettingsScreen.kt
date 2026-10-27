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

    val providerEntries = remember {
        listOf(
            "Groq (Recomendado • Ultra rápido)",
            "Google Gemini"
        )
    }
    val providerValues = remember {
        listOf(
            GeminiRepository.PROVIDER_GROQ,
            GeminiRepository.PROVIDER_GEMINI
        )
    }

    var selectedProvider by remember {
        mutableStateOf(
            viewModel.getString("ai_provider", GeminiRepository.PROVIDER_GROQ)
                .ifBlank { GeminiRepository.PROVIDER_GROQ }
        )
    }

    var groqApiKey by remember { mutableStateOf(viewModel.getString("groq_api_key", "")) }
    var geminiApiKey by remember { mutableStateOf(viewModel.getString("gemini_api_key", "")) }

    var groqModel by remember {
        mutableStateOf(
            viewModel.getString("groq_model", GeminiRepository.DEFAULT_GROQ_MODEL)
                .ifBlank { GeminiRepository.DEFAULT_GROQ_MODEL }
        )
    }
    var geminiModel by remember {
        mutableStateOf(
            viewModel.getString("gemini_model", GeminiRepository.DEFAULT_GEMINI_MODEL)
                .ifBlank { GeminiRepository.DEFAULT_GEMINI_MODEL }
        )
    }

    var deepThinking by remember { mutableStateOf(viewModel.getBoolean("gemini_deep_thinking", true)) }
    var reasoningEffort by remember {
        mutableStateOf(viewModel.getString("ai_reasoning_effort", "medium").ifBlank { "medium" })
    }

    val effortEntries = remember {
        listOf(
            "Bajo (Más rápido)",
            "Medio (Equilibrado • Recomendado)",
            "Alto (Análisis máximo)"
        )
    }
    val effortValues = remember { listOf("low", "medium", "high") }

    val modelEntries = remember { mutableStateListOf<String>() }
    val modelValues = remember { mutableStateListOf<String>() }

    val activeApiKey = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) groqApiKey else geminiApiKey
    val activeModel = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) groqModel else geminiModel

    LaunchedEffect(selectedProvider, activeApiKey) {
        val defaultPairs = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            GeminiRepository.CURATED_GROQ_MODELS.map { it.id to it.displayName }
        } else {
            GeminiRepository.CURATED_GEMINI_MODELS.map { it.id to it.displayName }
        }

        // Mostrar primero el catálogo oficial actualizado de inmediato
        modelEntries.clear()
        modelValues.clear()
        modelEntries.addAll(defaultPairs.map { it.second })
        modelValues.addAll(defaultPairs.map { it.first })

        // Sincronizar en vivo con la API si hay clave configurada
        val synced = geminiRepository.fetchAvailableModels(
            providedApiKey = activeApiKey,
            providerOverride = selectedProvider
        )
        if (synced.isNotEmpty()) {
            modelEntries.clear()
            modelValues.clear()
            modelEntries.addAll(synced.map { it.second })
            modelValues.addAll(synced.map { it.first })
        }

        if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            if (groqModel !in modelValues) {
                groqModel = modelValues.firstOrNull() ?: GeminiRepository.DEFAULT_GROQ_MODEL
                viewModel.updateString("groq_model", groqModel)
            }
        } else {
            if (geminiModel !in modelValues) {
                geminiModel = modelValues.firstOrNull() ?: GeminiRepository.DEFAULT_GEMINI_MODEL
                viewModel.updateString("gemini_model", geminiModel)
            }
        }
    }

    var aiContentRating by remember { mutableStateOf(viewModel.getBoolean("ai_content_rating_enabled", true)) }
    var aiMotivationalShare by remember { mutableStateOf(viewModel.getBoolean("ai_share_motivational_message", false)) }

    val providerIndex = providerValues.indexOf(selectedProvider).coerceAtLeast(0)
    val modelIndex = modelValues.indexOf(activeModel).coerceAtLeast(0)
    val modelSummary = if (modelEntries.isNotEmpty()) {
        modelEntries[modelIndex.coerceIn(0, modelEntries.lastIndex)]
    } else {
        activeModel
    }
    val effortIndex = effortValues.indexOf(reasoningEffort).coerceAtLeast(1)

    CategorySettingsScreen(
        title = "Inteligencia Artificial",
        onBack = onBack
    ) {
        SettingsCategoryHeader(title = "Cerebro de Aura (Multi-Proveedor)")

        SettingsListItem(
            title = "Proveedor de Inteligencia Artificial",
            summary = providerEntries[providerIndex],
            entries = providerEntries,
            entryValues = providerValues,
            currentValue = selectedProvider,
            isInitialFocus = true,
            onValueChange = { newProvider ->
                selectedProvider = newProvider
                viewModel.updateString("ai_provider", newProvider)
                geminiRepository.initialize()
            }
        )

        val openProviderApiKeyPage = {
            val targetUrl = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
                "https://console.groq.com/keys"
            } else {
                "https://aistudio.google.com/app/apikey"
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
        }

        if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            SettingsEditTextItem(
                title = "Establecer Clave API de Groq",
                summary = if (groqApiKey.isBlank()) {
                    "Introduce tu clave de Groq (gsk_...) para activar a Aura a máxima velocidad."
                } else {
                    "Clave de Groq configurada."
                },
                value = groqApiKey,
                placeholder = "Pega aquí tu clave (ej. gsk_...)",
                helperButtonLabel = "Crear o copiar API Key en Groq Console",
                onHelperButtonClick = openProviderApiKeyPage,
                onValueChange = { newKey ->
                    val clean = newKey.trim()
                    groqApiKey = clean
                    viewModel.updateString("groq_api_key", clean)
                    scope.launch {
                        if (clean.isNotBlank()) {
                            val models = geminiRepository.fetchAvailableModels(clean, GeminiRepository.PROVIDER_GROQ)
                            if (models.isNotEmpty()) {
                                Toast.makeText(context, "¡Clave de Groq guardada y sincronizada!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            SettingsClickItem(
                title = "Crear o copiar Clave API de Groq",
                summary = "Abre directamente Groq Cloud Console (console.groq.com/keys) para generar o copiar tu clave gratis.",
                onClick = openProviderApiKeyPage
            )
        } else {
            SettingsEditTextItem(
                title = "Establecer Clave API de Google Gemini",
                summary = if (geminiApiKey.isBlank()) {
                    "Introduce tu clave de Google AI Studio (AIzaSy...) para activar Gemini."
                } else {
                    "Clave de Gemini configurada."
                },
                value = geminiApiKey,
                placeholder = "Pega aquí tu clave (ej. AIzaSy...)",
                helperButtonLabel = "Crear o copiar API Key en Google AI Studio",
                onHelperButtonClick = openProviderApiKeyPage,
                onValueChange = { newKey ->
                    val clean = newKey.trim()
                    geminiApiKey = clean
                    viewModel.updateString("gemini_api_key", clean)
                    scope.launch {
                        if (clean.isNotBlank()) {
                            val models = geminiRepository.fetchAvailableModels(clean, GeminiRepository.PROVIDER_GEMINI)
                            if (models.isNotEmpty()) {
                                Toast.makeText(context, "¡Clave de Gemini guardada y sincronizada!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            SettingsClickItem(
                title = "Crear o copiar Clave API de Google Gemini",
                summary = "Abre directamente Google AI Studio (aistudio.google.com/app/apikey) para generar o copiar tu clave gratis.",
                onClick = openProviderApiKeyPage
            )
        }

        if (modelEntries.isNotEmpty()) {
            SettingsListItem(
                title = "Modelo de Inteligencia Artificial",
                summary = modelSummary,
                entries = modelEntries,
                entryValues = modelValues,
                currentValue = activeModel,
                onValueChange = { chosenModel ->
                    if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
                        groqModel = chosenModel
                        viewModel.updateString("groq_model", chosenModel)
                    } else {
                        geminiModel = chosenModel
                        viewModel.updateString("gemini_model", chosenModel)
                    }
                    geminiRepository.initialize()
                }
            )
        }

        SettingsSwitchItem(
            title = "Pensamiento profundo",
            summary = "Permite que Aura razone internamente antes de responder (GPT-OSS, Qwen 3, DeepSeek R1, Gemini 2.5 y Gemma 4).",
            checked = deepThinking,
            onCheckedChange = {
                deepThinking = it
                viewModel.updateBoolean("gemini_deep_thinking", it)
            }
        )

        if (deepThinking) {
            SettingsListItem(
                title = "Esfuerzo de razonamiento",
                summary = effortEntries[effortIndex],
                entries = effortEntries,
                entryValues = effortValues,
                currentValue = reasoningEffort,
                onValueChange = { newEffort ->
                    reasoningEffort = newEffort
                    viewModel.updateString("ai_reasoning_effort", newEffort)
                }
            )
        }

        SettingsCategoryHeader(title = "Funciones Asistidas por IA")

        SettingsSwitchItem(
            title = "Clasificación de contenido por IA",
            summary = "Muestra una advertencia de edad y descriptores de contenido al iniciar la reproducción.",
            checked = aiContentRating,
            onCheckedChange = {
                aiContentRating = it
                viewModel.updateBoolean("ai_content_rating_enabled", it)
            }
        )

        SettingsSwitchItem(
            title = "Generar mensaje de motivación al compartir",
            summary = "Aura redacta una invitación personalizada y entusiasta con el enlace al compartir contenido.",
            checked = aiMotivationalShare,
            onCheckedChange = {
                aiMotivationalShare = it
                viewModel.updateBoolean("ai_share_motivational_message", it)
            }
        )

        SettingsCategoryHeader(title = "Diagnóstico y Obtención de Claves")

        SettingsClickItem(
            title = "Comprobar estado de la conexión IA",
            summary = "Realiza una prueba en vivo con el proveedor y modelo seleccionados.",
            onClick = {
                scope.launch {
                    val startMs = System.currentTimeMillis()
                    if (geminiRepository.initialize()) {
                        val providerLabel = geminiRepository.getActiveProvider().uppercase()
                        val modelLabel = geminiRepository.getSelectedModel()
                        val result = geminiRepository.generateContent(
                            "[DIRECTIVA: SILENT_MODE] Responde únicamente con 'Conexión exitosa con $providerLabel ($modelLabel)'."
                        )
                        val elapsedMs = System.currentTimeMillis() - startMs
                        if (result.isSuccess) {
                            Toast.makeText(
                                context,
                                "✅ ${result.getOrNull()} (${elapsedMs}ms)",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "❌ Error de API ($providerLabel): ${result.exceptionOrNull()?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        val provName = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) "Groq" else "Google Gemini"
                        Toast.makeText(
                            context,
                            "Configura primero tu Clave API de $provName.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            SettingsClickItem(
                title = "¿No tienes una API Key de Groq?",
                summary = "Pulsa aquí para conseguir una gratis en Groq Cloud Console (console.groq.com)",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys")))
                }
            )
        } else {
            SettingsClickItem(
                title = "¿No tienes una API Key de Gemini?",
                summary = "Pulsa aquí para conseguir una gratis en Google AI Studio (aistudio.google.com)",
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey")))
                }
            )
        }
    }
}
