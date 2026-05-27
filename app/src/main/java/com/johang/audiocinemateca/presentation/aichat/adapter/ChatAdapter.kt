package com.johang.audiocinemateca.presentation.aichat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.aichat.ChatMessage
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.johang.audiocinemateca.presentation.aichat.LinkedContent
import io.noties.markwon.Markwon

class ChatAdapter(
    private val markwon: Markwon,
    private val onMessageClick: (List<LinkedContent>) -> Unit,
    private val onRetryClick: () -> Unit,
    private val onLinkedContentClick: (LinkedContent) -> Unit = {}
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    override fun getItemViewType(position: Int): Int = if (getItem(position).isUser) 1 else 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 1) {
            UserMessageViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AiMessageViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false), markwon, onMessageClick, onRetryClick, onLinkedContentClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        if (holder is UserMessageViewHolder) holder.bind(message)
        else if (holder is AiMessageViewHolder) holder.bind(message)
    }

    class UserMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textBody: TextView = itemView.findViewById(R.id.text_message_body)
        fun bind(message: ChatMessage) {
            textBody.text = message.text
            itemView.contentDescription = "Tú: ${message.text}"
        }
    }

    class AiMessageViewHolder(
        itemView: View, 
        private val markwon: Markwon,
        private val onMessageClick: (List<LinkedContent>) -> Unit,
        private val onRetryClick: () -> Unit,
        private val onLinkedContentClick: (LinkedContent) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val textBody: TextView = itemView.findViewById(R.id.text_message_body)
        private val cardContainer: MaterialCardView = itemView.findViewById(R.id.card_message_container)
        private val buttonRetry: Button = itemView.findViewById(R.id.button_retry)
        private val buttonRecs: View = itemView.findViewById(R.id.button_view_recommendations)

        fun bind(message: ChatMessage) {
            markwon.setMarkdown(textBody, message.text)
            
            val hasLinks = message.linkedItems.isNotEmpty()
            
            // RESET TOTAL PARA EVITAR ERRORES DE RECICLAJE
            itemView.setOnClickListener(null)
            cardContainer.setOnClickListener(null)
            buttonRetry.setOnClickListener(null)
            buttonRecs.setOnClickListener(null)
            
            // ACCESIBILIDAD
            itemView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            itemView.isClickable = false
            itemView.isFocusable = false

            if (message.isError) {
                buttonRetry.visibility = View.VISIBLE
                buttonRecs.visibility = View.GONE
                buttonRetry.setOnClickListener { onRetryClick() }
                cardContainer.strokeWidth = 2
                cardContainer.strokeColor = itemView.context.getColor(android.R.color.holo_red_dark)
                cardContainer.isClickable = false
            } else {
                buttonRetry.visibility = View.GONE
                buttonRecs.visibility = if (hasLinks) View.VISIBLE else View.GONE
                cardContainer.strokeWidth = if (hasLinks) 2 else 0 // Más sutil
                if (hasLinks) cardContainer.strokeColor = itemView.context.getColor(androidx.appcompat.R.color.material_deep_teal_500)
            }
            
            val baseDesc = "Aura dice: ${message.text}"
            
            if (hasLinks && !message.isError) {
                val listener = View.OnClickListener { 
                    onMessageClick(message.linkedItems)
                }
                
                cardContainer.setOnClickListener(listener)
                buttonRecs.setOnClickListener(listener)
                
                cardContainer.isClickable = true
                cardContainer.isFocusable = true
                cardContainer.contentDescription = baseDesc
                
                buttonRecs.contentDescription = "Ver todas las recomendaciones"
                ViewCompat.setScreenReaderFocusable(cardContainer, true)

                // IMPLEMENTAR ACCIONES DE ACCESIBILIDAD
                ViewCompat.setAccessibilityDelegate(cardContainer, object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        
                        // Acción principal (opcional, ya es el click)
                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            AccessibilityNodeInfoCompat.ACTION_CLICK, "Abrir lista de recomendaciones"
                        ))

                        // Acciones personalizadas para cada título
                        message.linkedItems.forEachIndexed { index, linked ->
                            val actionLabel = "Ver detalles de [[${linked.title}]]"
                            val actionId = 1000 + index // IDs únicos para las acciones
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(actionId, actionLabel))
                        }
                    }

                    override fun performAccessibilityAction(host: View, action: Int, args: android.os.Bundle?): Boolean {
                        if (action >= 1000 && action < 1000 + message.linkedItems.size) {
                            val linked = message.linkedItems[action - 1000]
                            onLinkedContentClick(linked)
                            return true
                        }
                        return super.performAccessibilityAction(host, action, args)
                    }
                })
            } else {
                cardContainer.isClickable = false
                cardContainer.isFocusable = true
                cardContainer.contentDescription = baseDesc
                buttonRecs.visibility = View.GONE
                ViewCompat.setAccessibilityDelegate(cardContainer, null)
            }
            
            textBody.isClickable = false
            textBody.isFocusable = false
            textBody.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean = oldItem == newItem
    }
}
