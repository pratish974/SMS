package com.dns.sms.di

import android.app.role.RoleManager
import android.content.ContentResolver
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.dns.sms.data.AppDatabase
import com.dns.sms.data.DeliveryReportDao
import com.dns.sms.data.MessageMetaDao
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.data.ThreadMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase = AppDatabase.get(ctx)

    @Provides fun senderOverrideDao(db: AppDatabase): SenderOverrideDao = db.senderOverrideDao()
    @Provides fun messageMetaDao(db: AppDatabase): MessageMetaDao = db.messageMetaDao()
    @Provides fun deliveryReportDao(db: AppDatabase): DeliveryReportDao = db.deliveryReportDao()
    @Provides fun threadMetaDao(db: AppDatabase): ThreadMetaDao = db.threadMetaDao()
    @Provides fun financeDao(db: AppDatabase): com.dns.sms.data.FinanceDao = db.financeDao()
    @Provides fun scanStateDao(db: AppDatabase): com.dns.sms.data.ScanStateDao = db.scanStateDao()
    @Provides fun reminderDao(db: AppDatabase): com.dns.sms.data.ReminderDao = db.reminderDao()
    @Provides fun orderDao(db: AppDatabase): com.dns.sms.data.OrderDao = db.orderDao()
    @Provides fun messageFtsDao(db: AppDatabase): com.dns.sms.data.MessageFtsDao = db.messageFtsDao()
    @Provides fun scheduledDao(db: AppDatabase): com.dns.sms.data.ScheduledDao = db.scheduledDao()

    @Provides fun contentResolver(@ApplicationContext ctx: Context): ContentResolver = ctx.contentResolver

    @Provides fun roleManager(@ApplicationContext ctx: Context): RoleManager =
        ctx.getSystemService(RoleManager::class.java)

    @Provides fun notificationManager(@ApplicationContext ctx: Context): NotificationManagerCompat =
        NotificationManagerCompat.from(ctx)
}
