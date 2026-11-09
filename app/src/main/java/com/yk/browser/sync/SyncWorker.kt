package com.yk.browser.sync

import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yk.browser.backup.BackupManager
import com.yk.browser.backup.BackupPinHolder
import com.yk.browser.backup.BackupResult
import java.util.concurrent.TimeUnit

/**
 * Periodic background vault sync. Runs only when the user has entered their
 * backup PIN this session (the PIN lives in RAM only, never on disk).
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val pin = BackupPinHolder.pin ?: return Result.success()
        return try {
            val results = BackupManager(applicationContext).backupNow(pin)
            if (results.any { it.second is BackupResult.Error }) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val WORK_NAME = "yk_vault_sync"

    fun schedule(context: Context) {
        // Defensive: if the startup provider didn't run (e.g. broken manifest
        // merge), initialize manually instead of crashing on launch.
        try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            WorkManager.initialize(context, Configuration.Builder().build())
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
