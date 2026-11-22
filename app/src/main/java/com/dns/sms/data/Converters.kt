package com.dns.sms.data

import androidx.room.TypeConverter
import com.dns.sms.classify.Category

class Converters {
    @TypeConverter
    fun fromCategory(value: Category?): String? = value?.name

    /** Unknown names (e.g. from a future version) read as null rather than crashing. */
    @TypeConverter
    fun toCategory(value: String?): Category? = Category.entries.firstOrNull { it.name == value }
}
