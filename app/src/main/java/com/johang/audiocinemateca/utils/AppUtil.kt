package com.johang.audiocinemateca.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import android.content.Intent
import android.net.Uri
import java.util.zip.GZIPInputStream

object AppUtil {

    fun showAlertDialog(context: Context, title: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Aceptar") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }
    }

    fun decompressGzip(bytes: ByteArray): String {
        val gzipInputStream = GZIPInputStream(ByteArrayInputStream(bytes))
        val reader = InputStreamReader(gzipInputStream, "UTF-8")
        return reader.readText()
    }

    fun openUrlInBrowser(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}