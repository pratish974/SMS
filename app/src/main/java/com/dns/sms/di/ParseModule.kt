package com.dns.sms.di

import com.dns.sms.parse.FinanceProcessor
import com.dns.sms.parse.MessageProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Used by Hilt (generated code), so the IDE's "never used" check doesn't see it. */
@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class ParseModule {
    @Binds @IntoSet abstract fun finance(p: FinanceProcessor): MessageProcessor
    @Binds @IntoSet abstract fun reminders(p: com.dns.sms.parse.ReminderProcessor): MessageProcessor
    @Binds @IntoSet abstract fun index(p: com.dns.sms.parse.MessageIndexProcessor): MessageProcessor
    @Binds @IntoSet abstract fun orders(p: com.dns.sms.parse.OrderProcessor): MessageProcessor
}
