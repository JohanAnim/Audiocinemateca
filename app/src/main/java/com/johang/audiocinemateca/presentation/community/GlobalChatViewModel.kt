package com.johang.audiocinemateca.presentation.community

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GlobalChatViewModel @Inject constructor(
    application: Application,
    private val repository: GlobalChatRepository
) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isOpen = MutableStateFlow(true)
    val isOpen = _isOpen.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private var lastMessageTimestamp = 0L

    init {
        // En el ViewModel solo nos encargamos de actualizar la lista y el estado
        // Los anuncios de voz ahora son globales y se manejan en MainActivity
        repository.getMessages()
            .onEach { _messages.value = it }
            .launchIn(viewModelScope)

        repository.getChatStatus()
            .onEach { _isOpen.value = it }
            .launchIn(viewModelScope)
    }

    fun sendMessage(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastMessageTimestamp < 2000) return false // Cooldown 2s
        if (text.isBlank()) return false

        val currentUser = auth.currentUser ?: return false
        val mentions = extractMentions(text)

        viewModelScope.launch {
            repository.sendMessage(
                ChatMessage(
                    senderId = currentUser.uid,
                    senderName = currentUser.displayName ?: "Usuario",
                    text = text.trim(),
                    mentions = mentions
                )
            )
        }
        lastMessageTimestamp = now
        return true
    }

    private fun extractMentions(text: String): List<String> {
        val regex = Regex("@([a-zA-Z0-9]+)")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }
}
