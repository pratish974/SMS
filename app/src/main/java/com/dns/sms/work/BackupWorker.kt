package com.dns.sms.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dns.sms.backup.BackupManager
import com.dns.sms.settings.AppSettings
import com.dns.sms.settings.AutoBackup
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = when (val o = backups.backup()) {
        is BackupManager.Outcome.Done -> Result.success()
        // Network trouble: try again later. Sign-in or password problems need the user.
        is BackupManager.Outcome.Failed -> if (o.reason.startsWith("drive-5") || o.reason == "IOException" || o.reason.contains("Socket")) Result.retry() else Result.failure()
        else -> Result.failure()
    }

    companion object {
        const val NOW = "backup-now"
        private const val PERIODIC = "backup-auto"
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        private val autoConstraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()

        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(network).build(),
            )
        }

        /** Call when the Auto backup setting changes. */
        fun schedule(ctx: Context, settings: AppSettings) {
            val wm = WorkManager.getInstance(ctx)
            val days = when (settings.autoBackup.value) {
                AutoBackup.MANUAL -> { wm.cancelUniqueWork(PERIODIC); return }
                AutoBackup.DAILY -> 1L
                AutoBackup.WEEKLY -> 7L
                AutoBackup.MONTHLY -> 30L
            }
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                // FEAT-4: automatic backups wait for a healthy battery; "Back up now" doesn't.
                PeriodicWorkRequestBuilder<BackupWorker>(days, TimeUnit.DAYS).setConstraints(autoConstraints).build(),
            )
        }
    }
}
