package com.johang.audiocinemateca.presentation.community

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmojiAdapter(
    private val emojis: List<String>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        view.textSize = 28f
        view.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojis[position]
        (holder.itemView as TextView).text = emoji
        holder.itemView.setOnClickListener { onEmojiClick(emoji) }
        holder.itemView.contentDescription = "Emoji $emoji"
    }

    override fun getItemCount(): Int = emojis.size

    class EmojiViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view)
}
