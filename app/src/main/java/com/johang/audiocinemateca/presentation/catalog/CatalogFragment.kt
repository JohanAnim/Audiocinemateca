package com.johang.audiocinemateca.presentation.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.util.ShareUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CatalogFragment : Fragment() {

    @Inject
    lateinit var searchRepository: SearchRepository

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var geminiRepository: GeminiRepository

    private val catalogViewModel: CatalogViewModel by viewModels()
    private val peliculasViewModel: PeliculasViewModel by viewModels()
    private val seriesViewModel: SeriesViewModel by viewModels()
    private val cortometrajesViewModel: CortometrajesViewModel by viewModels()
    private val documentalesViewModel: DocumentalesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val defaultTab = sharedPreferencesManager.getString("default_content_tab", "peliculas")
        val initialTabIndex = when (defaultTab) {
            "series" -> 1
            "cortometrajes" -> 2
            "documentales" -> 3
            else -> 0
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    CatalogScreen(
                        initialTabIndex = initialTabIndex,
                        catalogViewModel = catalogViewModel,
                        peliculasViewModel = peliculasViewModel,
                        seriesViewModel = seriesViewModel,
                        cortometrajesViewModel = cortometrajesViewModel,
                        documentalesViewModel = documentalesViewModel,
                        onMenuClick = {
                            (activity as? MainActivity)?.openNavigationDrawer()
                        },
                        onSearchClick = {
                            try {
                                findNavController().navigate(CatalogFragmentDirections.actionCatalogFragmentToSearchFragment())
                            } catch (e: Exception) {
                                Toast.makeText(requireContext(), "Error al abrir la búsqueda: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        onSurpriseMeClick = { category ->
                            lifecycleScope.launch {
                                val randomItem = searchRepository.getRandomCatalogItem(category)
                                randomItem?.let { item ->
                                    val action = MainNavGraphDirections.actionGlobalContentDetailFragment(item.id, category)
                                    findNavController().navigate(action)
                                } ?: run {
                                    Toast.makeText(requireContext(), "No se pudo encontrar un elemento aleatorio en esta categoría.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onItemClick = { itemId, category ->
                            val action = MainNavGraphDirections.actionGlobalContentDetailFragment(itemId, category)
                            findNavController().navigate(action)
                        },
                        onShareItem = { item ->
                            ShareUtils.shareContent(requireContext(), item, geminiRepository)
                        }
                    )
                }
            }
        }
    }
}