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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.FragmentAiChatBinding
import com.johang.audiocinemateca.presentation.aichat.adapter.ChatAdapter
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class AIChatFragment : Fragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AIChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private var speechRecognizer: SpeechRecognizer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Se necesita permiso de micrófono para usar la voz.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Forzar sincronización de la barra superior, el icono y el título
        (activity as? androidx.appcompat.app.AppCompatActivity)?.let { act ->
            act.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            act.supportActionBar?.title = "Asistente IA"
        }
        
        // Asegurar que el chat esté al final
        if (::chatAdapter.isInitialized && chatAdapter.itemCount > 0) {
            binding.recyclerChat.scrollToPosition(chatAdapter.itemCount - 1)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.title = "Asistente IA"

        setupRecyclerView()
        setupInput()
        setupHeader()
        observeViewModel()
        setupSpeechRecognizer()

        viewModel.initializeChat()
    }

    private fun setupHeader() {
        // Botón Configuración IA → navega directo a la categoría IA
        binding.buttonAiSettings.setOnClickListener {
            navigateToAiSettings()
        }

        // Botón Reiniciar conversación
        binding.buttonRestartChat.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reiniciar Chat")
                .setMessage("¿Estás seguro de que quieres borrar la conversación actual?")
                .setPositiveButton("Sí, borrar") { _, _ -> viewModel.restartChat() }
                .setNegativeButton("No", null)
                .show()
        }
    }

    /**
     * Navega directamente a la configuración de IA (sin pasar por la pantalla de ajustes general).
     */
    private fun navigateToAiSettings() {
        try {
            findNavController().navigate(
                R.id.action_global_categorySettingsFragment,
                bundleOf("category" to "ai")
            )
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Error navegando a ajustes IA", e)
            // Fallback: navegar a ajustes generales
            val action = MainNavGraphDirections.actionGlobalSettingsFragment()
            findNavController().navigate(action)
        }
    }

    private fun setupRecyclerView() {
        val markwon = Markwon.create(requireContext())
        chatAdapter = ChatAdapter(
            markwon = markwon,
            onMessageClick = { linkedItems ->
                if (linkedItems.isNotEmpty()) {
                    showRecommendationsDialog(linkedItems)
                }
            },
            onRetryClick = {
                // Al reintentar: reinicializar el modelo (por si cambió en ajustes),
                // luego reenviar el último mensaje del usuario
                viewModel.reinitializeAndRetry()
            },
            onLinkedContentClick = { linked ->
                navigateToDetail(linked)
            }
        )

        binding.recyclerChat.apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = chatAdapter
        }
    }

    private fun setupInput() {
        binding.buttonSend.visibility = View.GONE
        binding.buttonVoice.visibility = View.VISIBLE

        binding.editTextMessage.addTextChangedListener { text ->
            val hasText = !text.isNullOrBlank()
            binding.buttonSend.visibility = if (hasText) View.VISIBLE else View.GONE
            binding.buttonVoice.visibility = if (hasText) View.GONE else View.VISIBLE
        }

        binding.buttonSend.setOnClickListener { sendMessage() }
        binding.buttonVoice.setOnClickListener { }

        binding.editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        binding.buttonVoice.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    checkPermissionAndStartListening()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopListening()
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { 
                    viewModel.vibrateShort() 
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // Filtrar códigos que no son errores reales para el usuario:
                    // 7: No match, 6: Timeout, 5: Client error (a veces al cancelar), 8: Busy
                    val isIgnoreable = error == SpeechRecognizer.ERROR_NO_MATCH || 
                                     error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                     error == 5 || error == 8
                    
                    if (!isIgnoreable) {
                        viewModel.onVoiceError()
                        Toast.makeText(context, "Error al capturar voz.", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = data?.get(0)
                    if (!text.isNullOrBlank()) {
                        binding.editTextMessage.setText(text)
                        sendMessage()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
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
        viewModel.onVoiceStart()
        binding.buttonVoice.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
        binding.editTextMessage.hint = "Escuchando..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
        viewModel.onVoiceEnd()
        binding.buttonVoice.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
        binding.editTextMessage.hint = "Escribe un mensaje..."
        speechRecognizer?.stopListening()
    }

    private fun sendMessage() {
        val text = binding.editTextMessage.text.toString().trim()
        if (text.isNotEmpty()) {
            viewModel.sendMessage(text)
            binding.editTextMessage.text?.clear()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { messages ->
                        chatAdapter.submitList(messages) {
                            if (messages.isNotEmpty()) binding.recyclerChat.smoothScrollToPosition(messages.size - 1)
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { isLoading ->
                        binding.progressBarLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.buttonSend.isEnabled = !isLoading
                    }
                }

                launch {
                    viewModel.auraStatus.collect { status ->
                        binding.textAuraStatus.text = status
                        val color = when {
                            status.contains("Escribiendo") || status.contains("pensando") || status.contains("consultando") || status.contains("redactando") || status.contains("formulando") || status.contains("preparando") -> android.graphics.Color.parseColor("#FBC02D")
                            status.contains("En línea") -> android.graphics.Color.parseColor("#4CAF50")
                            else -> android.graphics.Color.parseColor("#F44336")
                        }
                        binding.viewStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                    }
                }

                launch {
                    viewModel.navigationEvent.collect { event ->
                        if (event is AIChatViewModel.ChatNavigationEvent.PlayContent) {
                            navigateToPlayer(event.item, event.seasonIndex, event.episodeIndex, event.startPosition)
                            viewModel.clearNavigationEvent()
                        }
                    }
                }
            }
        }
    }

    private fun navigateToPlayer(item: com.johang.audiocinemateca.domain.model.CatalogItem, seasonIndex: Int, episodeIndex: Int, startPosition: Long) {
        // Primero vamos al detalle (opcional, pero ayuda a cargar contexto)
        val type = when(item) {
            is com.johang.audiocinemateca.data.model.Serie -> "series"
            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
            is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
            else -> "peliculas"
        }

        findNavController().navigate(MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, type))

        // Y luego al player directamente
        findNavController().navigate(R.id.action_global_playerFragment, bundleOf(
            "catalogItem" to item,
            "partIndex" to seasonIndex,
            "episodeIndex" to episodeIndex,
            "startPosition" to startPosition
        ))
    }


    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }

    private fun showRecommendationsDialog(items: List<LinkedContent>) {
        val titles = items.map { it.title }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Recomendaciones de Aura")
            .setItems(titles) { _, which -> navigateToDetail(items[which]) }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun navigateToDetail(linked: LinkedContent) {
        val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId = linked.id, itemType = linked.type)
        findNavController().navigate(action)
    }
}
