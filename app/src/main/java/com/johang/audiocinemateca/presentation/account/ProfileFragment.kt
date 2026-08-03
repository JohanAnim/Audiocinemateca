package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.presentation.theme.AudiocinematecaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    @Inject
    lateinit var authCatalogRepository: AuthCatalogRepository

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var cloudRepository: CloudRepository

    @Inject
    lateinit var favoritesDao: FavoritesDao

    @Inject
    lateinit var playbackProgressDao: PlaybackProgressDao

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialManager = CredentialManager.create(requireContext())
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
                    ProfileScreen(
                        authCatalogRepository = authCatalogRepository,
                        sharedPreferencesManager = sharedPreferencesManager,
                        cloudRepository = cloudRepository,
                        favoritesDao = favoritesDao,
                        playbackProgressDao = playbackProgressDao,
                        onBackClick = { findNavController().navigateUp() },
                        onSignInWithGoogle = { signInWithGoogle() },
                        onNavigateToLogin = {
                            findNavController().navigate(R.id.action_profileFragment_to_loginCloudFragment)
                        },
                        onNavigateToRegister = {
                            findNavController().navigate(R.id.action_profileFragment_to_registerCloudFragment)
                        },
                        onSyncStarted = {
                            Toast.makeText(requireContext(), "Iniciando Sincronización Inteligente...", Toast.LENGTH_SHORT).show()
                        },
                        onSyncFinished = { summary ->
                            Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show()
                        },
                        onSyncError = { error ->
                            Toast.makeText(requireContext(), "Fallo en la sincronización: $error", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // COMPROBACIÓN AUTOMÁTICA AL ENTRAR
        if (firebaseAuth.currentUser != null) {
            checkAndPromptSync()
        }
    }

    private fun signInWithGoogle() {
        val rawNonce = java.util.UUID.randomUUID().toString()
        val bytes = rawNonce.toByteArray()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

        val webClientId = getString(R.string.default_web_client_id)

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .setAutoSelectEnabled(false)
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    context = requireContext(),
                    request = request
                )
                handleSignIn(result)
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Error al obtener credenciales: ${e.message}")
                Toast.makeText(requireContext(), "Error al conectar con Google: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                firebaseAuthWithGoogle(idToken)
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Error al procesar el token de Google", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        Toast.makeText(requireContext(), "Verificando credenciales...", Toast.LENGTH_SHORT).show()

        firebaseAuth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    lifecycleScope.launch {
                        try {
                            // INTENTO DE CREACIÓN/VERIFICACIÓN DE PERFIL EN BASE DE DATOS
                            cloudRepository.syncUserProfile()
                            
                            // Si pasa de aquí, todo está correcto
                            syncUsernameWithFirebase()
                            checkAndPromptSync()
                            
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("¡Bienvenido a la Nube!")
                                .setMessage("Tu cuenta ha sido vinculada y tu base de datos está lista.")
                                .setPositiveButton("Genial", null)
                                .show()

                        } catch (e: Exception) {
                            Log.e("GoogleLogin", "Error iniciando DB: ${e.message}")
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Error de Inicialización")
                                .setMessage("Se inició sesión, pero no se pudo crear tu espacio en la base de datos.\n\nError: ${e.localizedMessage}\n\nEs posible que algunas funciones de guardado fallen.")
                                .setPositiveButton("Entendido", null)
                                .show()
                        }
                    }
                } else {
                    val error = task.exception?.localizedMessage ?: "Fallo de autenticación"
                    Toast.makeText(requireContext(), "Fallo en la nube: $error", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkAndPromptSync() {
        val user = firebaseAuth.currentUser ?: return
        val syncKey = "initial_sync_done_${user.uid}"
        if (sharedPreferencesManager.getBoolean(syncKey, false)) return

        lifecycleScope.launch {
            val localFavorites = favoritesDao.getAllFavorites().first()
            val localHistory = playbackProgressDao.getAllPlaybackProgress().first()

            if (localFavorites.isNotEmpty() || localHistory.isNotEmpty()) {
                // CASO A: Hay datos locales -> Ofrecer SUBIR a la nube
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Sincronización de Subida")
                    .setMessage("Hemos detectado datos en este dispositivo. ¿Quieres subirlos a la nube para tenerlos siempre seguros?")
                    .setPositiveButton("Sí, subir") { _, _ ->
                        syncLocalDataToCloud(localFavorites, localHistory)
                    }
                    .setNegativeButton("No por ahora", null)
                    .show()
            } else {
                // CASO B: El móvil está vacío -> BAJAR de la nube directamente (Modo Netflix)
                try {
                    val cloudFavs = cloudRepository.getAllCloudFavorites()
                    val cloudHist = cloudRepository.getAllCloudHistory()

                    if (cloudFavs.isNotEmpty() || cloudHist.isNotEmpty()) {
                        // Restauración automática
                        downloadCloudDataToLocal(cloudFavs, cloudHist)
                    } else {
                        // No hay nada en ningún lado, marcamos como sincronizado
                        sharedPreferencesManager.saveBoolean(syncKey, true)
                    }
                } catch (e: Exception) {
                    Log.e("SyncCheck", "Error al comprobar datos en la nube")
                }
            }
        }
    }

    private fun downloadCloudDataToLocal(cloudFavs: List<com.johang.audiocinemateca.data.remote.model.CloudFavorite>, cloudHist: List<com.johang.audiocinemateca.data.remote.model.CloudHistory>) {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "Restaurando tus datos...", Toast.LENGTH_SHORT).show()
                
                // Convertir y guardar Favoritos
                cloudFavs.forEach { cloud ->
                    // REPARACIÓN DE FECHA: Si viene en segundos (10 dígitos) convertir a milisegundos
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

                // Convertir y guardar Historial
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

                val user = firebaseAuth.currentUser
                if (user != null) {
                    sharedPreferencesManager.saveBoolean("initial_sync_done_${user.uid}", true)
                }

                Toast.makeText(requireContext(), "¡Tus datos han sido restaurados!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("RestoreError", "Error al restaurar: ${e.message}")
                Toast.makeText(requireContext(), "Error al descargar datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncLocalDataToCloud(
        localFavorites: List<com.johang.audiocinemateca.data.local.entities.FavoriteEntity>,
        localHistory: List<com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity>
    ) {
        lifecycleScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser == null) return@launch

                Toast.makeText(requireContext(), "Iniciando Sincronización Inteligente...", Toast.LENGTH_SHORT).show()
                
                // 1. Obtener datos de la nube (ya vienen reparados por el repositorio)
                val cloudFavorites = cloudRepository.getAllCloudFavorites()
                val cloudHistory = cloudRepository.getAllCloudHistory()

                // --- SMART SYNC: FAVORITOS ---
                var favsUploaded = 0
                var favsDownloaded = 0
                
                val cloudFavMap = cloudFavorites.associateBy { it.contentId }
                val localFavMap = localFavorites.associateBy { it.contentId }

                // De Local a Nube
                localFavorites.forEach { local ->
                    val cloud = cloudFavMap[local.contentId]
                    if (cloud == null || local.addedAt > cloud.addedAt) {
                        cloudRepository.uploadFavorite(local)
                        favsUploaded++
                    }
                }
                
                // De Nube a Local
                cloudFavorites.forEach { cloud ->
                    val local = localFavMap[cloud.contentId]
                    if (local == null || cloud.addedAt > local.addedAt) {
                        favoritesDao.insertFavorite(
                            com.johang.audiocinemateca.data.local.entities.FavoriteEntity(
                                contentId = cloud.contentId,
                                title = cloud.title,
                                contentType = cloud.contentType,
                                addedAt = cloud.addedAt
                            )
                        )
                        favsDownloaded++
                    }
                }

                // --- SMART SYNC: HISTORIAL ---
                val cloudHistMap = cloudHistory.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }
                val localHistMap = localHistory.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }
                
                var histUploaded = 0
                var histDownloaded = 0

                // De Local a Nube
                localHistory.forEach { local ->
                    val key = "${local.contentId}_${local.partIndex}_${local.episodeIndex}"
                    val cloud = cloudHistMap[key]

                    if (cloud == null || local.lastPlayedTimestamp > cloud.lastPlayedTimestamp) {
                        cloudRepository.uploadHistory(local)
                        histUploaded++
                    }
                }

                // De Nube a Local
                cloudHistory.forEach { cloud ->
                    val key = "${cloud.contentId}_${cloud.partIndex}_${cloud.episodeIndex}"
                    val local = localHistMap[key]

                    if (local == null || cloud.lastPlayedTimestamp > local.lastPlayedTimestamp) {
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
                        histDownloaded++
                    }
                }

                sharedPreferencesManager.saveBoolean("initial_sync_done_${currentUser.uid}", true)
                
                val summary = "Sincronizado: +$favsUploaded subidos, +$favsDownloaded bajados."
                Toast.makeText(requireContext(), summary, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("SyncError", "Error en la sincronización", e)
                Toast.makeText(requireContext(), "Fallo en la sincronización: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncUsernameWithFirebase() {
        lifecycleScope.launch {
            val localUsername = authCatalogRepository.getStoredUsername()
            val user = firebaseAuth.currentUser
            if (user != null && localUsername != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(localUsername)
                    .build()
                
                user.updateProfile(profileUpdates).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("FirebaseSync", "Nombre de usuario sincronizado: $localUsername")
                    }
                }
            }
        }
    }
}