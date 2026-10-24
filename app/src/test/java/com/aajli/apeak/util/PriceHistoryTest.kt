package com.aajli.apeak.util

import com.aajli.apeak.backup.BackupCodec
import com.aajli.apeak.backup.BackupData
import com.aajli.apeak.backup.BackupMerge
import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class PriceHistoryTest {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun pointIdIsPerStockPerDay() {
        val a = PriceHistory.point("s", ms(2026, 3, 5) + 5000, 100)
        val b = PriceHistory.point("s", ms(2026, 3, 5) + 9000, 120)
        assertEquals(a.id, b.id)
        assertEquals(ms(2026, 3, 5), a.dateMillis)
    }

    @Test fun priceAtTakesLatestBefore() {
        val pts = listOf(PricePointEntity("1", "s", ms(2026, 3, 1), 100), PricePointEntity("2", "s", ms(2026, 3, 20), 150))
        assertNull(PriceHistory.priceAt(pts, ms(2026, 3, 1)))
        assertEquals(100L, PriceHistory.priceAt(pts, ms(2026, 3, 10)))
        assertEquals(150L, PriceHistory.priceAt(pts, ms(2026, 4, 1)))
    }

    @Test fun netWorthUsesRecordedPricesElseCost() {
        val buy = StockTradeEntity("t", "s", TradeType.BUY, 2.0, 10000, 0, ms(2026, 3, 5), "")
        val now = LocalDate.of(2026, 3, 15)
        assertEquals(20000L, OverviewStats.netWorthSeries(emptyList(), emptyList(), listOf(buy), emptyList(), now).last().value)
        val p = listOf(PricePointEntity("p", "s", ms(2026, 3, 10), 15000))
        assertEquals(30000L, OverviewStats.netWorthSeries(emptyList(), emptyList(), listOf(buy), p, now).last().value)
    }

    @Test fun categoryByMonthStacks() {
        fun t(cat: String, a: Long, d: Long) = TransactionEntity("i$cat$d", Tx.EXPENSE, a, d, "", "a", "A", "", "c", cat, "", 0)
        val txs = listOf(t("Food", 100, ms(2026, 1, 3)), t("Fun", 30, ms(2026, 2, 3)), t("Misc", 5, ms(2026, 2, 4)))
        val s = CashStats.categoryByMonth(txs, 2026, 2, "Other")
        assertEquals(listOf("Food", "Fun", "Other"), s.map { it.first })
        assertEquals(100L, s[0].second[0]); assertEquals(5L, s[2].second[1])
    }

    @Test fun backupKeepsAndMatchesPrices() {
        val p = PricePointEntity("s:1", "s", 1L, 100)
        val d = BackupData("1.3.0", 1L, "A", prices = listOf(p))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
        val moved = BackupData(prices = listOf(p.copy(id = "other:1")))
        val r = BackupMerge.merge(BackupData(prices = listOf(p)), moved)
        assertEquals(1, r.report.first { it.key == "prices" }.updated + r.report.first { it.key == "prices" }.unchanged)
        assertEquals(0, r.report.first { it.key == "prices" }.new)
    }
}
