package com.johang.audiocinemateca.util

import java.util.concurrent.TimeUnit

object TimeFormatUtils {

    fun formatDuration(minutes: Int): String {
        val millis = TimeUnit.MINUTES.toMillis(minutes.toLong())
        return formatDuration(millis)
    }

    fun formatDuration(millis: Long): String {
        if (millis < 0) return "0 Segundos"

        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1)

        val parts = mutableListOf<String>()

        if (hours > 0) {
            parts.add("$hours Hora${if (hours != 1L) "s" else ""}")
        }
        if (minutes > 0) {
            parts.add("$minutes Minuto${if (minutes != 1L) "s" else ""}")
        }
        if (seconds > 0) {
            parts.add("$seconds Segundo${if (seconds != 1L) "s" else ""}")
        }

        if (parts.isEmpty()) {
            return "0 Segundos"
        }

        return when (parts.size) {
            1 -> parts[0]
            2 -> parts.joinToString(" y ")
            3 -> "${parts[0]} con ${parts[1]} y ${parts[2]}"
            else -> parts.joinToString(", ") // Fallback for unexpected cases
        }
    }
}
