package com.johang.audiocinemateca.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.johang.audiocinemateca.data.local.SharedPreferencesManager
import com.johang.audiocinemateca.domain.model.SemVer
import com.johang.audiocinemateca.domain.model.UpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ServerMigrationStatus(
    val minOnlineVersion: String = ServerMigrationManager.REQUIRED_SERVER_VERSION,
    val isMigrationRequired: Boolean = false,
    val isWarningActive: Boolean = false,
    val customMessage: String = "",
    val latestUpdateInfo: UpdateInfo? = null,
    val firestorePermissionDenied: Boolean = false,
    val firestoreUnavailable: Boolean = false
) {
    val shouldShowBanner: Boolean
        get() = isMigrationRequired || isWarningActive

    fun resolvedMessage(): String {
        if (customMessage.isNotBlank()) return customMessage
        return when {
            isMigrationRequired && firestorePermissionDenied ->
                "El servidor anterior de Firebase fue desconectado. Para seguir usando Comunidad, Perfil en la Nube y sincronización en línea en el nuevo servidor propio, actualiza a Audiocinemateca $minOnlineVersion o superior.\n\nEl catálogo local, tus descargas, el reproductor y Aura siguen funcionando con normalidad."
            isMigrationRequired ->
                "Las funciones en línea de Audiocinemateca (Comunidad, Perfil en la Nube y sincronización) han migrado a nuestro nuevo servidor propio y requieren la versión $minOnlineVersion o superior.\n\nTus descargas, reproducción y Aura continúan funcionando sin problemas."
            else ->
                "Aviso próximo: pronto desconectaremos el servidor anterior de Firebase para pasar a nuestro nuevo servidor propio. Para seguir usando las funciones en línea necesitarás actualizar a Audiocinemateca $minOnlineVersion en cuanto esté disponible."
        }
    }
}

/**
 * Coordina el estado de migración hacia el nuevo servidor propio (>= 3.5.0) combinando:
 * 1) Documento remoto `app_config/server_migration` en Firestore (con respaldo local en preferencias).
 * 2) Detección de errores `PERMISSION_DENIED` o desconexión de Firestore en Comunidad y Nube.
 * 3) Detección automática cuando GitHub Releases publica una versión >= 3.5.0.
 */
@Singleton
class ServerMigrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val sharedPreferencesManager: SharedPreferencesManager
) {
    companion object {
        private const val TAG = "ServerMigrationMgr"
        const val REQUIRED_SERVER_VERSION = "3.5.0"
        private const val COLLECTION_APP_CONFIG = "app_config"
        private const val DOC_SERVER_MIGRATION = "server_migration"

        private const val KEY_CACHED_NOTICE_ACTIVE = "migration_cached_notice_active"
        private const val KEY_CACHED_REQUIRE_UPDATE = "migration_cached_require_update"
        private const val KEY_CACHED_MIN_VERSION = "migration_cached_min_version"
        private const val KEY_CACHED_MESSAGE = "migration_cached_message"
        private const val KEY_DISMISSED_WARNING_FOR_VERSION = "migration_dismissed_warning_version"
    }

    private var configListener: ListenerRegistration? = null
    private var hasShownDialogInSession = false

    private val _status = MutableStateFlow(buildInitialCachedStatus())
    val status: StateFlow<ServerMigrationStatus> = _status.asStateFlow()

    fun getInstalledVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "3.4.0"
        } catch (_: Exception) {
            "3.4.0"
        }
    }

    fun isVersionBelowRequired(
        minVersion: String = _status.value.minOnlineVersion,
        installedVersion: String = getInstalledVersionName()
    ): Boolean {
        return SemVer.isNewer(minVersion, installedVersion)
    }

    private fun buildInitialCachedStatus(): ServerMigrationStatus {
        val cachedMin = sharedPreferencesManager.getString(KEY_CACHED_MIN_VERSION, REQUIRED_SERVER_VERSION)
            ?.ifBlank { REQUIRED_SERVER_VERSION } ?: REQUIRED_SERVER_VERSION
        val belowRequired = SemVer.isNewer(cachedMin, getInstalledVersionName())
        if (!belowRequired) {
            return ServerMigrationStatus(minOnlineVersion = cachedMin)
        }
        val cachedNotice = sharedPreferencesManager.getBoolean(KEY_CACHED_NOTICE_ACTIVE, false)
        val cachedRequire = sharedPreferencesManager.getBoolean(KEY_CACHED_REQUIRE_UPDATE, false)
        val cachedMsg = sharedPreferencesManager.getString(KEY_CACHED_MESSAGE, "") ?: ""
        return ServerMigrationStatus(
            minOnlineVersion = cachedMin,
            isMigrationRequired = cachedRequire,
            isWarningActive = cachedNotice && !cachedRequire,
            customMessage = cachedMsg
        )
    }

    fun startMonitoringConfig() {
        if (configListener != null) return
        configListener = firestore.collection(COLLECTION_APP_CONFIG)
            .document(DOC_SERVER_MIGRATION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Listener app_config/server_migration reportó error: ${error.message}")
                    reportFirestoreError(error)
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    // Documento aún no creado: mantener cualquier detección previa solo por GitHub/error de reglas.
                    sharedPreferencesManager.saveBoolean(KEY_CACHED_NOTICE_ACTIVE, false)
                    sharedPreferencesManager.saveBoolean(KEY_CACHED_REQUIRE_UPDATE, false)
                    sharedPreferencesManager.saveString(KEY_CACHED_MESSAGE, "")
                    val current = _status.value
                    val githubRequires = current.latestUpdateInfo?.let { isReleaseAtOrAboveMin(it.version, current.minOnlineVersion) } == true
                    _status.value = current.copy(
                        isMigrationRequired = current.firestorePermissionDenied || githubRequires,
                        isWarningActive = false,
                        customMessage = "",
                        firestoreUnavailable = false
                    )
                    return@addSnapshotListener
                }

                val noticeActive = snapshot.getBoolean("migration_notice_active") ?: false
                val requireUpdate = snapshot.getBoolean("require_update_for_online") ?: false
                val minVersion = snapshot.getString("min_online_version")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: REQUIRED_SERVER_VERSION
                val customMsg = snapshot.getString("notice_message")?.trim().orEmpty()

                sharedPreferencesManager.saveBoolean(KEY_CACHED_NOTICE_ACTIVE, noticeActive)
                sharedPreferencesManager.saveBoolean(KEY_CACHED_REQUIRE_UPDATE, requireUpdate)
                sharedPreferencesManager.saveString(KEY_CACHED_MIN_VERSION, minVersion)
                sharedPreferencesManager.saveString(KEY_CACHED_MESSAGE, customMsg)

                val belowRequired = isVersionBelowRequired(minVersion)
                val current = _status.value
                val githubRequires = belowRequired &&
                    current.latestUpdateInfo?.let { isReleaseAtOrAboveMin(it.version, minVersion) } == true

                _status.value = current.copy(
                    minOnlineVersion = minVersion,
                    isMigrationRequired = belowRequired && (requireUpdate || current.firestorePermissionDenied || githubRequires),
                    isWarningActive = belowRequired && noticeActive && !requireUpdate,
                    customMessage = customMsg,
                    firestoreUnavailable = false
                )
            }
    }

    fun onRemoteReleaseChecked(updateInfo: UpdateInfo) {
        val current = _status.value
        val installed = getInstalledVersionName()
        val belowRequired = isVersionBelowRequired(current.minOnlineVersion, installed)
        val remoteReachedRequired = belowRequired &&
            isReleaseAtOrAboveMin(updateInfo.version, current.minOnlineVersion) &&
            SemVer.isNewer(updateInfo.version, installed)

        _status.value = current.copy(
            latestUpdateInfo = updateInfo,
            isMigrationRequired = current.isMigrationRequired || remoteReachedRequired
        )
    }

    fun reportFirestoreError(error: Throwable?) {
        if (error == null) return
        val firestoreEx = findFirestoreException(error)
        val msgLower = (error.message ?: "").lowercase()
        val isPermissionDenied = firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            msgLower.contains("permission_denied") ||
            msgLower.contains("missing or insufficient permissions")

        val isUnavailable = firestoreEx?.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            firestoreEx?.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ||
            msgLower.contains("unavailable") ||
            msgLower.contains("unable to resolve host")

        val current = _status.value
        val belowRequired = isVersionBelowRequired(current.minOnlineVersion)

        when {
            isPermissionDenied -> {
                Log.w(TAG, "Detectado PERMISSION_DENIED en Firestore (belowRequired=$belowRequired)")
                _status.value = current.copy(
                    firestorePermissionDenied = true,
                    isMigrationRequired = current.isMigrationRequired || belowRequired,
                    firestoreUnavailable = false
                )
            }
            isUnavailable -> {
                _status.value = current.copy(firestoreUnavailable = true)
            }
        }
    }

    fun clearTransientErrors() {
        val current = _status.value
        if (current.firestoreUnavailable) {
            _status.value = current.copy(firestoreUnavailable = false)
        }
    }

    fun getFriendlyFirestoreErrorMessage(error: Throwable?): String {
        val current = _status.value
        val firestoreEx = findFirestoreException(error)
        val msgLower = (error?.message ?: "").lowercase()
        val isPermissionDenied = firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            msgLower.contains("permission_denied") ||
            msgLower.contains("missing or insufficient permissions")

        return when {
            isPermissionDenied && isVersionBelowRequired(current.minOnlineVersion) ->
                "El servidor anterior de Firebase fue desconectado. Actualiza a la versión ${current.minOnlineVersion} o superior para usar el nuevo servidor en línea."
            firestoreEx?.code == FirebaseFirestoreException.Code.UNAVAILABLE || msgLower.contains("unable to resolve host") ->
                "No se pudo conectar con los servicios en línea. Comprueba tu conexión a internet o si ya hay una actualización disponible."
            else ->
                error?.localizedMessage ?: "No se pudo completar la conexión en línea."
        }
    }

    fun shouldAutoPresentDialog(): Boolean {
        if (hasShownDialogInSession) return false
        val current = _status.value
        if (!isVersionBelowRequired(current.minOnlineVersion)) return false
        if (current.isMigrationRequired) return true
        if (current.isWarningActive) {
            val dismissedVer = sharedPreferencesManager.getString(KEY_DISMISSED_WARNING_FOR_VERSION, "") ?: ""
            return dismissedVer != getInstalledVersionName()
        }
        return false
    }

    fun markDialogDismissed(isWarningOnly: Boolean) {
        hasShownDialogInSession = true
        if (isWarningOnly) {
            sharedPreferencesManager.saveString(KEY_DISMISSED_WARNING_FOR_VERSION, getInstalledVersionName())
        }
    }

    private fun isReleaseAtOrAboveMin(remoteVersion: String, minVersion: String): Boolean {
        val remote = SemVer.parseOrNull(remoteVersion) ?: return false
        val min = SemVer.parseOrNull(minVersion) ?: return false
        return remote >= min
    }

    private fun findFirestoreException(error: Throwable?): FirebaseFirestoreException? {
        var cursor: Throwable? = error
        while (cursor != null) {
            if (cursor is FirebaseFirestoreException) return cursor
            cursor = cursor.cause
        }
        return null
    }
}
