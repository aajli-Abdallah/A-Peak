package com.aajli.apeak.util

import com.aajli.apeak.data.PricePointEntity
import java.time.Instant
import java.time.ZoneId

object PriceHistory {
    fun dayStart(ms: Long): Long {
        val z = ZoneId.systemDefault()
        return Instant.ofEpochMilli(ms).atZone(z).toLocalDate().atStartOfDay(z).toInstant().toEpochMilli()
    }

    fun point(stockId: String, ms: Long, price: Long): PricePointEntity {
        val d = dayStart(ms)
        return PricePointEntity("$stockId:$d", stockId, d, price)
    }

    /** Latest recorded price strictly before [endExclusive], or null when none. */
    fun priceAt(points: List<PricePointEntity>, endExclusive: Long): Long? =
        points.filter { it.dateMillis < endExclusive }.maxByOrNull { it.dateMillis }?.priceMinor
}
