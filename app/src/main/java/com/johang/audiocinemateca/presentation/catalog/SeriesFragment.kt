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
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.presentation.catalog.CatalogFragmentDirections

@AndroidEntryPoint
class SeriesFragment : Fragment() {

    private val viewModel: SeriesViewModel by viewModels()

    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var seriesAdapter: SeriesAdapter
    private lateinit var noResultsTextView: TextView
    private lateinit var loadingTextView: TextView
    private lateinit var loadingIndicatorContainer: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_series, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.series_recycler_view)
        progressBar = view.findViewById(R.id.progress_bar)
        noResultsTextView = view.findViewById(R.id.no_results_text_view)
        loadingTextView = view.findViewById(R.id.loading_text_view)
        loadingIndicatorContainer = view.findViewById(R.id.loading_indicator_container)
        recyclerView.layoutManager = LinearLayoutManager(context)

        seriesAdapter = SeriesAdapter(emptyList()) { itemId, itemType ->
            val action = CatalogFragmentDirections.actionGlobalContentDetailFragment(itemId, itemType)
            findNavController().navigate(action)
        }
        recyclerView.adapter = seriesAdapter

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
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) { // Cargar antes de llegar al final
                        viewModel.loadSeries()
                    }
                }
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.series.collect {
                seriesAdapter.updateSeries(it)
                updateVisibility(it.isEmpty(), viewModel.isLoading.value)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect {
                updateVisibility(viewModel.series.value.isEmpty(), it)
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect {
                it?.let { message ->
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateVisibility(isSeriesListEmpty: Boolean, isLoading: Boolean) {
        if (isLoading) {
            loadingIndicatorContainer.visibility = View.VISIBLE
            noResultsTextView.visibility = View.GONE
            recyclerView.visibility = View.GONE
        } else {
            loadingIndicatorContainer.visibility = View.GONE
            if (isSeriesListEmpty) {
                noResultsTextView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                noResultsTextView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }
}