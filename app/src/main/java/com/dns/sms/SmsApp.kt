package com.dns.sms

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dns.sms.notify.NotificationChannels
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.work.SmsScanWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds
import javax.inject.Inject

@HiltAndroidApp
class SmsApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var gate: DefaultSmsGate
    @Inject lateinit var settings: com.dns.sms.settings.AppSettings
    @Inject lateinit var folders: dagger.Lazy<com.dns.sms.classify.Folders>

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        com.dns.sms.util.CrashLog.install(this)
        NotificationChannels.create(this)
        // Open the encrypted database (Keystore unwrap, one-time migration) off the main thread,
        // so the first screen doesn't pay for it.
        // Startup (perf 2026-11-22): the inbox used to start loading only after the first screen was
        // composed (~0.8 s in), so it appeared at ~1.5 s. Start loading it here, in parallel with
        // the activity, and keep WorkManager's own database setup off the main thread too.
        com.dns.sms.util.Perf.log("app onCreate")
        Thread({
            runCatching { com.dns.sms.data.AppDatabase.get(this).openHelper.writableDatabase }
            com.dns.sms.util.Perf.log("db open")
            if (gate.isDefaultNow()) runCatching {
                kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(10.seconds) { folders.get().observe().first() } }
            }
        }, "startup-warmup").start()
        Thread({
            // Pick up anything that arrived while we weren't running (and the first full scan).
            if (gate.isDefaultNow()) SmsScanWorker.enqueueIfIdle(this)
            com.dns.sms.work.MessageRulesWorker.schedule(this, settings)
            com.dns.sms.work.BackupWorker.schedule(this, settings)
        }, "work-schedule").start()
    }
}
