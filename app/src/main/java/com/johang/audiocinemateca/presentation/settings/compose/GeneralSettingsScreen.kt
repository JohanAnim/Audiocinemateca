package com.johang.audiocinemateca.presentation.settings.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.presentation.settings.ThemeManager
import com.johang.audiocinemateca.util.CrashLogger
import androidx.core.content.FileProvider

@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Obtenemos los estados del ViewModel
    val currentTheme by viewModel.theme.collectAsState()
    val bootFrequency by viewModel.bootAnimationFrequency.collectAsState()
    val startupTab by viewModel.startupTab.collectAsState()
    val hideHomeFeed by viewModel.hideHomeFeed.collectAsState()
    val defaultContentTab by viewModel.defaultContentTab.collectAsState()
    val defaultFilter by viewModel.defaultFilter.collectAsState()
    val autoCheckCatalog by viewModel.autoCheckCatalog.collectAsState()
    val autoCheckApp by viewModel.autoCheckApp.collectAsState()
    
    // Cargamos los arrays de recursos
    val themeEntries = stringArrayResource(id = R.array.theme_entries).toList()
    val themeValues = stringArrayResource(id = R.array.theme_values).toList()

    val bootEntries = stringArrayResource(id = R.array.boot_animation_frequency_entries).toList()
    val bootValues = stringArrayResource(id = R.array.boot_animation_frequency_values).toList()
    
    val rawStartupEntries = stringArrayResource(id = R.array.startup_tab_entries).toList()
    val rawStartupValues = stringArrayResource(id = R.array.startup_tab_values).toList()

    val (startupEntries, startupValues) = remember(hideHomeFeed, rawStartupEntries, rawStartupValues) {
        if (hideHomeFeed) {
            val entries = mutableListOf<String>()
            val values = mutableListOf<String>()
            rawStartupValues.forEachIndexed { index, value ->
                if (value != "home") {
                    entries.add(rawStartupEntries.getOrElse(index) { value })
                    values.add(value)
                }
            }
            Pair(entries, values)
        } else {
            Pair(rawStartupEntries, rawStartupValues)
        }
    }

    val contentEntries = stringArrayResource(id = R.array.default_content_tab_entries).toList()
    val contentValues = stringArrayResource(id = R.array.default_content_tab_values).toList()

    val filterEntries = stringArrayResource(id = R.array.default_filter_entries).toList()
    val filterValues = stringArrayResource(id = R.array.default_filter_values).toList()

    CategorySettingsScreen(
        title = "Ajustes generales",
        onBack = onBack
    ) {
        // 1. Tema
        SettingsListItem(
            title = "Tema de la aplicación",
            summary = themeEntries[themeValues.indexOf(currentTheme).coerceAtLeast(0)],
            entries = themeEntries,
            entryValues = themeValues,
            currentValue = currentTheme,
            onValueChange = { 
                viewModel.updateString("theme", it)
                ThemeManager.applyTheme(it)
            }
        )

        // 2. Frecuencia de animación de arranque
        SettingsListItem(
            title = "Frecuencia de animación de arranque",
            summary = bootEntries[bootValues.indexOf(bootFrequency).coerceAtLeast(0)],
            entries = bootEntries,
            entryValues = bootValues,
            currentValue = bootFrequency,
            onValueChange = { viewModel.updateString("boot_animation_frequency", it) }
        )

        // 3. Pestaña de inicio
        val currentStartupIndex = startupValues.indexOf(startupTab).takeIf { it >= 0 } ?: 0
        SettingsListItem(
            title = "Pestaña de inicio",
            summary = startupEntries.getOrElse(currentStartupIndex) { "Explorar (Catálogo)" },
            entries = startupEntries,
            entryValues = startupValues,
            currentValue = if (startupValues.contains(startupTab)) startupTab else startupValues.firstOrNull() ?: "catalog",
            onValueChange = { viewModel.updateString("startup_tab", it) }
        )

        // 4. Ocultar Feed y Pestaña de Inicio
        SettingsSwitchItem(
            title = "Ocultar por completo la pestaña de inicio y el feed",
            summary = "Oculta la pestaña de Inicio de la barra inferior y muestra directamente la sección de Explorar al abrir la app.",
            checked = hideHomeFeed,
            onCheckedChange = { viewModel.updateBoolean("hide_home_feed", it) }
        )

        // 5. Pestaña de contenido predeterminada
        SettingsListItem(
            title = "Pestaña de contenido predeterminada",
            summary = contentEntries[contentValues.indexOf(defaultContentTab).coerceAtLeast(0)],
            entries = contentEntries,
            entryValues = contentValues,
            currentValue = defaultContentTab,
            onValueChange = { viewModel.updateString("default_content_tab", it) }
        )

        // 4. Orden de visualización predeterminado
        SettingsListItem(
            title = "Orden de visualización predeterminado",
            summary = filterEntries[filterValues.indexOf(defaultFilter).coerceAtLeast(0)],
            entries = filterEntries,
            entryValues = filterValues,
            currentValue = defaultFilter,
            onValueChange = { viewModel.updateString("default_filter", it) }
        )

        // 5. Check catálogo
        SettingsSwitchItem(
            title = "Comprobación automática del catálogo",
            summary = "Buscar y aplicar actualizaciones del catálogo al iniciar la app.",
            checked = autoCheckCatalog,
            onCheckedChange = { viewModel.updateBoolean("auto_check_catalog", it) }
        )

        // 6. Check App
        SettingsSwitchItem(
            title = "Comprobación automática de la aplicación",
            summary = "Mostrar un diálogo al iniciar la app si hay una nueva versión disponible.",
            checked = autoCheckApp,
            onCheckedChange = { viewModel.updateBoolean("auto_check_app", it) }
        )

        SettingsCategoryHeader("Permisos y Enlaces")

        SettingsClickItem(
            title = "Optimización de batería",
            summary = "Permite que la app funcione mejor en segundo plano (Recomendado).",
            onClick = { openBatteryOptimizationSettings(context) }
        )

        SettingsClickItem(
            title = "Abrir enlaces de Audiocinemateca",
            summary = "Configura la app para abrir automáticamente los enlaces de audiocinemateca.com",
            onClick = { openDeepLinkSettings(context) }
        )

        SettingsCategoryHeader("Diagnóstico")

        var createCrashLog by remember { mutableStateOf(viewModel.getBoolean("create_crash_log", true)) }
        SettingsSwitchItem(
            title = "Generar archivo de registro de errores (.log)",
            summary = "Si la app falla, se guardará un reporte interno para compartirlo.",
            checked = createCrashLog,
            onCheckedChange = { 
                createCrashLog = it
                viewModel.updateBoolean("create_crash_log", it)
            }
        )

        SettingsClickItem(
            title = "Compartir último reporte de error",
            summary = "Envía el último archivo de registro generado.",
            onClick = { shareLatestLog(context) },
            enabled = createCrashLog
        )
    }
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (e: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun openDeepLinkSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }
}

private fun shareLatestLog(context: Context) {
    val latestLog = CrashLogger.getLatestLogFile(context)
    if (latestLog == null || !latestLog.exists()) {
        Toast.makeText(context, "No hay reportes de error disponibles.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", latestLog)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar reporte por..."))
    } catch (e: Exception) {
        Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
