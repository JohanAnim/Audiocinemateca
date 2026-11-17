package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.model.UpdateChannels
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import com.johang.audiocinemateca.util.initialAccessibilityFocus
import com.johang.audiocinemateca.util.parseMarkdownToAnnotatedString
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    authCatalogRepository: AuthCatalogRepository,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDocumentation: () -> Unit,
    onNavigateToWebView: (String) -> Unit,
    onShowDonation: () -> Unit,
    onShowUpdateProgress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseAuth = FirebaseAuth.getInstance()
    val updateState by viewModel.updateState.collectAsState()
    val updateChannel by viewModel.updateChannel.collectAsState()
    val channelLabel = remember(updateChannel) { UpdateChannels.displayName(updateChannel) }
    
    var username by remember { mutableStateOf("Cargando...") }
    var catalogVersionText by remember { mutableStateOf("Cargando...") }
    var appVersion by remember { mutableStateOf("N/A") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showNovedadesDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var showCatalogProgressDialog by remember { mutableStateOf(false) }
    var catalogProgress by remember { mutableIntStateOf(0) }
    var activeDialog by remember { mutableStateOf<AccountDialogSpec?>(null) }

    // Recuerda el último botón accionado en Cuenta (ej. "account_settings" al ir a Configuración)
    // para restaurar el foco exactamente en ese botón al regresar con Volver.
    var focusedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    val isAdmin = firebaseAuth.currentUser?.email?.lowercase() == "gutierrezjohanantonio@gmail.com"

    LaunchedEffect(Unit) {
        val storedUsername = authCatalogRepository.getStoredUsername()
        username = storedUsername ?: "Invitado"
        
        val catalogVersion = authCatalogRepository.getCatalogVersion()
        catalogVersionText = catalogVersion?.let { 
            android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString() 
        } ?: "N/A"
        
        try {
            appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.0.0"
            viewModel.checkForUpdates(appVersion)
        } catch (e: Exception) {
            appVersion = "3.0.0"
        }
    }

    LaunchedEffect(updateState) {
        if (updateState is UpdateCheckResult.UpdateAvailable || updateState is UpdateCheckResult.NoUpdateAvailable) {
            val show = updateState is UpdateCheckResult.UpdateAvailable
            val intent = Intent(if (show) MainActivity.ACTION_SHOW_UPDATE_INDICATOR else MainActivity.ACTION_HIDE_UPDATE_INDICATOR)
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }

    val isHeaderTargetFocused = focusedItemId == "account_profile" || focusedItemId == "account_settings"
    val isBodyTargetFocused = focusedItemId == "account_web"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .semantics { isTraversalGroup = true }
    ) {
        // Header Modernizado
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = if (isBodyTargetFocused) 1f else -1f
                }
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Definimos el aviso de mejora de cuenta
                val isGuest = firebaseAuth.currentUser == null
                val noticeText = if (isGuest) {
                    "¡La mejora de tu cuenta ya está disponible! Toca arriba en tu nombre para ver más información."
                } else {
                    "Cuenta conectada con la nube"
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // BOTÓN DE PERFIL (Solo el nombre como estaba antes)
                    val isProfileFocused = focusedItemId == "account_profile"
                    val onProfileClick = {
                        focusedItemId = "account_profile"
                        onNavigateToProfile()
                    }
                    Box(
                        modifier = Modifier
                            .clickable(onClick = onProfileClick)
                            .audiocinematecaAccessibility(
                                label = "$username, conectado actualmente. Toca aquí para ver tu perfil",
                                role = Role.Button,
                                onClickAction = onProfileClick,
                                testTag = "account_profile",
                                traversalIndex = if (isProfileFocused) -1f else 0f
                            )
                            .then(
                                if (isProfileFocused) {
                                    Modifier.initialAccessibilityFocus(tag = "account_profile")
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Text(
                            text = username,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // TEXTO DE AVISO APARTE (Como pediste)
                    Text(
                        text = noticeText,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .audiocinematecaAccessibility(label = noticeText)
                    )
                }

                // Botón Compartir
                val shareAction = {
                    val message = "¡Mira esta App increíble para disfrutar del cine accesible! 🎬🍿\n\nAudiocinemateca te permite escuchar películas, series y documentales con audiodescripción de alta calidad.\n\nDescarga la última versión aquí:\nhttps://github.com/JohanAnim/Audiocinemateca/releases/latest"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir Audiocinemateca vía"))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = shareAction)
                        .clearAndSetSemantics {
                            contentDescription = "Compartir aplicación"
                            role = Role.Button
                            onClick {
                                shareAction()
                                true
                            }
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Botón Configuración
                val isSettingsFocused = focusedItemId == "account_settings"
                val onSettingsClick = {
                    focusedItemId = "account_settings"
                    onNavigateToSettings()
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = onSettingsClick)
                        .clearAndSetSemantics {
                            testTag = "account_settings"
                            traversalIndex = if (isSettingsFocused) -1f else 0f
                            contentDescription = "Configuración"
                            role = Role.Button
                            onClick {
                                onSettingsClick()
                                true
                            }
                        }
                        .then(
                            if (isSettingsFocused) {
                                Modifier.initialAccessibilityFocus(tag = "account_settings")
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Cuerpo de la pantalla
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .semantics {
                    isTraversalGroup = true
                    traversalIndex = if (isBodyTargetFocused) -1f else 0f
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val currentUpdateInfo = when (val state = updateState) {
                is UpdateCheckResult.UpdateAvailable -> state.updateInfo
                is UpdateCheckResult.NoUpdateAvailable -> state.updateInfo
                else -> null
            }

            if (isAdmin && currentUpdateInfo != null) {
                val downloadsText = if (currentUpdateInfo.downloadCount == 1) {
                    "Esta versión de la app tiene 1 descarga oficial."
                } else {
                    "Esta versión de la app tiene ${currentUpdateInfo.downloadCount} descargas oficiales."
                }
                Text(
                    text = downloadsText,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .audiocinematecaAccessibility(label = downloadsText)
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            AccountButton(
                text = "Mi cuenta (Audiocinemateca.com)",
                onClick = {
                    focusedItemId = "account_web"
                    onNavigateToWebView("https://audiocinemateca.com/usuario")
                },
                accessibilityLabel = "Ir a mi cuenta en el sitio web",
                itemTag = "account_web",
                isInitialFocus = focusedItemId == "account_web"
            )

            AccountButton(
                text = "Documentación y Guía de Uso",
                onClick = {
                    focusedItemId = "documentation"
                    onNavigateToDocumentation()
                },
                accessibilityLabel = "Abrir la documentación oficial y guía de uso de la aplicación",
                itemTag = "documentation",
                isInitialFocus = focusedItemId == "documentation"
            )

            AccountButton(
                text = "Acerca de",
                onClick = { showAboutDialog = true },
                accessibilityLabel = "Información sobre Audiocinemateca"
            )

            AccountButton(
                text = "Apoyar el proyecto (Donaciones)",
                onClick = onShowDonation,
                accessibilityLabel = "Abrir opciones de donación"
            )

            AccountButton(
                text = "Novedades de la App",
                onClick = {
                    if (currentUpdateInfo != null) {
                        showNovedadesDialog = currentUpdateInfo
                    } else {
                        Toast.makeText(context, "Cargando novedades...", Toast.LENGTH_SHORT).show()
                    }
                },
                accessibilityLabel = "Ver qué hay de nuevo en esta versión"
            )

            AccountButton(
                text = "Historial de Versiones (GitHub)",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JohanAnim/Audiocinemateca/releases"))
                    context.startActivity(intent)
                },
                accessibilityLabel = "Abrir historial de versiones en GitHub"
            )

            AccountButton(
                text = "Grupo de Telegram",
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+faXgIluvZsExYWQx"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "No se pudo abrir Telegram", Toast.LENGTH_SHORT).show()
                    }
                },
                accessibilityLabel = "Unirse al grupo de Telegram"
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Información de Catálogo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catálogo: $catalogVersionText",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            activeDialog = AccountDialogSpec(
                                title = "Reparar Catálogo",
                                message = "¿Deseas forzar la descarga del catálogo completo de nuevo?",
                                confirmText = "Redescargar",
                                dismissText = "Cancelar",
                                onConfirm = {
                                    scope.launch {
                                        val serverVersion = authCatalogRepository.getCatalogVersion() ?: java.util.Date()
                                        showCatalogProgressDialog = true
                                        authCatalogRepository.downloadAndSaveCatalog(serverVersion).collect { res ->
                                            if (res is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress) {
                                                catalogProgress = res.percent
                                            } else if (res is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
                                                showCatalogProgressDialog = false
                                                Toast.makeText(context, "Catálogo actualizado", Toast.LENGTH_SHORT).show()
                                                val newVer = authCatalogRepository.getCatalogVersion()
                                                catalogVersionText = newVer?.let { android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString() } ?: "N/A"
                                            } else if (res is AuthCatalogRepository.LoadCatalogResultWithProgress.Error) {
                                                showCatalogProgressDialog = false
                                                Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        .audiocinematecaAccessibility(label = "Versión del catálogo local: $catalogVersionText. Toca para opciones de reparación")
                )
                
                val onCheckCatalogUpdate: () -> Unit = {
                    scope.launch {
                        activeDialog = AccountDialogSpec(
                            title = "Comprobando Catálogo",
                            message = "Verificando si hay actualizaciones en el servidor...",
                            isLoading = true
                        )

                        try {
                            authCatalogRepository.loadCatalog().collect { res ->
                                if (res is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress) return@collect

                                when (res) {
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                                        val verStr = authCatalogRepository.getCatalogVersion()?.let {
                                            android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString()
                                        } ?: catalogVersionText
                                        activeDialog = AccountDialogSpec(
                                            title = "Catálogo Actualizado",
                                            message = "El catálogo de Audiocinemateca ya se encuentra completamente actualizado a la última versión disponible (versión del $verStr). No hay títulos nuevos por descargar en este momento.",
                                            confirmText = "Entendido"
                                        )
                                    }
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                                        val serverVerStr = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", res.serverVersion).toString()
                                        activeDialog = AccountDialogSpec(
                                            title = "Actualización Disponible",
                                            message = "Hay una nueva versión del catálogo disponible en el servidor (versión del $serverVerStr). ¿Deseas descargar los nuevos títulos ahora?",
                                            confirmText = "Descargar Ahora",
                                            dismissText = "Cancelar",
                                            onConfirm = {
                                                scope.launch {
                                                    showCatalogProgressDialog = true
                                                    authCatalogRepository.downloadAndSaveCatalog(res.serverVersion).collect { dRes ->
                                                        if (dRes is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress) {
                                                            catalogProgress = dRes.percent
                                                        } else if (dRes is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
                                                            showCatalogProgressDialog = false
                                                            Toast.makeText(context, "¡Catálogo actualizado con éxito!", Toast.LENGTH_SHORT).show()
                                                            val newVer = authCatalogRepository.getCatalogVersion()
                                                            catalogVersionText = newVer?.let { android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString() } ?: "N/A"
                                                        } else if (dRes is AuthCatalogRepository.LoadCatalogResultWithProgress.Error) {
                                                            showCatalogProgressDialog = false
                                                            Toast.makeText(context, "Error: ${dRes.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                                        activeDialog = AccountDialogSpec(
                                            title = "Sin Conexión o Error",
                                            message = "No se pudo comprobar la versión del catálogo en el servidor: ${res.message}",
                                            confirmText = "Aceptar"
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            activeDialog = null
                            Toast.makeText(context, "Error al comprobar catálogo: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                TextButton(
                    onClick = onCheckCatalogUpdate,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Actualizar catálogo",
                        role = Role.Button,
                        onClickAction = onCheckCatalogUpdate
                    )
                ) {
                    Text("Actualizar")
                }
            }

            val onCheckAppUpdate: () -> Unit = {
                scope.launch {
                    activeDialog = AccountDialogSpec(
                        title = "Buscando Actualizaciones",
                        message = "Comprobando si hay una nueva versión de Audiocinemateca...",
                        isLoading = true
                    )

                    try {
                        when (val res = viewModel.manualCheckForUpdates(appVersion)) {
                            is UpdateCheckResult.UpdateAvailable -> {
                                val versionLabel = if (res.updateInfo.isPrerelease) {
                                    "versión Beta (${res.updateInfo.version})"
                                } else {
                                    "versión (${res.updateInfo.version})"
                                }
                                activeDialog = AccountDialogSpec(
                                    title = if (res.updateInfo.isPrerelease) {
                                        "Actualización Beta Disponible"
                                    } else {
                                        "Actualización Disponible"
                                    },
                                    message = "Hay una nueva $versionLabel de Audiocinemateca disponible. ¿Deseas descargarla e instalarla ahora?",
                                    confirmText = "Actualizar",
                                    dismissText = "Cancelar",
                                    onConfirm = {
                                        viewModel.downloadUpdate(res.updateInfo)
                                        onShowUpdateProgress()
                                    }
                                )
                            }
                            is UpdateCheckResult.NoUpdateAvailable -> {
                                activeDialog = AccountDialogSpec(
                                    title = "Aplicación Actualizada",
                                    message = "¡Ya tienes instalada la versión más reciente ($appVersion) en el canal $channelLabel! No hay actualizaciones disponibles.",
                                    confirmText = "Aceptar"
                                )
                            }
                            is UpdateCheckResult.Error -> {
                                activeDialog = AccountDialogSpec(
                                    title = "Sin Conexión o Error",
                                    message = "No se pudo comprobar si hay actualizaciones en el servidor: ${res.message}",
                                    confirmText = "Aceptar"
                                )
                            }
                            else -> {
                                activeDialog = null
                            }
                        }
                    } catch (e: Exception) {
                        activeDialog = null
                        Toast.makeText(context, "Error al comprobar actualizaciones: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Versión: $appVersion • Canal $channelLabel",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCheckAppUpdate() }
                        .audiocinematecaAccessibility(
                            label = "Versión de la aplicación: $appVersion, canal $channelLabel. Toca para buscar actualizaciones de la aplicación",
                            onClickAction = onCheckAppUpdate
                        )
                )

                TextButton(
                    onClick = onCheckAppUpdate,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Buscar actualizaciones de la aplicación",
                        role = Role.Button,
                        onClickAction = onCheckAppUpdate
                    )
                ) {
                    Text("Actualizar")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Diálogos
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Acerca de Audiocinemateca") },
            text = {
                Column {
                    Text("Audiocinemateca es un proyecto dedicado a llevar el cine a personas con discapacidad visual a través de audiodescripciones de alta calidad.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+faXgIluvZsExYWQx"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir Telegram", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enviar Feedback (Telegram)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Aceptar") }
            }
        )
    }

    showNovedadesDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { showNovedadesDialog = null },
            title = { Text("Novedades de esta versión") },
            text = {
                val formattedDate = remember(info.updatedAt) {
                    try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
                        val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
                        inputFormat.parse(info.updatedAt)?.let { outputFormat.format(it) } ?: "N/A"
                    } catch (e: Exception) { "N/A" }
                }
                
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Fecha de lanzamiento: $formattedDate", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val formattedChangelog = remember(info.changelog) {
                        parseMarkdownToAnnotatedString(info.changelog)
                    }
                    Text(
                        text = formattedChangelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNovedadesDialog = null }) { Text("Cerrar") }
            }
        )
    }

    if (showCatalogProgressDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Descargando Catálogo") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(progress = { catalogProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("$catalogProgress%", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { }
        )
    }

    activeDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { if (!dialog.isLoading) activeDialog = null },
            title = { Text(dialog.title) },
            text = {
                if (dialog.isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(dialog.message, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(dialog.message)
                }
            },
            confirmButton = {
                if (!dialog.isLoading) {
                    TextButton(
                        onClick = {
                            val confirmAction = dialog.onConfirm
                            activeDialog = null
                            confirmAction?.invoke()
                        }
                    ) {
                        Text(dialog.confirmText)
                    }
                }
            },
            dismissButton = if (!dialog.isLoading && dialog.dismissText != null) {
                {
                    TextButton(onClick = { activeDialog = null }) {
                        Text(dialog.dismissText)
                    }
                }
            } else null
        )
    }
}

@Composable
fun AccountButton(
    text: String,
    onClick: () -> Unit,
    accessibilityLabel: String,
    itemTag: String = "account_btn_$text",
    isInitialFocus: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clearAndSetSemantics {
                if (isInitialFocus) {
                    testTag = itemTag
                    traversalIndex = -1f
                }
                contentDescription = text
                role = Role.Button
                onClick(label = accessibilityLabel) {
                    onClick()
                    true
                }
            }
            .then(
                if (isInitialFocus) {
                    Modifier.initialAccessibilityFocus(tag = itemTag)
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class AccountDialogSpec(
    val title: String,
    val message: String,
    val isLoading: Boolean = false,
    val confirmText: String = "Aceptar",
    val dismissText: String? = null,
    val onConfirm: (() -> Unit)? = null
)
