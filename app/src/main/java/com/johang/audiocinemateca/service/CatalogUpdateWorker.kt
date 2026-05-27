package com.johang.audiocinemateca.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.johang.audiocinemateca.data.AuthCatalogRepository
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.model.CatalogResponse
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.johang.audiocinemateca.MainActivity
import com.johang.audiocinemateca.R

class CatalogUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CatalogUpdateWorkerEntryPoint {
        fun authCatalogRepository(): AuthCatalogRepository
        fun catalogRepository(): CatalogRepository
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CatalogUpdateWorkerEntryPoint::class.java
        )
        val authCatalogRepository = entryPoint.authCatalogRepository()
        val catalogRepository = entryPoint.catalogRepository()

        Log.d("CatalogUpdateWorker", "Checking for catalog updates in background...")

        try {
            val loadResult = authCatalogRepository.loadCatalog().first { 
                it is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable || 
                it is AuthCatalogRepository.LoadCatalogResultWithProgress.Success ||
                it is AuthCatalogRepository.LoadCatalogResultWithProgress.Error
            }

            if (loadResult is AuthCatalogRepository.LoadCatalogResultWithProgress.UpdateAvailable) {
                Log.d("CatalogUpdateWorker", "Update found! Downloading...")
                
                val oldCatalog = catalogRepository.getCatalog()
                
                val downloadResult = authCatalogRepository.downloadAndSaveCatalog(loadResult.serverVersion).first {
                    it is AuthCatalogRepository.LoadCatalogResultWithProgress.Success ||
                    it is AuthCatalogRepository.LoadCatalogResultWithProgress.Error
                }

                if (downloadResult is AuthCatalogRepository.LoadCatalogResultWithProgress.Success) {
                    val newCatalog = downloadResult.catalog
                    showUpdateNotification(oldCatalog, newCatalog)
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e("CatalogUpdateWorker", "Error during background catalog update", e)
            return Result.retry()
        }

        return Result.success()
    }

    private fun showUpdateNotification(oldCatalog: CatalogResponse?, newCatalog: CatalogResponse) {
        val moviesDiff = (newCatalog.movies?.size ?: 0) - (oldCatalog?.movies?.size ?: 0)
        val seriesDiff = (newCatalog.series?.size ?: 0) - (oldCatalog?.series?.size ?: 0)
        val shortsDiff = (newCatalog.shortFilms?.size ?: 0) - (oldCatalog?.shortFilms?.size ?: 0)
        val docsDiff = (newCatalog.documentaries?.size ?: 0) - (oldCatalog?.documentaries?.size ?: 0)

        val totalNew = moviesDiff.coerceAtLeast(0) + seriesDiff.coerceAtLeast(0) + 
                       shortsDiff.coerceAtLeast(0) + docsDiff.coerceAtLeast(0)

        if (totalNew <= 0) return // No hay títulos nuevos realmente, tal vez solo cambios menores

        val title = "¡Nuevas aventuras te esperan!"
        val message = StringBuilder("Se han añadido $totalNew nuevos títulos al catálogo: ")
        val parts = mutableListOf<String>()
        if (moviesDiff > 0) parts.add("$moviesDiff películas")
        if (seriesDiff > 0) parts.add("$seriesDiff series")
        if (shortsDiff > 0) parts.add("$shortsDiff cortos")
        if (docsDiff > 0) parts.add("$docsDiff documentales")
        
        message.append(parts.joinToString(", "))
        message.append(". ¡Entra ahora para descubrirlos!")

        val channelId = "catalog_updates"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message.toString())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.toString()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Actualizaciones del Catálogo", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        notificationManager.notify(1001, builder.build())
    }
}
