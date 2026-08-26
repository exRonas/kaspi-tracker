package com.kleinapps.kaspitracker.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTxType(value: TxType): String = value.name

    @TypeConverter
    fun toTxType(value: String): TxType =
        runCatching { TxType.valueOf(value) }.getOrDefault(TxType.UNKNOWN)

    @TypeConverter
    fun fromTxSource(value: TxSource): String = value.name

    @TypeConverter
    fun toTxSource(value: String): TxSource =
        runCatching { TxSource.valueOf(value) }.getOrDefault(TxSource.NOTIFICATION)

    @TypeConverter
    fun fromCategory(value: Category?): String? = value?.name

    @TypeConverter
    fun toCategory(value: String?): Category? =
        value?.let { runCatching { Category.valueOf(it) }.getOrDefault(Category.OTHER) }
}
