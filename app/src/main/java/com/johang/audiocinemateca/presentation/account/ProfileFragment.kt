package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.CheckBox
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
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

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
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
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        loadUserData()
        
        // COMPROBACIÓN AUTOMÁTICA AL ENTRAR
        if (firebaseAuth.currentUser != null) {
            checkAndPromptSync()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Fila de Conexión
        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }
        binding.btnEmail.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_loginCloudFragment)
        }
        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_registerCloudFragment)
        }

        // Botón Sincronización Manual
        binding.btnManualSync.setOnClickListener {
            lifecycleScope.launch {
                val favs = favoritesDao.getAllFavorites().first()
                val hist = playbackProgressDao.getAllPlaybackProgress().first()
                syncLocalDataToCloud(favs, hist)
            }
        }

        // Botón Asignar/Cambiar Contraseña
        binding.btnAssignPassword.setOnClickListener {
            showAssignPasswordDialog()
        }

        // Botón Desconectar de Firebase (Solo de la nube)
        binding.btnFirebaseDisconnect.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Desconectar de la nube")
                .setMessage("¿Quieres desconectarte de la sincronización en la nube? Seguirás conectado a tu cuenta local de Audiocinemateca.")
                .setNegativeButton("No", null)
                .setPositiveButton("Sí") { _, _ ->
                    firebaseAuth.signOut()
                    updateFirebaseUI()
                    Toast.makeText(requireContext(), "Te has desconectado de la nube", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        // Botón Cerrar Sesión (Completo)
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar Sesión")
                .setMessage("¿Estás seguro de que quieres cerrar la sesión de Audiocinemateca por completo?")
                .setNegativeButton("No", null)
                .setPositiveButton("Sí") { _, _ ->
                    lifecycleScope.launch {
                        firebaseAuth.signOut()
                        authCatalogRepository.logout()
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                }
                .show()
        }

        updateFirebaseUI()
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
                            updateFirebaseUI()
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
                updateFirebaseUI()
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
                updateFirebaseUI()
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

    private fun showAssignPasswordDialog() {
        val user = firebaseAuth.currentUser
        val hasPassword = user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } ?: false
        
        val context = requireContext()
        val title = if (hasPassword) "Cambiar Contraseña" else "Asignar Contraseña"
        
        val currentPasswordInput = EditText(context).apply {
            hint = "Contraseña actual"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            visibility = if (hasPassword) View.VISIBLE else View.GONE
        }
        val passwordInput = EditText(context).apply {
            hint = if (hasPassword) "Nueva contraseña" else "Elige una contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmInput = EditText(context).apply {
            hint = "Confirma la contraseña"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val showPasswordCheckbox = CheckBox(context).apply {
            text = "Mostrar contraseñas"
            setOnCheckedChangeListener { _, isChecked ->
                val transformation = if (isChecked) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
                currentPasswordInput.transformationMethod = transformation
                passwordInput.transformationMethod = transformation
                confirmInput.transformationMethod = transformation
            }
        }
        
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 10)
            addView(currentPasswordInput)
            addView(passwordInput)
            addView(confirmInput)
            addView(showPasswordCheckbox)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("Guardar") { _, _ ->
                val pass1 = passwordInput.text.toString()
                val pass2 = confirmInput.text.toString()
                if (pass1.length >= 6 && pass1 == pass2) {
                    if (hasPassword) {
                        val credential = EmailAuthProvider.getCredential(user?.email!!, currentPasswordInput.text.toString())
                        user.reauthenticate(credential).addOnCompleteListener { 
                            if (it.isSuccessful) user.updatePassword(pass1)
                        }
                    } else {
                        linkEmailPassword(pass1)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun linkEmailPassword(password: String) {
        val user = firebaseAuth.currentUser
        val email = user?.email ?: return
        val credential = EmailAuthProvider.getCredential(email, password)
        user.linkWithCredential(credential).addOnCompleteListener {
            if (it.isSuccessful) updateFirebaseUI()
        }
    }

    private fun updateFirebaseUI() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            binding.layoutNotLogged.visibility = View.VISIBLE
            binding.tvEmail.visibility = View.GONE
            binding.tvCreationDate.visibility = View.GONE
            binding.layoutStats.visibility = View.GONE
            binding.btnManualSync.visibility = View.GONE
            binding.btnFirebaseDisconnect.visibility = View.GONE
            binding.btnAssignPassword.visibility = View.GONE
            binding.spacerUserInfo.visibility = View.VISIBLE
            binding.tvUserRole.visibility = View.GONE
        } else {
            binding.layoutNotLogged.visibility = View.GONE
            binding.tvEmail.text = "Tu correo: ${user.email}"
            binding.tvEmail.visibility = View.VISIBLE
            
            val creationTimestamp = user.metadata?.creationTimestamp
            if (creationTimestamp != null && creationTimestamp > 0) {
                val date = Date(creationTimestamp)
                val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                binding.tvCreationDate.text = "Te uniste el: ${formatter.format(date)}"
                binding.tvCreationDate.visibility = View.VISIBLE
            }

            lifecycleScope.launch {
                binding.layoutStats.visibility = View.VISIBLE
                
                // Observar Favoritos en tiempo real
                launch {
                    favoritesDao.getAllFavorites().collect { list ->
                        binding.tvCountFavorites.text = "${list.size} títulos"
                    }
                }

                // Observar Historial en tiempo real
                launch {
                    playbackProgressDao.getAllPlaybackProgress().collect { list ->
                        // Agrupamos para contar títulos únicos igual que en la lista
                        val uniqueCount = list.distinctBy { it.contentId }.size
                        binding.tvCountHistory.text = "$uniqueCount títulos"
                    }
                }

                try {
                    // CARGAR ROL (Esto sí puede ser una sola vez o cuando cambie)
                    val role = cloudRepository.getUserRole()
                    binding.tvUserRole.text = when(role) {
                        "admin" -> "ADMINISTRADOR"
                        "editor" -> "EDITOR"
                        else -> "CLIENTE"
                    }
                    binding.tvUserRole.visibility = View.VISIBLE
                    
                    val colorRes = if (role == "admin") android.R.color.holo_blue_dark else android.R.color.darker_gray
                    binding.tvUserRole.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), colorRes)
                    )
                } catch (e: Exception) {
                    Log.e("Profile", "Error cargando rol")
                }
                binding.btnManualSync.visibility = View.VISIBLE
            }

            val hasPasswordProvider = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
            binding.btnAssignPassword.text = if (hasPasswordProvider) "Cambiar contraseña" else "Asignar contraseña"
            binding.btnAssignPassword.visibility = View.VISIBLE
            binding.btnFirebaseDisconnect.text = "Desconectar cuenta"
            binding.btnFirebaseDisconnect.visibility = View.VISIBLE
            binding.spacerUserInfo.visibility = View.GONE
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val username = authCatalogRepository.getStoredUsername()
            binding.tvUsername.text = username ?: "Invitado"
            binding.tvWelcomeTitle.text = "¡Hola, ${username ?: "amigo"}!"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}