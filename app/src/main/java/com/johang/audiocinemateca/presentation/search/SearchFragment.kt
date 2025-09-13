package com.johang.audiocinemateca.presentation.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.domain.model.CatalogItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchEditText: EditText
    private lateinit var voiceSearchButton: ImageButton
    private lateinit var clearTextButton: ImageButton
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var emptyResultsTextView: TextView
    private lateinit var searchAdapter: SearchAdapter

    private lateinit var searchHistoryRecyclerView: RecyclerView
    private lateinit var searchHistoryContainer: LinearLayout
    private lateinit var searchHistoryAdapter: SearchHistoryAdapter

    private var focusFirstResultAfterVoiceSearch = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startVoiceRecognition()
        } else {
            Toast.makeText(requireContext(), "Permiso de micrófono denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    private val startVoiceRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText: String? = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let {
                it[0]
            }
            spokenText?.let {
                searchEditText.setText(it)
                focusFirstResultAfterVoiceSearch = true
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(requireContext(), "Búsqueda por voz cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("SearchFragment", "onViewCreated: Fragment started")

        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.search_title)
            setDisplayHomeAsUpEnabled(true)
        }

        (activity as? AppCompatActivity)?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        searchEditText = view.findViewById(R.id.search_edit_text)
        voiceSearchButton = view.findViewById(R.id.voice_search_button)
        clearTextButton = view.findViewById(R.id.clear_text_button)
        searchResultsRecyclerView = view.findViewById(R.id.search_results_recycler_view)
        emptyResultsTextView = view.findViewById(R.id.empty_search_results_text)
        searchHistoryRecyclerView = view.findViewById(R.id.search_history_recycler_view)
        searchHistoryContainer = view.findViewById(R.id.search_history_container)
        Log.d("SearchFragment", "onViewCreated: Views initialized")

        searchResultsRecyclerView.layoutManager = LinearLayoutManager(context)
        searchAdapter = SearchAdapter(emptyList()) { catalogItem ->
            val itemType = when (catalogItem.javaClass.simpleName.lowercase()) {
                "movie" -> "peliculas"
                "serie" -> "series"
                "documentary" -> "documentales"
                "shortfilm" -> "cortometrajes"
                else -> ""
            }
            val action = SearchFragmentDirections.actionSearchFragmentToContentDetailFragment(catalogItem.id, itemType)
            findNavController().navigate(action)
        }
        searchResultsRecyclerView.adapter = searchAdapter
        Log.d("SearchFragment", "onViewCreated: Search Results RecyclerView and Adapter set")

        searchHistoryRecyclerView.layoutManager = LinearLayoutManager(context)
        searchHistoryAdapter = SearchHistoryAdapter(
            historyList = emptyList(),
            onItemClick = { query ->
                searchEditText.setText(query)
            },
            onItemLongClick = { query ->
                showDeleteConfirmationDialog(query)
                true
            }
        )
        searchHistoryRecyclerView.adapter = searchHistoryAdapter
        Log.d("SearchFragment", "onViewCreated: Search History RecyclerView and Adapter set")

        voiceSearchButton.setOnClickListener { checkPermissionAndStartVoiceRecognition() }
        clearTextButton.setOnClickListener { searchEditText.text.clear() }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.setSearchQuery(s.toString())
                clearTextButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                true
            }
            false
        }

        searchEditText.requestFocus()
        searchEditText.postDelayed({
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        observeViewModel()
        observeRecentSearches()
        Log.d("SearchFragment", "onViewCreated: ViewModel observed")
    }

    private fun checkPermissionAndStartVoiceRecognition() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceRecognition()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(requireContext(), "Necesitamos permiso de micrófono para la búsqueda por voz.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di algo para buscar...")
        }
        try {
            startVoiceRecognitionLauncher.launch(intent)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Tu dispositivo no soporta el reconocimiento de voz.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(false)
            show()
        }
        searchEditText.setOnEditorActionListener(null)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.searchState.collect { state ->
                when (state) {
                    is SearchState.Initial -> {
                        searchAdapter.updateList(emptyList(), "")
                        emptyResultsTextView.text = "Los resultados de búsqueda irán apareciendo aquí"
                        emptyResultsTextView.visibility = View.VISIBLE
                        searchResultsRecyclerView.visibility = View.GONE
                        searchHistoryContainer.visibility = View.VISIBLE
                    }
                    is SearchState.Loading -> {
                        searchAdapter.updateList(emptyList(), "")
                        emptyResultsTextView.text = "Buscando resultados..."
                        emptyResultsTextView.visibility = View.VISIBLE
                        searchResultsRecyclerView.visibility = View.GONE
                        searchHistoryContainer.visibility = View.GONE
                    }
                    is SearchState.NoResults -> {
                        searchAdapter.updateList(emptyList(), state.query)
                        emptyResultsTextView.text = "Ups, al parecer aún no contamos con el titulo de ${state.query}, ven más adelante para comprovar la disponivilidad"
                        emptyResultsTextView.visibility = View.VISIBLE
                        searchResultsRecyclerView.visibility = View.GONE
                        searchHistoryContainer.visibility = View.GONE
                    }
                    is SearchState.HasResults -> {
                        searchAdapter.updateList(state.results, state.query)
                        emptyResultsTextView.visibility = View.GONE
                        searchResultsRecyclerView.visibility = View.VISIBLE
                        searchHistoryContainer.visibility = View.GONE

                        if (focusFirstResultAfterVoiceSearch) {
                            searchResultsRecyclerView.post {
                                val firstItemView = searchResultsRecyclerView.layoutManager?.findViewByPosition(0)
                                firstItemView?.requestFocus()
                                // Forzar el cierre del teclado
                                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                            }
                            focusFirstResultAfterVoiceSearch = false
                        }
                    }
                }
            }
        }
    }

    private fun observeRecentSearches() {
        lifecycleScope.launch {
            viewModel.recentSearches.collect { history ->
                searchHistoryAdapter.updateList(history)
            }
        }
    }

    private fun showDeleteConfirmationDialog(queryToDelete: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar del historial")
            .setMessage("¿Estás seguro de que quieres eliminar \"$queryToDelete\" del historial de búsqueda?")
            .setPositiveButton("Eliminar") { dialog, _ ->
                viewModel.deleteSearchHistoryItem(queryToDelete)
                Toast.makeText(requireContext(), "\"$queryToDelete\" eliminado del historial.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}

