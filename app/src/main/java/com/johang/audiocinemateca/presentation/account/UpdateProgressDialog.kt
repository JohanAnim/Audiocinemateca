package com.johang.audiocinemateca.presentation.account

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.johang.audiocinemateca.util.DownloadProgress

@Composable
fun UpdateProgressDialog(
    viewModel: AccountViewModel,
    onDismiss: () -> Unit
) {
    val downloadState by viewModel.downloadProgress.collectAsState()
    var completedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadProgress.Idle -> {
                if (completedUri == null && errorMessage == null) {
                    onDismiss()
                }
            }
            is DownloadProgress.Success -> {
                completedUri = state.uri
            }
            is DownloadProgress.Error -> {
                errorMessage = state.message
            }
            else -> {}
        }
    }

    when {
        completedUri != null -> {
            AlertDialog(
                onDismissRequest = {
                    completedUri = null
                    onDismiss()
                },
                title = { Text("Descarga Completa") },
                text = { Text("La actualización se ha descargado correctamente. ¿Deseas instalarla ahora?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uri = completedUri
                            completedUri = null
                            if (uri != null) {
                                viewModel.installUpdate(uri)
                            }
                            onDismiss()
                        }
                    ) {
                        Text("Instalar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            completedUri = null
                            onDismiss()
                        }
                    ) {
                        Text("Más tarde")
                    }
                }
            )
        }
        errorMessage != null -> {
            AlertDialog(
                onDismissRequest = {
                    errorMessage = null
                    onDismiss()
                },
                title = { Text("Error de Descarga") },
                text = { Text("Ocurrió un error al descargar la actualización: $errorMessage") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            errorMessage = null
                            onDismiss()
                        }
                    ) {
                        Text("Aceptar")
                    }
                }
            )
        }
        else -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                UpdateProgressDialogContent(downloadState = downloadState)
            }
        }
    }
}

@Composable
private fun UpdateProgressDialogContent(downloadState: DownloadProgress) {
    val progressPercent = if (downloadState is DownloadProgress.Progress) downloadState.percent else 0
    val isIndeterminate = downloadState !is DownloadProgress.Progress

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clearAndSetSemantics {
                contentDescription = "Descargando actualización de la app. Progreso: $progressPercent por ciento"
            }
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Descargando Actualización",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Descargando la nueva versión de la app, por favor espere un momento...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isIndeterminate) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF0EA5E9)
                )
            } else {
                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = Color(0xFF0EA5E9)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0EA5E9)
                )
            }
        }
    }
}
