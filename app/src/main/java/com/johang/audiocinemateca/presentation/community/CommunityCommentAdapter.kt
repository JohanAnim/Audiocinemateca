package com.johang.audiocinemateca.presentation.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Locale

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.johang.audiocinemateca.domain.model.CatalogItem
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.ShortFilm

class CommunityCommentAdapter(
    private val findItemById: suspend (String, Boolean) -> CatalogItem?,
    private val onCommentClick: (Comment) -> Unit
) : RecyclerView.Adapter<CommunityCommentAdapter.ViewHolder>() {

    private var items: List<Comment> = emptyList()

    fun updateItems(newItems: List<Comment>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userNameText: TextView = view.findViewById(R.id.comment_user_name)
        private val commentBodyText: TextView = view.findViewById(R.id.comment_text)
        private val timestampText: TextView = view.findViewById(R.id.comment_date)
        private val likeButton: View = view.findViewById(R.id.comment_like_button)
        private val replyButton: View = view.findViewById(R.id.comment_reply_button)
        private val menuButton: View = view.findViewById(R.id.comment_menu_button)

        fun bind(comment: Comment) {
            likeButton.visibility = View.GONE
            replyButton.visibility = View.GONE
            menuButton.visibility = View.GONE

            // Inicialmente mostramos lo que tenemos
            updateContentText(comment, null)

            // Si faltan metadatos, resolvemos el item completo
            if (comment.contentTitle.isEmpty() && comment.contentId.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val item = withContext(Dispatchers.IO) { 
                        findItemById(comment.contentId, comment.episodeIndex != -1) 
                    }
                    if (item != null) {
                        updateContentText(comment, item)
                    }
                }
            }

            commentBodyText.text = comment.commentText
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            timestampText.text = sdf.format(comment.timestamp.toDate())

            itemView.setOnClickListener { onCommentClick(comment) }
        }

        private fun updateContentText(comment: Comment, resolvedItem: CatalogItem?) {
            val title = resolvedItem?.title ?: comment.contentTitle.ifEmpty { "un contenido" }
            
            // Detectamos el tipo: del objeto resuelto, o del comentario, o por defecto
            val type = when {
                resolvedItem is Serie -> "series"
                resolvedItem is Movie -> "peliculas"
                resolvedItem is Documentary -> "documentales"
                resolvedItem is ShortFilm -> "cortometrajes"
                comment.contentType.isNotEmpty() -> comment.contentType
                else -> ""
            }

            val contentInfo = when {
                comment.episodeIndex != -1 -> {
                    "en el capítulo ${comment.episodeIndex + 1} T${comment.partIndex + 1} de la serie $title"
                }
                type == "series" -> "en la serie $title"
                type == "peliculas" -> "en la película $title"
                type == "documentales" -> "en el documental $title"
                type == "cortometrajes" -> "en el cortometraje $title"
                else -> "en $title"
            }

            userNameText.text = "${comment.userName} $contentInfo"
            itemView.contentDescription = "${comment.userName} comentó $contentInfo: ${comment.commentText}. Toca para ir al contenido."
        }
    }
}