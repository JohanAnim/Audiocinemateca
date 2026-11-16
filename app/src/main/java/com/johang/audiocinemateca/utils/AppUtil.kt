package com.johang.audiocinemateca.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object AppUtil {

    fun openUrlInBrowser(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "No se pudo abrir el enlace: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}