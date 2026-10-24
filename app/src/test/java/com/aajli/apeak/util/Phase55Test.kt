package com.aajli.apeak.util

import com.aajli.apeak.backup.BackupCodec
import com.aajli.apeak.backup.BackupData
import com.aajli.apeak.backup.BackupMerge
import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Phase55Test {
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun trade(id: String, type: String, q: Double, p: Long, d: Long, fees: Long = 0L, note: String = "") = StockTradeEntity(id, "s", type, q, p, fees, d, note)
    private fun split(d: Long, n: Int, den: Int) = StockSplitEntity("x$d", "s", d, n, den)

    @Test fun splitDoublesQuantityAndKeepsCost() {
        val pos = Calc.position(Splits.adjustTrades(listOf(trade("a", TradeType.BUY, 10.0, 10000, ms(2026, 1, 5))), listOf(split(ms(2026, 2, 1), 2, 1))))
        assertEquals(20.0, pos.qty, 1e-9); assertEquals(100000L, pos.costMinor)
    }

    @Test fun oddSplitKeepsCostExact() {
        val pos = Calc.position(Splits.adjustTrades(listOf(trade("a", TradeType.BUY, 10.0, 10001, ms(2026, 1, 5))), listOf(split(ms(2026, 2, 1), 3, 1))))
        assertEquals(30.0, pos.qty, 1e-9); assertEquals(100010L, pos.costMinor)
    }

    @Test fun heldAtUsesSplitsUpToDate() {
        val t = listOf(trade("a", TradeType.BUY, 10.0, 10000, ms(2026, 1, 5)))
        val sp = listOf(split(ms(2026, 2, 1), 2, 1))
        assertEquals(10.0, Splits.heldAt(t, sp, ms(2026, 1, 20)), 1e-9)
        assertEquals(20.0, Splits.heldAt(t, sp, ms(2026, 2, 10)), 1e-9)
    }

    @Test fun pricesBeforeSplitAreAdjusted() {
        val p = listOf(PricePointEntity("1", "s", ms(2026, 1, 10), 10000), PricePointEntity("2", "s", ms(2026, 2, 5), 5200))
        assertEquals(listOf(5000L, 5200L), Splits.adjustPrices(p, listOf(split(ms(2026, 2, 1), 2, 1))).map { it.priceMinor })
    }

    @Test fun salesAndYears() {
        val t = listOf(trade("b", TradeType.BUY, 10.0, 10000, ms(2025, 1, 2)), trade("s1", TradeType.SELL, 4.0, 15000, ms(2026, 3, 2), 100))
        val s = Reports.sales(t)
        assertEquals(1, s.size)
        assertEquals(59900L, s[0].proceedsMinor); assertEquals(40000L, s[0].costMinor); assertEquals(19900L, s[0].gainMinor)
        assertEquals(listOf(2026), Reports.byYear(s).map { it.year })
    }

    @Test fun csvEscapesAndFiltersYear() {
        val st = StockEntity("s", "acc", "AAPL", "Apple", 0, 0, 0)
        val csv = CsvExport.trades(listOf(trade("a", TradeType.BUY, 1.5, 10000, ms(2026, 1, 5), 0, "a,\"b\""), trade("b", TradeType.BUY, 1.0, 100, ms(2025, 1, 5))), listOf(st), { "Broker" }, 2026)
        assertTrue(csv.lines()[0].startsWith("date,account,symbol"))
        assertTrue(csv.contains("\"a,\"\"b\"\"\""))
        assertEquals(2, csv.trim().lines().size) // header + the one 2026 trade
    }

    @Test fun priceAlerts() {
        assertEquals(PriceAlertKind.TARGET, PriceAlerts.crossed(100, 120, 120, 80))
        assertEquals(PriceAlertKind.STOP, PriceAlerts.crossed(100, 75, 120, 80))
        assertNull(PriceAlerts.crossed(130, 140, 120, 80))
        assertNull(PriceAlerts.status(0, 120, 80))
    }

    @Test fun backupKeepsSplitsAndStockFields() {
        val st = StockEntity("s", "a", "AAPL", "Apple", 100, 1, 2, "Tech", "n", 150, 80)
        val d = BackupData("1.3.55", 1L, "A", stocks = listOf(st), splits = listOf(StockSplitEntity("x", "s", 5L, 2, 1)))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
        val moved = BackupData(splits = listOf(StockSplitEntity("other", "s", 5L, 2, 1)))
        val r = BackupMerge.merge(BackupData(splits = d.splits), moved)
        assertEquals(0, r.report.first { it.key == "splits" }.new)
    }
}
