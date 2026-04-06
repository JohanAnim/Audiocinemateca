package com.johang.audiocinemateca.presentation.community

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.data.model.ChatMessage
import com.johang.audiocinemateca.databinding.ItemChatGlobalBinding
import java.text.SimpleDateFormat
import java.util.Locale

class GlobalChatAdapter : ListAdapter<ChatMessage, GlobalChatAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatGlobalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemChatGlobalBinding) : RecyclerView.ViewHolder(binding.root) {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: ChatMessage) {
            binding.tvSenderName.text = message.senderName
            binding.tvMessageText.text = message.text
            binding.tvTimestamp.text = timeFormat.format(message.timestamp.toDate())

            // Resaltar si hay menciones (aquí podrías añadir lógica de colores)
            
            // Accesibilidad para el item completo
            binding.chatItemContainer.contentDescription = 
                "${message.senderName} dijo: ${message.text}. Enviado a las ${binding.tvTimestamp.text}"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage) = oldItem == newItem
    }
}
