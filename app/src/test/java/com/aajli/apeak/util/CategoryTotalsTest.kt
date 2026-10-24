package com.aajli.apeak.util

import com.aajli.apeak.data.CategoryEntity
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CategoryTotalsTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun t(cat: String, a: Long, d: Long) = TransactionEntity("i$cat$d", Tx.EXPENSE, a, d, "", "a", "A", "", "c$cat", cat, "", 0)
    private val known = listOf(CategoryEntity("1", "Food", "F", Tx.EXPENSE, 0), CategoryEntity("2", "Fun", "G", Tx.EXPENSE, 1))

    @Test fun allCategoriesPresentIncludingDeletedAndZero() {
        val txs = listOf(t("Food", 100, ms(2026, 1, 1)), t("Old", 40, ms(2026, 2, 1)), t("Food", 10, ms(2025, 2, 1)))
        val r = CashStats.categoryTotals(txs, 2026, Tx.EXPENSE, known)
        assertEquals(listOf("Food", "Old", "Fun"), r.map { it.name })
        assertEquals(listOf(100L, 40L, 0L), r.map { it.amount })
    }

    @Test fun matrixPerYear() {
        val txs = listOf(t("Food", 100, ms(2026, 1, 1)), t("Food", 10, ms(2025, 2, 1)))
        val m = CashStats.categoryByYear(txs, listOf(2025, 2026), Tx.EXPENSE, known)
        assertEquals(listOf(10L, 100L), m.first { it.first.name == "Food" }.second)
        assertEquals(listOf(0L, 0L), m.first { it.first.name == "Fun" }.second)
    }
}
