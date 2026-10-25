package com.johang.audiocinemateca.presentation.equalizer

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.presentation.player.PlayerService

private data class EqualizerBandData(
    val index: Short,
    val centerFreqHz: Int,
    val description: String,
    val explanation: String,
    var levelDb: Short
)

@OptIn(UnstableApi::class)
@Composable
fun EqualizerDialog(
    sharedPreferencesManager: SharedPreferencesManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val equalizer = PlayerService.equalizer

    LaunchedEffect(equalizer) {
        if (equalizer == null) {
            Toast.makeText(context, "El ecualizador no está disponible en este momento.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    if (equalizer == null) return

    val bandLevelRange = equalizer.bandLevelRange
    val minLevel = bandLevelRange[0]
    val maxLevel = bandLevelRange[1]

    var isEnabled by remember { mutableStateOf(equalizer.enabled) }

    val bandsList = remember {
        mutableStateListOf<EqualizerBandData>().apply {
            for (i in 0 until equalizer.numberOfBands) {
                val bandIndex = i.toShort()
                val freq = equalizer.getCenterFreq(bandIndex)
                val level = equalizer.getBandLevel(bandIndex)
                add(
                    EqualizerBandData(
                        index = bandIndex,
                        centerFreqHz = freq / 1000,
                        description = getBandDescription(freq / 1000),
                        explanation = getBandExplanation(freq / 1000),
                        levelDb = level
                    )
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        EqualizerDialogContent(
            isEnabled = isEnabled,
            minLevelDb = minLevel,
            maxLevelDb = maxLevel,
            bands = bandsList,
            onEnableToggle = { checked ->
                isEnabled = checked
                equalizer.enabled = checked
                sharedPreferencesManager.saveBoolean("equalizer_enabled", checked)
            },
            onBandLevelChange = { bandIndex, newLevelDb ->
                equalizer.setBandLevel(bandIndex, newLevelDb)
                sharedPreferencesManager.saveInt("equalizer_band_${bandIndex}", newLevelDb.toInt())
            },
            onResetFlat = {
                for (i in 0 until equalizer.numberOfBands) {
                    val bandIndex = i.toShort()
                    equalizer.setBandLevel(bandIndex, 0)
                    sharedPreferencesManager.saveInt("equalizer_band_${bandIndex}", 0)
                    if (i < bandsList.size) {
                        bandsList[i] = bandsList[i].copy(levelDb = 0)
                    }
                }
            },
            onDismiss = onDismiss
        )
    }
}

private fun getBandDescription(freqHz: Int): String {
    return when (freqHz) {
        in 0..60 -> "Sub-graves"
        in 61..250 -> "Graves"
        in 251..500 -> "Medios-bajos"
        in 501..2000 -> "Medios"
        in 2001..4000 -> "Medios-altos"
        in 4001..6000 -> "Presencia"
        else -> "Agudos"
    }
}

private fun getBandExplanation(freqHz: Int): String {
    return when (freqHz) {
        in 0..60 -> "Aumentar para más 'punch' en la música. Disminuir para reducir el retumbe."
        in 61..250 -> "Ajusta el cuerpo y la calidez del sonido."
        in 251..500 -> "Afecta la claridad de instrumentos graves y la plenitud de las voces."
        in 501..2000 -> "Zona principal de la voz humana. Ajustar para mayor claridad vocal."
        in 2001..4000 -> "Define el ataque de instrumentos y la inteligibilidad."
        in 4001..6000 -> "Aumentar para más claridad y definición."
        else -> "Aporta brillo y aire al sonido."
    }
}

@Composable
private fun EqualizerDialogContent(
    isEnabled: Boolean,
    minLevelDb: Short,
    maxLevelDb: Short,
    bands: List<EqualizerBandData>,
    onEnableToggle: (Boolean) -> Unit,
    onBandLevelChange: (bandIndex: Short, newLevelDb: Short) -> Unit,
    onResetFlat: () -> Unit,
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
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Ecualizador de audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Cerrar ecualizador"
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

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnableToggle(!isEnabled) }
                    .clearAndSetSemantics {
                        contentDescription = "Activar ecualizador de audio"
                        role = Role.Switch
                        stateDescription = if (isEnabled) "Activado" else "Desactivado"
                        onClick(label = if (isEnabled) "Desactivar ecualizador" else "Activar ecualizador") {
                            onEnableToggle(!isEnabled)
                            true
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ecualización activa",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            bands.forEach { band ->
                var currentLevel by remember(band.levelDb) { mutableStateOf(band.levelDb) }
                val progressFraction = remember(currentLevel, minLevelDb, maxLevelDb) {
                    if (maxLevelDb > minLevelDb) ((currentLevel - minLevelDb).toFloat() / (maxLevelDb - minLevelDb).toFloat()).coerceIn(0f, 1f) else 0.5f
                }
                val dbFormatted = "${currentLevel / 100} dB"

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${band.description} (${band.centerFreqHz} Hz)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = dbFormatted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = band.explanation,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = progressFraction,
                            enabled = isEnabled,
                            onValueChange = { fraction ->
                                val newDb = (minLevelDb + (fraction * (maxLevelDb - minLevelDb))).toInt().toShort()
                                currentLevel = newDb
                                onBandLevelChange(band.index, newDb)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "${band.description} (${band.centerFreqHz} Hz)"
                                    stateDescription = dbFormatted
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onResetFlat,
                enabled = isEnabled,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = "Restablecer bandas a plano 0 dB"
                        role = Role.Button
                        onClick(label = "Restablecer a plano") {
                            onResetFlat()
                            true
                        }
                    },
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restablecer a plano (0 dB)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
