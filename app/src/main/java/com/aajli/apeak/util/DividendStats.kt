package com.aajli.apeak.util

import com.aajli.apeak.data.DividendEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

object DividendStats {
    private fun zdt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    /** (year, total), newest year first. */
    fun byYear(divs: List<DividendEntity>): List<Pair<Int, Long>> =
        divs.groupBy { zdt(it.dateMillis).year }.map { it.key to it.value.sumOf { d -> d.amountMinor } }.sortedByDescending { it.first }

    /** The last 12 calendar months ending with the current one, oldest first. */
    fun trailingMonths(divs: List<DividendEntity>, now: LocalDate = LocalDate.now()): List<Pair<YearMonth, Long>> {
        val cur = YearMonth.from(now)
        val sums = divs.groupBy { YearMonth.from(zdt(it.dateMillis)) }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        return (11 downTo 0).map { cur.minusMonths(it.toLong()) }.map { it to (sums[it] ?: 0L) }
    }

    fun last12(divs: List<DividendEntity>, now: LocalDate = LocalDate.now()): Long = trailingMonths(divs, now).sumOf { it.second }

    fun byStock(divs: List<DividendEntity>): Map<String, Long> = divs.groupBy { it.stockId }.mapValues { e -> e.value.sumOf { it.amountMinor } }

    fun yieldPct(amount: Long, base: Long): Double? = if (base > 0) amount * 100.0 / base else null

    /** Total in minor units for [qty] shares at [perShare] (in currency units, any number of decimals). */
    fun perShareTotal(qty: Double, perShare: Double): Long = (qty * perShare * 100).roundToLong()
}
