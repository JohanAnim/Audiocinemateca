package com.johang.audiocinemateca.presentation.community

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.data.model.OnlineUser
import com.johang.audiocinemateca.data.repository.GlobalChatRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.util.RelativeTimeUtils
import com.johang.audiocinemateca.util.SoundEffectsManager
import com.johang.audiocinemateca.util.TtsManager
import com.johang.audiocinemateca.presentation.player.PlayerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatListItem {
    data class Header(val date: String) : ChatListItem()
    data class UnreadHeader(val count: Int) : ChatListItem()
    data class Message(val message: ChatMessage) : ChatListItem()
}

@HiltViewModel
class GlobalChatViewModel @Inject constructor(
    application: Application,
    private val repository: GlobalChatRepository,
    private val jamRepository: com.johang.audiocinemateca.data.repository.JamRepository,
    private val catalogRepository: com.johang.audiocinemateca.data.local.CatalogRepository,
    private val contentRepository: com.johang.audiocinemateca.data.repository.ContentRepository,
    private val searchRepository: SearchRepository,
    private val sharedPreferencesManager: com.johang.audiocinemateca.data.local.SharedPreferencesManager,
    private val soundEffectsManager: SoundEffectsManager,
    private val ttsManager: TtsManager
) : AndroidViewModel(application) {

    private val adminEmail = "gutierrezjohanantonio@gmail.com"

    val currentJam: StateFlow<com.johang.audiocinemateca.data.model.LiveJamSession?> = jamRepository.observeCurrentJam()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _navigateToPlayerEvent = MutableSharedFlow<com.johang.audiocinemateca.domain.model.CatalogItem>()
    val navigateToPlayerEvent = _navigateToPlayerEvent.asSharedFlow()

    private val _isJoinedJam = MutableStateFlow(false)
    val isJoinedJam = _isJoinedJam.asStateFlow()

    private val _showExitJamConfirmation = MutableStateFlow(false)
    val showExitJamConfirmation = _showExitJamConfirmation.asStateFlow()

    private val _showJamSelectorDialog = MutableStateFlow(false)
    val showJamSelectorDialog = _showJamSelectorDialog.asStateFlow()

    private val _catalogForJam = MutableStateFlow<List<com.johang.audiocinemateca.domain.model.CatalogItem>>(emptyList())
    val catalogForJam = _catalogForJam.asStateFlow()

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

    private val _unreadPosition = MutableStateFlow<Int?>(null)
    val unreadPosition = _unreadPosition.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private var lastMessageTimestamp = 0L
    private var currentLastReadTimestamp: Long = sharedPreferencesManager.getLastReadChatTimestamp()
    private var hasHandledUnreadScroll = false

    val isAdmin: Boolean
        get() = auth.currentUser?.email?.equals(adminEmail, ignoreCase = true) == true

    val onlineUsers: StateFlow<List<OnlineUser>> = repository.getOnlineUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineCount: StateFlow<Int> = onlineUsers
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        startPresenceHeartbeat()

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
                
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val processedList = newList.map { msg ->
                        if (msg.linkedItems.isEmpty() && msg.text.contains("audiocinemateca.com", ignoreCase = true)) {
                            val links = parseLinkedContentFromText(msg.text)
                            if (links.isNotEmpty()) msg.copy(linkedItems = links) else msg
                        } else msg
                    }

                    _messages.value = processedList
                    _chatItems.value = groupMessagesByDate(processedList)
                }
            }
            .launchIn(viewModelScope)

        repository.getChatStatus().onEach { _isOpen.value = it }.launchIn(viewModelScope)

        viewModelScope.launch {
            currentJam.collect { jam ->
                val currentUserId = auth.currentUser?.uid
                val isHost = jam != null && jam.hostUserId == currentUserId
                com.johang.audiocinemateca.presentation.community.GlobalChatState.isJamListener = _isJoinedJam.value && !isHost

                if (_isJoinedJam.value) {
                    if (jam == null) {
                        _isJoinedJam.value = false
                        com.johang.audiocinemateca.presentation.community.GlobalChatState.isJamListener = false
                        soundEffectsManager.playSound("aura_voice_end")
                        stopListenerAudio()
                    } else if (!isHost) {
                        syncListenerAudioWithJam(jam)
                    }
                }
            }
        }
    }

    private fun startPresenceHeartbeat() {
        viewModelScope.launch {
            while (true) {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    repository.setUserPresence(
                        userId = currentUser.uid,
                        displayName = currentUser.displayName,
                        email = currentUser.email,
                        isOnline = true
                    )
                }
                delay(30000L) // Heartbeat cada 30 segundos
            }
        }
    }

    private fun groupMessagesByDate(messages: List<ChatMessage>): List<ChatListItem> {
        val result = mutableListOf<ChatListItem>()
        var lastDateLabel = ""
        val currentUserId = auth.currentUser?.uid

        // 1. Determinar el primer mensaje sin leer enviado por otro usuario (solo si no se ha procesado el scroll inicial)
        val firstUnreadIndex = if (!hasHandledUnreadScroll && currentLastReadTimestamp > 0L) {
            messages.indexOfFirst { msg ->
                msg.senderId != currentUserId && (msg.timestamp.seconds * 1000) > currentLastReadTimestamp
            }
        } else -1

        val unreadCount = if (firstUnreadIndex != -1) {
            messages.drop(firstUnreadIndex).count { it.senderId != currentUserId }
        } else 0

        var insertedUnreadHeader = false
        var unreadHeaderListIndex = -1

        for (i in messages.indices) {
            val message = messages[i]

            // Insertar separador de no leídos antes del primer mensaje no leído
            if (i == firstUnreadIndex && unreadCount > 0 && !insertedUnreadHeader) {
                unreadHeaderListIndex = result.size
                result.add(ChatListItem.UnreadHeader(unreadCount))
                insertedUnreadHeader = true
            }

            val dateLabel = RelativeTimeUtils.getRelativeDateLabel(message.timestamp.seconds * 1000)
            if (dateLabel != lastDateLabel) {
                result.add(ChatListItem.Header(dateLabel))
                lastDateLabel = dateLabel
            }
            result.add(ChatListItem.Message(message))
        }

        if (unreadHeaderListIndex != -1 && !hasHandledUnreadScroll) {
            _unreadPosition.value = unreadHeaderListIndex
        } else {
            _unreadPosition.value = null
        }

        return result
    }

    fun markChatAsRead() {
        hasHandledUnreadScroll = true
        _unreadPosition.value = null
        val now = System.currentTimeMillis()
        currentLastReadTimestamp = now
        sharedPreferencesManager.saveLastReadChatTimestamp(now)
        val currentItems = _chatItems.value
        if (currentItems.any { it is ChatListItem.UnreadHeader }) {
            _chatItems.value = currentItems.filterNot { it is ChatListItem.UnreadHeader }
        }
    }

    fun clearUnreadScrollPosition() {
        hasHandledUnreadScroll = true
        _unreadPosition.value = null
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
                repository.deleteMessage(message.id, isByAdmin = isAdmin)
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) { soundEffectsManager.playSound("aura_error") }
        }
    }

    private val _banStatusMessage = MutableStateFlow<String?>(null)
    val banStatusMessage = _banStatusMessage.asStateFlow()

    val bannedUsers: StateFlow<List<com.johang.audiocinemateca.data.model.BannedUser>> = repository.getBannedUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasSeenChatRules: Boolean
        get() = sharedPreferencesManager.getBoolean("has_seen_chat_rules", false)

    fun markRulesAsSeen() {
        sharedPreferencesManager.saveBoolean("has_seen_chat_rules", true)
    }

    fun clearBanStatusMessage() {
        _banStatusMessage.value = null
    }

    fun banUser(bannedUser: com.johang.audiocinemateca.data.model.BannedUser) {
        viewModelScope.launch {
            try {
                repository.banUser(bannedUser.copy(bannedBy = auth.currentUser?.email ?: "Admin"))
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) {
                soundEffectsManager.playSound("aura_error")
            }
        }
    }

    fun unbanUser(userId: String) {
        viewModelScope.launch {
            try {
                repository.unbanUser(userId)
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) {
                soundEffectsManager.playSound("aura_error")
            }
        }
    }

    fun sendMessage(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (_editingMessage.value == null && now - lastMessageTimestamp < 2000) return false
        if (text.isBlank()) return false
        val currentUser = auth.currentUser ?: return false

        val containsTodos = extractMentions(text).any { it.equals("todos", ignoreCase = true) } || text.contains("@todos", ignoreCase = true)
        if (containsTodos && !isAdmin) {
            soundEffectsManager.playSound("aura_error")
            _banStatusMessage.value = "La mención masiva @todos está reservada únicamente para la administración."
            return false
        }

        markChatAsRead()

        viewModelScope.launch {
            try {
                val (isBanned, reason) = repository.isUserBanned(currentUser.uid)
                if (isBanned) {
                    soundEffectsManager.playSound("aura_error")
                    _banStatusMessage.value = "Tu cuenta tiene una sanción activa en el Chat Global. Motivo: $reason"
                    return@launch
                }

                val editing = _editingMessage.value
                if (editing != null) {
                    repository.updateMessage(editing.id, text.trim())
                    _editingMessage.value = null
                } else {
                    val currentName = (currentUser.displayName ?: "").lowercase()
                    val currentEmailPrefix = (currentUser.email ?: "").substringBefore("@").lowercase()
                    val rawMentions = extractMentions(text).filter { m ->
                        val cleanM = m.replace("@", "").trim().lowercase()
                        cleanM != currentName && cleanM != currentEmailPrefix && cleanM != currentUser.uid.lowercase()
                    }
                    val reply = _replyingTo.value
                    val finalMentions = if (reply != null && reply.senderId != currentUser.uid && !rawMentions.contains(reply.senderName)) {
                        rawMentions + reply.senderName
                    } else {
                        rawMentions
                    }
                    
                    val autoLinked = parseLinkedContentFromText(text.trim())
                    repository.sendMessage(ChatMessage(
                        senderId = currentUser.uid,
                        senderName = currentUser.displayName ?: "Usuario",
                        senderEmail = currentUser.email ?: "",
                        text = text.trim(),
                        mentions = finalMentions,
                        replyToMessageId = reply?.id,
                        replyToSenderName = reply?.senderName,
                        replyToSenderId = reply?.senderId,
                        replyToText = reply?.text,
                        linkedItems = autoLinked
                    ))
                    _replyingTo.value = null
                }
                soundEffectsManager.playSound("aura_send")
            } catch (e: Exception) { soundEffectsManager.playSound("aura_error") }
        }
        if (_editingMessage.value == null) lastMessageTimestamp = now
        return true
    }

    private suspend fun parseLinkedContentFromText(text: String): List<com.johang.audiocinemateca.presentation.aichat.LinkedContent> {
        val results = mutableListOf<com.johang.audiocinemateca.presentation.aichat.LinkedContent>()
        if (text.isBlank()) return results

        // 1. Extraer URLs de audiocinemateca.com (robusto con o sin protocolo, ignorando puntuación final)
        val urlRegex = Regex("""(?:https?://)?(?:www\.)?audiocinemateca\.com/[^\s<>\)"']\S*""", RegexOption.IGNORE_CASE)
        val urlMatches = urlRegex.findAll(text)

        for (match in urlMatches) {
            try {
                // Limpiar puntuación al final de la URL (ej: parenthesis, puntos, comillas)
                var rawUrl = match.value.trim().trimEnd('.', ',', ')', ']', '"', '\'', '>', '!', '?', ';', ':', '}')
                if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    rawUrl = "https://$rawUrl"
                }

                val uri = android.net.Uri.parse(rawUrl)
                val pathSegments = uri.pathSegments
                val firstSeg = pathSegments.getOrNull(0)?.lowercase() ?: ""
                val secondSeg = pathSegments.getOrNull(1)

                val categoryHint = when {
                    firstSeg.contains("peli") -> "peliculas"
                    firstSeg.contains("serie") -> "series"
                    firstSeg.contains("docu") -> "documentales"
                    firstSeg.contains("corto") -> "cortometrajes"
                    else -> null
                }

                val rawIdParam = uri.getQueryParameter("id")
                    ?: uri.getQueryParameter("id_contenido")
                    ?: secondSeg
                    ?: if (categoryHint == null && firstSeg.isNotBlank() && firstSeg != "com" && firstSeg != "www") firstSeg else null

                val cleanId = rawIdParam?.trim()?.takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }

                if (!cleanId.isNullOrBlank()) {
                    val item = if (categoryHint != null) {
                        searchRepository.getCatalogItemByIdAndType(cleanId, categoryHint)
                            ?: searchRepository.findCatalogItemById(cleanId)
                    } else {
                        searchRepository.findCatalogItemById(cleanId)
                    }

                    if (item != null) {
                        val realType = when (item) {
                            is com.johang.audiocinemateca.data.model.Movie -> "peliculas"
                            is com.johang.audiocinemateca.data.model.Serie -> "series"
                            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
                            else -> "cortometrajes"
                        }
                        if (results.none { it.id == item.id }) {
                            results.add(com.johang.audiocinemateca.presentation.aichat.LinkedContent(id = item.id, title = item.title, type = realType))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Soporte adicional para corchetes [[Título de Obra]]
        val bracketRegex = Regex("""\[\[(.*?)]]""")
        val bracketMatches = bracketRegex.findAll(text)
        for (bMatch in bracketMatches) {
            val titleQuery = bMatch.groupValues[1].trim()
            if (titleQuery.isNotBlank()) {
                try {
                    val foundItems = searchRepository.searchCatalog(titleQuery)
                    val exactItem = foundItems.find { it.title.equals(titleQuery, ignoreCase = true) }
                        ?: foundItems.find { it.title.contains(titleQuery, ignoreCase = true) }
                        ?: foundItems.firstOrNull()

                    if (exactItem != null && results.none { it.id == exactItem.id }) {
                        val realType = when (exactItem) {
                            is com.johang.audiocinemateca.data.model.Movie -> "peliculas"
                            is com.johang.audiocinemateca.data.model.Serie -> "series"
                            is com.johang.audiocinemateca.data.model.Documentary -> "documentales"
                            else -> "cortometrajes"
                        }
                        results.add(com.johang.audiocinemateca.presentation.aichat.LinkedContent(id = exactItem.id, title = exactItem.title, type = realType))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return results
    }

    private fun extractMentions(text: String): List<String> {
        val regex = Regex("@([a-zA-Z0-9_.-]+)")
        return regex.findAll(text).map { it.groupValues[1] }.toList()
    }

    private val _showPinPromptDialog = MutableStateFlow<com.johang.audiocinemateca.data.model.LiveJamSession?>(null)
    val showPinPromptDialog = _showPinPromptDialog.asStateFlow()

    private val _showRecommendationsDialog = MutableStateFlow<List<com.johang.audiocinemateca.presentation.aichat.LinkedContent>?>(null)
    val showRecommendationsDialog = _showRecommendationsDialog.asStateFlow()

    fun openRecommendationsDialog(items: List<com.johang.audiocinemateca.presentation.aichat.LinkedContent>) {
        _showRecommendationsDialog.value = items
    }

    fun dismissRecommendationsDialog() {
        _showRecommendationsDialog.value = null
    }

    fun openJamSelector() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val catalog = catalogRepository.getCatalog()
            val allItems = mutableListOf<com.johang.audiocinemateca.domain.model.CatalogItem>().apply {
                catalog?.movies?.let { addAll(it) }
                catalog?.series?.let { addAll(it) }
                catalog?.documentaries?.let { addAll(it) }
                catalog?.shortFilms?.let { addAll(it) }
            }
            _catalogForJam.value = allItems
            _showJamSelectorDialog.value = true
        }
    }

    fun dismissJamSelector() {
        _showJamSelectorDialog.value = false
    }

    fun startJamSession(item: com.johang.audiocinemateca.domain.model.CatalogItem, pinCode: String = "") {
        val currentUser = auth.currentUser ?: return
        val hostName = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "Admin"
        val itemType = when (item) {
            is com.johang.audiocinemateca.data.model.Serie -> "serie"
            is com.johang.audiocinemateca.data.model.Movie -> "pelicula"
            is com.johang.audiocinemateca.data.model.Documentary -> "documental"
            is com.johang.audiocinemateca.data.model.ShortFilm -> "cortometraje"
            else -> "pelicula"
        }

        viewModelScope.launch {
            _showJamSelectorDialog.value = false
            jamRepository.startJam(
                hostUserId = currentUser.uid,
                hostName = hostName,
                contentId = item.id,
                title = item.title,
                contentType = itemType,
                pinCode = pinCode
            )
            _isJoinedJam.value = true
            soundEffectsManager.playSound("aura_send")
            _navigateToPlayerEvent.emit(item)
        }
    }

    fun onAttemptJoinJam(jam: com.johang.audiocinemateca.data.model.LiveJamSession) {
        if (jam.pinCode.isNotBlank() && !isAdmin) {
            _showPinPromptDialog.value = jam
        } else {
            joinJamSession(jam)
        }
    }

    fun verifyPinAndJoin(enteredPin: String) {
        val jam = _showPinPromptDialog.value ?: return
        if (enteredPin.trim() == jam.pinCode.trim()) {
            _showPinPromptDialog.value = null
            joinJamSession(jam)
        } else {
            soundEffectsManager.playSound("aura_error")
            _banStatusMessage.value = "La contraseña o PIN ingresado es incorrecto."
        }
    }

    fun dismissPinPrompt() {
        _showPinPromptDialog.value = null
    }

    fun joinJamSession(jam: com.johang.audiocinemateca.data.model.LiveJamSession) {
        viewModelScope.launch {
            jamRepository.joinJam()
            _isJoinedJam.value = true
            soundEffectsManager.playSound("aura_send")
            syncListenerAudioWithJam(jam)
        }
    }

    fun onOpenJamPlayer() {
        val jam = currentJam.value ?: return
        val currentUserId = auth.currentUser?.uid
        // Solo el anfitrión puede abrir el reproductor a pantalla completa
        if (jam.hostUserId == currentUserId || isAdmin) {
            viewModelScope.launch {
                val item = contentRepository.getContentItem(jam.contentId, jam.contentType)
                if (item != null) {
                    _navigateToPlayerEvent.emit(item)
                }
            }
        }
    }

    fun leaveJamSession() {
        viewModelScope.launch {
            if (_isJoinedJam.value) {
                jamRepository.leaveJam()
                _isJoinedJam.value = false
                com.johang.audiocinemateca.presentation.community.GlobalChatState.isJamListener = false
                soundEffectsManager.playSound("aura_voice_end")
                stopListenerAudio()
            }
        }
    }

    fun endJamSession() {
        viewModelScope.launch {
            jamRepository.endJam()
            _isJoinedJam.value = false
            com.johang.audiocinemateca.presentation.community.GlobalChatState.isJamListener = false
            soundEffectsManager.playSound("aura_send")
            stopListenerAudio()
        }
    }

    fun onAttemptExitCommunity(onProceedExit: () -> Unit) {
        if (_isJoinedJam.value) {
            _showExitJamConfirmation.value = true
        } else {
            onProceedExit()
        }
    }

    fun cancelExitJam() {
        _showExitJamConfirmation.value = false
    }

    fun confirmExitJamAndStop(onProceedExit: () -> Unit) {
        leaveJamSession()
        _showExitJamConfirmation.value = false
        onProceedExit()
    }

    private fun syncListenerAudioWithJam(jam: com.johang.audiocinemateca.data.model.LiveJamSession) {
        val now = System.currentTimeMillis()
        val elapsed = (now - jam.lastUpdatedTimestamp).coerceAtLeast(0L)
        val expectedPos = if (jam.isPlaying) jam.positionMs + elapsed else jam.positionMs
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_SYNC_JAM_STATE
            putExtra("extra_content_id", jam.contentId)
            putExtra("extra_content_type", jam.contentType)
            putExtra(PlayerService.EXTRA_JAM_POSITION, expectedPos)
            putExtra(PlayerService.EXTRA_JAM_IS_PLAYING, jam.isPlaying)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e("GlobalChatViewModel", "Error starting PlayerService for Jam sync", e)
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun stopListenerAudio() {
        val context = getApplication<Application>()
        val intent = android.content.Intent(context, PlayerService::class.java).apply {
            action = PlayerService.ACTION_STOP
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    override fun onCleared() {
        super.onCleared()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            repository.setUserPresence(uid, isOnline = false)
            val jam = currentJam.value
            if (jam != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        if (jam.hostUserId == uid) {
                            jamRepository.endJam()
                        } else if (_isJoinedJam.value) {
                            jamRepository.leaveJam()
                        }
                    } catch (e: Exception) {}
                }
            }
        }
    }
}
