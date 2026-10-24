package com.aajli.apeak.util

import com.aajli.apeak.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class PerfRow(val symbol: String, val totalReturn: Long, val boughtMinor: Long, val costMinor: Long, val dividendsMinor: Long)

/** Everything the Overview page shows, computed once per data change. */
data class OverviewState(
    val ready: Boolean = false, val hasData: Boolean = false, val noAccounts: Boolean = true,
    val current: Long = 0, val savings: Long = 0, val stockValue: Long = 0, val liquid: Long = 0, val net: Long = 0,
    val interest: Long = 0, val dividends: Long = 0, val realized: Long = 0, val unrealized: Long = 0, val gains: Long = 0,
    val stocksCost: Long = 0, val deposits: Long = 0, val withdrawals: Long = 0,
    val years: List<YearRow> = emptyList(), val series: List<NetPoint> = emptyList(),
    val contributions: Long = 0, val growth: Long = 0, val avgSpend: Long = 0,
    val hasPassive: Boolean = false, val passiveTotal: Long = 0, val passiveYear: Long = 0, val passive12: Long = 0, val passiveAvg: Long = 0,
    val perf: List<PerfRow> = emptyList(), val parts: List<Pair<String, Long>> = emptyList(),
    val topSymbol: String? = null, val topSharePct: Long = 0
) {
    companion object { val EMPTY = OverviewState() }
}

object OverviewCalc {
    fun compute(banks: List<BankAccountEntity>, entries: List<BankEntryEntity>, brokers: List<BrokerAccountEntity>,
                stocks: List<StockEntity>, trades: List<StockTradeEntity>, divs: List<DividendEntity>,
                prices: List<PricePointEntity>, txs: List<TransactionEntity>, today: LocalDate): OverviewState {
        val zone = ZoneId.systemDefault()
        val byAcc = entries.groupBy { it.accountId }
        fun bal(b: BankAccountEntity): Long = Calc.bankBalance(b, byAcc[b.id].orEmpty())
        val current = banks.filter { it.kind == BankKind.CURRENT }.sumOf { bal(it) }
        val savings = banks.filter { it.kind == BankKind.SAVINGS }.sumOf { bal(it) }
        val liquid = banks.sumOf { bal(it) }
        val rows = stocks.map { Calc.stockRow(it, trades, divs) }
        val stockValue = rows.sumOf { it.valueMinor }
        val unreal = rows.sumOf { it.unrealizedMinor }
        val realized = rows.sumOf { it.pos.realizedMinor }
        val dividends = rows.sumOf { it.dividendsMinor }
        val interest = entries.filter { it.type == BankType.INTEREST }.sumOf { it.amountMinor }
        val deposits = entries.filter { it.type == BankType.DEPOSIT }.sumOf { it.amountMinor }
        val withdrawals = entries.filter { it.type == BankType.WITHDRAW }.sumOf { it.amountMinor }
        val net = liquid + stockValue
        val contributions = banks.sumOf { it.openingMinor } + deposits - withdrawals + rows.sumOf { it.pos.costMinor - it.pos.realizedMinor }

        val passive = entries.filter { it.type == BankType.INTEREST }.map { it.dateMillis to it.amountMinor } + divs.map { it.dateMillis to it.amountMinor }
        val passiveTotal = passive.sumOf { it.second }
        val cutoff = today.minusMonths(12).atStartOfDay(zone).toInstant().toEpochMilli()
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val span = if (passive.isEmpty()) 1L else (ChronoUnit.MONTHS.between(dateOf(passive.minOf { it.first }), today) + 1).coerceAtLeast(1L)

        val perf = rows.filter { it.pos.boughtMinor > 0 }.sortedByDescending { it.totalReturn.toDouble() / it.pos.boughtMinor }
            .map { PerfRow(it.stock.symbol, it.totalReturn, it.pos.boughtMinor, it.pos.costMinor, it.dividendsMinor) }
        val parts = (banks.map { it.name to bal(it) } + brokers.map { b -> b.name to rows.filter { it.stock.accountId == b.id }.sumOf { it.valueMinor } }).filter { it.second > 0 }
        val top = rows.maxByOrNull { it.valueMinor }

        return OverviewState(
            ready = true,
            hasData = txs.isNotEmpty() || banks.isNotEmpty() || brokers.isNotEmpty(),
            noAccounts = banks.isEmpty() && brokers.isEmpty(),
            current = current, savings = savings, stockValue = stockValue, liquid = liquid, net = net,
            interest = interest, dividends = dividends, realized = realized, unrealized = unreal, gains = interest + dividends + realized + unreal,
            stocksCost = rows.sumOf { it.pos.costMinor }, deposits = deposits, withdrawals = withdrawals,
            years = Calc.yearly(entries, divs, rows),
            series = OverviewStats.netWorthSeries(banks, entries, trades, prices, today),
            contributions = contributions, growth = net - contributions,
            avgSpend = OverviewStats.avgMonthlySpending(txs, today),
            hasPassive = passive.isNotEmpty(), passiveTotal = passiveTotal,
            passiveYear = passive.filter { dateOf(it.first).year == today.year }.sumOf { it.second },
            passive12 = passive.filter { it.first >= cutoff }.sumOf { it.second },
            passiveAvg = passiveTotal / span,
            perf = perf, parts = parts,
            topSymbol = top?.stock?.symbol,
            topSharePct = if (top != null && stockValue > 0) top.valueMinor * 100 / stockValue else 0L
        )
    }
}
