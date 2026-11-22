package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A reminder: parsed from SMS (bills, movies, bus, flights) or added by hand. */
@Entity(
    tableName = "reminder",
    indices = [Index(value = ["sourceMessageId", "kind"], unique = true), Index("dueAt")],
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** BILL, MOVIE, BUS, FLIGHT, EVENT or MANUAL. */
    val kind: String,
    val title: String,
    val detail: String?,
    val dueAt: Long,
    /** null for manual reminders. */
    val sourceMessageId: Long?,
    val threadId: Long?,
    val dismissed: Boolean = false,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder WHERE dismissed = 0 AND dueAt >= :now ORDER BY dueAt ASC")
    fun observeUpcoming(now: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE dismissed = 1 OR dueAt < :now ORDER BY dueAt DESC LIMIT 300")
    fun observePast(now: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminder WHERE dismissed = 0 AND dueAt >= :now")
    suspend fun upcoming(now: Long): List<Reminder>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminder WHERE sourceMessageId = :messageId AND kind = :kind")
    suspend fun forMessage(messageId: Long, kind: String): Reminder?

    @Upsert
    suspend fun upsert(r: Reminder): Long

    @Query("UPDATE reminder SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminder WHERE sourceMessageId IS NOT NULL AND dismissed = 0")
    suspend fun clearParsedActive()
}

/** A shipment, grouped by merchant + order id. */
@Entity(tableName = "shop_order")
data class ShopOrder(
    @PrimaryKey val key: String,
    val merchant: String,
    val title: String,
    val orderId: String?,
    /** DISPATCHED, OUT_FOR_DELIVERY, ARRIVING, DELIVERED, CANCELLED, PLACED. */
    val status: String,
    val statusAt: Long,
    /** Tracking link as written in the SMS. Shown to the user before opening; never auto-opened. */
    val url: String?,
    val expectedAt: Long?,
    val threadId: Long?,
)

@Entity(tableName = "order_event", primaryKeys = ["orderKey", "messageId"])
data class OrderEvent(
    val orderKey: String,
    val messageId: Long,
    val status: String,
    val at: Long,
)

@Dao
interface OrderDao {
    /** Not delivered/cancelled and updated recently: the "delivered" SMS often comes from a courier
     *  under another key, so old shipments would otherwise stay "active" forever. */
    @Query("SELECT * FROM shop_order WHERE status NOT IN ('DELIVERED', 'CANCELLED') AND statusAt >= :since ORDER BY statusAt DESC")
    fun observeActive(since: Long): Flow<List<ShopOrder>>

    @Query("SELECT * FROM shop_order ORDER BY statusAt DESC LIMIT 300")
    fun observeAll(): Flow<List<ShopOrder>>

    @Query("SELECT * FROM shop_order WHERE `key` = :key")
    suspend fun get(key: String): ShopOrder?

    @Query("SELECT * FROM shop_order WHERE `key` = :key")
    fun observe(key: String): Flow<ShopOrder?>

    @Query("SELECT * FROM order_event WHERE orderKey = :key ORDER BY at DESC")
    fun observeEvents(key: String): Flow<List<OrderEvent>>

    @Upsert
    suspend fun upsert(o: ShopOrder)

    @Upsert
    suspend fun upsertEvent(e: OrderEvent)

    @Query("DELETE FROM shop_order")
    suspend fun clearOrders()

    @Query("DELETE FROM order_event")
    suspend fun clearEvents()
}
