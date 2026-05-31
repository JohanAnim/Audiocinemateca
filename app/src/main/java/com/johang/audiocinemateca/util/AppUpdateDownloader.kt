package com.johang.audiocinemateca.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.johang.audiocinemateca.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class AppUpdateDownloader @Inject constructor(@ApplicationContext private val context: Context) {

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    fun downloadAndInstall(updateInfo: UpdateInfo, coroutineScope: CoroutineScope) {
        val url = updateInfo.downloadUrl
        val fileName = url.substring(url.lastIndexOf('/') + 1)
        startDownload(url, fileName, coroutineScope)
    }

    private fun startDownload(url: String, fileName: String, coroutineScope: CoroutineScope) {
        _downloadProgress.value = DownloadProgress.Progress(0)

        // Guardar en el directorio de archivos externos privados de la app
        val destinationDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val destinationFile = File(destinationDir, fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription(fileName)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        coroutineScope.launch(Dispatchers.IO) {
            monitorDownloadProgress(downloadManager, downloadId)
        }
    }

    private suspend fun monitorDownloadProgress(downloadManager: DownloadManager, downloadId: Long) {
        var downloading = true
        while (downloading) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUriString = cursor.getString(localUriIndex)
                        if (localUriString != null) {
                            val apkFile = File(Uri.parse(localUriString).path!!)
                            val authority = "${context.packageName}.fileprovider"
                            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)
                            _downloadProgress.value = DownloadProgress.Success(contentUri)
                        } else {
                            _downloadProgress.value = DownloadProgress.Error("No se pudo encontrar la ruta del archivo descargado.")
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonIndex)
                        _downloadProgress.value = DownloadProgress.Error("Falló la descarga. Razón: $reason")
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)
                        if (bytesTotal > 0) {
                            val progress = (bytesDownloaded * 100 / bytesTotal).toInt()
                            _downloadProgress.value = DownloadProgress.Progress(progress)
                        }
                    }
                }
                cursor.close()
            } else {
                downloading = false
                _downloadProgress.value = DownloadProgress.Error("La descarga no fue encontrada.")
            }
            delay(500)
        }
    }

    fun installPackage(uri: Uri) {
        // En Android 8.0 (API 26) o superior, debemos verificar si tenemos el permiso de instalar fuentes desconocidas
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                // Informar al usuario que debe otorgar el permiso y volver a intentar
                kotlinx.coroutines.MainScope().launch {
                    android.widget.Toast.makeText(context, "Por favor, otorga el permiso para instalar la actualización y vuelve a pulsar instalar.", android.widget.Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            kotlinx.coroutines.MainScope().launch {
                android.widget.Toast.makeText(context, "Error al abrir el instalador: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

sealed class DownloadProgress {
    object Idle : DownloadProgress()
    data class Progress(val percent: Int) : DownloadProgress()
    data class Success(val uri: Uri) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
