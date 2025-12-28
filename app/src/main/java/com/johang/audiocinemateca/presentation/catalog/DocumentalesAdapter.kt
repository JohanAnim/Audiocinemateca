package com.johang.audiocinemateca.presentation.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Documentary

class DocumentalesAdapter(
    private val onItemClick: (String, String) -> Unit,
    private val onFavoriteToggle: (Documentary, Boolean) -> Unit,
    private val onShareClick: (Documentary) -> Unit
) : ListAdapter<Documentary, DocumentalesAdapter.DocumentalesViewHolder>(DocumentaryDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentalesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_documentales, parent, false)
        return DocumentalesViewHolder(view)
    }

    override fun onBindViewHolder(holder: DocumentalesViewHolder, position: Int) {
        holder.bind(getItem(position), favoriteIds.contains(getItem(position).id))
    }

    inner class DocumentalesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.documentales_title)
        private val yearTextView: TextView = itemView.findViewById(R.id.documentales_year)

        fun bind(documental: Documentary, isFavorite: Boolean) {
            titleTextView.text = documental.title
            yearTextView.text = documental.anio
            
            val favoriteStatusText = if (isFavorite) "En favoritos" else ""
            itemView.contentDescription = "${documental.title}, ${documental.anio}. $favoriteStatusText"
            
            itemView.setOnClickListener {
                onItemClick(documental.id, "documentales")
            }

            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1001, "Ver información del documental"))
                    
                    val favLabel = if (isFavorite) "Eliminar de favoritos" else "Agregar a favoritos"
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1002, favLabel))
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1003, "Compartir documental"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        1001 -> { onItemClick(documental.id, "documentales"); true }
                        1002 -> { 
                            val nextState = !isFavorite
                            val message = if (nextState) "Se ha agregado ${documental.title} a favoritos" else "Se ha eliminado ${documental.title} de favoritos"
                            itemView.announceForAccessibility(message)
                            onFavoriteToggle(documental, nextState)
                            true 
                        }
                        1003 -> { onShareClick(documental); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })
        }
    }

    class DocumentaryDiffCallback : DiffUtil.ItemCallback<Documentary>() {
        override fun areItemsTheSame(oldItem: Documentary, newItem: Documentary): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Documentary, newItem: Documentary): Boolean = oldItem == newItem
    }
}
