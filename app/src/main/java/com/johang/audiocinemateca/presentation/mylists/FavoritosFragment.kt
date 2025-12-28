package com.johang.audiocinemateca.presentation.mylists

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.databinding.FragmentFavoritosBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritosFragment : Fragment() {

    private var _binding: FragmentFavoritosBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoritosViewModel by viewModels()
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
        observeFavorites()
    }

    private fun setupRecyclerView() {
        adapter = FavoritesAdapter(
            onItemClick = { favorite ->
                navigateToContent(favorite)
            },
            onLongClick = { favorite ->
                performHapticFeedback()
                showOptionsDialog(favorite)
            },
            onDeleteRequest = { favorite ->
                showDeleteConfirmation(favorite)
            },
            onPlayRequest = { favorite ->
                viewModel.playFavorite(favorite)
            }
        )
        binding.favoritesRecyclerView.layoutManager = GridLayoutManager(context, 3)
        binding.favoritesRecyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.navigateToPlayer.collect { (item, partIndex, episodeIndex) ->
                val action = MyListsFragmentDirections.actionGlobalPlayerFragment(
                    catalogItem = item,
                    partIndex = partIndex,
                    episodeIndex = episodeIndex
                )
                findNavController().navigate(action)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performHapticFeedback() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }

    private fun showOptionsDialog(favorite: FavoriteEntity) {
        val options = arrayOf("Reproducir ahora", "Ver información", "Eliminar de favoritos")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(favorite.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.playFavorite(favorite) // Reproducir
                    1 -> navigateToContent(favorite) // Ver info
                    2 -> showDeleteConfirmation(favorite) // Eliminar
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(favorite: FavoriteEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Eliminar favorito")
            .setMessage("¿Estás seguro de que quieres eliminar '${favorite.title}' de tus favoritos?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.removeFavorite(favorite)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun navigateToContent(favorite: FavoriteEntity) {
        val typeForNav = when (favorite.contentType) {
            "movie" -> "peliculas"
            "serie" -> "series"
            "documentary" -> "documentales"
            "shortfilm" -> "cortometrajes"
            else -> "unknown"
        }
        val action = MyListsFragmentDirections.actionMyListsFragmentToContentDetailFragment(
            itemId = favorite.contentId,
            itemType = typeForNav
        )
        findNavController().navigate(action)
    }

    private fun observeFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.favorites.collectLatest { favorites ->
                adapter.submitList(favorites)
                binding.favoritesRecyclerView.isVisible = favorites.isNotEmpty()
                binding.emptyFavoritesText.isVisible = favorites.isEmpty()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}