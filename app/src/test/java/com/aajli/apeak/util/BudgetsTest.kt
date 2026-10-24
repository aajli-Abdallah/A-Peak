package com.aajli.apeak.util

import com.aajli.apeak.backup.BackupCodec
import com.aajli.apeak.backup.BackupData
import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class BudgetsTest {
    private fun b(id: String, start: Int, limit: Long) = BudgetEntity(id, "c", "Food", start, limit)
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun effectiveLimitIsVersioned() {
        val rows = listOf(b("1", 202601, 100), b("2", 202603, 200), b("3", 202605, 0))
        assertEquals(0L, Budgets.limitFor(rows, "c", YearMonth.of(2025, 12)))
        assertEquals(100L, Budgets.limitFor(rows, "c", YearMonth.of(2026, 2)))
        assertEquals(200L, Budgets.limitFor(rows, "c", YearMonth.of(2026, 4)))
        assertEquals(0L, Budgets.limitFor(rows, "c", YearMonth.of(2026, 6)))
    }

    @Test fun levelsAndCrossing() {
        assertEquals(BudgetLevel.OK, Budgets.level(79, 100))
        assertEquals(BudgetLevel.WARN, Budgets.level(80, 100))
        assertEquals(BudgetLevel.OVER, Budgets.level(100, 100))
        assertEquals(BudgetLevel.OK, Budgets.level(500, 0))
        assertEquals(BudgetLevel.WARN, Budgets.crossed(70, 85, 100))
        assertEquals(BudgetLevel.OVER, Budgets.crossed(90, 110, 100))
        assertNull(Budgets.crossed(81, 90, 100))
    }

    @Test fun progressMatchesById() {
        val tx = TransactionEntity("t", Tx.EXPENSE, 50, ms(2026, 3, 5), "", "a", "A", "", "c", "Food", "", 0)
        val p = Budgets.progress(listOf(b("1", 202603, 100)), listOf(CategoryEntity("c", "Food", "F", Tx.EXPENSE, 0)), listOf(tx), YearMonth.of(2026, 3))
        assertEquals(50L, p[0].spent)
        assertEquals(0.5f, p[0].ratio, 1e-6f)
    }

    @Test fun backupKeepsBudgets() {
        val d = BackupData("1.2.0", 1L, "A", budgets = listOf(b("1", 202603, 100)))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
    }
}
