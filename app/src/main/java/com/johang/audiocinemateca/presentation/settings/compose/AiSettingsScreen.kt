package com.johang.audiocinemateca.presentation.settings.compose

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.johang.audiocinemateca.data.repository.AiConnectionStatus
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
        mutableStateOf(viewModel.getString("groq_model", "").trim())
    }
    var geminiModel by remember {
        mutableStateOf(viewModel.getString("gemini_model", "").trim().removePrefix("models/"))
    }

    var reasoningEffort by remember {
        mutableStateOf(
            viewModel.getString("ai_reasoning_effort", "medium")
                .let { if (it in listOf("low", "medium", "high", "extra")) it else "medium" }
        )
    }

    val effortEntries = remember {
        listOf(
            "Bajo",
            "Medio (Predeterminado)",
            "Alto",
            "Extra (Solo modelos compatibles)"
        )
    }
    val effortValues = remember { listOf("low", "medium", "high", "extra") }

    val connectionStates by geminiRepository.connectionStates.collectAsState()
    val liveModelsMap by geminiRepository.liveModelsByProvider.collectAsState()
    var isSyncingModels by remember { mutableStateOf(false) }

    val activeApiKey = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) groqApiKey else geminiApiKey
    val activeModel = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) groqModel else geminiModel
    val liveModels = liveModelsMap[selectedProvider].orEmpty()
    val modelEntries = liveModels.map { it.second }
    val modelValues = liveModels.map { it.first }
    val currentConnState = connectionStates[selectedProvider] ?: geminiRepository.getConnectionState(selectedProvider)

    suspend fun syncLiveModelsForCurrentProvider(verifyPromptToo: Boolean = false) {
        val key = activeApiKey.trim()
        if (key.isBlank()) {
            geminiRepository.clearProviderCache(selectedProvider)
            return
        }

        val hadRuntimeError = currentConnState.status == AiConnectionStatus.ERROR && !verifyPromptToo
        val prevErrorMsg = currentConnState.message

        isSyncingModels = true
        val syncResult = geminiRepository.fetchAvailableModelsLive(
            providedApiKey = key,
            providerOverride = selectedProvider
        )
        isSyncingModels = false

        if (syncResult.isSuccess && syncResult.models.isNotEmpty()) {
            val validModelId = geminiRepository.pickPreferredModelId(
                provider = selectedProvider,
                liveModels = syncResult.models,
                currentModel = activeModel
            )
            if (validModelId.isNotBlank()) {
                if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
                    groqModel = validModelId
                    viewModel.updateString("groq_model", validModelId)
                } else {
                    geminiModel = validModelId
                    viewModel.updateString("gemini_model", validModelId)
                }
            }
            geminiRepository.initialize(providerOverride = selectedProvider)

            if (hadRuntimeError && prevErrorMsg.isNotBlank()) {
                geminiRepository.recordConnectionState(
                    provider = selectedProvider,
                    status = AiConnectionStatus.ERROR,
                    message = prevErrorMsg,
                    modelName = validModelId,
                    modelsCount = syncResult.models.size
                )
            }
        }
    }

    LaunchedEffect(selectedProvider, activeApiKey) {
        syncLiveModelsForCurrentProvider(verifyPromptToo = false)
    }

    var aiContentRating by remember { mutableStateOf(viewModel.getBoolean("ai_content_rating_enabled", true)) }
    var aiMotivationalShare by remember { mutableStateOf(viewModel.getBoolean("ai_share_motivational_message", false)) }

    val hasGeminiKey = geminiApiKey.trim().isNotBlank()
    var auraVoiceMode by remember {
        mutableStateOf(
            viewModel.getString("aura_voice_mode", GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS)
                .let {
                    if (it == GeminiRepository.AURA_VOICE_MODE_GEMINI && hasGeminiKey) {
                        GeminiRepository.AURA_VOICE_MODE_GEMINI
                    } else {
                        GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS
                    }
                }
        )
    }
    var geminiTtsModel by remember {
        mutableStateOf(geminiRepository.getSelectedGeminiTtsModel())
    }
    var geminiTtsVoice by remember {
        mutableStateOf(geminiRepository.getSelectedGeminiTtsVoice())
    }
    var isPreviewingVoice by remember { mutableStateOf(false) }

    val liveTtsModels by geminiRepository.liveTtsModels.collectAsState()

    // Si el usuario tiene Clave de Gemini (incluso usando Groq para chatear), sincronizamos modelos TTS
    LaunchedEffect(geminiApiKey, selectedProvider) {
        val gKey = geminiApiKey.trim()
        if (gKey.isNotBlank() && selectedProvider != GeminiRepository.PROVIDER_GEMINI && liveTtsModels.isEmpty()) {
            val res = geminiRepository.fetchAvailableModelsLive(
                providedApiKey = gKey,
                providerOverride = GeminiRepository.PROVIDER_GEMINI
            )
            if (res.isSuccess) {
                val syncedTts = geminiRepository.liveTtsModels.value
                if (syncedTts.isNotEmpty()) {
                    val chosen = geminiRepository.pickPreferredTtsModelId(syncedTts, geminiTtsModel)
                    geminiTtsModel = chosen
                    viewModel.updateString("gemini_tts_model", chosen)
                }
            }
        }
    }

    LaunchedEffect(liveTtsModels) {
        if (liveTtsModels.isNotEmpty()) {
            val chosen = geminiRepository.pickPreferredTtsModelId(liveTtsModels, geminiTtsModel)
            if (chosen.isNotBlank() && chosen != geminiTtsModel) {
                geminiTtsModel = chosen
                viewModel.updateString("gemini_tts_model", chosen)
            }
        }
    }

    LaunchedEffect(hasGeminiKey) {
        if (!hasGeminiKey && auraVoiceMode == GeminiRepository.AURA_VOICE_MODE_GEMINI) {
            auraVoiceMode = GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS
            viewModel.updateString("aura_voice_mode", GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.auraVoicePlayer.stop()
        }
    }

    val providerIndex = providerValues.indexOf(selectedProvider).coerceAtLeast(0)
    val modelIndex = modelValues.indexOf(activeModel).coerceAtLeast(0)
    val modelSummary = when {
        activeApiKey.isBlank() -> "Configura tu Clave API para cargar los modelos disponibles."
        isSyncingModels -> "Cargando modelos en vivo desde el proveedor..."
        modelEntries.isNotEmpty() -> modelEntries[modelIndex.coerceIn(0, modelEntries.lastIndex)]
        else -> currentConnState.message.ifBlank { "No se pudieron obtener los modelos disponibles." }
    }
    val effortIndex = effortValues.indexOf(reasoningEffort).let { if (it >= 0) it else 1 }

    val openProviderApiKeyPage = {
        val targetUrl = if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            "https://console.groq.com/keys"
        } else {
            "https://aistudio.google.com/app/apikey"
        }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
        }.onFailure {
            Toast.makeText(context, "No se pudo abrir el navegador en este dispositivo.", Toast.LENGTH_SHORT).show()
        }
        Unit
    }

    CategorySettingsScreen(
        title = "Inteligencia Artificial",
        onBack = onBack
    ) {
        SettingsCategoryHeader(title = "Proveedor y Clave de Acceso")

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
                geminiRepository.initialize(providerOverride = newProvider)
            }
        )

        if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
            SettingsEditTextItem(
                title = "Establecer Clave API de Groq",
                summary = if (groqApiKey.isBlank()) {
                    "Sin clave configurada. Pulsa para pegar o escribir tu clave (gsk_...)."
                } else {
                    "Clave de Groq configurada. Pulsa para editarla o cambiarla."
                },
                value = groqApiKey,
                placeholder = "Pega aquí tu clave (ej. gsk_...)",
                helperButtonLabel = "Crear o copiar Clave API en Groq",
                onHelperButtonClick = openProviderApiKeyPage,
                onValueChange = { newKey ->
                    val clean = newKey.trim()
                    groqApiKey = clean
                    viewModel.updateString("groq_api_key", clean)
                    scope.launch {
                        if (clean.isBlank()) {
                            geminiRepository.clearProviderCache(GeminiRepository.PROVIDER_GROQ)
                            Toast.makeText(context, "Clave de Groq eliminada.", Toast.LENGTH_SHORT).show()
                        } else {
                            isSyncingModels = true
                            val res = geminiRepository.testLiveConnection(GeminiRepository.PROVIDER_GROQ)
                            isSyncingModels = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Conectado con Groq y modelos cargados.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.exceptionOrNull()?.message ?: "Error al validar la clave de Groq.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )

            SettingsClickItem(
                title = "Crear o copiar Clave API de Groq",
                summary = "Abre la página oficial console.groq.com/keys para generar tu clave gratuita.",
                onClick = openProviderApiKeyPage
            )
        } else {
            SettingsEditTextItem(
                title = "Establecer Clave API de Google Gemini",
                summary = if (geminiApiKey.isBlank()) {
                    "Sin clave configurada. Pulsa para pegar o escribir tu clave (AIzaSy...)."
                } else {
                    "Clave de Google Gemini configurada. Pulsa para editarla o cambiarla."
                },
                value = geminiApiKey,
                placeholder = "Pega aquí tu clave (ej. AIzaSy...)",
                helperButtonLabel = "Crear o copiar Clave API en Google AI Studio",
                onHelperButtonClick = openProviderApiKeyPage,
                onValueChange = { newKey ->
                    val clean = newKey.trim()
                    geminiApiKey = clean
                    viewModel.updateString("gemini_api_key", clean)
                    scope.launch {
                        if (clean.isBlank()) {
                            geminiRepository.clearProviderCache(GeminiRepository.PROVIDER_GEMINI)
                            Toast.makeText(context, "Clave de Google Gemini eliminada.", Toast.LENGTH_SHORT).show()
                        } else {
                            isSyncingModels = true
                            val res = geminiRepository.testLiveConnection(GeminiRepository.PROVIDER_GEMINI)
                            isSyncingModels = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Conectado con Google Gemini y modelos cargados.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    res.exceptionOrNull()?.message ?: "Error al validar la clave de Gemini.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )

            SettingsClickItem(
                title = "Crear o copiar Clave API de Google Gemini",
                summary = "Abre Google AI Studio (aistudio.google.com/app/apikey) para generar tu clave gratuita.",
                onClick = openProviderApiKeyPage
            )
        }

        SettingsCategoryHeader(title = "Modelo y Razonamiento")

        SettingsListItem(
            title = "Modelo de Inteligencia Artificial",
            summary = modelSummary,
            entries = modelEntries,
            entryValues = modelValues,
            currentValue = activeModel,
            enabled = activeApiKey.isNotBlank() && !isSyncingModels && modelEntries.isNotEmpty(),
            onValueChange = { chosenModel ->
                if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
                    groqModel = chosenModel
                    viewModel.updateString("groq_model", chosenModel)
                } else {
                    geminiModel = chosenModel
                    viewModel.updateString("gemini_model", chosenModel)
                }
                geminiRepository.initialize(providerOverride = selectedProvider)
                scope.launch {
                    isSyncingModels = true
                    val res = geminiRepository.testLiveConnection(selectedProvider, refreshModels = false)
                    isSyncingModels = false
                    if (res.isFailure) {
                        Toast.makeText(
                            context,
                            res.exceptionOrNull()?.message ?: "Error al probar el modelo.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

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

        SettingsCategoryHeader(title = "Estado de la Conexión")

        val statusTitle = when {
            activeApiKey.isBlank() -> "Estado: Desconectado"
            isSyncingModels || currentConnState.status == AiConnectionStatus.CONNECTING -> "Estado: Conectando..."
            currentConnState.status == AiConnectionStatus.CONNECTED -> "Estado: Conectado"
            currentConnState.status == AiConnectionStatus.ERROR -> "Estado: Con errores"
            else -> "Estado: Desconectado"
        }

        val statusDetail = when {
            activeApiKey.isBlank() -> "No hay ninguna Clave API configurada para este proveedor."
            isSyncingModels || currentConnState.status == AiConnectionStatus.CONNECTING ->
                "Verificando tu Clave API y consultando los modelos disponibles..."
            else -> currentConnState.message
        }

        val runLiveTest: () -> Unit = {
            if (!isSyncingModels) {
                scope.launch {
                    val startMs = System.currentTimeMillis()
                    isSyncingModels = true
                    val result = geminiRepository.testLiveConnection(selectedProvider, refreshModels = true)
                    isSyncingModels = false
                    val elapsedMs = System.currentTimeMillis() - startMs

                    if (result.isSuccess) {
                        val refreshedLive = geminiRepository.getCachedLiveModels(selectedProvider)
                        if (refreshedLive.isNotEmpty()) {
                            val validId = geminiRepository.pickPreferredModelId(
                                selectedProvider,
                                refreshedLive,
                                activeModel
                            )
                            if (validId.isNotBlank()) {
                                if (selectedProvider == GeminiRepository.PROVIDER_GROQ) {
                                    groqModel = validId
                                    viewModel.updateString("groq_model", validId)
                                } else {
                                    geminiModel = validId
                                    viewModel.updateString("gemini_model", validId)
                                }
                            }
                        }
                        Toast.makeText(
                            context,
                            "Conexión exitosa (${elapsedMs}ms)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            result.exceptionOrNull()?.message ?: "No se pudo conectar.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        SettingsClickItem(
            title = statusTitle,
            summary = statusDetail,
            onClick = runLiveTest
        )

        SettingsClickItem(
            title = "Comprobar conexión y actualizar modelos",
            summary = "Verifica en vivo tu Clave API, sincroniza la lista real de modelos y prueba el modelo activo.",
            onClick = runLiveTest
        )

        SettingsCategoryHeader(title = "Voz de Aura")

        val voiceModeEntries = remember {
            listOf(
                "TTS del dispositivo (Predeterminado)",
                "Voz expresiva de Aura (Google Gemini)"
            )
        }
        val voiceModeValues = remember {
            listOf(
                GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS,
                GeminiRepository.AURA_VOICE_MODE_GEMINI
            )
        }
        val effectiveVoiceMode = if (auraVoiceMode == GeminiRepository.AURA_VOICE_MODE_GEMINI && hasGeminiKey) {
            GeminiRepository.AURA_VOICE_MODE_GEMINI
        } else {
            GeminiRepository.AURA_VOICE_MODE_DEVICE_TTS
        }
        val voiceModeSummary = when {
            effectiveVoiceMode == GeminiRepository.AURA_VOICE_MODE_GEMINI ->
                "Voz expresiva de Aura (Google Gemini) • Con respaldo automático al TTS local"
            !hasGeminiKey ->
                "TTS del dispositivo (Predeterminado). Configura una Clave API de Google Gemini para desbloquear la voz expresiva de Aura."
            else ->
                "TTS del dispositivo (Predeterminado)"
        }

        SettingsListItem(
            title = "Forma de leer las respuestas de Aura",
            summary = voiceModeSummary,
            entries = voiceModeEntries,
            entryValues = voiceModeValues,
            currentValue = effectiveVoiceMode,
            onValueChange = { newMode ->
                if (newMode == GeminiRepository.AURA_VOICE_MODE_GEMINI && !hasGeminiKey) {
                    Toast.makeText(
                        context,
                        "Necesitas guardar una Clave API de Google Gemini para usar la voz expresiva de Aura (funciona incluso si chateas con Groq).",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    auraVoiceMode = newMode
                    viewModel.updateString("aura_voice_mode", newMode)
                }
            }
        )

        if (effectiveVoiceMode == GeminiRepository.AURA_VOICE_MODE_GEMINI) {
            val ttsModelEntries = if (liveTtsModels.isNotEmpty()) {
                liveTtsModels.map { it.second }
            } else {
                listOf(
                    "Gemini 3.8 Flash TTS (${GeminiRepository.DEFAULT_GEMINI_TTS_MODEL})",
                    "Gemini 3.8 Flash-Lite TTS (gemini-3.8-flash-lite-tts)",
                    "Gemini 2.5 Flash Preview TTS (gemini-2.5-flash-preview-tts)",
                    "Gemini 2.5 Pro Preview TTS (gemini-2.5-pro-preview-tts)"
                )
            }
            val ttsModelValues = if (liveTtsModels.isNotEmpty()) {
                liveTtsModels.map { it.first }
            } else {
                listOf(
                    GeminiRepository.DEFAULT_GEMINI_TTS_MODEL,
                    "gemini-3.8-flash-lite-tts",
                    "gemini-2.5-flash-preview-tts",
                    "gemini-2.5-pro-preview-tts"
                )
            }
            val ttsModelIdx = ttsModelValues.indexOf(geminiTtsModel).coerceAtLeast(0)
            val ttsModelSummary = when {
                isSyncingModels && liveTtsModels.isEmpty() -> "Consultando modelos TTS disponibles..."
                else -> ttsModelEntries[ttsModelIdx.coerceIn(0, ttsModelEntries.lastIndex)]
            }

            SettingsListItem(
                title = "Modelo de voz Gemini",
                summary = ttsModelSummary,
                entries = ttsModelEntries,
                entryValues = ttsModelValues,
                currentValue = ttsModelValues[ttsModelIdx.coerceIn(0, ttsModelValues.lastIndex)],
                onValueChange = { chosenTtsModel ->
                    geminiTtsModel = chosenTtsModel
                    viewModel.updateBoolean("gemini_tts_model_user_chosen", true)
                    viewModel.updateString("gemini_tts_model", chosenTtsModel)
                }
            )

            val voiceEntries = remember { GeminiRepository.GEMINI_TTS_VOICES.map { it.second } }
            val voiceValues = remember { GeminiRepository.GEMINI_TTS_VOICES.map { it.first } }
            val voiceIdx = voiceValues.indexOf(geminiTtsVoice).coerceAtLeast(0)

            SettingsListItem(
                title = "Timbre de voz de Aura",
                summary = voiceEntries[voiceIdx.coerceIn(0, voiceEntries.lastIndex)],
                entries = voiceEntries,
                entryValues = voiceValues,
                currentValue = voiceValues[voiceIdx.coerceIn(0, voiceValues.lastIndex)],
                onValueChange = { chosenVoice ->
                    geminiTtsVoice = chosenVoice
                    viewModel.updateString("gemini_tts_voice", chosenVoice)
                }
            )

            SettingsClickItem(
                title = if (isPreviewingVoice) "Generando muestra de voz..." else "Probar voz expresiva de Aura",
                summary = if (isPreviewingVoice) {
                    "Sintetizando saludo de prueba con $geminiTtsVoice..."
                } else {
                    "Escucha cómo suena Aura usando $geminiTtsVoice ($geminiTtsModel)."
                },
                onClick = {
                    if (!isPreviewingVoice) {
                        scope.launch {
                            isPreviewingVoice = true
                            val previewRes = viewModel.auraVoicePlayer.previewExpressiveVoice(
                                modelId = geminiTtsModel,
                                voiceName = geminiTtsVoice
                            )
                            isPreviewingVoice = false
                            previewRes.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: "No se pudo reproducir la voz de prueba.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            )
        }

        SettingsCategoryHeader(title = "Funciones Asistidas")

        SettingsSwitchItem(
            title = "Clasificación de contenido al reproducir",
            summary = "Muestra una advertencia informativa de edad y descriptores antes de iniciar una obra.",
            checked = aiContentRating,
            onCheckedChange = {
                aiContentRating = it
                viewModel.updateBoolean("ai_content_rating_enabled", it)
            }
        )

        SettingsSwitchItem(
            title = "Mensaje personalizado al compartir",
            summary = "Aura redacta una invitación cálida junto al enlace cuando compartes una película o serie.",
            checked = aiMotivationalShare,
            onCheckedChange = {
                aiMotivationalShare = it
                viewModel.updateBoolean("ai_share_motivational_message", it)
            }
        )
    }
}
