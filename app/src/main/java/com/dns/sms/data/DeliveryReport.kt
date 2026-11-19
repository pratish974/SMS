package com.dns.sms.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Upsert

/** When a sent message was delivered. Ids and time only, never content. kind: "sms" or "mms". */
@Entity(tableName = "delivery_report", primaryKeys = ["kind", "messageId"])
data class DeliveryReport(
    val kind: String,
    val messageId: Long,
    val deliveredAt: Long,
)

@Dao
interface DeliveryReportDao {
    @Upsert
    suspend fun upsert(report: DeliveryReport)

    @Query("SELECT * FROM delivery_report WHERE kind = :kind AND messageId IN (:ids)")
    suspend fun forMessages(kind: String, ids: List<Long>): List<DeliveryReport>
}
