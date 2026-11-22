package com.dns.sms.util

import android.os.SystemClock
import android.util.Log
import com.dns.sms.BuildConfig

/**
 * Debug-only timing logs for performance testing (tag "Perf"). Logs counts and milliseconds only,
 * never message content. R8 strips Log.d from release builds.
 */
object Perf {
    @Volatile var folderSwitchStartedAt = 0L

    fun now(): Long = SystemClock.elapsedRealtime()

    fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d("Perf", msg)
    }

    inline fun <T> time(label: String, block: () -> T): T {
        val start = now()
        val r = block()
        log("$label: ${now() - start} ms")
        return r
    }
}
