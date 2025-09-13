package com.johang.audiocinemateca.presentation.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.entities.SearchHistoryEntity

class SearchHistoryAdapter(
    private var historyList: List<SearchHistoryEntity>,
    private val onItemClick: (String) -> Unit,
    private val onItemLongClick: (String) -> Boolean
) : RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View, val onItemClick: (String) -> Unit, val onItemLongClick: (String) -> Boolean) : RecyclerView.ViewHolder(itemView) {
        val queryTextView: TextView = itemView.findViewById(R.id.history_query_text)

        fun bind(history: SearchHistoryEntity) {
            queryTextView.text = history.query
            itemView.setOnClickListener { onItemClick(history.query) }
            itemView.setOnLongClickListener { onItemLongClick(history.query) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_history, parent, false)
        return HistoryViewHolder(view, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(historyList[position])
    }

    override fun getItemCount(): Int = historyList.size

    fun updateList(newList: List<SearchHistoryEntity>) {
        historyList = newList
        notifyDataSetChanged()
    }
}
