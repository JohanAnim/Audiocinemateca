package com.johang.audiocinemateca.presentation.mylists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm

class PlaybackHistoryAdapter(
    private val onClick: (PlaybackProgressEntity, CatalogItem?) -> Unit, // Modificado
    private val onLongClick: (PlaybackProgressEntity, CatalogItem?) -> Boolean // Modificado
) :
    ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(PlaybackHistoryDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryListItem.Header -> VIEW_TYPE_HEADER
            is HistoryListItem.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history, parent, false)
                ItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Tipo de vista desconocido: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_HEADER -> {
                val headerHolder = holder as HeaderViewHolder
                val headerItem = getItem(position) as HistoryListItem.Header
                headerHolder.bind(headerItem.date)
            }
            VIEW_TYPE_ITEM -> {
                val itemHolder = holder as ItemViewHolder
                val historyItem = getItem(position) as HistoryListItem.Item
                itemHolder.bind(historyItem.playbackProgress, historyItem.catalogItem, onClick, onLongClick) // Modificado
            }
        }
    }

    inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.history_item_title)
        private val typePartTextView: TextView = itemView.findViewById(R.id.history_item_type_part)
        private val timeRemainingTextView: TextView = itemView.findViewById(R.id.history_item_time_remaining)

        fun bind(
            playbackProgress: PlaybackProgressEntity,
            catalogItem: CatalogItem?,
            onClick: (PlaybackProgressEntity, CatalogItem?) -> Unit,
            onLongClick: (PlaybackProgressEntity, CatalogItem?) -> Boolean
        ) {
            titleTextView.text = catalogItem?.title ?: "Título desconocido"

            val remainingMs = playbackProgress.totalDurationMs - playbackProgress.currentPositionMs
            val timeRemainingText = if (remainingMs > 1000) { // Considerar completado si queda menos de 1 segundo
                val formattedDuration = com.johang.audiocinemateca.util.TimeFormatUtils.formatDuration(remainingMs)
                "Continuar: $formattedDuration restantes"
            } else {
                "Completado"
            }
            timeRemainingTextView.text = timeRemainingText

            // Determinar el tipo y la parte/episodio
            val typePartText = when (catalogItem) {
                is Movie -> "Película"
                is Documentary -> "Documental"
                is ShortFilm -> "Cortometraje"
                is Serie -> {
                    val season = playbackProgress.partIndex + 1
                    val episode = playbackProgress.episodeIndex + 1
                    "Serie - T${season}:E${episode}"
                }
                else -> "Contenido"
            }
            typePartTextView.text = typePartText

            itemView.setOnClickListener { onClick(playbackProgress, catalogItem) } // Modificado
            itemView.setOnLongClickListener { onLongClick(playbackProgress, catalogItem) } // Modificado
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerTitle: TextView = itemView.findViewById(R.id.header_title)

        fun bind(title: String) {
            headerTitle.text = title
        }
    }

    class PlaybackHistoryDiffCallback : DiffUtil.ItemCallback<HistoryListItem>() {
        override fun areItemsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
            return when {
                oldItem is HistoryListItem.Header && newItem is HistoryListItem.Header -> oldItem.date == newItem.date
                oldItem is HistoryListItem.Item && newItem is HistoryListItem.Item ->
                    oldItem.playbackProgress.contentId == newItem.playbackProgress.contentId &&
                    oldItem.playbackProgress.partIndex == newItem.playbackProgress.partIndex &&
                    oldItem.playbackProgress.episodeIndex == newItem.playbackProgress.episodeIndex
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
            return when {
                oldItem is HistoryListItem.Header && newItem is HistoryListItem.Header -> oldItem.date == newItem.date
                oldItem is HistoryListItem.Item && newItem is HistoryListItem.Item ->
                    oldItem.playbackProgress.currentPositionMs == newItem.playbackProgress.currentPositionMs &&
                    oldItem.playbackProgress.totalDurationMs == newItem.playbackProgress.totalDurationMs &&
                    oldItem.playbackProgress.lastPlayedTimestamp == newItem.playbackProgress.lastPlayedTimestamp &&
                    oldItem.catalogItem?.id == newItem.catalogItem?.id
                else -> false
            }
        }
    }
}