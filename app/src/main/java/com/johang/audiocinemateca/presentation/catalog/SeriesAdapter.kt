package com.johang.audiocinemateca.presentation.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Serie

class SeriesAdapter(private var series: List<Serie>, private val onItemClick: (String, String) -> Unit) : RecyclerView.Adapter<SeriesAdapter.SeriesViewHolder>() {

    fun updateSeries(newSeries: List<Serie>) {
        val oldSize = series.size
        series = newSeries
        val newSize = series.size

        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (newSize < oldSize) {
            notifyDataSetChanged() // Para simplificar, si la lista se reduce, notificar todo
        } else {
            notifyDataSetChanged() // Si el tamaño es el mismo, pero el contenido puede haber cambiado
        }
    }

    class SeriesViewHolder(itemView: View, val onItemClick: (String, String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.series_title)
        val yearTextView: TextView = itemView.findViewById(R.id.series_year)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_series, parent, false)
        return SeriesViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        val serie = series[position]
        holder.titleTextView.text = serie.title
        holder.yearTextView.text = serie.anio
        holder.itemView.contentDescription = "${serie.title}, ${serie.anio}"
        holder.itemView.setOnClickListener {
            holder.onItemClick(serie.id, "series")
        }
    }

    override fun getItemCount(): Int = series.size
}