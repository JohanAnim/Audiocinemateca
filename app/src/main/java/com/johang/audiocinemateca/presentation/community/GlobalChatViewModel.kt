package com.johang.audiocinemateca.presentation.community

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.util.RelativeTimeUtils
import com.johang.audiocinemateca.util.SoundEffectsManager
import com.johang.audiocinemateca.util.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatListItem {
    data class Header(val date: String) : ChatListItem()
    data class Message(val message: ChatMessage) : ChatListItem()
}

@HiltViewModel
class GlobalChatViewModel @Inject constructor(
    application: Application,
    private val repository: GlobalChatRepository,
    private val searchRepository: SearchRepository,
    private val sharedPreferencesManager: com.johang.audiocinemateca.data.local.SharedPreferencesManager,
    private val soundEffectsManager: SoundEffectsManager,
    private val ttsManager: TtsManager
) : AndroidViewModel(application) {

    private val adminEmail = "gutierrezjohanantonio@gmail.com"

    private val _chatItems = MutableStateFlow<List<ChatListItem>>(emptyList())
    val chatItems = _chatItems.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isOpen = MutableStateFlow(true)
    val isOpen = _isOpen.asStateFlow()

    private val _replyingTo = MutableStateFlow<ChatMessage?>(null)
    val replyingTo = _replyingTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage = _editingMessage.asStateFlow()

    private val _onlineCount = MutableStateFlow(0) 
    val onlineCount = _onlineCount.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private var lastMessageTimestamp = 0L

    init {
        repository.getMessages()
            .onEach { newList -> 
                val currentUserId = auth.currentUser?.uid
                val oldList = _messages.value
                
                if (newList.isNotEmpty()) {
                    val lastMsg = newList.last()
                    val isNew = oldList.none { it.id == lastMsg.id }
                    
                    if (isNew) {
                        if (lastMsg.senderId != currentUserId) {
                            soundEffectsManager.playSound("aura_receive")
                        }
                    }
                }
                
                _messages.value = newList
                _chatItems.value = groupMessagesByDate(newList)
            }
            .launchIn(viewModelScope)

        repository.getOnlineCount()
            .onEach { _onlineCount.value = it }
            .launchIn(viewModelScope)

        repository.getChatStatus().onEach { _isOpen.value = it }.launchIn(viewModelScope)
    }

    private fun groupMessagesByDate(messages: List<ChatMessage>): List<ChatListItem> {
        val result = mutableListOf<ChatListItem>()
        var lastDateLabel = ""
        for (message in messages) {
            val dateLabel = RelativeTimeUtils.getRelativeDateLabel(message.timestamp.seconds * 1000)
            if (dateLabel != lastDateLabel) {
                result.add(ChatListItem.Header(dateLabel))
                lastDateLabel = dateLabel
            }
            result.add(ChatListItem.Message(message))
        }
        return result
    }

    fun onVoiceStart() { ttsManager.stop(); soundEffectsManager.playSound("aura_voice_start") }
    fun onVoiceEnd() { soundEffectsManager.playSound("aura_voice_end") }
    fun onVoiceError() { soundEffectsManager.playSound("aura_error") }

    fun setReplyingTo(message: ChatMessage?) { 
        _replyingTo.value = message 
        if (message != null) _editingMessage.value = null
    }

    fun setEditingMessage(message: ChatMessage?) {
        _editingMessage.value = message
        if (message != null) _replyingTo.value = null
    }

    fun toggleReaction(message: ChatMessage, emoji: String) {
        val userId = auth.currentUser?.uid ?: return
        val currentReactions = message.reactions.toMutableMap()
        val userList = currentReactions[emoji]?.toMutableList() ?: mutableListOf()
        if (userList.contains(userId)) userList.remove(userId) else userList.add(userId)
        if (userList.isEmpty()) currentReactions.remove(emoji) else currentReactions[emoji] = userList
        viewModelScope.launch {
            try {
                repository.updateMessageReactions(message.id, currentReactions)
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) { soundEffectsManager.playSound("aura_error") }
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(message.id)
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) { soundEffectsManager.playSound("aura_error") }
        }
    }

    fun sendMessage(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (_editingMessage.value == null && now - lastMessageTimestamp < 2000) return false
        if (text.isBlank()) return false
        val currentUser = auth.currentUser ?: return false

        viewModelScope.launch {
            try {
                val editing = _editingMessage.value
                if (editing != null) {
                    repository.updateMessage(editing.id, text.trim())
                    _editingMessage.value = null
                } else {
                    val mentions = extractMentions(text)
                    val reply = _replyingTo.value
                    val finalMentions = if (reply != null && !mentions.contains(reply.senderName)) mentions + reply.senderName else mentions
                    
                    repository.sendMessage(ChatMessage(
                        senderId = currentUser.uid,
                        senderName = currentUser.displayName ?: "Usuario",
                        text = text.trim(),
                        mentions = finalMentions,
                        replyToMessageId = reply?.id,
                        replyToSenderName = reply?.senderName,
                        replyToSenderId = reply?.senderId,
                        replyToText = reply?.text
                    ))
                    _replyingTo.value = null
                }
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) { soundEffectsManager.playSound("aura_error") }
        }
        if (_editingMessage.value == null) lastMessageTimestamp = now
        return true
    }

    private fun extractMentions(text: String): List<String> {
        val regex = Regex("@([a-zA-Z0-9]+)")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }
}
