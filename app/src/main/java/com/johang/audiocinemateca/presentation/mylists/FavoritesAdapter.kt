package com.johang.audiocinemateca.presentation.mylists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.databinding.ItemFavoriteBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FavoritesAdapter(
    private val onItemClick: (FavoriteEntity) -> Unit,
    private val onLongClick: (FavoriteEntity) -> Unit,
    private val onDeleteRequest: (FavoriteEntity) -> Unit,
    private val onPlayRequest: (FavoriteEntity) -> Unit // Nuevo callback para reproducir
) : ListAdapter<FavoriteEntity, FavoritesAdapter.FavoriteViewHolder>(FavoriteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FavoriteViewHolder(private val binding: ItemFavoriteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FavoriteEntity) {
            binding.favoriteTitle.text = item.title
            
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(Date(item.addedAt))
            
            val typeStr = when (item.contentType) {
                "movie" -> "Película"
                "serie" -> "Serie"
                "documentary" -> "Documental"
                "shortfilm" -> "Corto"
                else -> "Contenido"
            }
            
            binding.favoriteType.text = typeStr.uppercase()
            binding.favoriteDate.text = "Agregado: $dateStr"
            
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }

            // Acciones de Accesibilidad
            ViewCompat.addAccessibilityAction(binding.root, "Reproducir ahora") { _, _ ->
                onPlayRequest(item)
                true
            }

            ViewCompat.addAccessibilityAction(binding.root, "Ver información") { _, _ ->
                onItemClick(item)
                true
            }

            ViewCompat.addAccessibilityAction(binding.root, "Eliminar de favoritos") { _, _ ->
                onDeleteRequest(item)
                true
            }
        }
    }

    class FavoriteDiffCallback : DiffUtil.ItemCallback<FavoriteEntity>() {
        override fun areItemsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem.contentId == newItem.contentId
        }

        override fun areContentsTheSame(oldItem: FavoriteEntity, newItem: FavoriteEntity): Boolean {
            return oldItem == newItem
        }
    }
}