package com.johang.audiocinemateca.presentation.account

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsAccessibility
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.johang.audiocinemateca.util.TtsManager
import com.johang.audiocinemateca.util.accessibleHeading
import com.johang.audiocinemateca.util.initialAccessibilityFocus
import com.johang.audiocinemateca.util.parseMarkdownToHtml

private data class DocTopic(
    val id: String,
    val fileName: String,
    val title: String,
    val summary: String,
    val icon: ImageVector
)

private val APP_DOC_TOPICS = listOf(
    DocTopic(
        id = "primeros_pasos",
        fileName = "01_primeros_pasos.md",
        title = "Primeros pasos",
        summary = "Todo lo necesario para crear tu cuenta y empezar a explorar el catálogo.",
        icon = Icons.AutoMirrored.Filled.MenuBook
    ),
    DocTopic(
        id = "reproductor_dualsense",
        fileName = "02_reproductor_cast_y_dualsense.md",
        title = "Reproducción y dispositivos",
        summary = "Controles de audio, transmisión al televisor y uso con mandos.",
        icon = Icons.Default.Headphones
    ),
    DocTopic(
        id = "listas_descargas",
        fileName = "03_listas_y_descargas.md",
        title = "Tu biblioteca personal",
        summary = "Organiza tus obras favoritas, tu historial de escucha y tus descargas.",
        icon = Icons.Default.VideoLibrary
    ),
    DocTopic(
        id = "aura_comunidad",
        fileName = "04_aura_y_comunidad.md",
        title = "Aura y Comunidad",
        summary = "Conversa con nuestra compañera de cine y comparte experiencias con otros oyentes.",
        icon = Icons.Default.AutoAwesome
    ),
    DocTopic(
        id = "configurar_ia",
        fileName = "05_configurar_ia_y_api_keys.md",
        title = "Configuración de Inteligencia Artificial",
        summary = "Elige tu proveedor preferido y conecta tu clave personal para activar a Aura.",
        icon = Icons.Default.Key
    ),
    DocTopic(
        id = "accesibilidad_ajustes",
        fileName = "06_accesibilidad_y_ajustes.md",
        title = "Accesibilidad y personalización",
        summary = "Adapta los lectores de pantalla, las voces de lectura y las preferencias de uso.",
        icon = Icons.Default.SettingsAccessibility
    ),
    DocTopic(
        id = "novedades_proyecto",
        fileName = "07_novedades_proyecto.md",
        title = "Novedades de esta versión",
        summary = "Descubre las mejoras más recientes incorporadas en la aplicación.",
        icon = Icons.Default.NewReleases
    )
)

private fun Color.toCssHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentationScreen(
    ttsManager: TtsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastFocusedTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var isReadingAloud by remember { mutableStateOf(false) }

    val selectedTopic = remember(selectedTopicId) {
        APP_DOC_TOPICS.find { it.id == selectedTopicId }
    }

    val closeDetail: () -> Unit = {
        if (isReadingAloud) {
            ttsManager.stop()
            isReadingAloud = false
        }
        selectedTopicId = null
    }

    BackHandler(enabled = selectedTopic != null) {
        closeDetail()
    }

    val rawMarkdown = remember(selectedTopic?.fileName) {
        selectedTopic?.let { topic ->
            runCatching {
                context.assets.open("docs/${topic.fileName}").bufferedReader().use { it.readText() }
            }.getOrElse {
                "# Error al cargar la guía\n\nNo se pudo abrir esta sección de la documentación."
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val renderedHtml = remember(
        rawMarkdown,
        colorScheme.background,
        colorScheme.surfaceVariant,
        colorScheme.onBackground,
        colorScheme.onSurfaceVariant,
        colorScheme.primary,
        colorScheme.outlineVariant
    ) {
        rawMarkdown?.let { md ->
            parseMarkdownToHtml(
                markdown = md,
                backgroundHex = colorScheme.background.toCssHex(),
                surfaceHex = colorScheme.surfaceVariant.toCssHex(),
                textHex = colorScheme.onBackground.toCssHex(),
                secondaryTextHex = colorScheme.onSurfaceVariant.toCssHex(),
                accentHex = colorScheme.primary.toCssHex(),
                dividerHex = colorScheme.outlineVariant.toCssHex()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTopic?.title ?: "Documentación",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedTopic != null) {
                                closeDetail()
                            } else {
                                if (isReadingAloud) {
                                    ttsManager.stop()
                                    isReadingAloud = false
                                }
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectedTopic != null) {
                                "Volver al índice de documentación"
                            } else {
                                "Volver a Cuenta"
                            }
                        )
                    }
                },
                actions = {
                    if (selectedTopic != null && rawMarkdown != null) {
                        IconButton(
                            onClick = {
                                if (isReadingAloud) {
                                    ttsManager.stop()
                                    isReadingAloud = false
                                } else {
                                    val cleanSpeech = rawMarkdown
                                        .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
                                        .replace(Regex("[#*`>_~\\-]+"), " ")
                                        .replace(Regex("\\s+"), " ")
                                        .trim()
                                    ttsManager.speak(cleanSpeech, interrupt = true)
                                    isReadingAloud = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isReadingAloud) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isReadingAloud) {
                                    "Detener lectura en voz alta"
                                } else {
                                    "Escuchar guía en voz alta"
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (selectedTopic == null || renderedHtml == null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Guía oficial de Audiocinemateca",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .accessibleHeading("Guía oficial de Audiocinemateca")
                    )
                    Text(
                        text = "Selecciona un tema para consultarlo o escucharlo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(APP_DOC_TOPICS, key = { _, item -> item.id }) { index, topic ->
                    val topicTag = "doc_topic_${topic.id}"
                    val shouldFocus = when {
                        lastFocusedTopicId != null -> lastFocusedTopicId == topic.id
                        else -> index == 0
                    }
                    val openTopicAction = {
                        lastFocusedTopicId = topic.id
                        selectedTopicId = topic.id
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .clickable(onClick = openTopicAction)
                            .clearAndSetSemantics {
                                if (shouldFocus) {
                                    testTag = topicTag
                                    traversalIndex = -1f
                                }
                                contentDescription = "${topic.title}. ${topic.summary}"
                                onClick(label = "Abrir guía") {
                                    openTopicAction()
                                    true
                                }
                            }
                            .then(
                                if (shouldFocus) Modifier.initialAccessibilityFocus(topicTag)
                                else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = topic.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topic.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        setBackgroundColor(colorScheme.background.toArgb())
                        settings.javaScriptEnabled = false
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.textZoom = 105
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri: Uri = request?.url ?: return false
                                val scheme = targetUri.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") {
                                    runCatching {
                                        viewContext.startActivity(Intent(Intent.ACTION_VIEW, targetUri))
                                    }.onFailure {
                                        Toast.makeText(
                                            viewContext,
                                            "No se encontró un navegador para abrir el enlace.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadDataWithBaseURL(
                            "https://audiocinemateca.local/",
                            renderedHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { webView ->
                    webView.setBackgroundColor(colorScheme.background.toArgb())
                    val lastLoaded = webView.tag as? String
                    if (lastLoaded != renderedHtml) {
                        webView.tag = renderedHtml
                        webView.loadDataWithBaseURL(
                            "https://audiocinemateca.local/",
                            renderedHtml,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )
        }
    }
}
