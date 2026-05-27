package com.johang.audiocinemateca.presentation.community

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.databinding.ItemChatGlobalBinding
import com.johang.audiocinemateca.databinding.ItemChatReactionBinding
import com.johang.audiocinemateca.presentation.aichat.LinkedContent
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

class GlobalChatAdapter(
    private val currentUserId: String?,
    private val currentUserName: String?,
    private val onSwipeToReply: (ChatMessage) -> Unit,
    private val onMentionUser: (String) -> Unit,
    private val onReplyClicked: (String) -> Unit,
    private val onOptionsClicked: (ChatMessage) -> Unit,
    private val onReactionClicked: (ChatMessage, String) -> Unit,
    private val onLinkedContentClick: (LinkedContent) -> Unit = {}
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MESSAGE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatListItem.Header -> VIEW_TYPE_HEADER
            is ChatListItem.Message -> VIEW_TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_MESSAGE -> {
                val binding = ItemChatGlobalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MessageViewHolder(binding, currentUserId, currentUserName, onSwipeToReply, onMentionUser, onReplyClicked, onOptionsClicked, onReactionClicked, onLinkedContentClick)
            }
            else -> throw IllegalArgumentException("Tipo de vista desconocido")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.Header -> (holder as HeaderViewHolder).bind(item.date)
            is ChatListItem.Message -> (holder as MessageViewHolder).bind(item.message)
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.header_title)
        fun bind(date: String) {
            tvTitle.text = date
            itemView.contentDescription = date
        }
    }

    class MessageViewHolder(
        private val binding: ItemChatGlobalBinding,
        private val currentUserId: String?,
        private val currentUserName: String?,
        private val onSwipeToReply: (ChatMessage) -> Unit,
        private val onMentionUser: (String) -> Unit,
        private val onReplyClicked: (String) -> Unit,
        private val onOptionsClicked: (ChatMessage) -> Unit,
        private val onReactionClicked: (ChatMessage, String) -> Unit,
        private val onLinkedContentClick: (LinkedContent) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val fullDateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.getDefault())

        fun bind(message: ChatMessage) {
            val date = message.timestamp.toDate()
            val timeStr = timeFormat.format(date)
            val fullDateStr = fullDateFormat.format(date)
            
            val isMe = currentUserId != null && message.senderId == currentUserId
            val status = if (isMe) "Enviado" else "Recibido"
            
            binding.tvSenderName.text = message.senderName
            binding.tvMessageText.text = message.text
            
            // Marca de Editado
            val timeDisplay = if (message.edited) "Editado $timeStr" else timeStr
            binding.tvTimestamp.text = timeDisplay
            
            if (message.replyToMessageId != null) {
                binding.layoutReply.visibility = View.VISIBLE
                binding.tvReplySender.text = message.replyToSenderName
                binding.tvReplyText.text = message.replyToText
                binding.layoutReply.setOnClickListener { onReplyClicked(message.replyToMessageId) }
            } else {
                binding.layoutReply.visibility = View.GONE
            }

            binding.cgReactions.removeAllViews()
            if (message.reactions.isNotEmpty()) {
                message.reactions.forEach { (emoji, userIds) ->
                    val reactionBinding = ItemChatReactionBinding.inflate(LayoutInflater.from(binding.root.context), binding.cgReactions, false)
                    reactionBinding.tvReactionEmoji.text = emoji
                    reactionBinding.tvReactionCount.text = userIds.size.toString()
                    val hasMe = currentUserId != null && userIds.contains(currentUserId)
                    reactionBinding.root.alpha = if (hasMe) 1.0f else 0.7f
                    reactionBinding.root.setOnClickListener { onReactionClicked(message, emoji) }
                    binding.cgReactions.addView(reactionBinding.root)
                }
                binding.cgReactions.visibility = View.VISIBLE
            } else {
                binding.cgReactions.visibility = View.GONE
            }

            val isMentioned = currentUserName != null && message.mentions.contains(currentUserName)
            val isReplyToMe = currentUserId != null && message.replyToSenderId == currentUserId
            
            var prefix = when {
                isReplyToMe -> "${message.senderName} te respondió: "
                isMentioned -> "${message.senderName} te mencionó: "
                message.replyToSenderName != null -> "${message.senderName} respondió a ${message.replyToSenderName}: "
                isMe -> "Tú: "
                else -> "${message.senderName}: "
            }

            val reactionsInfo = if (message.reactions.isNotEmpty()) {
                val total = message.reactions.values.sumOf { it.size }
                ". Con $total reacciones."
            } else ""

            val editedInfo = if (message.edited) ". Mensaje editado." else ""

            binding.root.contentDescription = "$prefix ${message.text}$reactionsInfo$editedInfo. $status el $fullDateStr a las $timeStr"
            binding.tvTimestamp.contentDescription = "$status el $fullDateStr a las $timeStr"

            binding.root.setOnLongClickListener { vibrate(it.context); onOptionsClicked(message); true }

            ViewCompat.setAccessibilityDelegate(binding.root, null)
            ViewCompat.addAccessibilityAction(binding.root, "Más opciones") { _, _ -> onOptionsClicked(message); true }
            ViewCompat.addAccessibilityAction(binding.root, "Responder") { _, _ -> onSwipeToReply(message); true }
            ViewCompat.addAccessibilityAction(binding.root, "Mencionar") { _, _ -> onMentionUser(message.senderName); true }

            // ACCIONES DE RECOMENDACIONES (Si Aura recomienda algo)
            if (message.linkedItems.isNotEmpty()) {
                ViewCompat.setAccessibilityDelegate(binding.root, object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        
                        message.linkedItems.forEachIndexed { index, linked ->
                            val actionLabel = "Ver detalles de [[${linked.title}]]"
                            val actionId = 2000 + index
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(actionId, actionLabel))
                        }
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                        if (action >= 2000 && action < 2000 + message.linkedItems.size) {
                            val linked = message.linkedItems[action - 2000]
                            onLinkedContentClick(linked)
                            return true
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                })
            } else {
                ViewCompat.setAccessibilityDelegate(binding.root, null)
            }
        }

        private fun vibrate(context: Context) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") vibrator.vibrate(50)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatListItem>() {
        override fun areItemsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean {
            return when {
                oldItem is ChatListItem.Header && newItem is ChatListItem.Header -> oldItem.date == newItem.date
                oldItem is ChatListItem.Message && newItem is ChatListItem.Message -> oldItem.message.id == newItem.message.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean = oldItem == newItem
    }
}
