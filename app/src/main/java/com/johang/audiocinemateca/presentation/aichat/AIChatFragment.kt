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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.johang.audiocinemateca.MainNavGraphDirections
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
        binding.buttonRestartChat.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reiniciar Chat")
                .setMessage("¿Estás seguro de que quieres borrar la conversación actual?")
                .setPositiveButton("Sí, borrar") { _, _ -> viewModel.restartChat() }
                .setNegativeButton("No", null)
                .show()
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
                val lastUserMsg = viewModel.messages.value.lastOrNull { it.isUser }
                lastUserMsg?.let { viewModel.sendMessage(it.text) }
            },
            onSettingsClick = {
                val action = MainNavGraphDirections.actionGlobalSettingsFragment()
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(action)
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
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
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
        viewModel.vibrateShort()
        binding.buttonVoice.animate().scaleX(1.2f).scaleY(1.2f).setDuration(100).start()
        binding.editTextMessage.hint = "Escuchando..."
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun stopListening() {
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
                            status.contains("Escribiendo") || status.contains("pensando") || status.contains("consultando") || status.contains("redactando") -> android.graphics.Color.parseColor("#FBC02D")
                            status.contains("En línea") -> android.graphics.Color.parseColor("#4CAF50")
                            else -> android.graphics.Color.parseColor("#F44336")
                        }
                        binding.viewStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                    }
                }

                launch {
                    viewModel.accessibilityAnnouncement.collect { announcement ->
                        announcement?.let {
                            binding.recyclerChat.announceForAccessibility(it)
                            viewModel.clearAnnouncement()
                        }
                    }
                }
            }
        }
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
        androidx.navigation.fragment.NavHostFragment.findNavController(this).navigate(action)
    }
}
