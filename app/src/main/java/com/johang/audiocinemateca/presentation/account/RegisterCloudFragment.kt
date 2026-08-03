package com.johang.audiocinemateca.presentation.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterCloudFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    @Inject
    lateinit var cloudRepository: CloudRepository

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AudiocinematecaTheme {
                    var isLoading by remember { mutableStateOf(false) }

                    RegisterCloudScreen(
                        isLoading = isLoading,
                        onBackClick = { findNavController().navigateUp() },
                        onRegisterClick = { email, password ->
                            isLoading = true
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        syncUsernameAndFinish {
                                            isLoading = false
                                        }
                                    } else {
                                        isLoading = false
                                        val exception = task.exception
                                        if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                                            MaterialAlertDialogBuilder(requireContext())
                                                .setTitle("Cuenta ya registrada")
                                                .setMessage("Este correo electrónico ya está vinculado a una cuenta de Audiocinemateca (posiblemente a través de Google).\n\nPor favor, inicia sesión con Google primero y luego podrás asignar una contraseña desde tu perfil.")
                                                .setPositiveButton("Entendido") { _, _ -> 
                                                    findNavController().navigateUp() 
                                                }
                                                .show()
                                        } else {
                                            Toast.makeText(
                                                requireContext(), 
                                                "Error: ${exception?.localizedMessage}", 
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                        }
                    )
                }
            }
        }
    }

    private fun syncUsernameAndFinish(onComplete: () -> Unit) {
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername() ?: "Usuario"
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()

            auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                lifecycleScope.launch {
                    try {
                        cloudRepository.syncUserProfile()
                    } catch (e: Exception) {}
                    
                    onComplete()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cuenta Creada")
                        .setMessage("¡Bienvenido! Tu cuenta en la nube ha sido creada y vinculada con éxito.")
                        .setPositiveButton("Excelente") { _, _ -> findNavController().navigateUp() }
                        .show()
                }
            }
        }
    }
}
