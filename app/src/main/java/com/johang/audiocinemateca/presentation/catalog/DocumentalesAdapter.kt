package com.johang.audiocinemateca.presentation.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Documentary

class DocumentalesAdapter(private var documentales: List<Documentary>, private val onItemClick: (String, String) -> Unit) : RecyclerView.Adapter<DocumentalesAdapter.DocumentalesViewHolder>() {

    fun updateDocumentales(newDocumentales: List<Documentary>) {
        val oldSize = documentales.size
        documentales = newDocumentales
        val newSize = documentales.size

        if (newSize > oldSize) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else if (newSize < oldSize) {
            notifyDataSetChanged() // Para simplificar, si la lista se reduce, notificar todo
        } else {
            notifyDataSetChanged() // Si el tamaño es el mismo, pero el contenido puede haber cambiado
        }
    }

    class DocumentalesViewHolder(itemView: View, val onItemClick: (String, String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.documentales_title)
        val yearTextView: TextView = itemView.findViewById(R.id.documentales_year)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentalesViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_documentales, parent, false)
        return DocumentalesViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: DocumentalesViewHolder, position: Int) {
        val documental = documentales[position]
        holder.titleTextView.text = documental.title
        holder.yearTextView.text = documental.anio
        holder.itemView.contentDescription = "${documental.title}, ${documental.anio}"
        holder.itemView.setOnClickListener {
            holder.onItemClick(documental.id, "documentales")
        }
    }

    override fun getItemCount(): Int = documentales.size
}