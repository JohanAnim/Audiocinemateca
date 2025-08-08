package com.johang.audiocinemateca.presentation.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.ShortFilm

class CortometrajesAdapter(private var cortometrajes: List<ShortFilm>, private val onItemClick: (String, String) -> Unit) : RecyclerView.Adapter<CortometrajesAdapter.CortometrajesViewHolder>() {

    fun updateCortometrajes(newCortometrajes: List<ShortFilm>) {
        val oldSize = cortometrajes.size
        cortometrajes = newCortometrajes
        val newSize = cortometrajes.size

        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (newSize < oldSize) {
            notifyDataSetChanged() // Para simplificar, si la lista se reduce, notificar todo
        } else {
            notifyDataSetChanged() // Si el tamaño es el mismo, pero el contenido puede haber cambiado
        }
    }

    class CortometrajesViewHolder(itemView: View, val onItemClick: (String, String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.cortometrajes_title)
        val yearTextView: TextView = itemView.findViewById(R.id.cortometrajes_year)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CortometrajesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cortometrajes, parent, false)
        return CortometrajesViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: CortometrajesViewHolder, position: Int) {
        val cortometraje = cortometrajes[position]
        holder.titleTextView.text = cortometraje.title
        holder.yearTextView.text = cortometraje.anio
        holder.itemView.setOnClickListener {
            holder.onItemClick(cortometraje.id, "cortometrajes")
        }
    }

    override fun getItemCount(): Int = cortometrajes.size
}