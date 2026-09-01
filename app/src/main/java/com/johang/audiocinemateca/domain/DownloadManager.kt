package com.johang.audiocinemateca.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.repository.DownloadRepository
import com.johang.audiocinemateca.domain.model.DownloadProgressInfo
import com.johang.audiocinemateca.presentation.download.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
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

    private val _activeProgressMap = MutableStateFlow<Map<String, DownloadProgressInfo>>(emptyMap())
    val activeProgressFlow: StateFlow<Map<String, DownloadProgressInfo>> = _activeProgressMap.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Calcula la concurrencia máxima óptima:
     * - En WiFi, Ethernet o redes no medidas: 3 descargas en paralelo (balance ideal de velocidad y finalización secuencial rápida).
     * - En datos móviles / redes medidas: 2 descargas en paralelo para optimizar ancho de banda.
     */
    private fun getMaxConcurrentDownloads(): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return 3
        val activeNetwork = cm.activeNetwork ?: return 2
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return 2

        val isHighSpeed = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return if (isHighSpeed) 3 else 2
    }

    fun updateProgress(info: DownloadProgressInfo) {
        val key = "${info.contentId}_${info.partIndex}_${info.episodeIndex}"
        val current = _activeProgressMap.value.toMutableMap()
        current[key] = info
        _activeProgressMap.value = current
    }

    fun clearProgress(contentId: String, partIndex: Int, episodeIndex: Int) {
        val key = "${contentId}_${partIndex}_${episodeIndex}"
        if (_activeProgressMap.value.containsKey(key)) {
            val current = _activeProgressMap.value.toMutableMap()
            current.remove(key)
            _activeProgressMap.value = current
        }
    }

    fun clearAllProgressForSeason(contentId: String, seasonIndex: Int) {
        val prefix = "${contentId}_${seasonIndex}_"
        val current = _activeProgressMap.value.toMutableMap()
        val toRemove = current.keys.filter { it.startsWith(prefix) }
        if (toRemove.isNotEmpty()) {
            toRemove.forEach { current.remove(it) }
            _activeProgressMap.value = current
        }
    }

    fun enqueueDownload(request: DownloadRequest) {
        coroutineScope.launch {
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

    fun enqueueBatch(requests: List<DownloadRequest>) {
        coroutineScope.launch {
            requests.forEach { request ->
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
            }
            processQueue()
        }
    }

    fun cancelDownload(contentId: String, partIndex: Int, episodeIndex: Int) {
        coroutineScope.launch {
            // 1. Quitar de la cola de espera
            downloadQueue.removeAll { it.contentId == contentId && it.partIndex == partIndex && it.episodeIndex == episodeIndex }

            // 2. Limpiar progreso
            clearProgress(contentId, partIndex, episodeIndex)

            // 3. Notificar al servicio para detener el hilo activo
            val intent = DownloadService.getCancelIntent(context, contentId, partIndex, episodeIndex)
            context.startService(intent)

            // 4. Limpiar registro en base de datos si no estaba completo
            val entity = downloadRepository.getDownload(contentId, partIndex, episodeIndex)
            if (entity != null && entity.downloadStatus != "COMPLETE") {
                downloadRepository.deleteDownload(contentId, partIndex, episodeIndex)
            }
        }
    }

    fun cancelSeason(contentId: String, seasonIndex: Int) {
        coroutineScope.launch {
            // 1. Quitar todos los episodios de esta temporada de la cola
            downloadQueue.removeAll { it.contentId == contentId && it.partIndex == seasonIndex }

            // 2. Limpiar estados de progreso de la temporada
            clearAllProgressForSeason(contentId, seasonIndex)

            // 3. Notificar al servicio para detener todas las descargas activas de esta temporada
            val intent = DownloadService.getCancelIntent(context, contentId, seasonIndex, -1)
            context.startService(intent)

            // 4. Eliminar del almacenamiento físico y de la base de datos todos los episodios de esta temporada
            val downloads = downloadRepository.getDownloadsForContent(contentId).firstOrNull() ?: emptyList()
            downloads.filter { it.partIndex == seasonIndex }.forEach { entity ->
                if (!entity.filePath.isNullOrBlank()) {
                    downloadRepository.deleteDownloadedFile(context, entity.filePath)
                }
                downloadRepository.deleteDownload(contentId, entity.partIndex, entity.episodeIndex)
            }

            for (epIndex in 0..200) {
                val entity = downloadRepository.getDownload(contentId, seasonIndex, epIndex) ?: continue
                if (!entity.filePath.isNullOrBlank()) {
                    downloadRepository.deleteDownloadedFile(context, entity.filePath)
                }
                downloadRepository.deleteDownload(contentId, seasonIndex, epIndex)
            }
        }
    }

    fun cancelAllForContent(contentId: String) {
        coroutineScope.launch {
            downloadQueue.removeAll { it.contentId == contentId }
            val current = _activeProgressMap.value.toMutableMap()
            val toRemove = current.keys.filter { it.startsWith("${contentId}_") }
            toRemove.forEach { current.remove(it) }
            _activeProgressMap.value = current

            val intent = DownloadService.getCancelIntent(context, contentId, -1, -1)
            context.startService(intent)
        }
    }

    @Synchronized
    private fun processQueue() {
        val maxConcurrent = getMaxConcurrentDownloads()
        while (activeDownloads.get() < maxConcurrent && downloadQueue.isNotEmpty()) {
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
