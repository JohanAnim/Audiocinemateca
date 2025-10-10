package com.johang.audiocinemateca.presentation.downloads

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.DownloadEntity

class DownloadsAdapter(
    private var items: List<GroupedDownload> = emptyList(),
    private val onSeriesGroupClick: (String) -> Unit,
    private val onItemClick: (DownloadEntity) -> Unit,
    private val onMoreClick: (GroupedDownload) -> Unit,
    private val onEpisodeClick: (DownloadEntity) -> Unit,
    private val onEpisodeMoreClick: (DownloadEntity) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SINGLE = 1
        private const val VIEW_TYPE_SERIES = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].contentType == "serie") {
            VIEW_TYPE_SERIES
        } else {
            VIEW_TYPE_SINGLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SERIES) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_series_group, parent, false)
            SeriesGroupViewHolder(view, onSeriesGroupClick, onItemClick, onMoreClick, onEpisodeClick, onEpisodeMoreClick)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_single, parent, false)
            SingleDownloadViewHolder(view, onMoreClick, onItemClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is SeriesGroupViewHolder) {
            holder.bind(item)
        } else if (holder is SingleDownloadViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<GroupedDownload>) {
        items = newItems
        notifyDataSetChanged() // This is inefficient, will be improved later with DiffUtil
    }

    // ViewHolder for Movies, Documentaries, etc.
    class SingleDownloadViewHolder(
        itemView: View, 
        private val onMoreClick: (GroupedDownload) -> Unit,
        private val onItemClick: (DownloadEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.item_info)
        private val iconImageView: ImageView = itemView.findViewById(R.id.item_icon)
        private val moreButton: ImageButton = itemView.findViewById(R.id.item_more_button)

        fun bind(item: GroupedDownload) {
            val download = item.episodes.first()
            titleTextView.text = item.title
            val contentTypeSpanish = when (item.contentType) {
                "movie" -> "Película"
                "shortfilm" -> "Cortometraje"
                "documentary" -> "Documental"
                else -> item.contentType.replaceFirstChar { it.uppercase() }
            }

            infoTextView.text = when (download.downloadStatus) {
                "QUEUED" -> "En cola"
                "DOWNLOADING" -> "Descargando..."
                "COMPLETE" -> "$contentTypeSpanish | ${String.format("%.2f", item.totalSizeMb)} MB"
                "FAILED" -> "Error en la descarga"
                else -> ""
            }

            moreButton.setOnClickListener { onMoreClick(item) }
            itemView.setOnClickListener { onItemClick(download) }
            // TODO: Set icon based on content type
        }
    }

    // ViewHolder for a group of Series episodes
    class SeriesGroupViewHolder(
        itemView: View, 
        private val onSeriesGroupClick: (String) -> Unit,
        private val onItemClick: (DownloadEntity) -> Unit, // For the main series item click
        private val onMoreClick: (GroupedDownload) -> Unit, // For the series group more button
        private val onEpisodeClick: (DownloadEntity) -> Unit,
        private val onEpisodeMoreClick: (DownloadEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.item_info)
        private val expansionStateTextView: TextView = itemView.findViewById(R.id.item_expansion_state)
        private val iconImageView: ImageView = itemView.findViewById(R.id.item_icon)
        private val moreButton: ImageButton = itemView.findViewById(R.id.item_more_button)
        private val episodesContainer: ViewGroup = itemView.findViewById(R.id.episodes_container)
        private val headerContainer: View = itemView.findViewById(R.id.series_header_container)

        fun bind(item: GroupedDownload) {
            titleTextView.text = item.title
            val episodeCount = item.episodes.size
            val plural = if (episodeCount > 1) "s" else ""
            val infoText = "Serie | $episodeCount episodio$plural"
            infoTextView.text = infoText
            
            val expansionText = if (item.isExpanded) "Expandido" else "Contraído"
            expansionStateTextView.text = expansionText
            
            headerContainer.contentDescription = "${item.title}, $infoText, $expansionText"

            headerContainer.setOnClickListener {
                // Announce the state change for accessibility
                val newStateText = if (item.isExpanded) {
                    "Contraído"
                } else {
                    val episodeCount = item.episodes.size
                    val plural = if (episodeCount > 1) "s" else ""
                    "Expandido, mostrando $episodeCount episodio$plural"
                }
                it.announceForAccessibility(newStateText)
                // Trigger the actual state change
                onSeriesGroupClick(item.contentId)
            }
            moreButton.setOnClickListener { onMoreClick(item) }

            if (item.isExpanded) {
                episodesContainer.visibility = View.VISIBLE
                episodesContainer.removeAllViews()
                val inflater = LayoutInflater.from(itemView.context)
                item.episodes.forEach { episode ->
                    val episodeView = inflater.inflate(R.layout.item_download_episode, episodesContainer, false)
                    val episodeTitle: TextView = episodeView.findViewById(R.id.episode_item_title)
                    val episodeSize: TextView = episodeView.findViewById(R.id.episode_item_size)
                    val episodeMore: ImageButton = episodeView.findViewById(R.id.episode_item_more_button)

                    episodeTitle.text = episode.title
                    episodeSize.text = when (episode.downloadStatus) {
                        "QUEUED" -> "En cola"
                        "DOWNLOADING" -> "Descargando..."
                        "COMPLETE" -> "${String.format("%.2f", episode.totalSizeMb)} MB"
                        "FAILED" -> "Error en la descarga"
                        else -> ""
                    }
                    episodeView.setOnClickListener { onEpisodeClick(episode) }
                    episodeMore.setOnClickListener { onEpisodeMoreClick(episode) }
                    
                    episodesContainer.addView(episodeView)
                }
            } else {
                episodesContainer.visibility = View.GONE
                episodesContainer.removeAllViews()
            }
        }
    }
}
