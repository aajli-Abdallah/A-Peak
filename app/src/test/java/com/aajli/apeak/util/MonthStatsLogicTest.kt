package com.aajli.apeak.util

import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthStatsLogicTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun t(a: Long, d: Long) = TransactionEntity("i$d$a", Tx.EXPENSE, a, d, "", "a", "A", "", "c", "Food", "", 0)

    @Test fun pct() { assertEquals(50.0, CashStats.pctChange(150, 100)!!, 1e-9); assertNull(CashStats.pctChange(5, 0)) }
    @Test fun forecast() { assertEquals(3100L, CashStats.forecast(1000, 10, 31)) }
    @Test fun running() { assertEquals(listOf(100L, 50L), CashStats.runningBalance(listOf(MonthTotals(100, 0), MonthTotals(0, 50)))) }
    @Test fun daily() {
        val d = CashStats.dailyExpense(listOf(t(70, ms(2026, 2, 3)), t(30, ms(2026, 2, 3))), YearMonth.of(2026, 2))
        assertEquals(28, d.size); assertEquals(100L, d[2])
    }
}
