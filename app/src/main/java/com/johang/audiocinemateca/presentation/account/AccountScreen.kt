package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.net.Uri
import android.widget.TextView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.domain.model.UpdateInfo
import com.johang.audiocinemateca.domain.usecase.UpdateCheckResult
import com.johang.audiocinemateca.util.audiocinematecaAccessibility
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    authCatalogRepository: AuthCatalogRepository,
    onNavigateToProfile: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWebView: (String) -> Unit,
    onShowDonation: () -> Unit,
    onShowUpdateProgress: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseAuth = FirebaseAuth.getInstance()
    val updateState by viewModel.updateState.collectAsState()
    
    var username by remember { mutableStateOf("Cargando...") }
    var catalogVersionText by remember { mutableStateOf("Cargando...") }
    var appVersion by remember { mutableStateOf("N/A") }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showNovedadesDialog by remember { mutableStateOf<UpdateInfo?>(null) }
    var showCatalogProgressDialog by remember { mutableStateOf(false) }
    var catalogProgress by remember { mutableIntStateOf(0) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Header Modernizado
        Surface(
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
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
                    Box(
                        modifier = Modifier
                            .clickable(onClick = onNavigateToProfile)
                            .audiocinematecaAccessibility(
                                label = "$username, conectado actualmente. Toca aquí para ver tu perfil",
                                role = Role.Button,
                                onClickAction = onNavigateToProfile
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

                IconButton(
                    onClick = shareAction,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Compartir aplicación",
                        role = Role.Button,
                        onClickAction = shareAction
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Botón Configuración
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Configuración",
                        role = Role.Button,
                        onClickAction = onNavigateToSettings
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
                .padding(24.dp),
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
                onClick = { onNavigateToWebView("https://audiocinemateca.com/usuario") },
                accessibilityLabel = "Ir a mi cuenta en el sitio web"
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
                            MaterialAlertDialogBuilder(context)
                                .setTitle("Reparar Catálogo")
                                .setMessage("¿Deseas forzar la descarga del catálogo completo de nuevo?")
                                .setPositiveButton("Redescargar") { _, _ ->
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
                                .setNegativeButton("Cancelar", null)
                                .show()
                        }
                        .audiocinematecaAccessibility(label = "Versión del catálogo local: $catalogVersionText. Toca para opciones de reparación")
                )
                
                val onCheckCatalogUpdate: () -> Unit = {
                    scope.launch {
                        val loadingDialog = MaterialAlertDialogBuilder(context)
                            .setTitle("Comprobando Catálogo")
                            .setMessage("Verificando si hay actualizaciones en el servidor...")
                            .setCancelable(false)
                            .create()
                        loadingDialog.show()

                        try {
                            authCatalogRepository.loadCatalog().collect { res ->
                                if (res is AuthCatalogRepository.LoadCatalogResultWithProgress.Progress) return@collect

                                loadingDialog.dismiss()
                                when (res) {
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Success -> {
                                        val verStr = authCatalogRepository.getCatalogVersion()?.let { 
                                            android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", it).toString() 
                                        } ?: catalogVersionText
                                        MaterialAlertDialogBuilder(context)
                                            .setTitle("Catálogo Actualizado")
                                            .setMessage("El catálogo de Audiocinemateca ya se encuentra completamente actualizado a la última versión disponible (versión del $verStr). No hay títulos nuevos por descargar en este momento.")
                                            .setPositiveButton("Entendido", null)
                                            .show()
                                    }
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable -> {
                                        val serverVerStr = android.text.format.DateFormat.format("dd/MM/yyyy HH:mm", res.serverVersion).toString()
                                        MaterialAlertDialogBuilder(context)
                                            .setTitle("Actualización Disponible")
                                            .setMessage("Hay una nueva versión del catálogo disponible en el servidor (versión del $serverVerStr). ¿Deseas descargar los nuevos títulos ahora?")
                                            .setPositiveButton("Descargar Ahora") { _, _ ->
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
                                            .setNegativeButton("Cancelar", null)
                                            .show()
                                    }
                                    is AuthCatalogRepository.LoadCatalogResultWithProgress.Error -> {
                                        MaterialAlertDialogBuilder(context)
                                            .setTitle("Sin Conexión o Error")
                                            .setMessage("No se pudo comprobar la versión del catálogo en el servidor: ${res.message}")
                                            .setPositiveButton("Aceptar", null)
                                            .show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            loadingDialog.dismiss()
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

            Text(
                text = "App Versión: $appVersion (for Android)",
                fontSize = 14.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { 
                        scope.launch {
                            val res = viewModel.manualCheckForUpdates(appVersion)
                            if (res is UpdateCheckResult.UpdateAvailable) {
                                MaterialAlertDialogBuilder(context)
                                    .setTitle("Actualización Disponible")
                                    .setMessage("Hay una nueva versión de la aplicación disponible. ¿Deseas descargarla ahora mismo?")
                                    .setPositiveButton("Sí") { _, _ ->
                                        viewModel.downloadUpdate(res.updateInfo)
                                        onShowUpdateProgress()
                                    }
                                    .setNegativeButton("No", null).show()
                            }
                        }
                    }
                    .audiocinematecaAccessibility(label = "Versión de la aplicación: $appVersion. Toca para buscar actualizaciones")
            )
            
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
                val locale = Locale.getDefault()
                val formattedDate = remember(info.updatedAt) {
                    try {
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", locale)
                        val outputFormat = SimpleDateFormat("dd/MM/yyyy", locale)
                        inputFormat.parse(info.updatedAt)?.let { outputFormat.format(it) } ?: "N/A"
                    } catch (e: Exception) { "N/A" }
                }
                
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Fecha de lanzamiento: $formattedDate", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                Markwon.create(ctx).setMarkdown(this, info.changelog)
                                setTextColor(android.graphics.Color.GRAY)
                            }
                        },
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
}

@Composable
fun AccountButton(
    text: String,
    onClick: () -> Unit,
    accessibilityLabel: String
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .audiocinematecaAccessibility(
                label = text,
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClickAction = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(16.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
