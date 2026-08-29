package com.johang.audiocinemateca.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.data.repository.GeminiRepository
import com.johang.audiocinemateca.domain.model.CatalogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShareUtils {
    fun shareContent(
        context: Context,
        contentItem: CatalogItem,
        geminiRepository: GeminiRepository? = null
    ) {
        val prefs = SharedPreferencesManager(context)
        val isAiMotivationalEnabled = prefs.getBoolean("ai_share_motivational_message", false)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        val itemType = when (contentItem) {
            is Movie -> "película"
            is Serie -> "serie"
            is Documentary -> "documental"
            is ShortFilm -> "cortometraje"
            else -> "contenido"
        }

        val article = when (itemType) {
            "película", "serie" -> "esta increíble $itemType"
            else -> "este increíble $itemType"
        }

        val callWord = when (itemType) {
            "película", "serie" -> "llamada"
            else -> "llamado"
        }

        val typeForUrl = when (contentItem) {
            is Movie -> "peliculas"
            is Serie -> "series"
            is Documentary -> "documentales"
            is ShortFilm -> "cortometrajes"
            else -> "contenido"
        }
        
        val url = "https://audiocinemateca.com/$typeForUrl?id=${contentItem.id}"

        fun launchChooser(message: String) {
            val fullMessage = "$message\n\n$url"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fullMessage)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir contenido"))
        }

        val defaultMessage = "¡Oye! Te recomiendo escuchar $article $callWord '${contentItem.title}' en la Audiocinemateca. ¡Seguro que a ti también te encantará! Da clic en este enlace para escucharla en la app. (Si el enlace no se abre directamente en la app, asegúrate de tener activada la opción 'Abrir enlaces compatibles' en los ajustes de la aplicación)."

        if (isAiMotivationalEnabled && apiKey.isNotBlank() && geminiRepository != null) {
            Toast.makeText(context, "Generando recomendación con IA...", Toast.LENGTH_SHORT).show()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val aiMessage = withContext(Dispatchers.IO) {
                        geminiRepository.generateMotivationalShareMessage(
                            title = contentItem.title,
                            itemType = itemType,
                            sinopsis = contentItem.sinopsis
                        )
                    }
                    if (!aiMessage.isNullOrBlank()) {
                        launchChooser(aiMessage)
                    } else {
                        launchChooser(defaultMessage)
                    }
                } catch (e: Exception) {
                    launchChooser(defaultMessage)
                }
            }
        } else {
            launchChooser(defaultMessage)
        }
    }
}
