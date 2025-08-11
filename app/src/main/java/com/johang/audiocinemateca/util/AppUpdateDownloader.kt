package com.johang.audiocinemateca.util

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.johang.audiocinemateca.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Descargando actualización")
            .setDescription(fileName)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

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
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                val bytesTotal = cursor.getLong(bytesTotalIndex)
                val status = cursor.getInt(statusIndex)

                if (bytesTotal > 0) {
                    val progress = (bytesDownloaded * 100 / bytesTotal).toInt()
                    _downloadProgress.value = DownloadProgress.Progress(progress)
                }

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloading = false
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        _downloadProgress.value = DownloadProgress.Success(uri)
                    } else {
                        _downloadProgress.value = DownloadProgress.Error("No se pudo encontrar el archivo descargado.")
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    downloading = false
                    val reason = cursor.getInt(reasonIndex)
                    _downloadProgress.value = DownloadProgress.Error("Falló la descarga. Razón: $reason")
                }
                cursor.close()
            } else {
                downloading = false
                _downloadProgress.value = DownloadProgress.Error("La descarga no fue encontrada.")
            }
            delay(500) // Actualizar cada 500ms
        }
    }

    fun installPackage(uri: Uri) { // Hecho público
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}

sealed class DownloadProgress {
    object Idle : DownloadProgress()
    data class Progress(val percent: Int) : DownloadProgress()
    data class Success(val uri: Uri) : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}