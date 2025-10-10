package com.johang.audiocinemateca.domain

import android.content.Context
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.presentation.download.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRequest(
    val url: String,
    val title: String,
    val contentId: String,
    val contentType: String,
    val partIndex: Int,
    val episodeIndex: Int,
    val seriesTitle: String?
)

@Singleton
class DownloadManager @Inject constructor(
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context
) {

    private val downloadQueue = ConcurrentLinkedQueue<DownloadRequest>()
    private val activeDownloads = AtomicInteger(0)
    private val maxConcurrentDownloads = 3

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun enqueueDownload(request: DownloadRequest) {
        coroutineScope.launch {
            // Create an entity with QUEUED status
            val entity = DownloadEntity(
                contentId = request.contentId,
                contentType = request.contentType,
                title = request.title,
                partIndex = request.partIndex,
                episodeIndex = request.episodeIndex,
                downloadStatus = "QUEUED",
                filePath = null,
                downloadedAt = System.currentTimeMillis(),
                totalSizeMb = 0.0,
                durationMs = 0L,
                errorMessage = null
            )
            downloadRepository.insertDownload(entity)
            downloadQueue.add(request)
            processQueue()
        }
    }

    @Synchronized
    private fun processQueue() {
        while (activeDownloads.get() < maxConcurrentDownloads && downloadQueue.isNotEmpty()) {
            val request = downloadQueue.poll() ?: continue
            activeDownloads.incrementAndGet()

            val intent = DownloadService.getStartIntent(
                context = context,
                url = request.url,
                title = request.title,
                contentId = request.contentId,
                contentType = request.contentType,
                partIndex = request.partIndex,
                episodeIndex = request.episodeIndex,
                seriesTitle = request.seriesTitle
            )
            context.startService(intent)
        }
    }

    fun onDownloadFinished() {
        activeDownloads.decrementAndGet()
        processQueue()
    }
}
