package com.hcwebhook.app

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val syncManager = SyncManager(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Nutricional Webhook: um envio por vez, para o automático de hora em hora e o
            // pedido pela tela não mandarem os mesmos dados em dobro.
            syncLock.withLock {
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: "auto"
            // Nutricional Webhook: o "Reenviar" pede um período em dias. Enquanto o histórico
            // inicial não tiver chegado ao Nutricional, todo envio (também o automático de hora
            // em hora) leva os últimos dias; depois disso é o incremental de sempre.
            val requestedDays = inputData.getInt(KEY_RANGE_DAYS, 0)
            val days = when {
                requestedDays > 0 -> requestedDays
                !com.hcwebhook.app.nutricional.NutricionalSetup.isHistorySent(applicationContext) ->
                    com.hcwebhook.app.nutricional.NutricionalSetup.HISTORY_DAYS
                else -> 0
            }
            val syncResult = syncManager.performSync(timeRangeDays = days.takeIf { it > 0 }, syncType = syncType)

            if (syncResult.isSuccess) {
                if (days > 0) com.hcwebhook.app.nutricional.NutricionalSetup.markHistorySent(applicationContext)
                Result.success()
            } else {
                mapFailure(syncResult.exceptionOrNull())
            }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            mapFailure(e)
        } catch (e: Exception) {
            Result.failure()
        }
    }

    /** Expedited work needs a notification on Android 11 and older. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        SyncForegroundService.ensureNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, SyncForegroundService.CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.sync_service_notification_title))
            .setContentText(applicationContext.getString(R.string.sync_service_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(FOREGROUND_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(FOREGROUND_ID, notification)
        }
    }

    private fun mapFailure(error: Throwable?): Result {
        return when (error) {
            is CancellationException -> throw error
            is IOException -> if (WebhookManager.isRetryableException(error)) Result.retry() else Result.failure()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_RANGE_DAYS = "range_days"
        private const val FOREGROUND_ID = 2002
        private val syncLock = Mutex()
    }
}
