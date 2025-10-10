package com.johang.audiocinemateca.data.repository

import com.johang.audiocinemateca.data.local.dao.DownloadDao
import com.johang.audiocinemateca.data.local.entities.DownloadEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
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

    fun getFilePathFromUri(context: android.content.Context, uri: android.net.Uri): String? {
        var filePath: String? = null
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                filePath = cursor.getString(columnIndex)
            }
        }
        return filePath
    }

    fun deleteEmptyDirectory(filePath: String) {
        try {
            val file = java.io.File(filePath)
            val parentDir = file.parentFile
            if (parentDir != null && parentDir.isDirectory && parentDir.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        } catch (e: Exception) {
            // Log error, but don't crash
            android.util.Log.e("DownloadRepository", "Error deleting empty directory", e)
        }
    }

    fun deleteDownloadedFile(context: android.content.Context, fileUriString: String): FileDeletionResult {
        return try {
            val fileUri = android.net.Uri.parse(fileUriString)
            // A reliable way to check for existence with content URIs is to try to open a stream.
            // If it fails with FileNotFoundException, we know it's gone.
            android.util.Log.d("DownloadRepository", "Attempting to delete file with URI: $fileUriString")
            context.contentResolver.openInputStream(fileUri)?.close()

            val rowsDeleted = context.contentResolver.delete(fileUri, null, null)
            android.util.Log.d("DownloadRepository", "MediaStore.delete returned $rowsDeleted rows affected for URI: $fileUriString")

            if (rowsDeleted > 0) {
                FileDeletionResult.Success
            } else {
                // This case is tricky, delete might return 0 if the row was already gone,
                // but we already confirmed existence. So this is a legitimate failure.
                FileDeletionResult.Failure(Exception("MediaStore.delete devolvió 0 filas afectadas."))
            }
        } catch (e: java.io.FileNotFoundException) {
            // The file does not exist, which is a success condition for our purpose.
            FileDeletionResult.FileDoesNotExist
        } catch (e: Exception) {
            // Any other exception is a failure.
            FileDeletionResult.Failure(e)
        }
    }
}
