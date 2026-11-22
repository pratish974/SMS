package com.dns.sms.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migrate1To2KeepsDataAndAddsDeliveryReports() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO sender_override (address, category) VALUES ('VM-BANK', 'TRANSACTIONS')")
        }
        helper.runMigrationsAndValidate(DB, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            db.query("SELECT category FROM sender_override WHERE address = 'VM-BANK'").use { c ->
                c.moveToFirst()
                assertEquals("TRANSACTIONS", c.getString(0))
            }
            db.execSQL("INSERT INTO delivery_report (kind, messageId, deliveredAt) VALUES ('sms', 1, 123)")
        }
    }

    @Test
    fun migrate2To3RecreatesMessageMetaWithKind() {
        helper.createDatabase(DB, 2).close()
        helper.runMigrationsAndValidate(DB, 3, true, AppDatabase.MIGRATION_2_3).use { db ->
            db.execSQL("INSERT INTO message_meta (kind, messageId, threadId, category, pinned, starred) VALUES ('sms', 7, 1, 'PROMOTIONS', 0, 0)")
            // Same id in the MMS table must not collide.
            db.execSQL("INSERT INTO message_meta (kind, messageId, threadId, category, pinned, starred) VALUES ('mms', 7, 1, NULL, 0, 0)")
            db.query("SELECT COUNT(*) FROM message_meta").use { c -> c.moveToFirst(); assertEquals(2, c.getInt(0)) }
        }
    }

    @Test
    fun migrate3To4AddsThreadMeta() {
        helper.createDatabase(DB, 3).close()
        helper.runMigrationsAndValidate(DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            db.execSQL("INSERT INTO thread_meta (threadId, pinned, archived) VALUES (5, 1, 0)")
        }
    }

    @Test
    fun migrate4To5AddsFinanceRemindersOrders() {
        helper.createDatabase(DB, 4).close()
        helper.runMigrationsAndValidate(DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.execSQL("INSERT INTO account (bank, mask, balance, balanceAt, sourceMessageId, parserVersion) VALUES ('ICICI', 'XXX439', '10.50', 1, 1, 1)")
            db.execSQL("INSERT INTO reminder (kind, title, detail, dueAt, sourceMessageId, threadId, dismissed) VALUES ('MANUAL', 'Pay rent', NULL, 1, NULL, NULL, 0)")
        }
    }

    @Test
    fun migrate5To6AddsScheduledMessages() {
        helper.createDatabase(DB, 5).close()
        helper.runMigrationsAndValidate(DB, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            db.execSQL("INSERT INTO scheduled_message (threadId, recipients, body, sendAt, state, createdAt, sentAt, failure) VALUES (NULL, '5551234', 'hi', 1, 'PENDING', 1, NULL, NULL)")
        }
    }

    @Test
    fun migrate6To7AddsMuteDraftSim() {
        helper.createDatabase(DB, 6).use { db -> db.execSQL("INSERT INTO thread_meta (threadId, pinned, archived) VALUES (7, 1, 0)") }
        helper.runMigrationsAndValidate(DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT pinned, muted, draft, subId FROM thread_meta WHERE threadId = 7").use { c ->
                check(c.moveToFirst() && c.getInt(0) == 1 && c.getInt(1) == 0 && c.isNull(2) && c.isNull(3))
            }
        }
    }

    @Test
    fun fullChain1To7() {
        helper.createDatabase(DB, 1).close()
        helper.runMigrationsAndValidate(
            DB, 7, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
        ).close()
    }

    private companion object { const val DB = "migration-test" }
}
