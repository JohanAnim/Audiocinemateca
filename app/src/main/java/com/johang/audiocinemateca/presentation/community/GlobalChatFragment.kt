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
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.databinding.FragmentGlobalChatBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import androidx.appcompat.app.AlertDialog
import android.widget.PopupMenu
import android.util.Log
import androidx.navigation.fragment.findNavController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johang.audiocinemateca.presentation.community.components.ConnectedUsersDialog
import com.johang.audiocinemateca.presentation.community.components.GlobalChatHeader

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
            },
            onShowAllRecommendationsClick = { items ->
                viewModel.openRecommendationsDialog(items)
            }
        ) 
    }
    
    private var speechRecognizer: SpeechRecognizer? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(requireContext(), "Permiso concedido.", Toast.LENGTH_SHORT).show()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (binding.emojiPickerContainer.visibility == View.VISIBLE) {
                toggleEmojiPicker(false)
                return
            }
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGlobalChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        GlobalChatState.isChatScreenActive = true
    }

    override fun onPause() {
        super.onPause()
        GlobalChatState.isChatScreenActive = false
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
        binding.composeChatHeader.setContent {
            com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme {
                val onlineCount by viewModel.onlineCount.collectAsStateWithLifecycle()
                val onlineUsers by viewModel.onlineUsers.collectAsStateWithLifecycle()
                val showRecommendationsDialog by viewModel.showRecommendationsDialog.collectAsStateWithLifecycle()

                var showConnectedUsersDialog by remember { mutableStateOf(false) }
                var showRulesDialog by remember { mutableStateOf(!viewModel.hasSeenChatRules) }
                var showBanUserDialogFor by remember { mutableStateOf<com.johang.audiocinemateca.data.model.OnlineUser?>(null) }
                var showBannedUsersDialog by remember { mutableStateOf(false) }

                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlobalChatHeader(
                        onlineCount = onlineCount,
                        isAdmin = viewModel.isAdmin,
                        onBackClick = {
                            findNavController().navigateUp()
                        },
                        onHeaderClick = {
                            showConnectedUsersDialog = true
                        },
                        onOptionsClick = {
                            val popup = PopupMenu(requireContext(), binding.composeChatHeader)
                            popup.menu.add("Reglas de la Comunidad")
                            if (viewModel.isAdmin) {
                                popup.menu.add("Usuarios Sancionados")
                            }
                            popup.setOnMenuItemClickListener { item ->
                                when (item.title) {
                                    "Reglas de la Comunidad" -> showRulesDialog = true
                                    "Usuarios Sancionados" -> showBannedUsersDialog = true
                                }
                                true
                            }
                            popup.show()
                        }
                    )
                }
                
                showRecommendationsDialog?.let { items ->
                    com.johang.audiocinemateca.presentation.community.components.AllRecommendationsDialog(
                        items = items,
                        onDismissRequest = { viewModel.dismissRecommendationsDialog() },
                        onItemSelected = { linked ->
                            viewModel.dismissRecommendationsDialog()
                            val action = com.johang.audiocinemateca.MainNavGraphDirections.actionGlobalContentDetailFragment(linked.id, linked.type)
                            findNavController().navigate(action)
                        }
                    )
                }

                if (showRulesDialog) {
                    com.johang.audiocinemateca.presentation.community.components.ChatRulesDialog(
                        onDismiss = {
                            viewModel.markRulesAsSeen()
                            showRulesDialog = false
                        }
                    )
                }

                if (showConnectedUsersDialog) {
                    com.johang.audiocinemateca.presentation.community.components.ConnectedUsersDialog(
                        onlineUsers = onlineUsers,
                        isAdmin = viewModel.isAdmin,
                        currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid,
                        onReplyToUser = { name ->
                            insertMention(name)
                            showConnectedUsersDialog = false
                        },
                        onMentionUser = { name ->
                            insertMention(name)
                            showConnectedUsersDialog = false
                        },
                        onBanUser = { user ->
                            showBanUserDialogFor = user
                            showConnectedUsersDialog = false
                        },
                        onDismiss = { showConnectedUsersDialog = false }
                    )
                }

                showBanUserDialogFor?.let { user ->
                    com.johang.audiocinemateca.presentation.community.components.BanUserDialog(
                        targetUserId = user.userId,
                        targetDisplayName = user.displayName,
                        targetEmail = user.email,
                        onConfirmBan = { bannedUser ->
                            viewModel.banUser(bannedUser)
                            showBanUserDialogFor = null
                            Toast.makeText(requireContext(), "Usuario ${bannedUser.displayName} suspendido.", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showBanUserDialogFor = null }
                    )
                }

                if (showBannedUsersDialog && viewModel.isAdmin) {
                    val bannedList by viewModel.bannedUsers.collectAsStateWithLifecycle()
                    com.johang.audiocinemateca.presentation.community.components.BannedUsersDialog(
                        bannedUsers = bannedList,
                        onUnbanUser = { uid ->
                            viewModel.unbanUser(uid)
                            Toast.makeText(requireContext(), "Sanción eliminada.", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showBannedUsersDialog = false }
                    )
                }
            }
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
                binding.rvGlobalChat.postDelayed({
                    if (_binding != null && adapter.itemCount > 0) {
                        binding.rvGlobalChat.scrollToPosition(adapter.itemCount - 1)
                    }
                }, 100)
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
        val bottomSheet = ChatMessageOptionsBottomSheet(message, FirebaseAuth.getInstance().currentUser?.uid, viewModel.isAdmin) { action ->
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
        val isAvailable = SpeechRecognizer.isRecognitionAvailable(requireContext())
        if (!isAvailable) {
            binding.btnVoice.alpha = 0.5f
            binding.btnVoice.setOnClickListener {
                Toast.makeText(requireContext(), "El dictado por voz no está disponible en este dispositivo. Asegúrate de tener instalada la aplicación de Google.", Toast.LENGTH_LONG).show()
            }
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
                val b = _binding ?: return
                val matches = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    val currentText = b.etMessage.text.toString().trim()
                    val newText = if (currentText.isEmpty()) text else "$currentText $text"
                    b.etMessage.setText(newText)
                    b.etMessage.setSelection(newText.length)
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
        val b = _binding ?: return
        viewModel.onVoiceStart()
        b.btnVoice.animate().scaleX(1.3f).scaleY(1.3f).setDuration(150).start()
        b.etMessage.hint = "Escuchando..."
        speechRecognizer?.startListening(intent)
    }

    private fun stopVoiceListening() {
        viewModel.onVoiceEnd()
        resetVoiceButtonUI()
        speechRecognizer?.stopListening()
    }

    private fun resetVoiceButtonUI() {
        val b = _binding ?: return
        b.btnVoice.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
        b.etMessage.hint = "Escribe un mensaje..."
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
                val targetScrollPos = viewModel.unreadPosition.value
                adapter.submitList(items) { 
                    val b = _binding ?: return@submitList
                    if (items.isNotEmpty()) {
                        if (targetScrollPos != null && targetScrollPos in items.indices) {
                            b.rvGlobalChat.scrollToPosition(targetScrollPos)
                            viewModel.clearUnreadScrollPosition()
                        } else {
                            val lm = b.rvGlobalChat.layoutManager as? LinearLayoutManager
                            val lastVisiblePos = lm?.findLastVisibleItemPosition() ?: -1
                            val isAtBottom = lastVisiblePos != -1 && lastVisiblePos >= (items.size - 2)
                            if (isAtBottom) {
                                b.rvGlobalChat.scrollToPosition(items.size - 1)
                            }
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isOpen.collectLatest { isOpen ->
                val b = _binding ?: return@collectLatest
                b.tvChatStatus.visibility = if (isOpen) View.GONE else View.VISIBLE
                b.inputContainer.visibility = if (isOpen) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.replyingTo.collectLatest { msg ->
                val b = _binding ?: return@collectLatest
                if (msg != null) {
                    b.layoutReplyPreview.visibility = View.VISIBLE
                    b.tvReplyPreviewSender.text = msg.senderName
                    b.tvReplyPreviewText.text = msg.text
                    b.etMessage.hint = "Respondiendo a ${msg.senderName}..."
                } else if (viewModel.editingMessage.value == null) {
                    b.layoutReplyPreview.visibility = View.GONE
                    b.etMessage.hint = "Escribe un mensaje..."
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.editingMessage.collectLatest { msg ->
                val b = _binding ?: return@collectLatest
                if (msg != null) {
                    b.layoutReplyPreview.visibility = View.VISIBLE
                    b.tvReplyPreviewSender.text = "Editando mensaje"
                    b.tvReplyPreviewText.text = msg.text
                    b.etMessage.hint = "Editando..."
                } else if (viewModel.replyingTo.value == null) {
                    b.layoutReplyPreview.visibility = View.GONE
                    b.etMessage.hint = "Escribe un mensaje..."
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.banStatusMessage.collectLatest { msg ->
                if (!msg.isNullOrBlank()) {
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                    viewModel.clearBanStatusMessage()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        GlobalChatState.isChatScreenActive = false
        viewModel.markChatAsRead()
    }

    override fun onDestroyView() { 
        viewModel.markChatAsRead()
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null 
    }
}
