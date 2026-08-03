package com.johang.audiocinemateca.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import androidx.lifecycle.lifecycleScope
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.repository.PlaybackProgressRepository
import com.johang.audiocinemateca.data.repository.SearchRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    lateinit var searchRepository: SearchRepository

    @Inject
    lateinit var playbackProgressRepository: PlaybackProgressRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AudiocinematecaTheme {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToLogin = {
                            try {
                                findNavController().navigate(R.id.loginCloudFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onNavigateToRegister = {
                            try {
                                findNavController().navigate(R.id.registerCloudFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onNavigateToExplore = {
                            try {
                                findNavController().navigate(R.id.catalogFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onNavigateToDetail = { itemId, itemType ->
                            try {
                                val bundle = Bundle().apply {
                                    putString("itemId", itemId)
                                    putString("itemType", itemType)
                                }
                                findNavController().navigate(R.id.action_homeFragment_to_contentDetailFragment, bundle)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onPlayContent = { itemId, itemType ->
                            lifecycleScope.launch {
                                try {
                                    val catalog = catalogRepository.getCatalog()
                                    val foundItem = catalog?.series?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.movies?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.documentaries?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.shortFilms?.find { it.id.equals(itemId, ignoreCase = true) }

                                    if (foundItem != null) {
                                        val allProgress = playbackProgressRepository.getPlaybackProgressForContent(foundItem.id)
                                        val latest = allProgress.maxByOrNull { it.lastPlayedTimestamp }
                                        val partIndex = latest?.partIndex ?: if (foundItem is Serie) 0 else -1
                                        val episodeIndex = latest?.episodeIndex ?: if (foundItem is Serie) 0 else -1

                                        val bundle = Bundle().apply {
                                            putParcelable("catalogItem", foundItem)
                                            putInt("partIndex", partIndex)
                                            putInt("episodeIndex", episodeIndex)
                                        }
                                        findNavController().navigate(R.id.action_global_playerFragment, bundle)
                                    } else {
                                        val bundle = Bundle().apply {
                                            putString("itemId", itemId)
                                            putString("itemType", itemType)
                                        }
                                        findNavController().navigate(R.id.action_homeFragment_to_contentDetailFragment, bundle)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onResumeContentWithPosition = { itemId, itemType, explicitPartIndex, explicitEpisodeIndex ->
                            lifecycleScope.launch {
                                try {
                                    val catalog = catalogRepository.getCatalog()
                                    val foundItem = catalog?.series?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.movies?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.documentaries?.find { it.id.equals(itemId, ignoreCase = true) }
                                        ?: catalog?.shortFilms?.find { it.id.equals(itemId, ignoreCase = true) }

                                    if (foundItem != null) {
                                        val bundle = Bundle().apply {
                                            putParcelable("catalogItem", foundItem)
                                            putInt("partIndex", explicitPartIndex)
                                            putInt("episodeIndex", explicitEpisodeIndex)
                                        }
                                        findNavController().navigate(R.id.action_global_playerFragment, bundle)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onMarkAsWatched = { item ->
                            viewModel.markAsWatched(item.progress)
                        },
                        onOpenDrawer = {
                            (activity as? com.johang.audiocinemateca.MainActivity)?.openNavigationDrawer()
                        },
                        onSearchClick = {
                            try {
                                findNavController().navigate(R.id.action_global_searchFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        onSurpriseMeClick = {
                            lifecycleScope.launch {
                                try {
                                    val randomItem = searchRepository.getRandomCatalogItem("peliculas")
                                        ?: searchRepository.getRandomCatalogItem("series")
                                    randomItem?.let { item ->
                                        val bundle = Bundle().apply {
                                            putString("itemId", item.id)
                                            putString("itemType", if (item is Serie) "series" else "peliculas")
                                        }
                                        findNavController().navigate(R.id.action_homeFragment_to_contentDetailFragment, bundle)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        onNavigateToProfile = {
                            try {
                                findNavController().navigate(R.id.profileFragment)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAuthState()
    }
}
