package com.johang.audiocinemateca.presentation.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem

class SearchAdapter(private var searchResults: List<CatalogItem>, private val onItemClick: (CatalogItem) -> Unit) :
    RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    class SearchViewHolder(itemView: View, val onItemClick: (CatalogItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.search_item_title)
        val typeTextView: TextView = itemView.findViewById(R.id.search_item_type)

        fun bind(catalogItem: CatalogItem) {
            titleTextView.text = catalogItem.title
            typeTextView.text = when (catalogItem) {
                is Movie -> "Película"
                is Serie -> "Serie"
                is Documentary -> "Documental"
                is ShortFilm -> "Cortometraje"
                else -> "Desconocido"
            }
            itemView.setOnClickListener { onItemClick(catalogItem) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(searchResults[position])
    }

    override fun getItemCount(): Int = searchResults.size

    fun updateList(newList: List<CatalogItem>) {
        searchResults = newList
        notifyDataSetChanged()
    }
}