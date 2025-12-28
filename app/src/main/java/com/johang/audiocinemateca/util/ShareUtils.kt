package com.johang.audiocinemateca.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.johang.audiocinemateca.data.model.Documentary
import com.johang.audiocinemateca.data.model.Movie
import com.johang.audiocinemateca.data.model.Serie
import com.johang.audiocinemateca.data.model.ShortFilm
import com.johang.audiocinemateca.domain.model.CatalogItem

object ShareUtils {
    fun shareContent(context: Context, contentItem: CatalogItem) {
        val itemType = when (contentItem) {
            is Movie -> "película"
            is Serie -> "serie"
            is Documentary -> "documental"
            is ShortFilm -> "cortometraje"
            else -> "contenido"
        }

        val message = "¡Oye! Estoy escuchando esta increíble $itemType llamada '${contentItem.title}' en la Audiocinemateca. ¡Seguro que a ti también te podría gustar! Da clic en este enlace para que lo escuches en la app. (Si el enlace no se abre directamente en la app, asegúrate de tener activada la opción 'Abrir enlaces compatibles' en la configuración de la aplicación Audiocinemateca en tu dispositivo.)"
        
        // Mapeo para la URL (usando los plurales que espera el Deep Link)
        val typeForUrl = when (contentItem) {
            is Movie -> "peliculas"
            is Serie -> "series"
            is Documentary -> "documentales"
            is ShortFilm -> "cortometrajes"
            else -> "contenido"
        }
        
        val url = "https://audiocinemateca.com/$typeForUrl?id=${contentItem.id}"
        val fullMessage = "$message\n\n$url"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, fullMessage)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir contenido"))
    }
}
