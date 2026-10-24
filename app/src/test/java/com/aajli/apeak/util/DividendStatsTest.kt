package com.aajli.apeak.util

import com.aajli.apeak.data.DividendEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class DividendStatsTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun dv(s: String, a: Long, d: Long) = DividendEntity("$s$d", s, a, d, "")

    @Test fun yearsAndTrailing() {
        val divs = listOf(dv("a", 100, ms(2026, 3, 1)), dv("a", 50, ms(2025, 12, 1)), dv("b", 20, ms(2024, 5, 1)))
        assertEquals(listOf(2026 to 100L, 2025 to 50L, 2024 to 20L), DividendStats.byYear(divs))
        val now = LocalDate.of(2026, 3, 15)
        val t = DividendStats.trailingMonths(divs, now)
        assertEquals(12, t.size)
        assertEquals(YearMonth.of(2026, 3), t.last().first)
        assertEquals(150L, DividendStats.last12(divs, now))
        assertEquals(mapOf("a" to 150L, "b" to 20L), DividendStats.byStock(divs))
    }

    @Test fun yieldAndPerShare() {
        assertEquals(5.0, DividendStats.yieldPct(50, 1000)!!, 1e-9)
        assertEquals(250L, DividendStats.perShareTotal(10.0, 0.25))
    }
}
