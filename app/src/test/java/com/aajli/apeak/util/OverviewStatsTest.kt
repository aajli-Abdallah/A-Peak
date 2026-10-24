package com.aajli.apeak.util

import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OverviewStatsTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun netWorthSeries() {
        val banks = listOf(BankAccountEntity("b", "B", "", BankKind.CURRENT, 0.0, 100000, "", 0))
        val entries = listOf(BankEntryEntity("e", "b", BankType.DEPOSIT, 50000, ms(2026, 2, 10), ""))
        val trades = listOf(StockTradeEntity("t", "s", TradeType.BUY, 2.0, 10000, 0, ms(2026, 3, 5), ""))
        val s = OverviewStats.netWorthSeries(banks, entries, trades, now = LocalDate.of(2026, 3, 15))
        assertEquals(listOf(150000L, 170000L), s.map { it.value })
    }

    @Test fun avgSpending() {
        fun t(a: Long, d: Long) = TransactionEntity("i$d", Tx.EXPENSE, a, d, "", "a", "A", "", "c", "Food", "", 0)
        val txs = listOf(t(100, ms(2026, 1, 5)), t(300, ms(2026, 2, 5)), t(999, ms(2026, 3, 2)))
        assertEquals(200L, OverviewStats.avgMonthlySpending(txs, LocalDate.of(2026, 3, 15)))
    }
}
