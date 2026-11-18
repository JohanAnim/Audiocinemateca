package com.johang.audiocinemateca.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johang.audiocinemateca.data.repository.ServerMigrationStatus
import com.johang.audiocinemateca.util.accessibleHeading
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun MigrationNoticeDialog(
    status: ServerMigrationStatus,
    isCheckingUpdate: Boolean = false,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isMandatory = status.isMigrationRequired
    val titleText = if (isMandatory) {
        "Actualización requerida (${status.minOnlineVersion}+)"
    } else {
        "Próxima actualización importante (${status.minOnlineVersion})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isMandatory) Icons.Default.SystemUpdate else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (isMandatory) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.accessibleHeading(titleText)
            )
        },
        text = {
            Text(
                text = status.resolvedMessage(),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onUpdateClick,
                enabled = !isCheckingUpdate,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = if (status.latestUpdateInfo != null && isMandatory) {
                        "Descargar actualización ${status.latestUpdateInfo.version}"
                    } else {
                        "Buscar actualización disponible"
                    },
                    role = Role.Button,
                    onClickAction = onUpdateClick
                )
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (status.latestUpdateInfo != null && isMandatory) {
                        "Actualizar ahora"
                    } else {
                        "Buscar actualización"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.audiocinematecaAccessibility(
                    label = if (isMandatory) "Usar modo local" else "Entendido",
                    role = Role.Button,
                    onClickAction = onDismiss
                )
            ) {
                Text(if (isMandatory) "Seguir sin funciones en línea" else "Entendido")
            }
        }
    )
}

@Composable
fun MigrationOrConnectionCard(
    status: ServerMigrationStatus,
    errorMessage: String? = null,
    onUpdateClick: () -> Unit,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val showMigration = status.shouldShowBanner
    if (!showMigration && errorMessage.isNullOrBlank()) return

    val isMandatory = status.isMigrationRequired
    val title = when {
        isMandatory -> "Actualización necesaria (v${status.minOnlineVersion}+)"
        status.isWarningActive -> "Próximo cambio de servidor (v${status.minOnlineVersion})"
        else -> "Sin conexión con servicios en línea"
    }
    val body = when {
        showMigration -> status.resolvedMessage()
        else -> errorMessage.orEmpty()
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = "$title. $body"
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isMandatory) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when {
                        isMandatory -> Icons.Default.SystemUpdate
                        status.isWarningActive -> Icons.Default.WarningAmber
                        else -> Icons.Default.CloudOff
                    },
                    contentDescription = null,
                    tint = if (isMandatory) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isMandatory) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.accessibleHeading(title)
                )
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = if (isMandatory) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onUpdateClick,
                    modifier = Modifier.audiocinematecaAccessibility(
                        label = "Buscar actualización a la versión ${status.minOnlineVersion}",
                        role = Role.Button,
                        onClickAction = onUpdateClick
                    )
                ) {
                    Text(
                        if (isMandatory && status.latestUpdateInfo != null) {
                            "Actualizar ahora"
                        } else {
                            "Buscar actualización"
                        }
                    )
                }

                if (onRetryClick != null && !isMandatory) {
                    OutlinedButton(
                        onClick = onRetryClick,
                        modifier = Modifier.audiocinematecaAccessibility(
                            label = "Reintentar conexión",
                            role = Role.Button,
                            onClickAction = onRetryClick
                        )
                    ) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}
