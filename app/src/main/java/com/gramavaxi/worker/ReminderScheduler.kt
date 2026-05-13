package com.gramavaxi.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.gramavaxi.data.model.Animal
import com.gramavaxi.util.DateUtils
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val THREE_DAYS_MS = 3 * 24 * 60 * 60 * 1000L

    fun scheduleVaccineReminder(context: Context, animal: Animal) {
        val delay = animal.nextShotDate - System.currentTimeMillis() - THREE_DAYS_MS
        if (delay <= 0L) return

        val request = OneTimeWorkRequestBuilder<VaccineReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    VaccineReminderWorker.KEY_ANIMAL_ID to animal.id,
                    VaccineReminderWorker.KEY_ANIMAL_NAME to animal.name,
                    VaccineReminderWorker.KEY_VACCINE_DATE to DateUtils.formatDate(animal.nextShotDate),
                    VaccineReminderWorker.KEY_OWNER_PHONE to animal.ownerPhone
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "vaccine-reminder-${animal.id}",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
