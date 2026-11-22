package com.dns.sms.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dns.sms.parse.SmsScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs the parsers over new SMS off the main thread (receivers only enqueue this). */
@HiltWorker
class SmsScanWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val scanner: SmsScanner,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        scanner.scan(full = inputData.getBoolean(KEY_FULL, false))
        return Result.success()
    }

    companion object {
        private const val KEY_FULL = "full"
        const val NAME = "sms-scan"

        /** Queues a scan after any running one, so new SMS are never skipped. */
        fun enqueue(ctx: Context, full: Boolean = false) = enqueue(ctx, full, ExistingWorkPolicy.APPEND_OR_REPLACE)

        /**
         * FEAT-4: app start and every return to the app call this. If a scan is already queued or
         * running, don't chain another one (it resumes from the last scanned id anyway); the next
         * resume picks up anything that arrived meanwhile.
         */
        fun enqueueIfIdle(ctx: Context) = enqueue(ctx, false, ExistingWorkPolicy.KEEP, delaySeconds = 3)

        /**
         * [delaySeconds]: on app start/resume the catch-up scan waits a moment so it doesn't compete
         * with loading the inbox for the first screen (perf 2026-11-22). New-SMS scans run at once.
         */
        private fun enqueue(ctx: Context, full: Boolean, policy: ExistingWorkPolicy, delaySeconds: Long = 0) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                NAME, policy,
                OneTimeWorkRequestBuilder<SmsScanWorker>().setInputData(workDataOf(KEY_FULL to full))
                    .setInitialDelay(delaySeconds, java.util.concurrent.TimeUnit.SECONDS).build(),
            )
        }
    }
}
