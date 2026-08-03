package com.johang.audiocinemateca.presentation.community.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@Composable
fun ExitJamConfirmationDialog(
    hostName: String,
    onStayInJam: () -> Unit,
    onConfirmExitAndStop: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onStayInJam,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "🎧 ¿Salir del Jam en Vivo?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Text(
                text = "Actualmente estás unido a la sala en vivo de $hostName. Si sales de la comunidad, la transmisión del audio sincronizado se detendrá.",
                fontSize = 14.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onStayInJam,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Permanecer en la sala del Jam",
                    role = Role.Button,
                    onClickAction = onStayInJam
                )
            ) {
                Text(
                    text = "Permanecer en el Jam",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onConfirmExitAndStop,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF87171)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.audiocinematecaAccessibility(
                    label = "Salir de la comunidad y detener el audio",
                    role = Role.Button,
                    onClickAction = onConfirmExitAndStop
                )
            ) {
                Text(
                    text = "Salir y detener audio",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
