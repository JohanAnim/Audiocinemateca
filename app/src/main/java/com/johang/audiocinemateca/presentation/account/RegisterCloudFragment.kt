package com.johang.audiocinemateca.presentation.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.databinding.FragmentRegisterCloudBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RegisterCloudFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    @Inject
    lateinit var cloudRepository: com.johang.audiocinemateca.data.repository.CloudRepository

    private var _binding: FragmentRegisterCloudBinding? = null
    private val binding get() = _binding!!
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterCloudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirm = binding.etConfirmPassword.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "El correo es obligatorio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), "Debes elegir una contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 6) {
                Toast.makeText(requireContext(), "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirm) {
                Toast.makeText(requireContext(), "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        syncUsernameAndFinish()
                    } else {
                        setLoading(false)
                        val exception = task.exception
                        if (exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            // EL CORREO YA EXISTE
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Cuenta ya registrada")
                                .setMessage("Este correo electrónico ya está vinculado a una cuenta de Audiocinemateca (posiblemente a través de Google).\n\nPor favor, inicia sesión con Google primero y luego podrás asignar una contraseña desde tu perfil.")
                                .setPositiveButton("Entendido") { _, _ -> findNavController().navigateUp() }
                                .show()
                        } else {
                            Toast.makeText(requireContext(), "Error: ${exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
    }

    private fun syncUsernameAndFinish() {
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername() ?: "Usuario"
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(username)
                .build()

            auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                lifecycleScope.launch {
                    try {
                        cloudRepository.syncUserProfile(username)
                    } catch (e: Exception) {}
                    
                    setLoading(false)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cuenta Creada")
                        .setMessage("¡Bienvenido! Tu cuenta en la nube ha sido creada y vinculada con éxito.")
                        .setPositiveButton("Excelente") { _, _ -> findNavController().navigateUp() }
                        .show()
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
