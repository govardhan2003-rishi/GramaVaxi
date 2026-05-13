package com.gramavaxi.util

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object DateUtils {
    fun formatDate(timeMillis: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(timeMillis))
    }

    fun nextShotFrom(lastVaccinationDate: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = lastVaccinationDate
            add(Calendar.MONTH, 6)
        }.timeInMillis
    }

    fun daysUntil(timeMillis: Long): Long {
        return TimeUnit.MILLISECONDS.toDays(timeMillis - System.currentTimeMillis())
    }
}
