package com.johang.audiocinemateca.presentation.mylists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.presentation.downloads.DownloadsViewModel
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyListsFragment : Fragment() {

    private val playbackHistoryViewModel: PlaybackHistoryViewModel by viewModels()
    private val favoritosViewModel: FavoritosViewModel by viewModels()
    private val downloadsViewModel: DownloadsViewModel by viewModels()

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
                    MyListsScreen(
                        playbackHistoryViewModel = playbackHistoryViewModel,
                        favoritosViewModel = favoritosViewModel,
                        downloadsViewModel = downloadsViewModel,
                        onNavigateToPlayer = { item, partIndex, episodeIndex, _ ->
                            val action = MainNavGraphDirections.actionGlobalPlayerFragment(
                                item,
                                partIndex,
                                episodeIndex
                            )
                            findNavController().navigate(action)
                        },
                        onNavigateToContentDetail = { contentId, contentType ->
                            val action = MainNavGraphDirections.actionGlobalContentDetailFragment(
                                itemId = contentId,
                                itemType = contentType
                            )
                            findNavController().navigate(action)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModelEvents()
    }

    private fun observeViewModelEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    favoritosViewModel.navigateToPlayer.collect { (item, partIndex, episodeIndex) ->
                        val action = MainNavGraphDirections.actionGlobalPlayerFragment(
                            catalogItem = item,
                            partIndex = partIndex,
                            episodeIndex = episodeIndex
                        )
                        findNavController().navigate(action)
                    }
                }

                launch {
                    favoritosViewModel.error.collect { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }

                launch {
                    downloadsViewModel.viewActions.collect { event ->
                        event.getContentIfNotHandled()?.let { action ->
                            when (action) {
                                is DownloadsViewModel.ViewAction.NavigateToPlayer -> {
                                    val navAction = MainNavGraphDirections.actionGlobalPlayerFragment(
                                        action.catalogItem,
                                        action.partIndex,
                                        action.episodeIndex
                                    )
                                    findNavController().navigate(navAction)
                                }
                                is DownloadsViewModel.ViewAction.ShowContentDetail -> {
                                    val navAction = MainNavGraphDirections.actionGlobalContentDetailFragment(
                                        itemId = action.itemId,
                                        itemType = action.itemType
                                    )
                                    findNavController().navigate(navAction)
                                }
                                is DownloadsViewModel.ViewAction.ShowError -> {
                                    Toast.makeText(requireContext(), action.message, Toast.LENGTH_SHORT).show()
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}