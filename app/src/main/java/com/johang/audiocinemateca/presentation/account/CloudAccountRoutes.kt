package com.johang.audiocinemateca.presentation.account

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.local.entities.FavoriteEntity
import com.johang.audiocinemateca.data.local.entities.PlaybackProgressEntity
import com.johang.audiocinemateca.data.remote.model.CloudFavorite
import com.johang.audiocinemateca.data.remote.model.CloudHistory
import com.johang.audiocinemateca.data.repository.CloudRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

@Composable
fun ProfileRoute(
    authCatalogRepository: AuthCatalogRepository,
    sharedPreferencesManager: SharedPreferencesManager,
    cloudRepository: CloudRepository,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onRequestAppUpdate: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showUploadSyncPrompt by remember {
        mutableStateOf<Pair<List<FavoriteEntity>, List<PlaybackProgressEntity>>?>(null)
    }

    fun checkAndPromptSync() {
        val user = firebaseAuth.currentUser ?: return
        val syncKey = "initial_sync_done_${user.uid}"
        if (sharedPreferencesManager.getBoolean(syncKey, false)) return

        scope.launch {
            val localFavorites = favoritesDao.getAllFavorites().first()
            val localHistory = playbackProgressDao.getAllPlaybackProgress().first()

            if (localFavorites.isNotEmpty() || localHistory.isNotEmpty()) {
                showUploadSyncPrompt = localFavorites to localHistory
            } else {
                try {
                    val cloudFavs = cloudRepository.getAllCloudFavorites()
                    val cloudHist = cloudRepository.getAllCloudHistory()
                    if (cloudFavs.isNotEmpty() || cloudHist.isNotEmpty()) {
                        downloadCloudDataToLocal(
                            context = context,
                            scope = scope,
                            cloudFavs = cloudFavs,
                            cloudHist = cloudHist,
                            favoritesDao = favoritesDao,
                            playbackProgressDao = playbackProgressDao,
                            sharedPreferencesManager = sharedPreferencesManager,
                            uid = user.uid,
                            purgeExisting = false
                        )
                    } else {
                        sharedPreferencesManager.saveBoolean(syncKey, true)
                    }
                } catch (e: Exception) {
                    cloudRepository.reportCloudError(e)
                    Log.e("SyncCheck", "Error al comprobar datos en la nube")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (firebaseAuth.currentUser != null) {
            checkAndPromptSync()
        }
    }

    ProfileScreen(
        authCatalogRepository = authCatalogRepository,
        sharedPreferencesManager = sharedPreferencesManager,
        cloudRepository = cloudRepository,
        favoritesDao = favoritesDao,
        playbackProgressDao = playbackProgressDao,
        onBackClick = onBackClick,
        onRequestAppUpdate = onRequestAppUpdate,
        onSignInWithGoogle = {
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setNonce(hashedNonce)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            scope.launch {
                try {
                    val result = credentialManager.getCredential(context = context, request = request)
                    val credential = result.credential
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        Toast.makeText(context, "Verificando credenciales...", Toast.LENGTH_SHORT).show()

                        firebaseAuth.signInWithCredential(firebaseCredential)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    scope.launch {
                                        try {
                                            cloudRepository.syncUserProfile()
                                            val localUsername = authCatalogRepository.getStoredUsername()
                                            val user = firebaseAuth.currentUser
                                            if (user != null && localUsername != null) {
                                                val profileUpdates = UserProfileChangeRequest.Builder()
                                                    .setDisplayName(localUsername)
                                                    .build()
                                                user.updateProfile(profileUpdates)
                                            }
                                            checkAndPromptSync()
                                            dialogTitle = "¡Bienvenido a la Nube!"
                                            dialogMessage = "Tu cuenta ha sido vinculada y tu base de datos está lista."
                                        } catch (e: Exception) {
                                            Log.e("GoogleLogin", "Error iniciando DB: ${e.message}")
                                            dialogTitle = "Error de Inicialización"
                                            dialogMessage = "Se inició sesión, pero no se pudo crear tu espacio en la base de datos.\n\nError: ${e.localizedMessage}\n\nEs posible que algunas funciones de guardado fallen."
                                        }
                                    }
                                } else {
                                    val error = task.exception?.localizedMessage ?: "Fallo de autenticación"
                                    Toast.makeText(context, "Fallo en la nube: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "Error al obtener credenciales: ${e.message}")
                    Toast.makeText(context, "Error al conectar con Google: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onNavigateToLogin = onNavigateToLogin,
        onNavigateToRegister = onNavigateToRegister,
        onSyncStarted = {
            Toast.makeText(context, "Iniciando Sincronización Inteligente...", Toast.LENGTH_SHORT).show()
        },
        onSyncFinished = { summary ->
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
        },
        onSyncError = { error ->
            Toast.makeText(context, "Fallo en la sincronización: $error", Toast.LENGTH_SHORT).show()
        }
    )

    if (dialogTitle != null && dialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                dialogTitle = null
                dialogMessage = null
            },
            title = { Text(dialogTitle ?: "") },
            text = { Text(dialogMessage ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogTitle = null
                        dialogMessage = null
                    }
                ) {
                    Text("Genial")
                }
            }
        )
    }

    showUploadSyncPrompt?.let { (localFavorites, localHistory) ->
        AlertDialog(
            onDismissRequest = { showUploadSyncPrompt = null },
            title = { Text("Sincronización de Subida") },
            text = { Text("Hemos detectado datos en este dispositivo. ¿Quieres subirlos a la nube para tenerlos siempre seguros?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUploadSyncPrompt = null
                        smartSyncLocalDataToCloud(
                            context = context,
                            scope = scope,
                            firebaseAuth = firebaseAuth,
                            cloudRepository = cloudRepository,
                            favoritesDao = favoritesDao,
                            playbackProgressDao = playbackProgressDao,
                            sharedPreferencesManager = sharedPreferencesManager,
                            localFavorites = localFavorites,
                            localHistory = localHistory
                        )
                    }
                ) {
                    Text("Sí, subir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadSyncPrompt = null }) {
                    Text("No por ahora")
                }
            }
        )
    }
}

@Composable
fun LoginCloudRoute(
    cloudRepository: CloudRepository,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    sharedPreferencesManager: SharedPreferencesManager,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }
    var showSyncPrompt by remember {
        mutableStateOf<Pair<List<FavoriteEntity>, List<PlaybackProgressEntity>>?>(null)
    }

    fun checkAndPromptSync() {
        val user = auth.currentUser ?: return
        val syncKey = "initial_sync_done_${user.uid}"
        if (sharedPreferencesManager.getBoolean(syncKey, false)) {
            showSuccessDialog = true
            return
        }

        scope.launch {
            val localFavorites = favoritesDao.getAllFavorites().first()
            val localHistory = playbackProgressDao.getAllPlaybackProgress().first()

            if (localFavorites.isNotEmpty() || localHistory.isNotEmpty()) {
                showSyncPrompt = localFavorites to localHistory
            } else {
                try {
                    val cloudFavs = cloudRepository.getAllCloudFavorites()
                    val cloudHist = cloudRepository.getAllCloudHistory()
                    if (cloudFavs.isNotEmpty() || cloudHist.isNotEmpty()) {
                        downloadCloudDataToLocal(
                            context = context,
                            scope = scope,
                            cloudFavs = cloudFavs,
                            cloudHist = cloudHist,
                            favoritesDao = favoritesDao,
                            playbackProgressDao = playbackProgressDao,
                            sharedPreferencesManager = sharedPreferencesManager,
                            uid = user.uid,
                            purgeExisting = true
                        )
                    } else {
                        sharedPreferencesManager.saveBoolean(syncKey, true)
                    }
                } catch (e: Exception) {
                    Log.e("SyncCheck", "Error al restaurar automáticamente")
                }
                showSuccessDialog = true
            }
        }
    }

    LoginCloudScreen(
        isLoading = isLoading,
        onBackClick = onBackClick,
        onLoginClick = { email, password ->
            isLoading = true
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        scope.launch {
                            try {
                                cloudRepository.syncUserProfile()
                                checkAndPromptSync()
                            } catch (e: Exception) {
                                Log.e("LoginCloud", "Error creando perfil DB: ${e.message}")
                                errorDialogMessage = "Se inició sesión, pero falló la creación de tu perfil en la nube.\n\nError: ${e.localizedMessage}"
                            } finally {
                                isLoading = false
                            }
                        }
                    } else {
                        isLoading = false
                        Toast.makeText(
                            context,
                            "Error: ${task.exception?.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    )

    showSyncPrompt?.let { (localFavorites, localHistory) ->
        AlertDialog(
            onDismissRequest = {
                showSyncPrompt = null
                showSuccessDialog = true
            },
            title = { Text("Sincronización Disponible") },
            text = { Text("Hemos detectado datos en este dispositivo. ¿Quieres subirlos a la nube ahora?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSyncPrompt = null
                        scope.launch {
                            try {
                                localFavorites.forEach { cloudRepository.uploadFavorite(it) }
                                localHistory.forEach { cloudRepository.uploadHistory(it) }
                                sharedPreferencesManager.saveBoolean("initial_sync_done_${auth.currentUser?.uid}", true)
                                Toast.makeText(context, "¡Sincronización completada!", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Error al subir datos", Toast.LENGTH_SHORT).show()
                            }
                            showSuccessDialog = true
                        }
                    }
                ) {
                    Text("Sí, sincronizar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSyncPrompt = null
                        showSuccessDialog = true
                    }
                ) {
                    Text("No por ahora")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBackClick()
            },
            title = { Text("Conexión Exitosa") },
            text = { Text("Has conectado tu cuenta con la nube y tu base de datos está lista.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Genial")
                }
            }
        )
    }

    errorDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorDialogMessage = null },
            title = { Text("Error de Base de Datos") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorDialogMessage = null }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@Composable
fun RegisterCloudRoute(
    authCatalogRepository: AuthCatalogRepository,
    cloudRepository: CloudRepository,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }

    var isLoading by remember { mutableStateOf(false) }
    var showCollisionDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    RegisterCloudScreen(
        isLoading = isLoading,
        onBackClick = onBackClick,
        onRegisterClick = { email, password ->
            isLoading = true
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        scope.launch {
                            val username = authCatalogRepository.getStoredUsername() ?: "Usuario"
                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build()
                            auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                                scope.launch {
                                    try {
                                        cloudRepository.syncUserProfile()
                                    } catch (_: Exception) {}
                                    isLoading = false
                                    showSuccessDialog = true
                                }
                            }
                        }
                    } else {
                        isLoading = false
                        val exception = task.exception
                        if (exception is FirebaseAuthUserCollisionException) {
                            showCollisionDialog = true
                        } else {
                            Toast.makeText(
                                context,
                                "Error: ${exception?.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
        }
    )

    if (showCollisionDialog) {
        AlertDialog(
            onDismissRequest = {
                showCollisionDialog = false
                onBackClick()
            },
            title = { Text("Cuenta ya registrada") },
            text = {
                Text("Este correo electrónico ya está vinculado a una cuenta de Audiocinemateca (posiblemente a través de Google).\n\nPor favor, inicia sesión con Google primero y luego podrás asignar una contraseña desde tu perfil.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCollisionDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onBackClick()
            },
            title = { Text("Cuenta Creada") },
            text = { Text("¡Bienvenido! Tu cuenta en la nube ha sido creada y vinculada con éxito.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        onBackClick()
                    }
                ) {
                    Text("Excelente")
                }
            }
        )
    }
}

private fun downloadCloudDataToLocal(
    context: Context,
    scope: CoroutineScope,
    cloudFavs: List<CloudFavorite>,
    cloudHist: List<CloudHistory>,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    sharedPreferencesManager: SharedPreferencesManager,
    uid: String?,
    purgeExisting: Boolean
) {
    scope.launch {
        try {
            Toast.makeText(context, "Restaurando tus datos...", Toast.LENGTH_SHORT).show()
            if (purgeExisting) {
                playbackProgressDao.deleteAllPlaybackProgress()
                favoritesDao.deleteAllFavorites()
            }

            cloudFavs.forEach { cloud ->
                var timestamp = cloud.addedAt
                if (timestamp in 1L..9999999999L) {
                    timestamp *= 1000
                }
                if (timestamp <= 0) timestamp = System.currentTimeMillis()

                favoritesDao.insertFavorite(
                    FavoriteEntity(
                        contentId = cloud.contentId,
                        title = cloud.title,
                        contentType = cloud.contentType,
                        addedAt = timestamp
                    )
                )
            }

            cloudHist.forEach { cloud ->
                playbackProgressDao.insertPlaybackProgress(
                    PlaybackProgressEntity(
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

            if (uid != null) {
                sharedPreferencesManager.saveBoolean("initial_sync_done_$uid", true)
            }
            Toast.makeText(context, "¡Tus datos han sido restaurados!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("RestoreError", "Error al restaurar: ${e.message}")
            Toast.makeText(context, "Error al descargar datos", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun smartSyncLocalDataToCloud(
    context: Context,
    scope: CoroutineScope,
    firebaseAuth: FirebaseAuth,
    cloudRepository: CloudRepository,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    sharedPreferencesManager: SharedPreferencesManager,
    localFavorites: List<FavoriteEntity>,
    localHistory: List<PlaybackProgressEntity>
) {
    scope.launch {
        try {
            val currentUser = firebaseAuth.currentUser ?: return@launch
            Toast.makeText(context, "Iniciando Sincronización Inteligente...", Toast.LENGTH_SHORT).show()

            val cloudFavorites = cloudRepository.getAllCloudFavorites()
            val cloudHistory = cloudRepository.getAllCloudHistory()

            var favsUploaded = 0
            var favsDownloaded = 0

            val cloudFavMap = cloudFavorites.associateBy { it.contentId }
            val localFavMap = localFavorites.associateBy { it.contentId }

            localFavorites.forEach { local ->
                val cloud = cloudFavMap[local.contentId]
                if (cloud == null || local.addedAt > cloud.addedAt) {
                    cloudRepository.uploadFavorite(local)
                    favsUploaded++
                }
            }

            cloudFavorites.forEach { cloud ->
                val local = localFavMap[cloud.contentId]
                if (local == null || cloud.addedAt > local.addedAt) {
                    favoritesDao.insertFavorite(
                        FavoriteEntity(
                            contentId = cloud.contentId,
                            title = cloud.title,
                            contentType = cloud.contentType,
                            addedAt = cloud.addedAt
                        )
                    )
                    favsDownloaded++
                }
            }

            val cloudHistMap = cloudHistory.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }
            val localHistMap = localHistory.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }

            localHistory.forEach { local ->
                val key = "${local.contentId}_${local.partIndex}_${local.episodeIndex}"
                val cloud = cloudHistMap[key]
                if (cloud == null || local.lastPlayedTimestamp > cloud.lastPlayedTimestamp) {
                    cloudRepository.uploadHistory(local)
                }
            }

            cloudHistory.forEach { cloud ->
                val key = "${cloud.contentId}_${cloud.partIndex}_${cloud.episodeIndex}"
                val local = localHistMap[key]
                if (local == null || cloud.lastPlayedTimestamp > local.lastPlayedTimestamp) {
                    playbackProgressDao.insertPlaybackProgress(
                        PlaybackProgressEntity(
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
            }

            sharedPreferencesManager.saveBoolean("initial_sync_done_${currentUser.uid}", true)
            val summary = "Sincronizado: +$favsUploaded subidos, +$favsDownloaded bajados."
            Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            cloudRepository.reportCloudError(e)
            Log.e("SyncError", "Error en la sincronización", e)
            Toast.makeText(context, cloudRepository.getFriendlyErrorMessage(e), Toast.LENGTH_LONG).show()
        }
    }
}
