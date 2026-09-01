package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.johang.audiocinemateca.data.local.dao.DownloadDao
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao,
    private val sharedPreferencesManager: SharedPreferencesManager
) {

    fun getAllDownloads(): Flow<List<DownloadEntity>> {
        return downloadDao.getAllDownloads()
    }

    fun getDownloadsForContent(contentId: String): Flow<List<DownloadEntity>> {
        return downloadDao.getDownloadsForContent(contentId)
    }

    suspend fun getDownload(contentId: String, partIndex: Int, episodeIndex: Int): DownloadEntity? {
        return downloadDao.getDownload(contentId, partIndex, episodeIndex)
    }

    suspend fun insertDownload(download: DownloadEntity) {
        downloadDao.insertDownload(download)
    }

    suspend fun deleteDownload(contentId: String, partIndex: Int, episodeIndex: Int) {
        downloadDao.deleteDownload(contentId, partIndex, episodeIndex)
    }

    suspend fun deleteAllDownloads() {
        downloadDao.deleteAllDownloads()
    }

    sealed class FileDeletionResult {
        object Success : FileDeletionResult()
        object FileDoesNotExist : FileDeletionResult()
        data class Failure(val exception: Exception) : FileDeletionResult()
    }

    fun getFilePathFromUri(context: Context, uri: Uri): String? {
        var filePath: String? = null
        val projection = arrayOf(MediaStore.MediaColumns.DATA)
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    filePath = cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error getting file path from URI: ${e.message}")
        }
        return filePath
    }

    fun deleteEmptyDirectory(filePath: String) {
        try {
            val file = File(filePath)
            val parentDir = file.parentFile
            cleanupDirectoryIfEmpty(parentDir)
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error deleting empty directory: ${e.message}")
        }
    }

    fun deleteSeriesDirectoryIfEmpty(seriesTitle: String) {
        try {
            val safeSeriesTitle = seriesTitle.replace(Regex("[\\/:*?\"<>|]"), "_")
            val rawLocation = sharedPreferencesManager.getString("download_location", "") ?: ""

            // 1. Directorio público estándar (Music/Audiocinemateca/Series/{safeSeriesTitle})
            val defaultMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val seriesDir1 = File(File(defaultMusicDir, "Audiocinemateca/Series"), safeSeriesTitle)
            cleanupDirectoryIfEmpty(seriesDir1)

            // 2. Directorio personalizado si está configurado en SharedPreferences
            if (rawLocation.isNotBlank() && rawLocation.startsWith("/")) {
                val seriesDir2 = File(File(File(rawLocation, "Music/Audiocinemateca"), "Series"), safeSeriesTitle)
                cleanupDirectoryIfEmpty(seriesDir2)
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error deleting series directory: ${e.message}")
        }
    }

    fun cleanupDirectoryIfEmpty(dir: File?) {
        try {
            if (dir != null && dir.exists() && dir.isDirectory) {
                val contents = dir.listFiles()
                val nonTmpFiles = contents?.filter { !it.name.endsWith(".tmp") } ?: emptyList()
                if (nonTmpFiles.isEmpty()) {
                    contents?.forEach { it.delete() }
                    dir.delete()
                    Log.d("DownloadRepository", "Directorio eliminado por estar vacío: ${dir.absolutePath}")

                    val parent = dir.parentFile
                    if (parent != null && parent.isDirectory && (parent.name == "Series" || parent.name == "Peliculas" || parent.name == "Documentales" || parent.name == "Cortometrajes") && parent.listFiles().isNullOrEmpty()) {
                        parent.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error cleaning directory: ${e.message}")
        }
    }

    fun cleanupAllEmptyDirectories() {
        try {
            val defaultMusicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val audioCinematecaDir = File(defaultMusicDir, "Audiocinemateca")
            if (audioCinematecaDir.exists() && audioCinematecaDir.isDirectory) {
                audioCinematecaDir.walkBottomUp().forEach { file ->
                    if (file.isDirectory && file.listFiles().isNullOrEmpty()) {
                        file.delete()
                    }
                }
            }
            val rawLocation = sharedPreferencesManager.getString("download_location", "") ?: ""
            if (rawLocation.isNotBlank() && rawLocation.startsWith("/")) {
                val customDir = File(rawLocation, "Music/Audiocinemateca")
                if (customDir.exists() && customDir.isDirectory) {
                    customDir.walkBottomUp().forEach { file ->
                        if (file.isDirectory && file.listFiles().isNullOrEmpty()) {
                            file.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DownloadRepository", "Error cleaning all empty directories: ${e.message}")
        }
    }

    fun deleteDownloadedFile(context: Context, fileUriString: String): FileDeletionResult {
        return try {
            val fileUri = Uri.parse(fileUriString)
            Log.d("DownloadRepository", "Attempting to delete file with URI: $fileUriString")
            val filePath = getFilePathFromUri(context, fileUri)
            context.contentResolver.openInputStream(fileUri)?.close()

            val rowsDeleted = context.contentResolver.delete(fileUri, null, null)
            Log.d("DownloadRepository", "MediaStore.delete returned $rowsDeleted rows affected for URI: $fileUriString")

            if (filePath != null) {
                deleteEmptyDirectory(filePath)
            }

            if (rowsDeleted > 0) {
                FileDeletionResult.Success
            } else {
                FileDeletionResult.Failure(Exception("MediaStore.delete devolvió 0 filas afectadas."))
            }
        } catch (e: FileNotFoundException) {
            FileDeletionResult.FileDoesNotExist
        } catch (e: Exception) {
            FileDeletionResult.Failure(e)
        }
    }
}
