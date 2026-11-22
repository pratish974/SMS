package com.dns.sms.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.dns.sms.R

/** Channels need no database, so they can be created on the main thread at app start. */
object NotificationChannels {
    fun create(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(MessageNotifier.CHANNEL_MESSAGES, ctx.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE },
        )
        nm.createNotificationChannel(
            NotificationChannel(MessageNotifier.CHANNEL_OTP, ctx.getString(R.string.channel_otp), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    description = ctx.getString(R.string.channel_otp_desc)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
        )
        nm.createNotificationChannel(
            NotificationChannel(MessageNotifier.CHANNEL_REMINDERS, ctx.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE },
        )
    }
}
