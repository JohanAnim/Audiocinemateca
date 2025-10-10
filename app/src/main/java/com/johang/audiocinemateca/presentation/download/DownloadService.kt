package com.johang.audiocinemateca.presentation.download

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                handleStartDownload(intent)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
                // This should not happen if the flow is correct, but as a fallback
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

            // Update status to DOWNLOADING
            val downloadingEntity = downloadEntity.copy(downloadStatus = "DOWNLOADING")
            downloadRepository.insertDownload(downloadingEntity)

            // Observar el progreso
            launch(Dispatchers.Main) {
                progressFlow.collect { progress ->
                    notificationManager.notify(NOTIFICATION_ID, createNotification(title, progress))
                }
            }

            try {
                val request = Request.Builder().url(url).build()
                val response = downloadOkHttpClient.newCall(request).execute()

                if (!response.isSuccessful) throw IOException("Error en la descarga: ${response.code}")

                val body = response.body ?: throw IOException("Cuerpo de respuesta vacío")
                val totalBytes = body.contentLength()

                val resolver = applicationContext.contentResolver

                // Sanitize file and folder names
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
                    val volumeName = sharedPreferencesManager.getString("download_location", MediaStore.VOLUME_EXTERNAL_PRIMARY)
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
                } ?: throw IOException("No se pudo crear el archivo en MediaStore")

                resolver.openOutputStream(uri).use { outputStream ->
                    body.byteStream().use { inputStream ->
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
                    0L // Fallback to 0 if duration can't be retrieved
                }

                val finalEntity = downloadingEntity.copy(
                    downloadStatus = "COMPLETE",
                    filePath = uri.toString(),
                    totalSizeMb = totalBytes / (1024.0 * 1024.0),
                    durationMs = durationMs
                )
                downloadRepository.insertDownload(finalEntity)
                notificationManager.notify(NOTIFICATION_ID, createNotification(title, 100, true))

                // Show a toast on the main thread
                launch(Dispatchers.Main) {
                    android.widget.Toast.makeText(applicationContext, "Descarga finalizada: $title", android.widget.Toast.LENGTH_SHORT).show()
                }

            } catch (e: IOException) {
                val failedEntity = downloadingEntity.copy(
                    downloadStatus = "FAILED",
                    errorMessage = e.message ?: "Error desconocido"
                )
                downloadRepository.insertDownload(failedEntity)
                // Aquí se podría mostrar una notificación de fallo
            } finally {
                downloadManager.onDownloadFinished()
                stopForeground(false) // False para que la notificación de completado/fallo permanezca
            }
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

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
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
