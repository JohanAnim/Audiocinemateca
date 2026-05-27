package com.johang.audiocinemateca.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashLogger(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val shouldLog = prefs.getBoolean("create_crash_log", true)

        if (shouldLog) {
            saveCrashReport(throwable)
        }

        // Dejar que el sistema maneje el cierre normal después de guardar el log
        defaultHandler?.uncaughtException(thread, throwable)
    }

    private fun saveCrashReport(throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "ERROR_AUDIOCINEMATECA_$timestamp.log"

            // Usar almacenamiento específico de la app (interno o externo emulado)
            // Esto es mucho más fiable en Android 10+
            val logDir = File(context.getExternalFilesDir(null), "crash_logs")
            
            if (!logDir.exists()) logDir.mkdirs()

            val logFile = File(logDir, filename)
            val out = FileOutputStream(logFile)

            val report = StringBuilder().apply {
                append("--- REPORTE DE ERROR FATAL (v3.0.0) ---\n")
                append("Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                append("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Versión Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                append("--------------------------------------\n\n")
                append("DETALLE DEL ERROR:\n")
                append(stackTrace)
            }.toString()

            out.write(report.toByteArray())
            out.close()
            
            Log.e("CrashLogger", "Log de error guardado en: ${logFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashLogger", "No se pudo guardar el log de error", e)
        }
    }

    companion object {
        fun getLatestLogFile(context: Context): File? {
            val logDir = File(context.getExternalFilesDir(null), "crash_logs")
            if (!logDir.exists()) return null
            
            return logDir.listFiles()?.filter { it.extension == "log" }
                ?.maxByOrNull { it.lastModified() }
        }
    }
}
