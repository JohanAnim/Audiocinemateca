package com.johang.audiocinemateca.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.johang.audiocinemateca.data.local.CatalogRepository
import com.johang.audiocinemateca.data.remote.ApiClient
import com.johang.audiocinemateca.data.remote.VersionResponse
import com.johang.audiocinemateca.data.remote.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.Date
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import kotlin.text.RegexOption
import dagger.hilt.android.qualifiers.ApplicationContext

import com.johang.audiocinemateca.data.repository.LoginRepository
import com.johang.audiocinemateca.domain.model.LoginResult

class AuthCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogRepository: CatalogRepository,
    private val authService: AuthService
) : LoginRepository {

    private val AUTH_URL = "https://audiocinemateca.com/system/files/catalogo/version.json.gz"
    private val CATALOG_URL = "https://audiocinemateca.com/system/files/catalogo/catalogo.json.gz"
    private val BAN_DURATION_MS = 2 * 60 * 60 * 1000L
    private val FAILED_ATTEMPTS_KEY = "failedAttempts"
    private val BAN_END_TIME_KEY = "banEndTime"
    private val APP_VERSION_KEY = "appVersion"
    private val storedUsernameKey = "storedUsername"
    private val storedPasswordKey = "storedPassword"

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var failedAttempts: Int
    private var banEndTime: Long?

    init {
        failedAttempts = prefs.getString(FAILED_ATTEMPTS_KEY, "0")?.toIntOrNull() ?: 0
        banEndTime = if (prefs.contains(BAN_END_TIME_KEY)) prefs.getLong(BAN_END_TIME_KEY, 0L) else null
        checkBanStatusOnLoad()
    }

    fun getStoredUsername(): String? = prefs.getString(storedUsernameKey, null)
    fun getStoredPassword(): String? = prefs.getString(storedPasswordKey, null)

    private fun loadBanState() {
        failedAttempts = prefs.getString(FAILED_ATTEMPTS_KEY, "0")?.toIntOrNull() ?: 0
        banEndTime = if (prefs.contains(BAN_END_TIME_KEY)) prefs.getLong(BAN_END_TIME_KEY, 0L) else null
    }

    private fun saveBanState() {
        prefs.edit().apply {
            putInt(FAILED_ATTEMPTS_KEY, failedAttempts)
            if (banEndTime != null) {
                putLong(BAN_END_TIME_KEY, banEndTime!!)
            } else {
                remove(BAN_END_TIME_KEY)
            }
            apply()
        }
    }

    private fun checkBanStatusOnLoad() {
        if (banEndTime != null && System.currentTimeMillis() >= banEndTime!!) {
            resetBan()
        }
    }

    override fun isBanned(): Boolean {
        return banEndTime != null && System.currentTimeMillis() < banEndTime!!
    }

    override fun getRemainingBanTime(): String {
        if (banEndTime == null) {
            return ""
        }
        val remainingMs = banEndTime!! - System.currentTimeMillis()
        if (remainingMs <= 0) {
            resetBan()
            return ""
        }

        val hours = remainingMs / (1000 * 60 * 60)
        val minutes = (remainingMs % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (remainingMs % (1000 * 60)) / 1000

        var timeString = ""
        if (hours > 0) {
            timeString += "$hours hora${if (hours != 1L) "s" else ""} "
        }
        if (minutes > 0) {
            timeString += "$minutes minuto${if (minutes != 1L) "s" else ""} "
        }
        timeString += "$seconds segundo${if (seconds != 1L) "s" else ""}"

        return timeString.trim()
    }

    fun handleSuccessfulLogin() {
        resetBan()
    }

    fun handleFailedLogin() {
        failedAttempts++
        if (failedAttempts >= 10 && banEndTime == null) {
            banEndTime = System.currentTimeMillis() + BAN_DURATION_MS
        }
        saveBanState()
    }

    suspend fun getCatalogVersion(): Date? {
        return catalogRepository.getCatalogVersion()
    }

    fun logout() {
        prefs.edit().remove(storedUsernameKey).remove(storedPasswordKey).apply()
    }

    private fun resetBan() {
        failedAttempts = 0
        banEndTime = null
        saveBanState()
    }

    override suspend fun isUserLoggedIn(): Boolean = withContext(Dispatchers.IO) {
        return@withContext getStoredUsername() != null && getStoredPassword() != null
    }

    override suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val authString = "Basic " + Base64.encodeToString("${username}:${password}".toByteArray(), Base64.NO_WRAP)
        try {
            val response = authService.getVersion(authString)
            if (response.isSuccessful && response.body() != null) {
                val decompressed = decompressGzip(response.body()!!.byteStream())
                val cleanedString = decompressed.trim()
                val jsonMatch = """(?s)\{.*\}""".toRegex().find(cleanedString)

                val newVersionData: VersionResponse
                jsonMatch?.let {
                    newVersionData = gson.fromJson(it.value, VersionResponse::class.java)
                } ?: run {
                    Log.e("AuthCatalogRepository", "No se encontró un objeto JSON válido en la respuesta.")
                    return@withContext com.johang.audiocinemateca.domain.model.LoginResult.Error("No se encontró un objeto JSON válido en la respuesta.")
                }
                val oldVersionJson = prefs.getString(APP_VERSION_KEY, null)
                val updateRequired = if (oldVersionJson != null) {
                    val oldVersionData = gson.fromJson(oldVersionJson, VersionResponse::class.java)
                    newVersionData != oldVersionData
                } else {
                    true // Si no hay versión antigua, se considera que se requiere actualización
                }

                prefs.edit()
                    .putString(APP_VERSION_KEY, gson.toJson(newVersionData))
                    .putString(storedUsernameKey, username)
                    .putString(storedPasswordKey, password)
                    .apply()

                com.johang.audiocinemateca.domain.model.LoginResult.Success(updateRequired = updateRequired)
            } else {
                Log.w("AuthCatalogRepository", "La respuesta no fue exitosa o no contenía cuerpo. Estado: ${response.code()}")
                com.johang.audiocinemateca.domain.model.LoginResult.Error("La respuesta no fue exitosa o no contenía cuerpo.", response.code())
            }
        } catch (e: Exception) {
            Log.e("AuthCatalogRepository", "Error en la petición HTTP o procesamiento: ${e.message}", e)
            com.johang.audiocinemateca.domain.model.LoginResult.Error("Error en la petición HTTP o procesamiento: ${e.message}")
        }
    }

    private suspend fun loadVersion(): Date? = withContext(Dispatchers.IO) {
        val username = getStoredUsername() ?: return@withContext null
        val password = getStoredPassword() ?: return@withContext null
        val authString = "Basic " + Base64.encodeToString("${username}:${password}".toByteArray(), Base64.NO_WRAP)
        try {
            val response = authService.getVersion(authString)
            if (response.isSuccessful && response.body() != null) {
                val decompressed = decompressGzip(response.body()!!.byteStream())
                val cleanedString = decompressed.trim()
                val jsonMatch = """(?s)\{.*\}""".toRegex().find(cleanedString)

                val versionData: VersionResponse
                jsonMatch?.let {
                    versionData = gson.fromJson(it.value, VersionResponse::class.java)
                } ?: run {
                    Log.w("AuthCatalogRepository", "loadVersion: No se encontró un objeto JSON válido en la respuesta.")
                    return@withContext null
                }
                java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, versionData.year)
                    set(java.util.Calendar.MONTH, versionData.mon - 1) // Month is 0-indexed
                    set(java.util.Calendar.DAY_OF_MONTH, versionData.mday)
                    set(java.util.Calendar.HOUR_OF_DAY, versionData.hours)
                    set(java.util.Calendar.MINUTE, versionData.minutes)
                    set(java.util.Calendar.SECOND, versionData.seconds)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.time
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AuthCatalogRepository", "Error al cargar la versión: ${e.message}", e)
            null
        }
    }

    suspend fun loadCatalog(): Flow<LoadCatalogResultWithProgress> = channelFlow {
        val serverCatalogDate = loadVersion()
        if (serverCatalogDate == null) {
            send(LoadCatalogResultWithProgress.Error("No se pudo obtener la versión del catálogo del servidor."))
            return@channelFlow
        }

        try {
            val localCatalogVersionDate = catalogRepository.getCatalogVersion()
            var needsDownload = true

            if (localCatalogVersionDate != null) {
                // Truncate milliseconds and seconds for a more lenient comparison
                val localCalendar = java.util.Calendar.getInstance().apply { time = localCatalogVersionDate; set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }
                val serverCalendar = java.util.Calendar.getInstance().apply { time = serverCatalogDate; set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }

                if (localCalendar.time.time >= serverCalendar.time.time) {
                    needsDownload = false
                }
            }

            if (needsDownload) {
                if (localCatalogVersionDate != null) {
                    // Hay una versión local, así que podemos preguntar al usuario
                    // Esto se manejará en la ViewModel/UI, el repositorio solo indica la necesidad
                    send(LoadCatalogResultWithProgress.UpdateAvailable(serverCatalogDate, localCatalogVersionDate))
                } else {
                    // No hay versión local, la descarga es obligatoria
                    downloadAndSaveCatalog(serverCatalogDate).collect { send(it) }
                }
            }
            else {
                val localCatalog = catalogRepository.getCatalog()
                if (localCatalog != null) {
                    send(LoadCatalogResultWithProgress.Success(localCatalog))
                } else {
                    // Si no hay catálogo local a pesar de que la versión es la misma, forzar descarga
                    downloadAndSaveCatalog(serverCatalogDate).collect { send(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("AuthCatalogRepository", "Error al acceder al repositorio local: ${e.message}", e)
            send(LoadCatalogResultWithProgress.Error("Error al acceder al repositorio local: ${e.message}"))
        }
    }

    suspend fun downloadAndSaveCatalog(newVersionDate: Date): Flow<LoadCatalogResultWithProgress> = channelFlow {
        val username = getStoredUsername()
        val password = getStoredPassword()
        val authString = if (username != null && password != null) {
            "Basic " + Base64.encodeToString("${username}:${password}".toByteArray(), Base64.NO_WRAP)
        } else {
            send(LoadCatalogResultWithProgress.Error("No hay credenciales para descargar el catálogo."))
            return@channelFlow
        }

        // Lanzar la recolección de progreso en una corrutina separada
        val progressJob = launch {
            ApiClient.downloadProgressFlow.collect { progress ->
                // Escalar el progreso de descarga (0-100) a 0-80%
                send(LoadCatalogResultWithProgress.Progress((progress * 0.8).toInt()))
            }
        }

        try {
            val response = authService.getCatalog(authString, "identity")
            if (response.isSuccessful && response.body() != null) {
                // Progreso de descompresión (80-90%)
                send(LoadCatalogResultWithProgress.Progress(80))
                val decompressed = decompressGzip(response.body()!!.byteStream())
                send(LoadCatalogResultWithProgress.Progress(85))

                val cleanedString = decompressed.trim()
                val catalogResponse = gson.fromJson(cleanedString, com.johang.audiocinemateca.data.model.CatalogResponse::class.java)

                // Progreso de guardado en DB (90-100%)
                send(LoadCatalogResultWithProgress.Progress(90))
                catalogRepository.saveCatalog(catalogResponse)
                catalogRepository.saveCatalogVersion(newVersionDate)
                send(LoadCatalogResultWithProgress.Progress(100))

                send(LoadCatalogResultWithProgress.Success(catalogResponse))
            } else {
                Log.e("AuthCatalogRepository", "Cuerpo de respuesta nulo para el catálogo. Estado: ${response.code()}")
                send(LoadCatalogResultWithProgress.Error("Respuesta inválida o cuerpo vacío para el catálogo. Estado: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("AuthCatalogRepository", "Error al descargar el catálogo: ${e.message}", e)
            val errorMessage = "Error al descargar o procesar el catálogo: ${e.message}"
            // Show a Toast on the main thread for immediate feedback and copy to clipboard
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, errorMessage, android.widget.Toast.LENGTH_LONG).show()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("error", errorMessage)
                clipboard.setPrimaryClip(clip)
            }
            send(LoadCatalogResultWithProgress.Error(errorMessage))
        } finally {
            progressJob.cancel() // Cancelar el job de progreso cuando la descarga termine
        }
    }

    private fun decompressGzip(inputStream: java.io.InputStream): String {
        val gzipInputStream = GZIPInputStream(inputStream)
        val reader = InputStreamReader(gzipInputStream, "UTF-8")
        return reader.readText()
    }

    // Extension function to convert JSONObject to Map<String, Any>
    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr: Iterator<String> = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value: Any = this.get(key)
            when (value) {
                is JSONObject -> value = value.toMap()
            }
            map[key] = value
        }
        return map
    }

    

    sealed class LoadCatalogResultWithProgress {
        data class Progress(val percent: Int) : LoadCatalogResultWithProgress()
        data class Success(val catalog: com.johang.audiocinemateca.data.model.CatalogResponse) : LoadCatalogResultWithProgress()
        data class UpdateAvailable(val serverVersion: Date, val localVersion: Date) : LoadCatalogResultWithProgress()
        data class Error(val message: String) : LoadCatalogResultWithProgress()
    }
}