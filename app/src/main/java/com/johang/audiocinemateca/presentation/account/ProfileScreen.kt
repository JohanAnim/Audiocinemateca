package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.LoginActivity
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.dao.FavoritesDao
import com.johang.audiocinemateca.data.local.dao.PlaybackProgressDao
import com.johang.audiocinemateca.data.repository.CloudRepository
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authCatalogRepository: AuthCatalogRepository,
    sharedPreferencesManager: com.johang.audiocinemateca.data.local.SharedPreferencesManager,
    cloudRepository: CloudRepository,
    favoritesDao: FavoritesDao,
    playbackProgressDao: PlaybackProgressDao,
    onBackClick: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onSyncStarted: () -> Unit,
    onSyncFinished: (String) -> Unit,
    onSyncError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseAuth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(firebaseAuth.currentUser) }

    // Listen to Firebase Auth state changes
    DisposableEffect(firebaseAuth) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        firebaseAuth.addAuthStateListener(listener)
        onDispose {
            firebaseAuth.removeAuthStateListener(listener)
        }
    }

    // Local Username & Stats State
    var username by remember { mutableStateOf("Invitado") }
    LaunchedEffect(currentUser) {
        username = authCatalogRepository.getStoredUsername() ?: "Invitado"
    }

    val favoritesList by favoritesDao.getAllFavorites().collectAsState(initial = emptyList())
    val historyList by playbackProgressDao.getAllPlaybackProgress().collectAsState(initial = emptyList())

    // Role state
    var userRole by remember { mutableStateOf<String?>(null) }
    var isLoadingRole by remember { mutableStateOf(false) }
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            isLoadingRole = true
            try {
                userRole = cloudRepository.getUserRole()
            } catch (e: Exception) {
                userRole = null
            } finally {
                isLoadingRole = false
            }
        } else {
            userRole = null
        }
    }

    // Dialog states
    var showAssignPasswordDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Detalles del Perfil",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Volver",
                            role = Role.Button,
                            onClickAction = onBackClick
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bienvenido/a Section
            Text(
                text = "¡Te damos la bienvenida!",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .audiocinematecaAccessibility("¡Te damos la bienvenida!")
            )

            // Username & Role Badge Row (no isTraversalGroup to avoid group reading)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = username,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .audiocinematecaAccessibility("Usuario: $username")
                )

                userRole?.let { role ->
                    Spacer(modifier = Modifier.width(12.dp))
                    val roleLabel = when (role.lowercase()) {
                        "admin" -> "ADMINISTRADOR"
                        "editor" -> "EDITOR"
                        else -> "CLIENTE"
                    }
                    val badgeColor = when (role.lowercase()) {
                        "admin" -> Color(0xFF1976D2)
                        "editor" -> Color(0xFF388E3C)
                        else -> Color(0xFF757575)
                    }
                    Surface(
                        color = badgeColor,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.audiocinematecaAccessibility("Rol de usuario: $roleLabel")
                    ) {
                        Text(
                            text = roleLabel,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (currentUser != null) {
                val user = currentUser!!
                
                val emailText = "Tu correo: ${user.email}"
                Text(
                    text = emailText,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .audiocinematecaAccessibility(emailText)
                )

                val creationTimestamp = user.metadata?.creationTimestamp
                if (creationTimestamp != null && creationTimestamp > 0) {
                    val date = Date(creationTimestamp)
                    val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val dateText = "Te uniste el: ${formatter.format(date)}"
                    Text(
                        text = dateText,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .audiocinematecaAccessibility(dateText)
                    )
                }

                // Statistics Card (no traversal/group semantics to let TalkBack read children directly)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val uniqueHistoryCount = remember(historyList) {
                            historyList.distinctBy { it.contentId }.size
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .audiocinematecaAccessibility("Historial: $uniqueHistoryCount títulos")
                        ) {
                            Text(
                                text = "🕒 Historial",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$uniqueHistoryCount títulos",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .width(1.dp)
                                .align(Alignment.CenterVertically),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .audiocinematecaAccessibility("Favoritos: ${favoritesList.size} títulos")
                        ) {
                            Text(
                                text = "❤️ Favoritos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${favoritesList.size} títulos",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Smart manual sync action
                val onSyncClick: () -> Unit = {
                    onSyncStarted()
                    scope.launch {
                        try {
                            val favs = favoritesDao.getAllFavorites().first()
                            val hist = playbackProgressDao.getAllPlaybackProgress().first()

                            val cloudFavorites = cloudRepository.getAllCloudFavorites()
                            val cloudHistory = cloudRepository.getAllCloudHistory()

                            var favsUploaded = 0
                            var favsDownloaded = 0
                            val cloudFavMap = cloudFavorites.associateBy { it.contentId }
                            val localFavMap = favs.associateBy { it.contentId }

                            // Sync favorites local -> cloud
                            favs.forEach { local ->
                                val cloud = cloudFavMap[local.contentId]
                                if (cloud == null || local.addedAt > cloud.addedAt) {
                                    cloudRepository.uploadFavorite(local)
                                    favsUploaded++
                                }
                            }

                            // Sync favorites cloud -> local
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

                            // Sync history local -> cloud
                            val cloudHistMap = cloudHistory.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }
                            val localHistMap = hist.associateBy { "${it.contentId}_${it.partIndex}_${it.episodeIndex}" }
                            var histUploaded = 0
                            var histDownloaded = 0

                            hist.forEach { local ->
                                val key = "${local.contentId}_${local.partIndex}_${local.episodeIndex}"
                                val cloud = cloudHistMap[key]
                                if (cloud == null || local.lastPlayedTimestamp > cloud.lastPlayedTimestamp) {
                                    cloudRepository.uploadHistory(local)
                                    histUploaded++
                                }
                            }

                            // Sync history cloud -> local
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

                            authCatalogRepository.getStoredUsername()?.let { userU ->
                                sharedPreferencesManager.saveBoolean("initial_sync_done_${user.uid}", true)
                            }

                            val summary = "Sincronizado: +$favsUploaded subidos, +$favsDownloaded bajados."
                            onSyncFinished(summary)
                        } catch (e: Exception) {
                            onSyncError(e.localizedMessage ?: "Error en la sincronización")
                        }
                    }
                }

                Button(
                    onClick = onSyncClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .audiocinematecaAccessibility(
                            label = "Sincronizar ahora con la nube",
                            role = Role.Button,
                            onClickAction = onSyncClick
                        ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "🔄 Sincronizar ahora con la nube",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val hasPasswordProvider = remember(user) {
                    user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
                }

                // Change or Assign Password
                val onAssignPasswordClick = { showAssignPasswordDialog = true }
                Button(
                    onClick = onAssignPasswordClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .audiocinematecaAccessibility(
                            label = if (hasPasswordProvider) "Cambiar contraseña" else "Asignar contraseña",
                            role = Role.Button,
                            onClickAction = onAssignPasswordClick
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = if (hasPasswordProvider) "Cambiar contraseña" else "Asignar contraseña",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Disconnect Account Button
                val onDisconnectClick = { showDisconnectDialog = true }
                Button(
                    onClick = onDisconnectClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .audiocinematecaAccessibility(
                            label = "Desconectar cuenta de la nube",
                            role = Role.Button,
                            onClickAction = onDisconnectClick
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(
                        text = "Desconectar cuenta",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Benefits Card (no traversal/group semantics to let TalkBack read children directly)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Ventajas de la Nube",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .audiocinematecaAccessibility("Ventajas de la Nube")
                        )

                        val benefits = listOf(
                            "• Sincronización: Tus favoritos e historial siempre contigo.",
                            "• Calificaciones: Puntúa lo que más te gusta.",
                            "• Popularidad: Mira qué títulos son tendencia.",
                            "• Feedback: Dale 'Me gusta' a capítulos y audios.",
                            "• Comunidad: Comenta y comparte."
                        )

                        benefits.forEach { benefit ->
                            Text(
                                text = benefit,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .audiocinematecaAccessibility(benefit)
                            )
                        }
                    }
                }

                // How to link account details
                Text(
                    text = "¿Cómo vincular tu cuenta?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .audiocinematecaAccessibility("¿Cómo vincular tu cuenta?")
                )

                val linkInstructions = "Es muy sencillo, solo elige una de las opciones de abajo:\n\n" +
                        "1. Conectar con Google: Recomendado. Vincula tu nombre de usuario automáticamente sin contraseñas extra.\n" +
                        "2. Conectar con Correo: Si ya tienes una cuenta de la nube creada.\n" +
                        "3. Registrarse: Crea una cuenta nueva. Por seguridad, usa una contraseña distinta a la de Audiocinemateca."
                Text(
                    text = linkInstructions,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .audiocinematecaAccessibility(linkInstructions)
                )

                val privacyDisclaimer = "Nota de privacidad: Solo guardamos tu nombre de usuario único para identificarte en la plataforma."
                Text(
                    text = privacyDisclaimer,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .audiocinematecaAccessibility(privacyDisclaimer)
                )

                // Horizontal row of sign-in buttons (without container group focus)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignInWithGoogle,
                        modifier = Modifier
                            .weight(1f)
                            .audiocinematecaAccessibility(
                                label = "Conectar con Google",
                                role = Role.Button,
                                onClickAction = onSignInWithGoogle
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Google",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToLogin,
                        modifier = Modifier
                            .weight(1f)
                            .audiocinematecaAccessibility(
                                label = "Conectar con Correo",
                                role = Role.Button,
                                onClickAction = onNavigateToLogin
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Correo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToRegister,
                        modifier = Modifier
                            .weight(1f)
                            .audiocinematecaAccessibility(
                                label = "Registrarse en la nube",
                                role = Role.Button,
                                onClickAction = onNavigateToRegister
                            ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Registro",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button (Cerrar sesión)
            val onLogoutClick = { showLogoutDialog = true }
            Button(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .audiocinematecaAccessibility(
                        label = "Cerrar sesión de Audiocinemateca",
                        role = Role.Button,
                        onClickAction = onLogoutClick
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(
                    text = "Cerrar sesión",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Dialogs implementation
    if (showAssignPasswordDialog) {
        val user = currentUser
        val hasPassword = remember(user) {
            user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } ?: false
        }

        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var showPasswordVisibility by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAssignPasswordDialog = false },
            title = {
                Text(
                    text = if (hasPassword) "Cambiar Contraseña" else "Asignar Contraseña",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasPassword) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text("Contraseña actual") },
                            visualTransformation = if (showPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswordVisibility = !showPasswordVisibility }) {
                                    Icon(
                                        imageVector = if (showPasswordVisibility) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showPasswordVisibility) "Ocultar contraseña" else "Mostrar contraseña"
                                    )
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(if (hasPassword) "Nueva contraseña" else "Elige una contraseña") },
                        visualTransformation = if (showPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showPasswordVisibility = !showPasswordVisibility }) {
                                Icon(
                                    imageVector = if (showPasswordVisibility) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPasswordVisibility) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirma la contraseña") },
                        visualTransformation = if (showPasswordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showPasswordVisibility = !showPasswordVisibility }) {
                                Icon(
                                    imageVector = if (showPasswordVisibility) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPasswordVisibility) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPassword.length < 6) {
                            Toast.makeText(context, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }
                        if (newPassword != confirmPassword) {
                            Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                            return@TextButton
                        }

                        if (user != null) {
                            if (hasPassword) {
                                val credential = EmailAuthProvider.getCredential(user.email!!, currentPassword)
                                user.reauthenticate(credential).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        user.updatePassword(newPassword).addOnCompleteListener { pTask ->
                                            if (pTask.isSuccessful) {
                                                Toast.makeText(context, "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                                                showAssignPasswordDialog = false
                                            } else {
                                                Toast.makeText(context, "Error: ${pTask.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "Autenticación fallida", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val credential = EmailAuthProvider.getCredential(user.email!!, newPassword)
                                user.linkWithCredential(credential).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(context, "Contraseña vinculada exitosamente", Toast.LENGTH_SHORT).show()
                                        showAssignPasswordDialog = false
                                    } else {
                                        Toast.makeText(context, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssignPasswordDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Desconectar de la nube", fontWeight = FontWeight.Bold) },
            text = { Text("¿Quieres desconectarte de la sincronización en la nube? Seguirás conectado a tu cuenta local de Audiocinemateca.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        firebaseAuth.signOut()
                        scope.launch {
                            try { playbackProgressDao.deleteAllPlaybackProgress() } catch (e: Exception) {}
                            try { favoritesDao.deleteAllFavorites() } catch (e: Exception) {}
                        }
                        showDisconnectDialog = false
                        Toast.makeText(context, "Te has desconectado de la nube", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Sí, desconectar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("No")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar Sesión", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que quieres cerrar la sesión de Audiocinemateca por completo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            firebaseAuth.signOut()
                            authCatalogRepository.logout()
                            try { playbackProgressDao.deleteAllPlaybackProgress() } catch (e: Exception) {}
                            try { favoritesDao.deleteAllFavorites() } catch (e: Exception) {}
                            val intent = Intent(context, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Sí, cerrar sesión")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}
