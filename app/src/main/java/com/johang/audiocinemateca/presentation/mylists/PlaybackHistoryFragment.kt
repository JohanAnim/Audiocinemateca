package com.johang.audiocinemateca.presentation.mylists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.johang.audiocinemateca.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.johang.audiocinemateca.MainNavGraphDirections
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.domain.model.CatalogItem

@AndroidEntryPoint
class PlaybackHistoryFragment : Fragment() {

    private val viewModel: PlaybackHistoryViewModel by viewModels()
    private lateinit var historyAdapter: PlaybackHistoryAdapter
    private lateinit var historyRecyclerView: RecyclerView
    private lateinit var emptyHistoryText: TextView
    // private lateinit var clearHistoryButton: Button // Eliminado del layout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playback_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // No llamar a setHasOptionsMenu(true) aquí
        // No configurar el título de la ActionBar aquí

        historyRecyclerView = view.findViewById(R.id.history_recycler_view)
        emptyHistoryText = view.findViewById(R.id.empty_history_text)
        // clearHistoryButton = view.findViewById(R.id.clear_history_button) // Eliminado del layout

        historyAdapter = PlaybackHistoryAdapter(
            onClick = { playbackProgress, catalogItem ->
                navigateToPlayer(playbackProgress, catalogItem)
            },
            onLongClick = { playbackProgress, catalogItem ->
                showDeleteSingleItemConfirmationDialog(playbackProgress)
                true // Consumir el evento long click
            }
        )

        historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = historyAdapter
        }

        // clearHistoryButton.setOnClickListener { showClearAllConfirmationDialog() } // Eliminado del layout

        observeViewModel()
    }

    // Eliminar onCreateOptionsMenu

    // Eliminar onOptionsItemSelected

    override fun onDestroyView() {
        super.onDestroyView()
        // No restaurar el título de la ActionBar aquí
        // No invalidar el menú de opciones aquí
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.historyItems.collect {
                historyAdapter.submitList(it)
                emptyHistoryText.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
                // clearHistoryButton.visibility = if (it.isEmpty()) View.GONE else View.VISIBLE // Eliminado del layout
            }
        }
    }

    private fun navigateToPlayer(playbackProgress: PlaybackProgressEntity, catalogItem: CatalogItem?) {
        catalogItem?.let { item ->
            val action = MainNavGraphDirections.actionGlobalPlayerFragment(
                item,
                playbackProgress.partIndex,
                playbackProgress.episodeIndex
            )
            findNavController().navigate(action)
        }
    }

    // Hacer esta función pública para que MyListsFragment pueda llamarla
    fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Olvidar todo el historial") // Título actualizado
            .setMessage("¿Estás seguro de que deseas olvidar todo el historial de reproducción? Esta acción es irreversible y eliminará todos los puntos de guardado.")
            .setPositiveButton("Sí, olvidar todo") { dialog, _ -> // Texto actualizado
                viewModel.clearAllHistory()
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteSingleItemConfirmationDialog(playbackProgress: PlaybackProgressEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("Olvidar título")
            .setMessage("¿Estás seguro de que deseas olvidar este título del historial? Esta acción es irreversible.")
            .setPositiveButton("Sí, olvidar") { dialog, _ ->
                viewModel.deleteHistoryItem(playbackProgress)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}