package com.johang.audiocinemateca.presentation.community

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.johang.audiocinemateca.databinding.FragmentGlobalChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import androidx.appcompat.app.AlertDialog
import android.widget.PopupMenu
import com.johang.audiocinemateca.R
import android.util.Log
import androidx.navigation.fragment.findNavController

@AndroidEntryPoint
class GlobalChatFragment : Fragment() {

    private var _binding: FragmentGlobalChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GlobalChatViewModel by viewModels()
    private val adapter by lazy { 
        val user = FirebaseAuth.getInstance().currentUser
        GlobalChatAdapter(
            currentUserId = user?.uid,
            currentUserName = user?.displayName,
            onSwipeToReply = { startReply(it) },
            onMentionUser = { insertMention(it) },
            onReplyClicked = { scrollToMessage(it) },
            onOptionsClicked = { showMessageOptions(it) },
            onReactionClicked = { msg, emoji -> viewModel.toggleReaction(msg, emoji) },
            onLinkedContentClick = { linked ->
                val action = com.johang.audiocinemateca.MainNavGraphDirections.actionGlobalContentDetailFragment(linked.id, linked.type)
                findNavController().navigate(action)
            }
        ) 
    }
    
    private var speechRecognizer: SpeechRecognizer? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(requireContext(), "Permiso concedido.", Toast.LENGTH_SHORT).show()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { if (binding.emojiPickerContainer.visibility == View.VISIBLE) toggleEmojiPicker(false) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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
        setupChatHeader()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        binding.rvGlobalChat.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        binding.rvGlobalChat.adapter = adapter
    }

    private fun setupChatHeader() {
        binding.btnChatOptions.setOnClickListener { view ->
            val popup = PopupMenu(requireContext(), view)
            popup.menu.add("Comentarios")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "Comentarios") {
                    Toast.makeText(context, "Sección de comentarios", Toast.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }
    }

    private fun setupInput() {
        binding.etMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { binding.btnSend.isEnabled = !s.isNullOrBlank() }
            override fun afterTextChanged(s: Editable?) {}
        })
        binding.btnSend.setOnClickListener {
            if (viewModel.sendMessage(binding.etMessage.text.toString())) {
                binding.etMessage.text.clear()
                if (binding.emojiPickerContainer.visibility == View.VISIBLE) toggleEmojiPicker(false)
            } else Toast.makeText(requireContext(), "Espera un poco entre mensajes.", Toast.LENGTH_SHORT).show()
        }
        binding.btnCancelReply.setOnClickListener { 
            viewModel.setReplyingTo(null)
            viewModel.setEditingMessage(null)
        }
    }

    private fun startReply(message: ChatMessage) {
        viewModel.setReplyingTo(message)
        binding.etMessage.requestFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun startEdit(message: ChatMessage) {
        viewModel.setEditingMessage(message)
        binding.etMessage.setText(message.text)
        binding.etMessage.setSelection(message.text.length)
        binding.etMessage.requestFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun confirmDelete(message: ChatMessage) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar mensaje")
            .setMessage("¿Estás seguro?")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.deleteMessage(message) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun insertMention(name: String) {
        val mention = "@$name "
        val pos = binding.etMessage.selectionStart
        if (pos >= 0) binding.etMessage.text.insert(pos, mention) else binding.etMessage.text.append(mention)
        binding.etMessage.requestFocus()
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun showMessageOptions(message: ChatMessage) {
        val bottomSheet = ChatMessageOptionsBottomSheet(message, FirebaseAuth.getInstance().currentUser?.uid) { action ->
            when (action) {
                is ChatMessageOptionsBottomSheet.Action.React -> viewModel.toggleReaction(message, action.emoji)
                ChatMessageOptionsBottomSheet.Action.Reply -> startReply(message)
                ChatMessageOptionsBottomSheet.Action.Mention -> insertMention(message.senderName)
                ChatMessageOptionsBottomSheet.Action.Edit -> startEdit(message)
                ChatMessageOptionsBottomSheet.Action.Delete -> confirmDelete(message)
                ChatMessageOptionsBottomSheet.Action.Report -> Toast.makeText(context, "Reportado.", Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheet.show(childFragmentManager, "MessageOptions")
    }

    private fun scrollToMessage(messageId: String) {
        val pos = adapter.currentList.indexOfFirst { it is ChatListItem.Message && it.message.id == messageId }
        if (pos != -1) binding.rvGlobalChat.smoothScrollToPosition(pos)
    }

    private fun setupEmojiPicker() {
        val emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "🙈", "🙉", "🙊", "💋", "💌", "💘", "💝", "💖", "💗", "💓", "💞", "💕", "💟", "❣️", "💔", "❤️", "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "💯", "💢", "💥", "💫", "💦", "💨", "🕳️", "💣", "💬", "👁️‍🗨️", "🗨️", "🗯️", "💭", "💤", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💖", "✨", "🎉")
        binding.rvEmojis.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.rvEmojis.adapter = EmojiAdapter(emojis) { emoji ->
            val start = binding.etMessage.selectionStart
            val end = binding.etMessage.selectionEnd
            binding.etMessage.text.replace(start, end, emoji)
        }
        binding.btnEmoji.setOnClickListener { toggleEmojiPicker(binding.emojiPickerContainer.visibility != View.VISIBLE) }
        binding.etMessage.setOnClickListener { if (binding.emojiPickerContainer.visibility == View.VISIBLE) toggleEmojiPicker(false) }
    }

    private fun toggleEmojiPicker(show: Boolean) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            imm.hideSoftInputFromWindow(binding.etMessage.windowToken, 0)
            binding.emojiPickerContainer.visibility = View.VISIBLE
            onBackPressedCallback.isEnabled = true
        } else {
            binding.emojiPickerContainer.visibility = View.GONE
            onBackPressedCallback.isEnabled = false
            binding.etMessage.requestFocus()
            imm.showSoftInput(binding.etMessage, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupVoiceToText() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            binding.btnVoice.visibility = View.GONE
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {
                Log.d("GlobalChat", "Reconocedor listo")
            }
            override fun onBeginningOfSpeech() {
                vibrateManually()
            }
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(e: Int) {
                // Errores que no son críticos para el usuario (timeout, no match al cancelar, etc)
                if (e != SpeechRecognizer.ERROR_NO_MATCH && e != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    viewModel.onVoiceError()
                    Toast.makeText(requireContext(), "Error de voz ($e). Reintenta.", Toast.LENGTH_SHORT).show()
                }
                resetVoiceButtonUI()
            }
            override fun onResults(r: Bundle?) {
                val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val currentText = binding.etMessage.text.toString().trim()
                    val newText = if (currentText.isEmpty()) text else "$currentText $text"
                    binding.etMessage.setText(newText)
                    binding.etMessage.setSelection(newText.length)
                    vibrateManually()
                }
                resetVoiceButtonUI()
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(ev: Int, p: Bundle?) {}
        })

        binding.btnVoice.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startVoiceListening(recognizerIntent)
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    view.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (view.isPressed) {
                        stopVoiceListening()
                        view.isPressed = false
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startVoiceListening(intent: Intent) {
        viewModel.onVoiceStart()
        binding.btnVoice.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start()
        binding.etMessage.hint = "Escuchando..."
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceListening() {
        viewModel.onVoiceEnd()
        resetVoiceButtonUI()
        speechRecognizer?.stopListening()
    }

    private fun resetVoiceButtonUI() {
        binding.btnVoice.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        binding.etMessage.hint = "Escribe un mensaje..."
    }

    private fun vibrateManually() {
        val v = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            v.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(50)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.chatItems.collectLatest { items ->
                adapter.submitList(items) { if (items.isNotEmpty()) binding.rvGlobalChat.scrollToPosition(items.size - 1) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isOpen.collectLatest { isOpen ->
                binding.tvChatStatus.visibility = if (isOpen) View.GONE else View.VISIBLE
                binding.inputContainer.visibility = if (isOpen) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.onlineCount.collectLatest { count ->
                binding.tvOnlineCount.text = "$count personas conectadas"
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.replyingTo.collectLatest { msg ->
                if (msg != null) {
                    binding.layoutReplyPreview.visibility = View.VISIBLE
                    binding.tvReplyPreviewSender.text = msg.senderName
                    binding.tvReplyPreviewText.text = msg.text
                    binding.etMessage.hint = "Respondiendo a ${msg.senderName}..."
                } else if (viewModel.editingMessage.value == null) {
                    binding.layoutReplyPreview.visibility = View.GONE
                    binding.etMessage.hint = "Escribe un mensaje..."
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editingMessage.collectLatest { msg ->
                if (msg != null) {
                    binding.layoutReplyPreview.visibility = View.VISIBLE
                    binding.tvReplyPreviewSender.text = "Editando mensaje"
                    binding.tvReplyPreviewText.text = msg.text
                    binding.etMessage.hint = "Editando..."
                } else if (viewModel.replyingTo.value == null) {
                    binding.layoutReplyPreview.visibility = View.GONE
                    binding.etMessage.hint = "Escribe un mensaje..."
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); speechRecognizer?.destroy(); _binding = null }
}
