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
import com.johang.audiocinemateca.data.model.Serie

class SeriesAdapter(
    private val onItemClick: (String, String) -> Unit,
    private val onFavoriteToggle: (Serie, Boolean) -> Unit,
    private val onShareClick: (Serie) -> Unit
) : ListAdapter<Serie, SeriesAdapter.SeriesViewHolder>(SerieDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_series, parent, false)
        return SeriesViewHolder(view)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(getItem(position), favoriteIds.contains(getItem(position).id))
    }

    inner class SeriesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.series_title)
        val yearTextView: TextView = itemView.findViewById(R.id.series_year)

        fun bind(serie: Serie, isFavorite: Boolean) {
            titleTextView.text = serie.title
            yearTextView.text = serie.anio
            
            val favoriteStatusText = if (isFavorite) "En favoritos" else ""
            itemView.contentDescription = "${serie.title}, ${serie.anio}. $favoriteStatusText"
            
            itemView.setOnClickListener {
                onItemClick(serie.id, "series")
            }

            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1001, "Ver información de la serie"))
                    
                    val favLabel = if (isFavorite) "Eliminar de favoritos" else "Agregar a favoritos"
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1002, favLabel))
                    
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1003, "Compartir serie"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        1001 -> { onItemClick(serie.id, "series"); true }
                        1002 -> { 
                            val nextState = !isFavorite
                            val message = if (nextState) "Se ha agregado ${serie.title} a favoritos" else "Se ha eliminado ${serie.title} de favoritos"
                            itemView.announceForAccessibility(message)
                            onFavoriteToggle(serie, nextState)
                            true 
                        }
                        1003 -> { onShareClick(serie); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })
        }
    }

    class SerieDiffCallback : DiffUtil.ItemCallback<Serie>() {
        override fun areItemsTheSame(oldItem: Serie, newItem: Serie): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Serie, newItem: Serie): Boolean = oldItem == newItem
    }
}
