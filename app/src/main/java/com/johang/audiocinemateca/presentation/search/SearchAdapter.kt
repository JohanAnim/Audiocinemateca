package com.johang.audiocinemateca.presentation.search

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
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

    private var query: String = ""

    class SearchViewHolder(itemView: View, val onItemClick: (CatalogItem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.search_item_title)
        val typeTextView: TextView = itemView.findViewById(R.id.search_item_type)

        fun bind(catalogItem: CatalogItem, query: String) {
            titleTextView.text = highlightText(catalogItem.title, query)
            typeTextView.text = when (catalogItem) {
                is Movie -> "Película"
                is Serie -> "Serie"
                is Documentary -> "Documental"
                is ShortFilm -> "Cortometraje"
                else -> "Desconocido"
            }
            itemView.setOnClickListener { onItemClick(catalogItem) }
        }

        private fun highlightText(text: String, query: String): SpannableString {
            val spannable = SpannableString(text)
            if (query.isNotEmpty()) {
                val lowerText = text.lowercase()
                val lowerQuery = query.lowercase()
                var startIndex = lowerText.indexOf(lowerQuery)
                while (startIndex >= 0) {
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        startIndex,
                        startIndex + query.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    startIndex = lowerText.indexOf(lowerQuery, startIndex + 1)
                }
            }
            return spannable
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return SearchViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.bind(searchResults[position], query)
    }

    override fun getItemCount(): Int = searchResults.size

    fun updateList(newList: List<CatalogItem>, newQuery: String) {
        searchResults = newList
        query = newQuery
        notifyDataSetChanged()
    }
}