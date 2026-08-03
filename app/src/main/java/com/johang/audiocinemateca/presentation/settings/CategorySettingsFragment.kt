package com.johang.audiocinemateca.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.presentation.settings.compose.*
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.util.TtsManager
import com.johang.audiocinemateca.presentation.mylists.PlaybackHistoryViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CategorySettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by viewModels()
    private val playbackHistoryViewModel: PlaybackHistoryViewModel by activityViewModels()

    @Inject lateinit var geminiRepository: GeminiRepository
    @Inject lateinit var ttsManager: TtsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val category = arguments?.getString("category") ?: "general"

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    when (category) {
                        "general" -> GeneralSettingsScreen(
                            viewModel = viewModel,
                            onBack = { findNavController().navigateUp() }
                        )
                        "playback" -> PlaybackSettingsScreen(
                            viewModel = viewModel,
                            onClearHistory = {
                                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                    .setTitle("Eliminar historial")
                                    .setMessage("¿Estás seguro de que deseas borrar todo el historial de reproducción?")
                                    .setPositiveButton("Eliminar") { _, _ -> playbackHistoryViewModel.clearAllHistory() }
                                    .setNegativeButton("Cancelar", null).show()
                            },
                            onBack = { findNavController().navigateUp() }
                        )
                        "community" -> CommunitySettingsScreen(
                            viewModel = viewModel,
                            onBack = { findNavController().navigateUp() }
                        )
                        "ai" -> AiSettingsScreen(
                            viewModel = viewModel,
                            geminiRepository = geminiRepository,
                            onBack = { findNavController().navigateUp() }
                        )
                        "tts" -> TtsSettingsScreen(
                            viewModel = viewModel,
                            ttsManager = ttsManager,
                            onBack = { findNavController().navigateUp() }
                        )
                        "downloads" -> DownloadsSettingsScreen(
                            viewModel = viewModel,
                            onBack = { findNavController().navigateUp() }
                        )
                        else -> {
                            CategorySettingsScreen(
                                title = "Ajustes",
                                onBack = { findNavController().navigateUp() }
                            ) {
                                androidx.compose.material3.Text("Categoría $category en desarrollo...")
                            }
                        }
                    }
                }
            }
        }
    }
}
