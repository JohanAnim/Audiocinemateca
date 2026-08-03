package com.johang.audiocinemateca.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.settings.compose.SettingsScreen
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    SettingsScreen(
                        onNavigateToCategory = { categoryTag ->
                            val bundle = Bundle().apply { putString("category", categoryTag) }
                            findNavController().navigate(R.id.action_settingsFragment_to_categorySettingsFragment, bundle)
                        },
                        onNavigateToPrivacy = {
                            val bundle = Bundle().apply { putString("url", "https://audiocinemateca.com/privacidad") }
                            findNavController().navigate(R.id.webViewFragment, bundle)
                        },
                        onNavigateToTerms = {
                            val bundle = Bundle().apply { putString("url", "https://audiocinemateca.com/terminos") }
                            findNavController().navigate(R.id.webViewFragment, bundle)
                        },
                        onBack = {
                            findNavController().navigateUp()
                        }
                    )
                }
            }
        }
    }
}
