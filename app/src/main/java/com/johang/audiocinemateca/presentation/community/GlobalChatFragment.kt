package com.johang.audiocinemateca.presentation.community

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.GridLayoutManager
import com.johang.audiocinemateca.databinding.FragmentGlobalChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class GlobalChatFragment : Fragment() {

    private var _binding: FragmentGlobalChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlobalChatViewModel by viewModels()
    private val adapter = GlobalChatAdapter()
    
    private var speechRecognizer: SpeechRecognizer? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Permiso concedido. Mantén pulsado para hablar.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "Se necesita el permiso de micrófono.", Toast.LENGTH_LONG).show()
        }
    }

    // Callback para manejar el botón atrás
    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (binding.emojiPickerContainer.visibility == View.VISIBLE) {
                toggleEmojiPicker(false)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlobalChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        setupRecyclerView()
        setupInput()
        setupEmojiPicker()
        setupVoiceToText()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvGlobalChat.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.rvGlobalChat.adapter = adapter
    }

    private fun setupInput() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnSend.isEnabled = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString()
            if (viewModel.sendMessage(text)) {
                binding.etMessage.text.clear()
                // Solo cerramos si estaba abierto
                if (binding.emojiPickerContainer.visibility == View.VISIBLE) {
                    toggleEmojiPicker(false)
                } else {
                    // Si no estaba el panel de emojis, simplemente ocultamos el teclado
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
                }
            } else {
                Toast.makeText(requireContext(), "Espera 2 segundos entre mensajes.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupEmojiPicker() {
        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", 
            "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", 
            "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", 
            "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", 
            "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", 
            "😽", "🙀", "😿", "😾", "🙈", "🙉", "🙊", "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞", "💕", 
            "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥", "💫", 
            "💦", "💨", "🕳️", "💣", "💬", "👁️‍🗨️", "🗨️", "🗯️", "💭", "💤", "👋", "🤚", "🖐️", "✋", "🖖", "👌", 
            "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", 
            "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", 
            "🦶", "👣", "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁️", "👅", "👄"
        )

        binding.rvEmojis.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvEmojis.adapter = EmojiAdapter(emojis) { emoji ->
            val start = binding.etMessage.selectionStart
            val end = binding.etMessage.selectionEnd
            binding.etMessage.text.replace(start, end, emoji)
        }

        binding.btnEmoji.setOnClickListener {
            val isVisible = binding.emojiPickerContainer.visibility == View.VISIBLE
            toggleEmojiPicker(!isVisible)
        }

        binding.etMessage.setOnClickListener {
            if (binding.emojiPickerContainer.visibility == View.VISIBLE) {
                toggleEmojiPicker(false)
            }
        }
    }

    private fun toggleEmojiPicker(show: Boolean) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val am = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

        if (show) {
            imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
            binding.emojiPickerContainer.visibility = View.VISIBLE
            onBackPressedCallback.isEnabled = true
            announceForAccessibility("Panel de emojis abierto en la parte inferior.")
        } else {
            binding.emojiPickerContainer.visibility = View.GONE
            onBackPressedCallback.isEnabled = false
            binding.etMessage.requestFocus()
            imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
            announceForAccessibility("Panel de emojis cerrado.")
        }
    }

    private fun announceForAccessibility(text: String) {
        val am = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (am.isEnabled) {
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.text.add(text)
            am.sendAccessibilityEvent(event)
        }
    }

    private fun setupVoiceToText() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                binding.etMessage.hint = "Escuchando..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                binding.etMessage.hint = "Escribe un mensaje..."
            }
            override fun onError(error: Int) {
                binding.etMessage.hint = "Escribe un mensaje..."
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val currentText = binding.etMessage.text.toString()
                    val newText = if (currentText.isEmpty()) matches[0] else "$currentText ${matches[0]}"
                    binding.etMessage.setText(newText)
                    binding.etMessage.setSelection(newText.length)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        binding.btnVoice.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        speechRecognizer?.startListening(recognizerIntent)
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    speechRecognizer?.stopListening()
                    true
                }
                else -> false
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                adapter.submitList(messages) {
                    binding.layoutEmptyChat.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                    if (messages.isNotEmpty()) {
                        binding.rvGlobalChat.scrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isOpen.collectLatest { isOpen ->
                binding.tvChatStatus.visibility = if (isOpen) View.GONE else View.VISIBLE
                binding.inputContainer.visibility = if (isOpen) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }
}
