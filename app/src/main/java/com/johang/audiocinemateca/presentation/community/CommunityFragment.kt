package com.johang.audiocinemateca.presentation.community

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.MainNavGraphDirections
import com.johang.audiocinemateca.presentation.community.compose.CommunityScreen
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class CommunityFragment : Fragment() {

    private val globalChatViewModel: GlobalChatViewModel by viewModels()
    private val announcementsViewModel: AnnouncementsViewModel by viewModels()
    private val communityViewModel: CommunityViewModel by viewModels()

    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                globalChatViewModel.sendMessage(spokenText.trim())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val navigateTo = arguments?.getString("select_tab") ?: activity?.intent?.getStringExtra("navigate_to")
        val initialTabIndex = if (navigateTo == "announcements") 1 else 0

        if (navigateTo == "announcements") {
            activity?.intent?.removeExtra("navigate_to")
            arguments?.remove("select_tab")
        }

        return ComposeView(requireContext()).apply {
            setContent {
                AudiocinematecaTheme {
                    CommunityScreen(
                        globalChatViewModel = globalChatViewModel,
                        announcementsViewModel = announcementsViewModel,
                        communityViewModel = communityViewModel,
                        initialTabIndex = initialTabIndex,
                        onVoiceClick = { launchVoiceInput() },
                        onNavigateToPlayer = { item, partIndex, episodeIndex ->
                            val action = MainNavGraphDirections.actionGlobalPlayerFragment(
                                catalogItem = item,
                                partIndex = partIndex,
                                episodeIndex = episodeIndex
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
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Comunidad"
    }

    private fun launchVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje para el Chat Global...")
            }
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {}
    }
}
