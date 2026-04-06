package com.johang.audiocinemateca.presentation.community

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

import android.content.Intent
import android.net.Uri
import android.util.Log

@AndroidEntryPoint
class CommunityCommentsFragment : Fragment() {

    private val viewModel: CommunityViewModel by viewModels()
    private lateinit var adapter: CommunityCommentAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var filterButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_community_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.community_comments_progress)
        emptyText = view.findViewById(R.id.empty_community_comments_text)
        filterButton = view.findViewById(R.id.filter_comments_button)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.community_comments_recycler_view)
        adapter = CommunityCommentAdapter(
            findItemById = { id, isSeries -> viewModel.getCatalogItem(id, "", isSeries) },
            onCommentClick = { comment -> navigateToContent(comment) }
        )
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (lastVisibleItemPosition >= totalItemCount - 5) {
                    viewModel.loadMoreComments()
                }
            }
        })

        filterButton.setOnClickListener { showFilterDialog() }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.comments.collect {
                adapter.updateItems(it)
                emptyText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect {
                progressBar.visibility = if (it) View.VISIBLE else View.GONE
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentFilter.collect {
                filterButton.text = "Filtrar por: $it"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.indexErrorUrl.collect { url ->
                showIndexErrorDialog(url)
            }
        }
    }

    private fun showIndexErrorDialog(url: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Falta un índice en la base de datos")
            .setMessage("Para poder ver los comentarios globales, Firebase requiere que se cree un índice específico. ¿Deseas crearlo ahora? (Se abrirá el navegador)")
            .setPositiveButton("Crear índice") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "No se pudo abrir el navegador. Por favor, crea el índice manualmente en la consola de Firebase.", Toast.LENGTH_LONG).show()
                    Log.e("CommunityFragment", "Error abriendo URL: $url", e)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFilterDialog() {
        val options = arrayOf("Más recientes", "Más antiguos", "Más populares", "Menos populares", "Mis comentarios")
        val checkedItem = options.indexOf(viewModel.currentFilter.value)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Ordenar comentarios")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                viewModel.setFilter(options[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun navigateToContent(comment: com.johang.audiocinemateca.data.model.Comment) {
        if (comment.contentId.isEmpty()) {
            Toast.makeText(requireContext(), "Este comentario no tiene información de origen.", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            // Buscamos el item en el catálogo (usando la caché en memoria que añadimos antes)
            val item = viewModel.getCatalogItem(comment.contentId, comment.contentType)
            progressBar.visibility = View.GONE
            
            if (item != null) {
                val action = MainNavGraphDirections.actionGlobalPlayerFragment(
                    catalogItem = item,
                    partIndex = comment.partIndex,
                    episodeIndex = comment.episodeIndex
                )
                findNavController().navigate(action)
            } else {
                Toast.makeText(requireContext(), "No se pudo encontrar '${comment.contentTitle}' en el catálogo local.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
