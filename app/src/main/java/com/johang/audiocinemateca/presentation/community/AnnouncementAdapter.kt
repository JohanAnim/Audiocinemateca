package com.johang.audiocinemateca.presentation.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Announcement
import java.text.SimpleDateFormat
import java.util.Locale

class AnnouncementAdapter(
    private val onItemClick: (Announcement) -> Unit,
    private val onReactClick: (Announcement) -> Unit
) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

    private var items: List<Announcement> = emptyList()

    fun updateItems(newItems: List<Announcement>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_announcement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val infoText: TextView = view.findViewById(R.id.announcement_admin_info)
        private val bodyText: TextView = view.findViewById(R.id.announcement_text)
        private val dateText: TextView = view.findViewById(R.id.announcement_date)
        private val reactButton: MaterialButton = view.findViewById(R.id.btn_react_announcement)
        private val card: View = view.findViewById(R.id.announcement_card)

        fun bind(item: Announcement) {
            infoText.text = "${item.adminName} Administrador anunció:"
            bodyText.text = item.text
            
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateText.text = sdf.format(item.timestamp.toDate())

            // Resumen de reacciones para el botón
            val reactionSummary = if (item.reactions.isEmpty()) {
                "Reaccionar"
            } else {
                val counts = item.reactions.values.groupingBy { it }.eachCount()
                counts.entries.joinToString(" ") { "${it.key} ${it.value}" }
            }
            
            reactButton.text = reactionSummary
            reactButton.setOnClickListener { onReactClick(item) }
            card.setOnClickListener { onItemClick(item) }
            
            // Accesibilidad: El botón ahora anuncia las reacciones
            card.contentDescription = "${item.adminName} anunció: ${item.text}. Publicado el ${dateText.text}. Toca para ver detalles."
            reactButton.contentDescription = if (item.reactions.isEmpty()) "Reaccionar al anuncio" else "Reacciones: $reactionSummary. Toca para cambiar o añadir la tuya."
        }
    }
}