package com.gramavaxi.worker

import android.content.Context
import android.telephony.SmsManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gramavaxi.R

class VaccineReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val animalName = inputData.getString(KEY_ANIMAL_NAME) ?: return Result.failure()
        val vaccineDate = inputData.getString(KEY_VACCINE_DATE) ?: return Result.failure()
        val ownerPhone = inputData.getString(KEY_OWNER_PHONE).orEmpty()
        val animalId = inputData.getInt(KEY_ANIMAL_ID, 0)
        val message = applicationContext.getString(R.string.notification_message, animalName, vaccineDate)

        NotificationHelper.createChannels(applicationContext)
        NotificationHelper.show(
            context = applicationContext,
            title = applicationContext.getString(R.string.notification_title),
            message = message,
            notificationId = animalId.ifBlankId()
        )
        sendSms(ownerPhone, message)
        return Result.success()
    }

    private fun sendSms(phoneNumber: String, message: String) {
        if (phoneNumber.isBlank()) return
        try {
            SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
        } catch (_: SecurityException) {
            NotificationHelper.show(
                context = applicationContext,
                title = applicationContext.getString(R.string.sms_permission_missing_title),
                message = applicationContext.getString(R.string.sms_permission_missing_body),
                notificationId = System.currentTimeMillis().toInt()
            )
        } catch (_: IllegalArgumentException) {
            NotificationHelper.show(
                context = applicationContext,
                title = applicationContext.getString(R.string.sms_failed_title),
                message = applicationContext.getString(R.string.sms_failed_body),
                notificationId = System.currentTimeMillis().toInt()
            )
        }
    }

    private fun Int.ifBlankId(): Int = if (this == 0) System.currentTimeMillis().toInt() else this

    companion object {
        const val KEY_ANIMAL_ID = "animal_id"
        const val KEY_ANIMAL_NAME = "animal_name"
        const val KEY_VACCINE_DATE = "vaccine_date"
        const val KEY_OWNER_PHONE = "owner_phone"
    }
}
