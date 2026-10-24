package com.aajli.apeak.util

import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class YearStatsTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun t(a: Long, d: Long, acc: String = "Cash") = TransactionEntity("i$d$a$acc", Tx.EXPENSE, a, d, "", "a", acc, "", "c", "Food", "", 0)

    @Test fun weekdayAccountsTopMonths() {
        val monday = ms(2026, 3, 2) // a Monday
        val txs = listOf(t(100, monday), t(50, monday, "Card"), t(30, ms(2026, 4, 1)))
        assertEquals(150L, CashStats.spendingByWeekday(txs, 2026)[0])
        assertEquals(listOf("Cash", "Card"), CashStats.spendingByAccount(txs, 2026).map { it.first })
        assertEquals(100L, CashStats.topExpenses(txs, 2026, 1)[0].amountMinor)
        assertEquals(2, CashStats.activeMonths(txs))
    }
}
