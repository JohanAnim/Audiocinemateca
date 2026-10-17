package com.johang.audiocinemateca.presentation.search

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private val viewModel: SearchViewModel by viewModels()

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
                viewModel.setSearchQuery(it)
                viewModel.saveQueryToHistory(it)
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Toast.makeText(requireContext(), "Búsqueda por voz cancelada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        (activity as? AppCompatActivity)?.supportActionBar?.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    val searchQuery by viewModel.searchQuery.collectAsState()
                    val searchState by viewModel.searchState.collectAsState()
                    val recentSearches by viewModel.recentSearches.collectAsState()

                    SearchScreen(
                        searchQuery = searchQuery,
                        searchState = searchState,
                        recentSearches = recentSearches,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onSearchAction = { viewModel.saveQueryToHistory(it) },
                        onVoiceClick = { checkPermissionAndStartVoiceRecognition() },
                        onItemClick = { item ->
                            val itemType = when (item.javaClass.simpleName.lowercase()) {
                                "movie" -> "peliculas"
                                "serie" -> "series"
                                "documentary" -> "documentales"
                                "shortfilm" -> "cortometrajes"
                                else -> "peliculas"
                            }
                            val action = SearchFragmentDirections.actionSearchFragmentToContentDetailFragment(
                                itemId = item.id,
                                itemType = itemType
                            )
                            findNavController().navigate(action)
                        },
                        onDeleteRecentItem = { viewModel.deleteSearchHistoryItem(it) },
                        onClearHistory = { viewModel.clearSearchHistory() },
                        onNavigateBack = { findNavController().navigateUp() }
                    )
                }
            }
        }
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
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Tu dispositivo no soporta el reconocimiento de voz.", Toast.LENGTH_SHORT).show()
        }
    }
}
