package com.aajli.apeak.util

import com.aajli.apeak.data.DividendEntity
import com.aajli.apeak.data.StockEntity
import com.aajli.apeak.data.StockTradeEntity
import com.aajli.apeak.data.TradeType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

data class SaleEvent(val tradeId: String, val stockId: String, val dateMillis: Long, val qty: Double,
                     val proceedsMinor: Long, val costMinor: Long, val feesMinor: Long) {
    val gainMinor: Long get() = proceedsMinor - costMinor
    val gainPct: Double? get() = if (costMinor > 0) gainMinor * 100.0 / costMinor else null
}

data class YearGains(val year: Int, val gain: Long, val fees: Long, val proceeds: Long, val count: Int)

enum class PriceAlertKind { TARGET, STOP }
data class PriceAlert(val symbol: String, val kind: PriceAlertKind, val price: Long, val level: Long)

object PriceAlerts {
    fun status(price: Long, target: Long, stop: Long): PriceAlertKind? = when {
        price <= 0 -> null
        target > 0 && price >= target -> PriceAlertKind.TARGET
        stop > 0 && price <= stop -> PriceAlertKind.STOP
        else -> null
    }

    /** The alert newly reached when the price moves from [before] to [after], or null. */
    fun crossed(before: Long, after: Long, target: Long, stop: Long): PriceAlertKind? {
        val a = status(after, target, stop) ?: return null
        return if (status(before, target, stop) == a) null else a
    }
}

object Reports {
    private fun year(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).year

    /** Every sale with its profit or loss (average-cost method, fees included), newest first. */
    fun sales(trades: List<StockTradeEntity>): List<SaleEvent> {
        val out = mutableListOf<SaleEvent>()
        for ((sid, ts) in trades.groupBy { it.stockId }) {
            var qty = 0.0; var cost = 0.0
            for (t in ts.sortedWith(compareBy<StockTradeEntity>({ it.dateMillis }, { if (it.type == TradeType.BUY) 0 else 1 }))) {
                if (t.type == TradeType.BUY) { qty += t.quantity; cost += t.quantity * t.priceMinor + t.feesMinor }
                else {
                    val q = minOf(t.quantity, qty)
                    val soldCost = (if (qty > 0) cost / qty else 0.0) * q
                    out += SaleEvent(t.id, sid, t.dateMillis, q, (q * t.priceMinor - t.feesMinor).roundToLong(), soldCost.roundToLong(), t.feesMinor)
                    cost -= soldCost; qty -= q
                }
            }
        }
        return out.sortedByDescending { it.dateMillis }
    }

    fun byYear(sales: List<SaleEvent>): List<YearGains> =
        sales.groupBy { year(it.dateMillis) }.map { (y, l) -> YearGains(y, l.sumOf { it.gainMinor }, l.sumOf { it.feesMinor }, l.sumOf { it.proceedsMinor }, l.size) }
            .sortedByDescending { it.year }
}

/** CSV with a dot as decimal separator and ISO dates, one file per call. */
object CsvExport {
    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    private fun money(m: Long) = BigDecimal(m).movePointLeft(2).toPlainString()
    private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun trades(trades: List<StockTradeEntity>, stocks: List<StockEntity>, accountName: (String) -> String, year: Int?): String {
        val by = stocks.associateBy { it.id }
        val rows = trades.filter { year == null || day(it.dateMillis).year == year }.sortedBy { it.dateMillis }.map { t ->
            val s = by[t.stockId]
            val gross = (t.quantity * t.priceMinor).roundToLong()
            val net = if (t.type == TradeType.BUY) gross + t.feesMinor else gross - t.feesMinor
            listOf(day(t.dateMillis).toString(), esc(s?.let { accountName(it.accountId) } ?: ""), esc(s?.symbol ?: ""), t.type,
                Money.qtyInput(t.quantity), money(t.priceMinor), money(t.feesMinor), money(gross), money(net), esc(t.note)).joinToString(",")
        }
        return (listOf("date,account,symbol,type,quantity,price,fees,gross,net,note") + rows).joinToString("\n") + "\n"
    }

    fun dividends(divs: List<DividendEntity>, stocks: List<StockEntity>, accountName: (String) -> String, year: Int?): String {
        val by = stocks.associateBy { it.id }
        val rows = divs.filter { year == null || day(it.dateMillis).year == year }.sortedBy { it.dateMillis }.map { d ->
            val s = by[d.stockId]
            listOf(day(d.dateMillis).toString(), esc(s?.let { accountName(it.accountId) } ?: ""), esc(s?.symbol ?: ""), money(d.amountMinor), esc(d.note)).joinToString(",")
        }
        return (listOf("date,account,symbol,amount,note") + rows).joinToString("\n") + "\n"
    }
}
