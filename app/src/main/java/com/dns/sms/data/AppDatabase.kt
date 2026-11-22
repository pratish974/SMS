package com.dns.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        SenderOverride::class, MessageMeta::class, DeliveryReport::class, ThreadMeta::class,
        Account::class, FinanceTxn::class, ScanState::class, Reminder::class, ShopOrder::class, OrderEvent::class,
        ScheduledMessage::class, MessageFts::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun senderOverrideDao(): SenderOverrideDao
    abstract fun messageMetaDao(): MessageMetaDao
    abstract fun deliveryReportDao(): DeliveryReportDao
    abstract fun threadMetaDao(): ThreadMetaDao
    abstract fun financeDao(): FinanceDao
    abstract fun scanStateDao(): ScanStateDao
    abstract fun reminderDao(): ReminderDao
    abstract fun orderDao(): OrderDao
    abstract fun scheduledDao(): ScheduledDao
    abstract fun messageFtsDao(): MessageFtsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `delivery_report` (`kind` TEXT NOT NULL, " +
                        "`messageId` INTEGER NOT NULL, `deliveredAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`kind`, `messageId`))"
                )
            }
        }

        /** message_meta was never written before v3, so it is recreated with (kind, messageId) as key. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `message_meta`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_meta` (`kind` TEXT NOT NULL, `messageId` INTEGER NOT NULL, " +
                        "`threadId` INTEGER NOT NULL, `category` TEXT, `pinned` INTEGER NOT NULL, " +
                        "`starred` INTEGER NOT NULL, PRIMARY KEY(`kind`, `messageId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_meta_threadId` ON `message_meta` (`threadId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_message_meta_category` ON `message_meta` (`category`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `thread_meta` (`threadId` INTEGER NOT NULL, " +
                        "`pinned` INTEGER NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`threadId`))"
                )
            }
        }

        /** Statements copied from the exported v5 schema (app/schemas/.../5.json). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                V5_TABLES.forEach(db::execSQL)
            }
        }

        /** FEAT-1 scheduled messages; statements copied from the exported v6 schema. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                V6_TABLES.forEach(db::execSQL)
            }
        }

        /** Mute, draft and chosen SIM per conversation (2026-11-21). */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `thread_meta` ADD COLUMN `muted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `thread_meta` ADD COLUMN `draft` TEXT")
                db.execSQL("ALTER TABLE `thread_meta` ADD COLUMN `subId` INTEGER")
            }
        }

        /**
         * Full-text search index; the table is filled by the scanner (the version bump of the index
         * processor triggers one full rescan). Statement copied from the exported v8 schema.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(V8_FTS)
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        const val NAME = "sms_app.db"

        /** Encrypted with SQLCipher (Requirement.md section 9); key from [DatabaseKey]. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        private fun open(ctx: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            // If the key can never be recovered the old encrypted file is unreadable: start fresh.
            val pass = DatabaseKey.passphrase(ctx, onLost = { ctx.deleteDatabase(NAME) })
            EncryptedDbMigrator.encryptIfPlaintext(ctx, NAME, pass)
            return Room.databaseBuilder(ctx, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(pass.toByteArray()))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
        }
    }
}

/** Filled from the exported schema; see [AppDatabase.MIGRATION_4_5]. */
internal val V5_TABLES: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `account` (`bank` TEXT NOT NULL, `mask` TEXT NOT NULL, `balance` TEXT, `balanceAt` INTEGER NOT NULL, `sourceMessageId` INTEGER NOT NULL, `parserVersion` INTEGER NOT NULL, PRIMARY KEY(`bank`, `mask`))",
    "CREATE TABLE IF NOT EXISTS `finance_txn` (`messageId` INTEGER NOT NULL, `bank` TEXT NOT NULL, `mask` TEXT NOT NULL, `type` TEXT NOT NULL, `amount` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`messageId`))",
    "CREATE INDEX IF NOT EXISTS `index_finance_txn_bank_mask` ON `finance_txn` (`bank`, `mask`)",
    "CREATE TABLE IF NOT EXISTS `scan_state` (`key` TEXT NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`key`))",
    "CREATE TABLE IF NOT EXISTS `reminder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `detail` TEXT, `dueAt` INTEGER NOT NULL, `sourceMessageId` INTEGER, `threadId` INTEGER, `dismissed` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_sourceMessageId_kind` ON `reminder` (`sourceMessageId`, `kind`)",
    "CREATE INDEX IF NOT EXISTS `index_reminder_dueAt` ON `reminder` (`dueAt`)",
    "CREATE TABLE IF NOT EXISTS `shop_order` (`key` TEXT NOT NULL, `merchant` TEXT NOT NULL, `title` TEXT NOT NULL, `orderId` TEXT, `status` TEXT NOT NULL, `statusAt` INTEGER NOT NULL, `url` TEXT, `expectedAt` INTEGER, `threadId` INTEGER, PRIMARY KEY(`key`))",
    "CREATE TABLE IF NOT EXISTS `order_event` (`orderKey` TEXT NOT NULL, `messageId` INTEGER NOT NULL, `status` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`orderKey`, `messageId`))",
)

/** Filled from the exported schema; see [AppDatabase.MIGRATION_5_6]. */
internal val V6_TABLES: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `scheduled_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `threadId` INTEGER, `recipients` TEXT NOT NULL, `body` TEXT NOT NULL, `sendAt` INTEGER NOT NULL, `state` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `sentAt` INTEGER, `failure` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_scheduled_message_threadId` ON `scheduled_message` (`threadId`)",
    "CREATE INDEX IF NOT EXISTS `index_scheduled_message_state_sendAt` ON `scheduled_message` (`state`, `sendAt`)",
)

/** See [AppDatabase.MIGRATION_7_8]. */
internal const val V8_FTS: String =
    "CREATE VIRTUAL TABLE IF NOT EXISTS `message_fts` USING FTS4(`body` TEXT NOT NULL, `address` TEXT NOT NULL, `threadId` INTEGER NOT NULL, `date` INTEGER NOT NULL, tokenize=unicode61, notindexed=`threadId`, notindexed=`date`)"
