package com.dns.sms.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dns.sms.classify.Category

/**
 * A "Move To" choice for a whole sender, remembered for future messages.
 * [address] holds a [com.dns.sms.classify.SenderKey] (brand or last-10-digits), not the raw address.
 */
@Entity(tableName = "sender_override")
data class SenderOverride(
    @PrimaryKey val address: String,
    val category: Category,
)
