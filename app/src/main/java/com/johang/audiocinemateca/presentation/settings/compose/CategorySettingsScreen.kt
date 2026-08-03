package com.johang.audiocinemateca.presentation.settings.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.johang.audiocinemateca.util.audiocinematecaAccessibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySettingsScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .audiocinematecaAccessibility(
                label = "$title. $summary. ${if (checked) "Activado" else "Desactivado"}",
                role = Role.Switch,
                onClickAction = if (enabled) { { onCheckedChange(!checked) } } else null
            ),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title, 
                    fontSize = 17.sp, 
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = summary, 
                    fontSize = 14.sp, 
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
fun SettingsListItem(
    title: String,
    summary: String,
    entries: List<String>,
    entryValues: List<String>,
    currentValue: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
            .audiocinematecaAccessibility(
                label = "$title. Actual: $summary",
                role = Role.Button,
                onClickAction = if (enabled) { { showDialog = true } } else null
            ),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = title, 
                fontSize = 17.sp, 
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = summary, 
                fontSize = 14.sp, 
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(entryValues[index])
                                    showDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentValue == entryValues[index],
                                onClick = null
                            )
                            Text(
                                text = entry,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun SettingsClickItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .audiocinematecaAccessibility(
                label = "$title. $summary",
                role = Role.Button,
                onClickAction = if (enabled) onClick else null
            ),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = title, 
                fontSize = 17.sp, 
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = summary, 
                fontSize = 14.sp, 
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
}

@Composable
fun SettingsEditTextItem(
    title: String,
    summary: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    var tempValue by remember(showDialog) { mutableStateOf(value) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showDialog = true }
            .audiocinematecaAccessibility(
                label = "$title. $summary",
                role = Role.Button,
                onClickAction = if (enabled) { { showDialog = true } } else null
            ),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(text = summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = androidx.compose.ui.graphics.Color(0xFF1E293B),
            title = {
                Text(
                    text = title,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tempValue,
                        onValueChange = { tempValue = it },
                        placeholder = {
                            Text(
                                text = placeholder.ifBlank { "Escribe o pega aquí la clave..." },
                                color = androidx.compose.ui.graphics.Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color(0xFF0F172A),
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color(0xFF0F172A),
                            focusedTextColor = androidx.compose.ui.graphics.Color.White,
                            unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                            focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF6366F1),
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFF334155)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    tempValue = clip.trim()
                                    android.widget.Toast.makeText(context, "Clave pegada desde el portapapeles.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "El portapapeles está vacío.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF6366F1),
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .audiocinematecaAccessibility(
                                    label = "Pegar clave desde el portapapeles",
                                    role = Role.Button,
                                    onClickAction = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!clip.isNullOrBlank()) tempValue = clip.trim()
                                    }
                                )
                        ) {
                            Text("📋 Pegar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (tempValue.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { tempValue = "" },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = androidx.compose.ui.graphics.Color(0xFFF87171)
                                ),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.audiocinematecaAccessibility(
                                    label = "Borrar clave ingresada",
                                    role = Role.Button,
                                    onClickAction = { tempValue = "" }
                                )
                            ) {
                                Text("❌ Limpiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onValueChange(tempValue.trim())
                        showDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF22C55E),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                ) { Text("Guardar", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.ui.graphics.Color(0xFF94A3B8)
                    )
                ) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0.1f..2.0f,
    steps: Int = 19,
    enabled: Boolean = true
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
