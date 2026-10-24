package com.aajli.apeak.util

import com.aajli.apeak.data.*
import kotlin.math.roundToLong
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class NetPoint(val month: YearMonth, val value: Long)

object OverviewStats {
    private fun zdt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    /** Month-end net worth. Banks are exact; stocks use the latest recorded price before month end, or cost when none exists. Max 60 points. */
    fun netWorthSeries(banks: List<BankAccountEntity>, entries: List<BankEntryEntity>, trades: List<StockTradeEntity>,
                       prices: List<PricePointEntity> = emptyList(), now: LocalDate = LocalDate.now()): List<NetPoint> {
        val dates = entries.map { it.dateMillis } + trades.map { it.dateMillis }
        if (dates.isEmpty()) return emptyList()
        val zone = ZoneId.systemDefault()
        val first = YearMonth.from(zdt(dates.min()))
        val last = YearMonth.from(now)
        val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList().takeLast(60)
        val opening = banks.sumOf { it.openingMinor }
        val byStock = trades.groupBy { it.stockId }
        val pricesByStock = prices.groupBy { it.stockId }
        return months.map { ym ->
            val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val bank = opening + entries.filter { it.dateMillis < end }.sumOf { if (it.type == BankType.WITHDRAW) -it.amountMinor else it.amountMinor }
            val stockPart = byStock.entries.sumOf { (sid, ts) ->
                val pos = Calc.position(ts.filter { it.dateMillis < end })
                val price = PriceHistory.priceAt(pricesByStock[sid].orEmpty(), end)
                if (price != null) (pos.qty * price).roundToLong() else pos.costMinor
            }
            NetPoint(ym, bank + stockPart)
        }
    }

    /** Average spending per month over the last 6 completed months with data; falls back to the current month. */
    fun avgMonthlySpending(txs: List<TransactionEntity>, now: LocalDate = LocalDate.now()): Long {
        val expenses = txs.filter { it.type == Tx.EXPENSE }
        val ym = YearMonth.from(now)
        fun sum(m: YearMonth) = expenses.filter { YearMonth.from(zdt(it.dateMillis)) == m }.sumOf { it.amountMinor }
        val past = (1..6).map { sum(ym.minusMonths(it.toLong())) }.filter { it > 0 }
        return if (past.isNotEmpty()) past.sum() / past.size else sum(ym)
    }
}
