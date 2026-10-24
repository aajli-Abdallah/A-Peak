package com.aajli.apeak.util

import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class OverviewCalcTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun totalsGainsAndContributions() {
        val banks = listOf(BankAccountEntity("c", "Current", "B", BankKind.CURRENT, 0.0, 100000, "", 0), BankAccountEntity("s", "Savings", "B", BankKind.SAVINGS, 3.0, 0, "", 0))
        val entries = listOf(
            BankEntryEntity("e1", "s", BankType.DEPOSIT, 50000, ms(2026, 1, 10), ""),
            BankEntryEntity("e2", "s", BankType.INTEREST, 1000, ms(2026, 2, 10), ""),
            BankEntryEntity("e3", "c", BankType.WITHDRAW, 10000, ms(2026, 2, 12), ""))
        val brokers = listOf(BrokerAccountEntity("b", "PEA", "Broker", "", 0))
        val stocks = listOf(StockEntity("st", "b", "AAPL", "Apple", 15000, ms(2026, 3, 1), 0))
        val trades = listOf(StockTradeEntity("t", "st", TradeType.BUY, 2.0, 10000, 0, ms(2026, 2, 1), ""))
        val divs = listOf(DividendEntity("d", "st", 500, ms(2026, 3, 1), ""))
        val s = OverviewCalc.compute(banks, entries, brokers, stocks, trades, divs, emptyList(), emptyList(), LocalDate.of(2026, 3, 15))
        assertTrue(s.ready)
        assertEquals(90000L, s.current); assertEquals(51000L, s.savings); assertEquals(30000L, s.stockValue); assertEquals(171000L, s.net)
        assertEquals(10000L, s.unrealized); assertEquals(500L, s.dividends); assertEquals(1000L, s.interest); assertEquals(11500L, s.gains)
        assertEquals(160000L, s.contributions)
        assertEquals(s.gains - s.dividends, s.growth)   // dividends are not inside the balances
        assertEquals(50000L, s.deposits); assertEquals(10000L, s.withdrawals); assertEquals(20000L, s.stocksCost)
        assertEquals(1500L, s.passiveTotal); assertTrue(s.hasPassive)
        assertEquals("AAPL", s.topSymbol); assertEquals(100L, s.topSharePct)
        assertEquals(1, s.perf.size)
        assertFalse(s.noAccounts)
    }

    @Test fun emptyDataIsReadyButEmpty() {
        val s = OverviewCalc.compute(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), LocalDate.of(2026, 3, 15))
        assertTrue(s.ready); assertTrue(s.noAccounts); assertFalse(s.hasData); assertEquals(0L, s.net)
        assertFalse(OverviewState.EMPTY.ready)
    }
}
