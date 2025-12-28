package com.johang.audiocinemateca.presentation.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint
import androidx.fragment.app.viewModels
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.presentation.catalog.CatalogFragmentDirections

import com.johang.audiocinemateca.util.ShareUtils

@AndroidEntryPoint
class DocumentalesFragment : Fragment() {

    private val viewModel: DocumentalesViewModel by viewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var documentalesAdapter: DocumentalesAdapter
    private lateinit var noResultsTextView: TextView
    private lateinit var loadingTextView: TextView
    private lateinit var loadingIndicatorContainer: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_documentales, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.documentales_recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        noResultsTextView = view.findViewById(R.id.no_results_text_view)
        loadingTextView = view.findViewById(R.id.loading_text_view)
        loadingIndicatorContainer = view.findViewById(R.id.loading_indicator_container)
        recyclerView.layoutManager = LinearLayoutManager(context)

        documentalesAdapter = DocumentalesAdapter(
            onItemClick = { itemId, itemType ->
                val action = CatalogFragmentDirections.actionGlobalContentDetailFragment(itemId, itemType)
                findNavController().navigate(action)
            },
            onFavoriteToggle = { documental, isFavorite ->
                viewModel.toggleFavorite(documental, isFavorite)
            },
            onShareClick = { documental ->
                ShareUtils.shareContent(requireContext(), documental)
            }
        )
        recyclerView.adapter = documentalesAdapter

        setupScrollListener()
        observeViewModel()
    }

    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!viewModel.isLoading.value && !viewModel.isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= viewModel.pageSize) {
                        viewModel.loadDocumentales()
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.documentales.collectLatest { documentales ->
                documentalesAdapter.submitList(documentales)
                updateVisibility(documentales.isEmpty(), viewModel.isLoading.value)
            }
        }

        lifecycleScope.launch {
            viewModel.favoriteIds.collectLatest { favoriteIds ->
                documentalesAdapter.setFavoriteIds(favoriteIds)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                updateVisibility(viewModel.documentales.value.isEmpty(), isLoading)
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { message ->
                message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateVisibility(isDocumentaryListEmpty: Boolean, isLoading: Boolean) {
        if (isLoading) {
            loadingIndicatorContainer.visibility = View.VISIBLE
            noResultsTextView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            loadingIndicatorContainer.visibility = View.GONE
            if (isDocumentaryListEmpty) {
                noResultsTextView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                noResultsTextView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }
}
