package com.johang.audiocinemateca.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DonationDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DonationDialogContent(
            onDonateOfficial = {
                openUrl("https://www.paypal.com/donate/?hosted_button_id=T4H2LCSZDRV8J")
                onDismiss()
            },
            onDonateDirect = {
                openUrl("https://www.paypal.me/johananimg")
                onDismiss()
            },
            onDonateExternal = {
                openUrl("https://audiocinemateca.com/donar")
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun DonationDialogContent(
    onDonateOfficial: () -> Unit,
    onDonateDirect: () -> Unit,
    onDonateExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Apoyar al proyecto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Cerrar diálogo de donación"
                        role = Role.Button
                        onClick(label = "Cerrar") {
                            onDismiss()
                            true
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Audiocinemateca es una plataforma libre e independiente creada con dedicación para llevar cine con audiodescripción a toda la comunidad ciega y de baja visión.\n\nTu apoyo nos ayuda a mantener los servidores activos y seguir añadiendo nuevas películas y series.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDonateOfficial,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0070BA)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "Donar por PayPal Oficial"
                        role = Role.Button
                        onClick(label = "Abrir donación en PayPal Oficial") {
                            onDonateOfficial()
                            true
                        }
                    },
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp)
            ) {
                Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Donar por PayPal Oficial", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDonateDirect,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "Donación rápida con PayPal Me"
                        role = Role.Button
                        onClick(label = "Abrir PayPal Me") {
                            onDonateDirect()
                            true
                        }
                    },
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEF4444))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Donación rápida con PayPal Me", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            TextButton(
                onClick = onDonateExternal,
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "Ver otras opciones en la web audiocinemateca.com"
                        role = Role.Button
                        onClick(label = "Abrir sitio web de donaciones") {
                            onDonateExternal()
                            true
                        }
                    }
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver más opciones en la web", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
