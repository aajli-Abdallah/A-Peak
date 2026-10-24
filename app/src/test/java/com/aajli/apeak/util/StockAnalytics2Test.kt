package com.aajli.apeak.util

import com.aajli.apeak.data.PricePointEntity
import com.aajli.apeak.data.StockTradeEntity
import com.aajli.apeak.data.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StockAnalytics2Test {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun mergeBySymbol() {
        val rows = listOf(testRow("AAPL", "a1", 2.0, 200, 300), testRow("aapl", "a2", 3.0, 300, 330), testRow("MSFT", "a1", 1.0, 100, 90))
        val m = StockAnalytics.holdings(rows, { if (it == "a1") "One" else "Two" }, true)
        val aapl = m.first { it.key == "AAPL" }
        assertEquals(2, m.size)
        assertEquals(5.0, aapl.qty, 1e-9)
        assertEquals(630L, aapl.valueMinor)
        assertEquals(listOf("One", "Two"), aapl.accounts)
        assertEquals(2, aapl.stockIds.size)
    }

    @Test fun bestAndWorst() {
        val h = StockAnalytics.holdings(listOf(testRow("A", "a", 1.0, 100, 200), testRow("B", "a", 1.0, 100, 50), testRow("C", "a", 1.0, 100, 110)), { "x" }, false)
        val (best, worst) = StockAnalytics.bestWorst(h)
        assertEquals("A", best!!.symbol); assertEquals("B", worst!!.symbol)
    }

    @Test fun portfolioSeriesValueVsCost() {
        val buy = StockTradeEntity("t", "s", TradeType.BUY, 2.0, 10000, 0, ms(2026, 3, 5), "")
        val p = listOf(PricePointEntity("p", "s", ms(2026, 3, 10), 15000))
        val s = StockAnalytics.portfolioSeries(listOf(buy), p, LocalDate.of(2026, 3, 15))
        assertEquals(1, s.size)
        assertEquals(30000L, s[0].value); assertEquals(20000L, s[0].cost)
    }
}
