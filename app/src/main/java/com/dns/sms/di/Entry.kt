package com.dns.sms.di

import android.content.Context
import com.dns.sms.data.DeliveryReportDao
import com.dns.sms.notify.MessageNotifier
import com.dns.sms.receive.IncomingSms
import com.dns.sms.send.Outbox
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.DefaultSmsGate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Dependencies for receivers and services, which we keep as plain Android classes. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface Entry {
    fun gate(): DefaultSmsGate
    fun repo(): ConversationRepository
    fun incoming(): IncomingSms
    fun notifier(): MessageNotifier
    fun outbox(): Outbox
    fun scheduled(): com.dns.sms.scheduled.ScheduledMessages
    fun reminderScheduler(): com.dns.sms.reminders.ReminderScheduler
    fun reminderDao(): com.dns.sms.data.ReminderDao
    fun settings(): com.dns.sms.settings.AppSettings
    fun actions(): com.dns.sms.sms.ConversationActions
    fun deliveryReports(): DeliveryReportDao
}

fun Context.entry(): Entry = EntryPointAccessors.fromApplication(applicationContext, Entry::class.java)
