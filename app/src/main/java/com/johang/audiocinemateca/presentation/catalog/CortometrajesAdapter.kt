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
import com.johang.audiocinemateca.data.model.ShortFilm

class CortometrajesAdapter(
    private val onItemClick: (String, String) -> Unit,
    private val onFavoriteToggle: (ShortFilm, Boolean) -> Unit,
    private val onShareClick: (ShortFilm) -> Unit
) : ListAdapter<ShortFilm, CortometrajesAdapter.CortometrajesViewHolder>(ShortFilmDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CortometrajesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cortometrajes, parent, false)
        return CortometrajesViewHolder(view)
    }

    override fun onBindViewHolder(holder: CortometrajesViewHolder, position: Int) {
        holder.bind(getItem(position), favoriteIds.contains(getItem(position).id))
    }

    inner class CortometrajesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.cortometrajes_title)
        private val yearTextView: TextView = itemView.findViewById(R.id.cortometrajes_year)

        fun bind(corto: ShortFilm, isFavorite: Boolean) {
            titleTextView.text = corto.title
            yearTextView.text = corto.anio
            
            val favoriteStatusText = if (isFavorite) "En favoritos" else ""
            itemView.contentDescription = "${corto.title}, ${corto.anio}. $favoriteStatusText"
            
            itemView.setOnClickListener {
                onItemClick(corto.id, "cortometrajes")
            }

            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1001, "Ver información del cortometraje"))
                    
                    val favLabel = if (isFavorite) "Eliminar de favoritos" else "Agregar a favoritos"
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1002, favLabel))
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1003, "Compartir cortometraje"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        1001 -> { onItemClick(corto.id, "cortometrajes"); true }
                        1002 -> { 
                            val nextState = !isFavorite
                            val message = if (nextState) "Se ha agregado ${corto.title} a favoritos" else "Se ha eliminado ${corto.title} de favoritos"
                            itemView.announceForAccessibility(message)
                            onFavoriteToggle(corto, nextState)
                            true 
                        }
                        1003 -> { onShareClick(corto); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })
        }
    }

    class ShortFilmDiffCallback : DiffUtil.ItemCallback<ShortFilm>() {
        override fun areItemsTheSame(oldItem: ShortFilm, newItem: ShortFilm): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ShortFilm, newItem: ShortFilm): Boolean = oldItem == newItem
    }
}
