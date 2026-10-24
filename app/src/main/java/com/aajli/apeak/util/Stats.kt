package com.aajli.apeak.util

import com.aajli.apeak.data.*
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

fun newId(): String = java.util.UUID.randomUUID().toString()

data class Position(val qty: Double, val costMinor: Long, val realizedMinor: Long, val boughtMinor: Long, val realizedByYear: Map<Int, Long>)

data class StockRow(val stock: StockEntity, val pos: Position, val valueMinor: Long, val unrealizedMinor: Long, val dividendsMinor: Long) {
    val totalReturn: Long get() = unrealizedMinor + pos.realizedMinor + dividendsMinor
    val avgPrice: Long get() = if (pos.qty > 0) (pos.costMinor.toDouble() / pos.qty).roundToLong() else 0L
}

data class YearRow(val year: Int, val interest: Long, val dividends: Long, val realized: Long) {
    val total: Long get() = interest + dividends + realized
}

object Calc {
    private fun year(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

    /** Average-cost method. Fees are added to the cost on buys and deducted from proceeds on sells. */
    fun position(trades: List<StockTradeEntity>): Position {
        var qty = 0.0; var cost = 0.0; var realized = 0.0; var bought = 0.0
        val ry = HashMap<Int, Double>()
        val sorted = trades.sortedWith(compareBy<StockTradeEntity>({ it.dateMillis }, { if (it.type == TradeType.BUY) 0 else 1 }))
        for (t in sorted) {
            if (t.type == TradeType.BUY) {
                val c = t.quantity * t.priceMinor + t.feesMinor
                qty += t.quantity; cost += c; bought += c
            } else {
                val q = minOf(t.quantity, qty)
                val avg = if (qty > 0) cost / qty else 0.0
                val soldCost = avg * q
                val gain = (q * t.priceMinor - t.feesMinor) - soldCost
                realized += gain
                ry[year(t.dateMillis)] = (ry[year(t.dateMillis)] ?: 0.0) + gain
                cost -= soldCost; qty -= q
            }
        }
        if (qty < 1e-9) { qty = 0.0; cost = 0.0 }
        return Position(qty, cost.roundToLong(), realized.roundToLong(), bought.roundToLong(), ry.mapValues { it.value.roundToLong() })
    }

    fun stockRow(s: StockEntity, trades: List<StockTradeEntity>, divs: List<DividendEntity>): StockRow {
        val p = position(trades.filter { it.stockId == s.id })
        val value = (p.qty * s.lastPriceMinor).roundToLong()
        return StockRow(s, p, value, if (p.qty > 0) value - p.costMinor else 0L, divs.filter { it.stockId == s.id }.sumOf { it.amountMinor })
    }

    fun bankBalance(a: BankAccountEntity, es: List<BankEntryEntity>): Long =
        a.openingMinor + es.sumOf { if (it.type == BankType.WITHDRAW) -it.amountMinor else it.amountMinor }

    fun balanceSeries(a: BankAccountEntity, es: List<BankEntryEntity>): List<Long> {
        var b = a.openingMinor
        val out = mutableListOf(b)
        es.sortedBy { it.dateMillis }.forEach { b += if (it.type == BankType.WITHDRAW) -it.amountMinor else it.amountMinor; out += b }
        return out
    }

    fun yearly(entries: List<BankEntryEntity>, divs: List<DividendEntity>, rows: List<StockRow>): List<YearRow> {
        val i = entries.filter { it.type == BankType.INTEREST }.groupBy { year(it.dateMillis) }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        val d = divs.groupBy { year(it.dateMillis) }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        val r = HashMap<Int, Long>()
        rows.forEach { row -> row.pos.realizedByYear.forEach { (y, v) -> r[y] = (r[y] ?: 0L) + v } }
        return (i.keys + d.keys + r.keys).toSortedSet(reverseOrder()).map { YearRow(it, i[it] ?: 0L, d[it] ?: 0L, r[it] ?: 0L) }
    }
}
