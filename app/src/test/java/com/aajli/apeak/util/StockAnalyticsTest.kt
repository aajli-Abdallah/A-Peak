package com.aajli.apeak.util

import com.aajli.apeak.data.StockEntity
import org.junit.Assert.assertEquals
import org.junit.Test

fun testRow(sym: String, acc: String, qty: Double, cost: Long, value: Long, bought: Long = cost, realized: Long = 0, div: Long = 0) =
    StockRow(StockEntity("id$sym$acc", acc, sym, "", 0, 0, 0), Position(qty, cost, realized, bought, emptyMap()), value, if (qty > 0) value - cost else 0L, div)

class StockAnalyticsTest {
    @Test fun sortModes() {
        val h = StockAnalytics.holdings(listOf(testRow("B", "a", 1.0, 100, 150), testRow("A", "a", 1.0, 100, 300), testRow("C", "a", 1.0, 100, 90)), { "x" }, false)
        assertEquals(listOf("A", "B", "C"), StockAnalytics.sort(h, 0).map { it.symbol })
        assertEquals(listOf("A", "B", "C"), StockAnalytics.sort(h, 1).map { it.symbol })
        assertEquals(listOf("A", "B", "C"), StockAnalytics.sort(h, 2).map { it.symbol })
    }

    @Test fun holdingMath() {
        val h = StockAnalytics.holdings(listOf(testRow("A", "a", 2.0, 200, 300, div = 10)), { "Acc" }, false)[0]
        assertEquals(100L, h.unrealizedMinor)
        assertEquals(110L, h.totalReturn)
        assertEquals(100L, h.avgPrice)
        assertEquals(listOf("Acc"), h.accounts)
    }

    @Test fun ageDays() { assertEquals(2, StockAnalytics.ageDays(0L, 2 * 86_400_000L + 5)) }
}
