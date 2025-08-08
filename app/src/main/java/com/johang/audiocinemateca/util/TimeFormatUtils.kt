package com.johang.audiocinemateca.util

import java.util.concurrent.TimeUnit

object TimeFormatUtils {
    fun formatDuration(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        val parts = mutableListOf<String>()
        if (hours > 0) {
            parts.add("$hours hora${if (hours > 1) "s" else ""}")
        }
        if (minutes > 0) {
            parts.add("$minutes minuto${if (minutes > 1) "s" else ""}")
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add("$seconds segundo${if (seconds > 1) "s" else ""}")
        }

        return parts.joinToString(", ")
    }
}
