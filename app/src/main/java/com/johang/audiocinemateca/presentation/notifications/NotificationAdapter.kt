package com.johang.audiocinemateca.presentation.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.data.local.entities.NotificationEntity
import com.johang.audiocinemateca.databinding.ItemNotificationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onItemLongClick: (com.johang.audiocinemateca.data.local.entities.NotificationEntity) -> Unit,
    private val onNavigate: (String?, String?) -> Unit
) : ListAdapter<NotificationEntity, NotificationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemLongClick, onNavigate)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemNotificationBinding,
        private val onItemLongClick: (NotificationEntity) -> Unit,
        private val onNavigate: (String?, String?) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        fun bind(notification: NotificationEntity) {
            binding.tvNotificationTitle.text = notification.title
            binding.tvNotificationBody.text = notification.body
            
            val dateStr = dateFormat.format(Date(notification.timestamp))
            binding.tvNotificationDate.text = "Recibido: $dateStr"

            // Unificamos la lógica en un solo botón inteligente (btnNotificationNav)
            // Ocultamos el otro (btnNotificationAction) para simplificar la UI
            binding.btnNotificationAction.visibility = android.view.View.GONE

            if (!notification.linkUrl.isNullOrBlank() || !notification.destination.isNullOrBlank()) {
                binding.btnNotificationNav.visibility = android.view.View.VISIBLE
                
                // Texto inteligente según el contenido
                val isDonation = notification.destination == "donation" || 
                                 notification.linkUrl?.contains("donar", ignoreCase = true) == true || 
                                 notification.linkUrl?.contains("paypal", ignoreCase = true) == true
                
                binding.btnNotificationNav.text = when {
                    isDonation -> "Donar ahora"
                    notification.destination == "announcements" -> "Ver Anuncios"
                    notification.destination == "content_detail" -> "Ver contenido"
                    notification.destination == "global_chat" -> "Ir al Chat"
                    notification.destination == "comments" -> "Ver comentarios"
                    notification.linkUrl?.contains("audiocinemateca.com") == true -> "Ver contenido"
                    else -> "Abrir enlace"
                }

                binding.btnNotificationNav.setOnClickListener {
                    onNavigate(notification.destination, notification.linkUrl)
                }
            } else {
                binding.btnNotificationNav.visibility = android.view.View.GONE
            }

            // Pulsación larga para eliminar
            binding.notificationContainer.setOnLongClickListener {
                onItemLongClick(notification)
                true
            }

            // Accesibilidad
            binding.notificationContainer.contentDescription = 
                "${notification.title}. ${notification.body}. Recibido el $dateStr. Mantén pulsado para eliminar."
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<NotificationEntity>() {
        override fun areItemsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: NotificationEntity, newItem: NotificationEntity) = oldItem == newItem
    }
}
