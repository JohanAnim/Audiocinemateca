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
import android.widget.LinearLayout
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
    private val onLinkedContentClick: (LinkedContent) -> Unit = {},
    private val onShowAllRecommendationsClick: (List<LinkedContent>) -> Unit = {}
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_MESSAGE = 1
        private const val VIEW_TYPE_UNREAD_HEADER = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ChatListItem.Header -> VIEW_TYPE_HEADER
            is ChatListItem.UnreadHeader -> VIEW_TYPE_UNREAD_HEADER
            is ChatListItem.Message -> VIEW_TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_UNREAD_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
                UnreadHeaderViewHolder(view)
            }
            VIEW_TYPE_MESSAGE -> {
                val binding = ItemChatGlobalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MessageViewHolder(binding, currentUserId, currentUserName, onSwipeToReply, onMentionUser, onReplyClicked, onOptionsClicked, onReactionClicked, onLinkedContentClick, onShowAllRecommendationsClick)
            }
            else -> throw IllegalArgumentException("Tipo de vista desconocido")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.Header -> (holder as HeaderViewHolder).bind(item.date)
            is ChatListItem.UnreadHeader -> (holder as UnreadHeaderViewHolder).bind(item.count)
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

    class UnreadHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.header_title)
        fun bind(count: Int) {
            val text = if (count == 1) "1 mensaje sin leer" else "$count mensajes sin leer"
            tvTitle.text = "--- $text ---"
            tvTitle.setTextColor(itemView.context.getColor(com.google.android.material.R.color.design_default_color_primary))
            itemView.contentDescription = text
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
        private val onLinkedContentClick: (LinkedContent) -> Unit,
        private val onShowAllRecommendationsClick: (List<LinkedContent>) -> Unit
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
            
            if (message.isDeleted) {
                binding.tvMessageText.text = "Un admin eliminó este mensaje"
                binding.tvMessageText.setTypeface(null, android.graphics.Typeface.ITALIC)
                binding.tvMessageText.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                binding.layoutReply.visibility = View.GONE
                binding.cgReactions.visibility = View.GONE
                binding.layoutLinkedContent.visibility = View.GONE
            } else {
                binding.tvMessageText.text = message.text
                binding.tvMessageText.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.tvMessageText.setTextColor(android.graphics.Color.WHITE)

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

            // ACCIONES Y FICHA VISUAL DE RECOMENDACIONES Y ENLACES (audiocinemateca.com)
            binding.layoutLinkedContent.removeAllViews()
            if (message.linkedItems.isNotEmpty()) {
                binding.layoutLinkedContent.visibility = View.VISIBLE
                
                val itemsToShow = if (message.linkedItems.size > 2) message.linkedItems.take(2) else message.linkedItems
                
                itemsToShow.forEach { linked ->
                    val card = com.google.android.material.card.MaterialCardView(binding.root.context).apply {
                        setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
                        radius = 12f.dpToPx(context)
                        cardElevation = 2f.dpToPx(context)
                        strokeWidth = 1.dpToPx(context).toInt()
                        setStrokeColor(android.graphics.Color.parseColor("#334155"))
                        
                        val innerLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(24, 16, 24, 16)
                            gravity = android.view.Gravity.CENTER_VERTICAL

                            val iconTv = TextView(context).apply {
                                text = when (linked.type.lowercase()) {
                                    "series" -> "📺"
                                    "documentales" -> "📽️"
                                    "cortometrajes" -> "🎞️"
                                    else -> "🎬"
                                }
                                textSize = 16f
                            }
                            addView(iconTv)

                            val textLayout = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(16, 0, 0, 0)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                                val titleTv = TextView(context).apply {
                                    text = linked.title
                                    setTextColor(android.graphics.Color.WHITE)
                                    textSize = 13f
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                    maxLines = 1
                                    ellipsize = android.text.TextUtils.TruncateAt.END
                                }
                                val subTv = TextView(context).apply {
                                    text = "Abrir ficha de contenido"
                                    setTextColor(android.graphics.Color.parseColor("#38BDF8"))
                                    textSize = 11f
                                }
                                addView(titleTv)
                                addView(subTv)
                            }
                            addView(textLayout)
                        }
                        addView(innerLayout)

                        contentDescription = "Ficha de contenido enlazado: ${linked.title}. Toca para abrir."
                        setOnClickListener { onLinkedContentClick(linked) }
                    }

                    val marginParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }

                    binding.layoutLinkedContent.addView(card, marginParams)
                }

                if (message.linkedItems.size > 2) {
                    val seeAllCard = com.google.android.material.card.MaterialCardView(binding.root.context).apply {
                        setCardBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                        radius = 12f.dpToPx(context)
                        cardElevation = 2f.dpToPx(context)
                        strokeWidth = 1.dpToPx(context).toInt()
                        setStrokeColor(android.graphics.Color.parseColor("#38BDF8"))

                        val innerLayout = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(24, 16, 24, 16)
                            gravity = android.view.Gravity.CENTER_VERTICAL

                            val iconTv = TextView(context).apply {
                                text = "📚"
                                textSize = 16f
                            }
                            addView(iconTv)

                            val textLayout = LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(16, 0, 0, 0)
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                                val titleTv = TextView(context).apply {
                                    text = "Ver todas las recomendaciones (${message.linkedItems.size})"
                                    setTextColor(android.graphics.Color.WHITE)
                                    textSize = 13f
                                    setTypeface(null, android.graphics.Typeface.BOLD)
                                }
                                val subTv = TextView(context).apply {
                                    text = "Toca para abrir la lista completa"
                                    setTextColor(android.graphics.Color.parseColor("#38BDF8"))
                                    textSize = 11f
                                }
                                addView(titleTv)
                                addView(subTv)
                            }
                            addView(textLayout)
                        }
                        addView(innerLayout)

                        contentDescription = "Ver las ${message.linkedItems.size} recomendaciones en este mensaje. Toca para abrir."
                        setOnClickListener { onShowAllRecommendationsClick(message.linkedItems) }
                    }

                    val marginParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 4) }

                    binding.layoutLinkedContent.addView(seeAllCard, marginParams)
                }

                ViewCompat.setAccessibilityDelegate(binding.root, object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        
                        if (message.linkedItems.size > 2) {
                            val actionLabel = "Ver todas las ${message.linkedItems.size} recomendaciones"
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1999, actionLabel))
                        }

                        message.linkedItems.forEachIndexed { index, linked ->
                            val actionLabel = "Ver detalles de [[${linked.title}]]"
                            val actionId = 2000 + index
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(actionId, actionLabel))
                        }
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                        if (action == 1999) {
                            onShowAllRecommendationsClick(message.linkedItems)
                            return true
                        }
                        if (action >= 2000 && action < 2000 + message.linkedItems.size) {
                            val linked = message.linkedItems[action - 2000]
                            onLinkedContentClick(linked)
                            return true
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                })
            } else {
                binding.layoutLinkedContent.visibility = View.GONE
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
                oldItem is ChatListItem.UnreadHeader && newItem is ChatListItem.UnreadHeader -> oldItem.count == newItem.count
                oldItem is ChatListItem.Message && newItem is ChatListItem.Message -> oldItem.message.id == newItem.message.id
                else -> false
            }
        }
        override fun areContentsTheSame(oldItem: ChatListItem, newItem: ChatListItem): Boolean = oldItem == newItem
    }
}

private fun Float.dpToPx(context: Context): Float = this * context.resources.displayMetrics.density
private fun Int.dpToPx(context: Context): Float = this.toFloat() * context.resources.displayMetrics.density
