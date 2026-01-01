package com.johang.audiocinemateca.presentation.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Comment
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter(
    private val currentUserId: String?,
    private val onLikeClick: (Comment) -> Unit,
    private val onReplyClick: (String) -> Unit,
    private val onMenuClick: (View, Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    private var comments: List<Comment> = emptyList()
    private val dateFormat = SimpleDateFormat("d 'de' MMMM, HH:mm", Locale.getDefault())

    fun submitList(newList: List<Comment>) {
        comments = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(comments[position])
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val userName: TextView = view.findViewById(R.id.comment_user_name)
        private val commentText: TextView = view.findViewById(R.id.comment_text)
        private val dateText: TextView = view.findViewById(R.id.comment_date)
        private val likeButton: MaterialButton = view.findViewById(R.id.comment_like_button)
        private val replyButton: MaterialButton = view.findViewById(R.id.comment_reply_button)
        private val menuButton: MaterialButton = view.findViewById(R.id.comment_menu_button)

        fun bind(comment: Comment) {
            userName.text = "${comment.userName} Dice:"
            commentText.text = comment.commentText
            dateText.text = dateFormat.format(comment.timestamp.toDate())
            
            val likesCount = comment.likes.size
            val isLikedByMe = currentUserId != null && comment.likes.containsKey(currentUserId)

            // Texto dinámico: Me gusta (X) o Quitar me gusta (X)
            likeButton.text = if (isLikedByMe) {
                "Quitar me gusta ($likesCount)"
            } else {
                "Me gusta ($likesCount)"
            }
            
            // Si el usuario actual le dio like, resaltamos el botón
            if (isLikedByMe) {
                likeButton.setIconResource(R.drawable.ic_thumb_up)
                likeButton.alpha = 1.0f
            } else {
                likeButton.setIconResource(R.drawable.ic_thumb_up) // Mismo icono pero con transparencia
                likeButton.alpha = 0.5f
            }

            likeButton.setOnClickListener { onLikeClick(comment) }
            
            // Lógica de responder
            val replyAction = {
                onReplyClick(comment.userName)
            }
            replyButton.setOnClickListener { replyAction() }
            itemView.setOnClickListener { replyAction() }

            // Menú de opciones
            menuButton.setOnClickListener { onMenuClick(menuButton, comment) }

            // Accesibilidad simplificada
            itemView.contentDescription = "${comment.userName} dice: ${comment.commentText}. Publicado el ${dateText.text}."
            
            likeButton.contentDescription = if (isLikedByMe) {
                "$likesCount me gusta. Toca para quitar."
            } else {
                "$likesCount me gusta. Toca para indicar que te gusta."
            }
            replyButton.contentDescription = "Responder a ${comment.userName}"
            menuButton.contentDescription = "Opciones del comentario"
        }
    }
}
