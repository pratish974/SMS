package com.dns.sms.util

import android.content.Context
import android.os.Build
import com.dns.sms.BuildConfig
import java.io.File

/**
 * On-device crash log (no crash-reporting SDK, nothing is uploaded). Keeps the last few crashes so the
 * owner can choose to share them from Settings > About.
 *
 * Privacy: only exception CLASS names and stack frames are written. Exception messages are dropped
 * because they can contain message text, numbers or other personal data.
 */
object CrashLog {
    private const val DIR = "crash"
    private const val KEEP = 5

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { write(app, thread.name, e) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** Stack trace without any exception message text (pure; unit tested). */
    fun sanitize(e: Throwable): String = buildString {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth < 5) {
            append(if (depth == 0) "" else "Caused by: ").append(t.javaClass.name).append('\n')
            t.stackTrace.take(40).forEach { f -> append("    at ").append(f.className).append('.').append(f.methodName)
                .append('(').append(f.fileName ?: "?").append(':').append(f.lineNumber).append(")\n") }
            t = t.cause?.takeIf { it !== t }
            depth++
        }
    }

    private fun write(ctx: Context, threadName: String, e: Throwable) {
        val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
        val header = "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
            "${Build.MANUFACTURER} ${Build.MODEL}, time ${System.currentTimeMillis()}, thread ${threadName.take(40)}\n"
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(header + sanitize(e))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /** All kept crashes, newest first, as one text (empty when there are none). */
    fun read(ctx: Context): String =
        File(ctx.filesDir, DIR).listFiles()?.sortedByDescending { it.name }?.joinToString("\n\n") { it.readText() }.orEmpty()

    fun clear(ctx: Context) { File(ctx.filesDir, DIR).deleteRecursively() }
}
