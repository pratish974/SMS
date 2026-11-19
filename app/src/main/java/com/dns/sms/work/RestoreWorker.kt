package com.dns.sms.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dns.sms.backup.BackupManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** S8: restore runs as background work, so leaving the Backup screen doesn't stop it. */
@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val o = backups.restore()
        val data = when (o) {
            is BackupManager.Outcome.Restored -> workDataOf(KEY_RESULT to "restored", KEY_ADDED to o.added, KEY_SKIPPED to o.skipped)
            BackupManager.Outcome.KeyNotOnThisPhone -> workDataOf(KEY_RESULT to "no-key")
            BackupManager.Outcome.NoBackupFound -> workDataOf(KEY_RESULT to "no-backup")
            BackupManager.Outcome.NeedsSignIn -> workDataOf(KEY_RESULT to "sign-in")
            BackupManager.Outcome.NotDefaultApp -> workDataOf(KEY_RESULT to "not-default")
            else -> workDataOf(KEY_RESULT to "failed")
        }
        return Result.success(data)
    }

    companion object {
        const val NAME = "restore"
        const val KEY_RESULT = "result"
        const val KEY_ADDED = "added"
        const val KEY_SKIPPED = "skipped"

        fun start(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RestoreWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}
