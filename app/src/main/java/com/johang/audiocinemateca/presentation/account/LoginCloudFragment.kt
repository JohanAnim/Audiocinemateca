package com.johang.audiocinemateca.presentation.account

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.databinding.FragmentLoginCloudBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LoginCloudFragment : Fragment() {

    @Inject
    lateinit var cloudRepository: CloudRepository

    @Inject
    lateinit var favoritesDao: FavoritesDao

    @Inject
    lateinit var playbackProgressDao: PlaybackProgressDao

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    private var _binding: FragmentLoginCloudBinding? = null
    private val binding get() = _binding!!
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginCloudBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Introduce tu correo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(requireContext(), "Introduce tu contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    setLoading(false)
                    if (task.isSuccessful) {
                        lifecycleScope.launch {
                            try {
                                // 1. OBLIGATORIO: Crear/Verificar DB del usuario
                                cloudRepository.syncUserProfile()
                                
                                // 2. Si éxito -> Continuar flujo
                                checkAndPromptSync()
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Conexión Exitosa")
                                    .setMessage("Has conectado tu cuenta con la nube y tu base de datos está lista.")
                                    .setPositiveButton("Genial") { _, _ -> findNavController().navigateUp() }
                                    .show()
                                    
                            } catch (e: Exception) {
                                // 3. Si fallo -> Alerta de Error
                                Log.e("LoginCloud", "Error creando perfil DB: ${e.message}")
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Error de Base de Datos")
                                    .setMessage("Se inició sesión, pero falló la creación de tu perfil en la nube.\n\nError: ${e.localizedMessage}")
                                    .setPositiveButton("Entendido", null)
                                    .show()
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun checkAndPromptSync() {
        val user = auth.currentUser ?: return
        val syncKey = "initial_sync_done_${user.uid}"
        if (sharedPreferencesManager.getBoolean(syncKey, false)) return

        lifecycleScope.launch {
            val localFavorites = favoritesDao.getAllFavorites().first()
            val localHistory = playbackProgressDao.getAllPlaybackProgress().first()

            if (localFavorites.isNotEmpty() || localHistory.isNotEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sincronización Disponible")
                    .setMessage("Hemos detectado datos en este dispositivo. ¿Quieres subirlos a la nube ahora?")
                    .setPositiveButton("Sí, sincronizar") { _, _ ->
                        syncLocalDataToCloud(localFavorites, localHistory)
                    }
                    .setNegativeButton("No por ahora", null)
                    .show()
            } else {
                try {
                    val cloudFavs = cloudRepository.getAllCloudFavorites()
                    val cloudHist = cloudRepository.getAllCloudHistory()
                    if (cloudFavs.isNotEmpty() || cloudHist.isNotEmpty()) {
                        downloadCloudDataToLocal(cloudFavs, cloudHist)
                    } else {
                        sharedPreferencesManager.saveBoolean(syncKey, true)
                    }
                } catch (e: Exception) {
                    Log.e("SyncCheck", "Error al restaurar automáticamente")
                }
            }
        }
    }

    private fun downloadCloudDataToLocal(cloudFavs: List<com.johang.audiocinemateca.data.remote.model.CloudFavorite>, cloudHist: List<com.johang.audiocinemateca.data.remote.model.CloudHistory>) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Restaurando datos...", Toast.LENGTH_SHORT).show()
                cloudFavs.forEach { cloud ->
                    var timestamp = cloud.addedAt
                    if (timestamp in 1L..9999999999L) {
                        timestamp *= 1000
                    }
                    if (timestamp <= 0) timestamp = System.currentTimeMillis()

                    favoritesDao.insertFavorite(
                        com.johang.audiocinemateca.data.local.entities.FavoriteEntity(
                            contentId = cloud.contentId,
                            title = cloud.title,
                            contentType = cloud.contentType,
                            addedAt = timestamp
                        )
                    )
                }
                cloudHist.forEach { cloud ->
                    playbackProgressDao.insertPlaybackProgress(
                        com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity(
                            contentId = cloud.contentId,
                            contentType = cloud.contentType,
                            currentPositionMs = cloud.currentPositionMs,
                            totalDurationMs = cloud.totalDurationMs,
                            partIndex = cloud.partIndex,
                            episodeIndex = cloud.episodeIndex,
                            lastPlayedTimestamp = cloud.lastPlayedTimestamp,
                            isFinished = cloud.isFinished
                        )
                    )
                }
                sharedPreferencesManager.saveBoolean("initial_sync_done_${auth.currentUser?.uid}", true)
                Toast.makeText(requireContext(), "¡Datos restaurados!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("RestoreError", "Error al restaurar: ${e.message}")
            }
        }
    }

    private fun syncLocalDataToCloud(favorites: List<com.johang.audiocinemateca.data.local.entities.FavoriteEntity>, history: List<com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity>) {
        lifecycleScope.launch {
            try {
                favorites.forEach { cloudRepository.uploadFavorite(it) }
                history.forEach { cloudRepository.uploadHistory(it) }
                sharedPreferencesManager.saveBoolean("initial_sync_done_${auth.currentUser?.uid}", true)
                Toast.makeText(requireContext(), "¡Sincronización completada!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al subir datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}