package com.johang.audiocinemateca.presentation.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.presentation.DonationDialogFragment
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    private val viewModel: AccountViewModel by activityViewModels()

    override fun onResume() {
        super.onResume()
        // Ocultar la ActionBar global para usar nuestro encabezado personalizado en Compose
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.hide()
    }

    override fun onPause() {
        super.onPause()
        // Mostrar la ActionBar global al salir
        (activity as? androidx.appcompat.app.AppCompatActivity)?.supportActionBar?.show()
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
                    AccountScreen(
                        viewModel = viewModel,
                        authCatalogRepository = authCatalogRepository,
                        onNavigateToProfile = {
                            findNavController().navigate(R.id.action_accountFragment_to_profileFragment)
                        },
                        onNavigateToSettings = {
                            findNavController().navigate(R.id.action_accountFragment_to_settingsFragment)
                        },
                        onNavigateToWebView = { url ->
                            val action = AccountFragmentDirections.actionAccountFragmentToWebViewFragment(url)
                            findNavController().navigate(action)
                        },
                        onShowDonation = {
                            DonationDialogFragment().show(childFragmentManager, DonationDialogFragment.TAG)
                        },
                        onShowUpdateProgress = {
                            UpdateProgressDialogFragment().show(parentFragmentManager, "UpdateProgressDialog")
                        }
                    )
                }
            }
        }
    }
}
