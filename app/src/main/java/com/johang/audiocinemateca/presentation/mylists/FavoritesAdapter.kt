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
            
            // Reparación de fecha: Si el número es demasiado grande o parece un formato YYYYMMDDHHmm, lo corregimos.
            val rawTimestamp = item.addedAt
            val validTimestamp = when {
                rawTimestamp > 200000000000L && rawTimestamp < 210000000000L -> {
                    // Si parece YYYYMMDDHHmm (ej. 202511122204), intentamos usar la actual como parche
                    System.currentTimeMillis()
                }
                rawTimestamp < 1704067200000L -> {
                    // Si es menor a 2024, usamos la actual
                    System.currentTimeMillis()
                }
                else -> rawTimestamp
            }

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            var dateStr = dateFormat.format(Date(validTimestamp))
            
            // Si el año tiene más de 4 dígitos (ej. 14/01/20262204), lo truncamos a 10 caracteres (dd/MM/yyyy)
            if (dateStr.length > 10) {
                dateStr = dateStr.substring(0, 10)
            }
            
            val typeStr = when (item.contentType.lowercase()) {
                "movie", "peliculas" -> "Película"
                "serie", "series" -> "Serie"
                "documentary", "documentales" -> "Documental"
                "shortfilm", "short", "cortometrajes" -> "Corto"
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