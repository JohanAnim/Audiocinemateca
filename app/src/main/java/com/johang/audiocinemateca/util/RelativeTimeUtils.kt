package com.johang.audiocinemateca.util

import java.util.Calendar
import java.util.concurrent.TimeUnit

object RelativeTimeUtils {

    fun getRelativeDateLabel(timestamp: Long): String {
        val now = Calendar.getInstance()
        val time = Calendar.getInstance().apply { timeInMillis = timestamp }

        // Reset time part for date-only comparison
        now.set(Calendar.HOUR_OF_DAY, 0)
        now.set(Calendar.MINUTE, 0)
        now.set(Calendar.SECOND, 0)
        now.set(Calendar.MILLISECOND, 0)

        time.set(Calendar.HOUR_OF_DAY, 0)
        time.set(Calendar.MINUTE, 0)
        time.set(Calendar.SECOND, 0)
        time.set(Calendar.MILLISECOND, 0)

        val diffMillis = now.timeInMillis - time.timeInMillis
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            diffDays == 0L -> "Hoy"
            diffDays == 1L -> "Ayer"
            diffDays < 7 -> "Hace $diffDays días"
            diffDays < 14 -> "La semana pasada"
            diffDays < 30 -> "Hace ${diffDays / 7} semanas"
            diffDays < 365 -> {
                val months = (diffDays / 30).toInt()
                if (months <= 1) "El mes pasado" else "Hace $months meses"
            }
            else -> {
                val years = (diffDays / 365).toInt()
                if (years <= 1) "El año pasado" else "Hace $years años"
            }
        }
    }
}