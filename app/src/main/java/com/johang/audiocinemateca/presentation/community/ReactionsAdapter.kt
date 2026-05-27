package com.johang.audiocinemateca.presentation.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReactionsAdapter(
    private val reactions: List<String>,
    private val onReactionClicked: (String) -> Unit
) : RecyclerView.Adapter<ReactionsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reaction = reactions[position]
        (holder.itemView as TextView).apply {
            text = reaction
            textSize = 24f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setOnClickListener { onReactionClicked(reaction) }
        }
    }

    override fun getItemCount() = reactions.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
