package com.johang.audiocinemateca.presentation.aichat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class AIChatFragment : Fragment() {

    private val viewModel: AIChatViewModel by viewModels()
    private var speechRecognizer: SpeechRecognizer? = null
    private val isListeningState = mutableStateOf(false)
    private val inputTextState = mutableStateOf("")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        } else {
            Toast.makeText(context, "Se necesita permiso de micrófono para usar la voz.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    val messages by viewModel.messages.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()
                    val auraStatus by viewModel.auraStatus.collectAsState()
                    val inputText by inputTextState
                    val isListening by isListeningState

                    AiChatScreen(
                        messages = messages,
                        isLoading = isLoading,
                        auraStatus = auraStatus,
                        inputText = inputText,
                        isListening = isListening,
                        onInputTextChange = { inputTextState.value = it },
                        onSendMessage = { text ->
                            viewModel.sendMessage(text)
                            inputTextState.value = ""
                        },
                        onVoiceClick = {
                            toggleVoiceListening()
                        },
                        onNavigateBack = {
                            findNavController().navigateUp()
                        },
                        onNavigateToAiSettings = {
                            navigateToAiSettings()
                        },
                        onRestartChat = {
                            viewModel.restartChat()
                        },
                        onRetryMessage = {
                            viewModel.reinitializeAndRetry()
                        },
                        onLinkedContentClick = { linked ->
                            navigateToDetail(linked)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSpeechRecognizer()
        observeNavigationEvents()
        viewModel.initializeChat()
    }

    private fun observeNavigationEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationEvent.collect { event ->
                    if (event is AIChatViewModel.ChatNavigationEvent.PlayContent) {
                        navigateToPlayer(event.item, event.seasonIndex, event.episodeIndex, event.startPosition)
                        viewModel.clearNavigationEvent()
                    }
                }
            }
        }
    }

    private fun setupSpeechRecognizer() {
        val isAvailable = SpeechRecognizer.isRecognitionAvailable(requireContext())
        if (isAvailable) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
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
                    isListeningState.value = false
                    val isIgnoreable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == 5 || error == 8

                    if (!isIgnoreable) {
                        viewModel.onVoiceError()
                        Toast.makeText(context, "Error al capturar voz ($error).", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    isListeningState.value = false
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0)
                    if (!text.isNullOrBlank()) {
                        viewModel.sendMessage(text)
                        inputTextState.value = ""
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun toggleVoiceListening() {
        if (isListeningState.value) {
            stopListening()
        } else {
            checkPermissionAndStartListening()
        }
    }

    private fun checkPermissionAndStartListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(requireContext(), "El dictado por voz no está disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }
        isListeningState.value = true
        viewModel.onVoiceStart()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        isListeningState.value = false
        viewModel.onVoiceEnd()
        speechRecognizer?.stopListening()
    }

    private fun navigateToAiSettings() {
        try {
            findNavController().navigate(
                R.id.action_global_categorySettingsFragment,
                bundleOf("category" to "ai")
            )
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Error navegando a ajustes IA", e)
            val action = MainNavGraphDirections.actionGlobalSettingsFragment()
            findNavController().navigate(action)
        }
    }

    private fun navigateToDetail(linked: LinkedContent) {
        val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId = linked.id, itemType = linked.type)
        findNavController().navigate(action)
    }

    private fun navigateToPlayer(
        item: com.johang.audiocinemateca.domain.model.CatalogItem,
        seasonIndex: Int,
        episodeIndex: Int,
        startPosition: Long
    ) {
        val type = when (item) {
            is com.johang.audiocinemateca.data.model.Serie -> "series"
            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
            is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
            else -> "peliculas"
        }

        findNavController().navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, type))
        findNavController().navigate(
            R.id.action_global_playerFragment, bundleOf(
                "catalogItem" to item,
                "partIndex" to seasonIndex,
                "episodeIndex" to episodeIndex,
                "startPosition" to startPosition
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
