package com.johang.audiocinemateca.presentation.catalog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.model.Movie

class MovieAdapter(private var movies: List<Movie>, private val onItemClick: (String, String) -> Unit) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    fun updateMovies(newMovies: List<Movie>) {
        movies = newMovies
        notifyDataSetChanged() // Simplificar la notificación para asegurar la actualización completa
    }

    class MovieViewHolder(itemView: View, val onItemClick: (String, String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.movie_title)
        val yearTextView: TextView = itemView.findViewById(R.id.movie_year)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return MovieViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movies[position]
        holder.titleTextView.text = movie.title
        holder.yearTextView.text = movie.anio
        holder.itemView.contentDescription = "${movie.title}, ${movie.anio}"
        holder.itemView.setOnClickListener {
            holder.onItemClick(movie.id, "peliculas")
        }
    }

    override fun getItemCount(): Int = movies.size
}