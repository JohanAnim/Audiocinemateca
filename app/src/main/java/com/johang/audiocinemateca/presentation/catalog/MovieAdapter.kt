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
import com.johang.audiocinemateca.data.model.Movie

class MovieAdapter(
    private val onItemClick: (String, String) -> Unit,
    private val onFavoriteToggle: (Movie, Boolean) -> Unit,
    private val onShareClick: (Movie) -> Unit
) : ListAdapter<Movie, MovieAdapter.MovieViewHolder>(MovieDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(getItem(position), favoriteIds.contains(getItem(position).id))
    }

    inner class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.movie_title)
        private val yearTextView: TextView = itemView.findViewById(R.id.movie_year)

        fun bind(movie: Movie, isFavorite: Boolean) {
            titleTextView.text = movie.title
            yearTextView.text = movie.anio
            
            val favoriteStatusText = if (isFavorite) "En favoritos" else ""
            itemView.contentDescription = "${movie.title}, ${movie.anio}. $favoriteStatusText"
            
            itemView.setOnClickListener {
                onItemClick(movie.id, "peliculas")
            }

            // AccessibilityDelegate para acciones personalizadas
            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    // Acción 1: Ver detalles (Navegación principal)
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1001, "Ver información de la película"))
                    
                    // Acción 2: Favoritos (Dinámica)
                    val favLabel = if (isFavorite) "Eliminar de favoritos" else "Agregar a favoritos"
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1002, favLabel))
                    
                    // Acción 3: Compartir
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(1003, "Compartir película"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        1001 -> { onItemClick(movie.id, "peliculas"); true }
                        1002 -> { 
                            val nextState = !isFavorite
                            val message = if (nextState) "Se ha agregado ${movie.title} a favoritos" else "Se ha eliminado ${movie.title} de favoritos"
                            itemView.announceForAccessibility(message)
                            onFavoriteToggle(movie, nextState)
                            true 
                        }
                        1003 -> { onShareClick(movie); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })
        }
    }

    class MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean = oldItem == newItem
    }
}
