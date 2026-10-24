package com.aajli.apeak.util

import com.aajli.apeak.data.StockTradeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CalcTest {
    private fun t(type: String, q: Double, p: Long, d: Long, f: Long = 0) = StockTradeEntity("id$d", "s", type, q, p, f, d, "")

    @Test fun averageCostAndRealized() {
        val pos = Calc.position(listOf(t("BUY", 10.0, 10000, 1), t("BUY", 10.0, 20000, 2), t("SELL", 5.0, 30000, 3)))
        assertEquals(15.0, pos.qty, 1e-9)
        assertEquals(225000L, pos.costMinor)
        assertEquals(75000L, pos.realizedMinor)
    }

    @Test fun feesAddedToCost() {
        assertEquals(10100L, Calc.position(listOf(t("BUY", 1.0, 10000, 1, 100))).costMinor)
    }
}
