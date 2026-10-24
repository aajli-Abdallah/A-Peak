package com.aajli.apeak.util

import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CashStatsTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun t(type: String, amount: Long, date: Long) = TransactionEntity("i$date$amount", type, amount, date, "", "a", "A", "", "c", "Food", "", 0)

    @Test fun monthAndYearTotals() {
        val txs = listOf(t(Tx.INCOME, 1000, ms(2026, 3, 1)), t(Tx.EXPENSE, 400, ms(2026, 3, 9)), t(Tx.EXPENSE, 50, ms(2025, 12, 31)))
        val m = CashStats.byMonth(txs, 2026)
        assertEquals(12, m.size)
        assertEquals(600L, m[2].balance)
        assertEquals(0L, m[0].income)
        assertEquals(listOf(2026, 2025), CashStats.byYear(txs).keys.toList())
        assertEquals(50L, CashStats.spendingByCategory(txs, 2025)[0].second)
    }
}
