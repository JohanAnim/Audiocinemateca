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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyResultsTextView: TextView
    private lateinit var searchAdapter: SearchAdapter

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
                performSearch()
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

        // Configurar la ActionBar de la actividad principal
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.search_title)
            setDisplayHomeAsUpEnabled(true)
        }

        // Manejar el clic en el botón de volver de la ActionBar
        (activity as? AppCompatActivity)?.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        searchEditText = view.findViewById(R.id.search_edit_text)
        voiceSearchButton = view.findViewById(R.id.voice_search_button)
        recyclerView = view.findViewById(R.id.search_results_recycler_view)
        emptyResultsTextView = view.findViewById(R.id.empty_search_results_text)
        Log.d("SearchFragment", "onViewCreated: Views initialized")

        recyclerView.layoutManager = LinearLayoutManager(context)
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
        recyclerView.adapter = searchAdapter
        Log.d("SearchFragment", "onViewCreated: RecyclerView and Adapter set")

        // Configurar listeners para la nueva barra de búsqueda
        voiceSearchButton.setOnClickListener { checkPermissionAndStartVoiceRecognition() }

        searchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            }
            false
        }

        // Mostrar el teclado virtual automáticamente
        searchEditText.requestFocus()
        searchEditText.postDelayed({
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        observeViewModel()
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES") // O el idioma que prefieras
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Di algo para buscar...")
        }
        try {
            startVoiceRecognitionLauncher.launch(intent)
        } catch (a: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Tu dispositivo no soporta el reconocimiento de voz.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch() {
        val query = searchEditText.text.toString()
        viewModel.setSearchQuery(query)
        // Ocultar el teclado
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restaurar la ActionBar de la actividad principal
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(false)
            show() // Mostrar la ActionBar de la actividad principal
        }
        // Limpiar referencias para evitar leaks
        searchEditText.setOnEditorActionListener(null)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.searchResults.collect {
                searchAdapter.updateList(it)
                if (it.isEmpty() && !viewModel.searchQuery.value.isNullOrBlank()) {
                    emptyResultsTextView.text = "No se encontraron resultados para \"${viewModel.searchQuery.value}\"";
                    emptyResultsTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else if (viewModel.searchQuery.value.isNullOrBlank()) {
                    emptyResultsTextView.text = "Escribe algo para buscar...";
                    emptyResultsTextView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyResultsTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
        }
    }
}