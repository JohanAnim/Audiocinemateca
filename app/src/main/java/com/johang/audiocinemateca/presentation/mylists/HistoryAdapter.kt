package com.johang.audiocinemateca.presentation.mylists

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm

class HistoryAdapter(private val onClick: (PlaybackProgressEntity) -> Unit) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var historyList: List<PlaybackProgressEntity> = emptyList()

    fun submitList(newList: List<PlaybackProgressEntity>) {
        historyList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = historyList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = historyList.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.history_item_title)
        private val typePartTextView: TextView = itemView.findViewById(R.id.history_item_type_part)
        private val timeRemainingTextView: TextView = itemView.findViewById(R.id.history_item_time_remaining)

        fun bind(playbackProgress: PlaybackProgressEntity) {
            titleTextView.text = "Cargando título..." // Placeholder temporal

            val remainingMs = playbackProgress.totalDurationMs - playbackProgress.currentPositionMs
            val remainingMinutes = remainingMs / (1000 * 60)
            val remainingSeconds = (remainingMs / 1000) % 60

            val timeRemainingText = if (remainingMs > 0) {
                "Continuar: ${remainingMinutes}m ${remainingSeconds}s restantes"
            } else {
                "Completado"
            }
            timeRemainingTextView.text = timeRemainingText

            // Determinar el tipo y la parte/episodio
            val typePartText = when (playbackProgress.contentType) {
                "peliculas" -> "Película - Parte ${playbackProgress.partIndex + 1}"
                "series" -> {
                    val season = playbackProgress.partIndex + 1
                    val episode = playbackProgress.episodeIndex + 1
                    "Serie - T${season}:E${episode}"
                }
                "cortometrajes" -> "Cortometraje"
                "documentales" -> "Documental"
                else -> "Contenido"
            }
            typePartTextView.text = typePartText

            itemView.setOnClickListener { onClick(playbackProgress) }
        }
    }
}