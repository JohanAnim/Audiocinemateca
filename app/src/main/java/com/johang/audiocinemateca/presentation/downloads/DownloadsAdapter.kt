package com.johang.audiocinemateca.presentation.downloads

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.DownloadEntity

class DownloadsAdapter(
    private var items: List<GroupedDownload> = emptyList(),
    private val onSeriesGroupClick: (String) -> Unit,
    private val onItemClick: (DownloadEntity) -> Unit,
    private val onMoreClick: (GroupedDownload) -> Unit,
    private val onEpisodeClick: (DownloadEntity) -> Unit,
    private val onEpisodeMoreClick: (DownloadEntity) -> Unit,
    private val onViewDetails: (String, String) -> Unit,
    private val onDelete: (String, Int, Int, String, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SINGLE = 1
        private const val VIEW_TYPE_SERIES = 2
        
        // IDs únicos para las acciones de accesibilidad
        private const val ACTION_PLAY = 1001
        private const val ACTION_DETAILS = 1002
        private const val ACTION_DELETE = 1003
        private const val ACTION_TOGGLE_EXPAND = 1004
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
            SeriesGroupViewHolder(view, onSeriesGroupClick, onItemClick, onMoreClick, onEpisodeClick, onEpisodeMoreClick, onViewDetails, onDelete)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_download_single, parent, false)
            SingleDownloadViewHolder(view, onMoreClick, onItemClick, onViewDetails, onDelete)
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
        notifyDataSetChanged()
    }

    class SingleDownloadViewHolder(
        itemView: View, 
        private val onMoreClick: (GroupedDownload) -> Unit,
        private val onItemClick: (DownloadEntity) -> Unit,
        private val onViewDetails: (String, String) -> Unit,
        private val onDelete: (String, Int, Int, String, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.item_info)
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

            // Usar AccessibilityDelegate para control total y evitar duplicados
            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_PLAY, "Reproducir ahora"))
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DETAILS, "Ver información del título"))
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DELETE, "Eliminar descarga"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        ACTION_PLAY -> { onItemClick(download); true }
                        ACTION_DETAILS -> { onViewDetails(item.contentId, item.contentType); true }
                        ACTION_DELETE -> { onDelete(item.contentId, download.partIndex, download.episodeIndex, item.title, false); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })
        }
    }

    class SeriesGroupViewHolder(
        itemView: View, 
        private val onSeriesGroupClick: (String) -> Unit,
        private val onItemClick: (DownloadEntity) -> Unit,
        private val onMoreClick: (GroupedDownload) -> Unit,
        private val onEpisodeClick: (DownloadEntity) -> Unit,
        private val onEpisodeMoreClick: (DownloadEntity) -> Unit,
        private val onViewDetails: (String, String) -> Unit,
        private val onDelete: (String, Int, Int, String, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        private val infoTextView: TextView = itemView.findViewById(R.id.item_info)
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
            headerContainer.contentDescription = "${item.title}, $infoText, $expansionText"

            headerContainer.setOnClickListener {
                val announceText = if (item.isExpanded) "Contraído" else "Expandido, mostrando $episodeCount episodio$plural"
                it.announceForAccessibility(announceText)
                onSeriesGroupClick(item.contentId)
            }
            moreButton.setOnClickListener { onMoreClick(item) }

            // AccessibilityDelegate para el encabezado de la serie
            ViewCompat.setAccessibilityDelegate(headerContainer, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    // SOLO una opción de expansión según el estado real
                    val toggleLabel = if (item.isExpanded) "Contraer" else "Expandir"
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_TOGGLE_EXPAND, toggleLabel))
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DETAILS, "Ver información de la serie"))
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DELETE, "Eliminar todos los episodios de esta serie"))
                }

                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    return when (action) {
                        ACTION_TOGGLE_EXPAND -> {
                            val announceText = if (item.isExpanded) "Contraído" else "Expandido, mostrando $episodeCount episodio$plural"
                            headerContainer.announceForAccessibility(announceText)
                            onSeriesGroupClick(item.contentId)
                            true
                        }
                        ACTION_DETAILS -> { onViewDetails(item.contentId, item.contentType); true }
                        ACTION_DELETE -> { onDelete(item.contentId, -1, -1, item.title, true); true }
                        else -> super.performAccessibilityAction(host, action, args)
                    }
                }
            })

            if (item.isExpanded) {
                episodesContainer.visibility = View.VISIBLE
                episodesContainer.removeAllViews()
                val inflater = LayoutInflater.from(itemView.context)
                item.episodes.forEach { episode ->
                    val episodeView = inflater.inflate(R.layout.item_download_episode, episodesContainer, false)
                    val episodeTitle: TextView = episodeView.findViewById(R.id.episode_title_text) ?: episodeView.findViewById(R.id.episode_item_title)
                    val episodeMore: ImageButton = episodeView.findViewById(R.id.episode_item_more_button)

                    episodeTitle.text = episode.title
                    episodeView.setOnClickListener { onEpisodeClick(episode) }
                    episodeMore.setOnClickListener { onEpisodeMoreClick(episode) }

                    // AccessibilityDelegate para cada episodio
                    ViewCompat.setAccessibilityDelegate(episodeView, object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            super.onInitializeAccessibilityNodeInfo(host, info)
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_PLAY, "Reproducir episodio"))
                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DELETE, "Eliminar episodio descargado"))
                        }

                        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                            return when (action) {
                                ACTION_PLAY -> { onEpisodeClick(episode); true }
                                ACTION_DELETE -> { onDelete(episode.contentId, episode.partIndex, episode.episodeIndex, episode.title, false); true }
                                else -> super.performAccessibilityAction(host, action, args)
                            }
                        }
                    })
                    
                    episodesContainer.addView(episodeView)
                }
            } else {
                episodesContainer.visibility = View.GONE
                episodesContainer.removeAllViews()
            }
        }
    }
}