package com.johang.audiocinemateca.presentation.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.johang.audiocinemateca.AudiocinematecaApp.Companion.DOWNLOAD_CHANNEL_ID
import com.johang.audiocinemateca.R
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.di.DownloadClient
import com.johang.audiocinemateca.domain.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    @DownloadClient
    lateinit var downloadOkHttpClient: OkHttpClient

    @Inject
    lateinit var progressFlow: MutableStateFlow<Int>

    @Inject
    lateinit var sharedPreferencesManager: SharedPreferencesManager

    @Inject
    lateinit var downloadManager: DownloadManager

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> handleStartDownload(intent)
            ACTION_CANCEL_DOWNLOAD -> {
                val contentId = intent.getStringExtra(EXTRA_CONTENT_ID)
                val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
                val episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)
                if (contentId != null) {
                    cleanupTempFile(contentId, partIndex, episodeIndex)
                }
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getTempFile(contentId: String, partIndex: Int, episodeIndex: Int): File {
        val tempDir = File(applicationContext.filesDir, "temp_downloads").apply {
            if (!exists()) mkdirs()
        }
        val safeFileName = "${contentId.replace(Regex("[^a-zA-Z0-9_]"), "_")}_${partIndex}_${episodeIndex}.tmp"
        return File(tempDir, safeFileName)
    }

    private fun cleanupTempFile(contentId: String, partIndex: Int, episodeIndex: Int) {
        try {
            val tempFile = getTempFile(contentId, partIndex, episodeIndex)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar archivo temporal: ${e.message}")
        }
    }

    private fun handleStartDownload(intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val contentId = intent.getStringExtra(EXTRA_CONTENT_ID) ?: return
        val contentType = intent.getStringExtra(EXTRA_CONTENT_TYPE) ?: return
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, -1)
        val episodeIndex = intent.getIntExtra(EXTRA_EPISODE_INDEX, -1)
        val seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE)

        startForeground(NOTIFICATION_ID, createNotification(title, 0))

        serviceScope.launch {
            var downloadEntity = downloadRepository.getDownload(contentId, partIndex, episodeIndex)
            if (downloadEntity == null) {
                downloadEntity = DownloadEntity(
                    contentId = contentId,
                    contentType = contentType,
                    title = title,
                    partIndex = partIndex,
                    episodeIndex = episodeIndex,
                    downloadStatus = "QUEUED",
                    filePath = null,
                    downloadedAt = System.currentTimeMillis(),
                    totalSizeMb = 0.0,
                    durationMs = 0L,
                    errorMessage = null
                )
            }

            var downloadingEntity = downloadEntity.copy(downloadStatus = "DOWNLOADING", errorMessage = null)
            downloadRepository.insertDownload(downloadingEntity)

            val tempFile = getTempFile(contentId, partIndex, episodeIndex)
            var isCompleted = false
            var attempt = 0
            val maxAttempts = 20
            var totalExpectedBytes = 0L

            while (!isCompleted && attempt < maxAttempts) {
                // 1. Si no hay conexión, esperar a que regrese sin cancelar ni marcar como fallido
                if (!isNetworkAvailable()) {
                    notificationManager.notify(NOTIFICATION_ID, createWaitingNotification(title))
                    downloadRepository.insertDownload(
                        downloadingEntity.copy(
                            downloadStatus = "QUEUED",
                            errorMessage = "Pausado: esperando conexión a internet..."
                        )
                    )

                    var secondsWaited = 0
                    while (!isNetworkAvailable() && secondsWaited < 60) {
                        delay(3000)
                        secondsWaited += 3
                    }

                    if (!isNetworkAvailable()) {
                        attempt++
                        continue
                    }
                }

                // 2. Reanudar la descarga usando el encabezado HTTP Range
                try {
                    val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                    val requestBuilder = Request.Builder().url(url)
                    if (existingBytes > 0) {
                        requestBuilder.header("Range", "bytes=$existingBytes-")
                    }
                    val request = requestBuilder.build()
                    val response = downloadOkHttpClient.newCall(request).execute()

                    val code = response.code
                    if (code == 416) {
                        // Range Not Satisfiable: el archivo ya se descargó completo
                        if (existingBytes > 0) {
                            isCompleted = true
                            response.close()
                            break
                        }
                    }

                    if (!response.isSuccessful && code != 206) {
                        response.close()
                        throw IOException("Respuesta del servidor HTTP: $code")
                    }

                    val body = response.body ?: throw IOException("Cuerpo de respuesta vacío")
                    val responseContentLength = body.contentLength()
                    val isPartial = (code == 206)

                    totalExpectedBytes = if (isPartial && existingBytes > 0) {
                        existingBytes + if (responseContentLength > 0) responseContentLength else 0L
                    } else {
                        if (responseContentLength > 0) responseContentLength else existingBytes
                    }

                    val appendMode = isPartial && existingBytes > 0
                    val outputStream = FileOutputStream(tempFile, appendMode)
                    val inputStream = body.byteStream()

                    downloadingEntity = downloadingEntity.copy(downloadStatus = "DOWNLOADING", errorMessage = null)
                    downloadRepository.insertDownload(downloadingEntity)

                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var currentDownloaded = if (appendMode) existingBytes else 0L
                    var lastNotifyTime = System.currentTimeMillis()

                    try {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            currentDownloaded += bytesRead

                            if (totalExpectedBytes > 0) {
                                val progress = ((currentDownloaded * 100) / totalExpectedBytes).toInt().coerceIn(0, 100)
                                progressFlow.value = progress

                                val now = System.currentTimeMillis()
                                if (now - lastNotifyTime > 500) {
                                    lastNotifyTime = now
                                    notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress))
                                }
                            }
                        }
                        outputStream.flush()
                        isCompleted = true
                    } finally {
                        try { outputStream.close() } catch (e: Exception) {}
                        try { inputStream.close() } catch (e: Exception) {}
                        try { response.close() } catch (e: Exception) {}
                    }

                } catch (e: Exception) {
                    attempt++
                    Log.w(TAG, "Conexión interrumpida para '$title' (intento $attempt/$maxAttempts): ${e.message}")
                    if (attempt < maxAttempts) {
                        notificationManager.notify(NOTIFICATION_ID, createWaitingNotification(title))
                        delay((attempt * 2000L).coerceAtMost(10000L))
                    }
                }
            }

            // 3. Cuando la descarga finaliza con éxito, mover a MediaStore / Almacenamiento final
            if (isCompleted && tempFile.exists() && tempFile.length() > 0) {
                try {
                    val totalBytes = tempFile.length()
                    val resolver = applicationContext.contentResolver

                    val safeTitle = title.replace(Regex("[\\/:*?\"<>|]"), "_")
                    val safeSeriesTitle = seriesTitle?.replace(Regex("[\\/:*?\"<>|]"), "_")

                    val subfolder = when (contentType) {
                        "movie" -> "Peliculas"
                        "serie" -> "Series"
                        "documentary" -> "Documentales"
                        "shortfilm" -> "Cortometrajes"
                        else -> "Otros"
                    }

                    val relativePath = if (contentType == "serie" && safeSeriesTitle != null) {
                        "Music/Audiocinemateca/$subfolder/$safeSeriesTitle/"
                    } else {
                        "Music/Audiocinemateca/$subfolder/"
                    }

                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "$safeTitle.mp3")
                        put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                    }

                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                        val rawLocation = sharedPreferencesManager.getString("download_location", MediaStore.VOLUME_EXTERNAL_PRIMARY) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                        val volumeName = if (rawLocation.startsWith("/") || rawLocation.isBlank()) MediaStore.VOLUME_EXTERNAL_PRIMARY else rawLocation
                        val collection = MediaStore.Audio.Media.getContentUri(volumeName)
                        resolver.insert(collection, contentValues)
                    } else {
                        val downloadDir = sharedPreferencesManager.getString("download_location", android.os.Environment.getExternalStorageDirectory().absolutePath)
                        val finalPath = File(File(downloadDir, "Music/Audiocinemateca"), subfolder)
                        if (!finalPath.exists()) {
                            finalPath.mkdirs()
                        }
                        val file = File(finalPath, "$safeTitle.mp3")
                        contentValues.put(MediaStore.MediaColumns.DATA, file.absolutePath)
                        resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
                    } ?: throw IOException("No se pudo registrar el archivo descargado en MediaStore")

                    resolver.openOutputStream(uri).use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }

                    val durationMs = try {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(applicationContext, uri)
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        retriever.release()
                        durationStr?.toLong() ?: 0L
                    } catch (e: Exception) {
                        0L
                    }

                    // Limpiar archivo temporal
                    tempFile.delete()

                    val finalEntity = downloadingEntity.copy(
                        downloadStatus = "COMPLETE",
                        filePath = uri.toString(),
                        totalSizeMb = totalBytes / (1024.0 * 1024.0),
                        durationMs = durationMs,
                        errorMessage = null
                    )
                    downloadRepository.insertDownload(finalEntity)
                    notificationManager.notify(NOTIFICATION_ID, createNotification(title, 100, true))

                    launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Descarga finalizada: $title", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al guardar descarga finalizada: ${e.message}")
                    val failedEntity = downloadingEntity.copy(
                        downloadStatus = "FAILED",
                        errorMessage = "Error al guardar el archivo: ${e.message}"
                    )
                    downloadRepository.insertDownload(failedEntity)
                }
            } else {
                val failedEntity = downloadingEntity.copy(
                    downloadStatus = "FAILED",
                    errorMessage = "Error de conexión tras múltiples reintentos."
                )
                downloadRepository.insertDownload(failedEntity)
            }

            downloadManager.onDownloadFinished()
            stopForeground(false)
        }
    }

    private fun createNotification(title: String, progress: Int, completed: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_downloads)
            .setOnlyAlertOnce(true)

        if (completed) {
            builder.setContentText("Descarga completada")
                .setProgress(0, 0, false)
        } else {
            builder.setContentText("Descargando... $progress%")
                .setProgress(100, progress, false)
        }
        return builder.build()
    }

    private fun createWaitingNotification(title: String): Notification {
        return NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_downloads)
            .setOnlyAlertOnce(true)
            .setContentText("Pausado: esperando conexión a internet...")
            .setProgress(0, 0, true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START_DOWNLOAD = "com.johang.audiocinemateca.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.johang.audiocinemateca.action.CANCEL_DOWNLOAD"

        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT_ID = "extra_content_id"
        const val EXTRA_CONTENT_TYPE = "extra_content_type"
        const val EXTRA_PART_INDEX = "extra_part_index"
        const val EXTRA_EPISODE_INDEX = "extra_episode_index"
        const val EXTRA_SERIES_TITLE = "extra_series_title"

        fun getStartIntent(context: Context, url: String, title: String, contentId: String, contentType: String, partIndex: Int, episodeIndex: Int, seriesTitle: String?): Intent {
            return Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_CONTENT_ID, contentId)
                putExtra(EXTRA_CONTENT_TYPE, contentType)
                putExtra(EXTRA_PART_INDEX, partIndex)
                putExtra(EXTRA_EPISODE_INDEX, episodeIndex)
                putExtra(EXTRA_SERIES_TITLE, seriesTitle)
            }
        }
    }
}
