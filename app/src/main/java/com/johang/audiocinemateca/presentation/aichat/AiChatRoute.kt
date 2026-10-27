package com.johang.audiocinemateca.presentation.aichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.johang.audiocinemateca.domain.model.CatalogItem
import java.util.Locale

@Composable
fun AiChatRoute(
    onNavigateBack: () -> Unit,
    onNavigateToAiSettings: () -> Unit,
    onNavigateToDetail: (String, String) -> Unit,
    onPlayContent: (CatalogItem, Int, Int) -> Unit,
    viewModel: AIChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val auraStatus by viewModel.auraStatus.collectAsState()
    val chatSessions by viewModel.chatSessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val activeSessionTitle by viewModel.activeSessionTitle.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopListening() {
        isListening = false
        viewModel.onVoiceEnd()
        speechRecognizer?.stopListening()
    }

    fun startListening() {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            Toast.makeText(context, "El dictado por voz no está disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }
        isListening = true
        viewModel.onVoiceStart()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        recognizer.startListening(intent)
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(context, "Se necesita permiso de micrófono para usar la voz.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeChat()
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { event ->
            if (event is AIChatViewModel.ChatNavigationEvent.PlayContent) {
                onPlayContent(event.item, event.seasonIndex, event.episodeIndex)
                viewModel.clearNavigationEvent()
            }
        }
    }

    DisposableEffect(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    viewModel.vibrateShort()
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    stopListening()
                }
                override fun onError(error: Int) {
                    isListening = false
                    val isIgnoreable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == 5 || error == 8
                    if (!isIgnoreable) {
                        viewModel.onVoiceError()
                        Toast.makeText(context, "Error al capturar voz ($error).", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        viewModel.sendMessage(text)
                        inputText = ""
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer
        }

        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    AiChatScreen(
        messages = messages,
        isLoading = isLoading,
        auraStatus = auraStatus,
        chatSessions = chatSessions,
        activeSessionId = activeSessionId,
        activeSessionTitle = activeSessionTitle,
        activeProvider = activeProvider,
        selectedModelId = selectedModelId,
        availableModels = availableModels,
        inputText = inputText,
        isListening = isListening,
        onInputTextChange = { inputText = it },
        onSendMessage = { text ->
            viewModel.sendMessage(text)
            inputText = ""
        },
        onVoicePressStart = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startListening()
            }
        },
        onVoicePressEnd = {
            if (isListening) {
                stopListening()
            }
        },
        onNavigateBack = onNavigateBack,
        onNavigateToAiSettings = onNavigateToAiSettings,
        onNewChat = { viewModel.startNewChatSession() },
        onSelectSession = { sessionId -> viewModel.selectChatSession(sessionId) },
        onRenameSession = { sessionId, newTitle -> viewModel.renameChatSession(sessionId, newTitle) },
        onDeleteSession = { sessionId -> viewModel.deleteChatSession(sessionId) },
        onClearAllSessions = { viewModel.clearAllChatSessions() },
        onSelectModel = { modelId -> viewModel.selectAiModel(modelId) },
        onRestartChat = { viewModel.restartChat() },
        onRetryMessage = { viewModel.reinitializeAndRetry() },
        onLinkedContentClick = { linked ->
            onNavigateToDetail(linked.id, linked.type)
        }
    )
}
