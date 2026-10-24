package com.aajli.apeak.util

import kotlin.math.roundToLong

/** One line of the holdings list: a single stock, or all stocks with the same ticker when merged. */
data class Holding(
    val key: String, val symbol: String, val name: String,
    val accounts: List<String>, val stockIds: List<String>,
    val qty: Double, val costMinor: Long, val valueMinor: Long,
    val realizedMinor: Long, val dividendsMinor: Long, val boughtMinor: Long
) {
    val unrealizedMinor: Long get() = if (qty > 0) valueMinor - costMinor else 0L
    val totalReturn: Long get() = unrealizedMinor + realizedMinor + dividendsMinor
    val returnPct: Double? get() = if (boughtMinor > 0) totalReturn * 100.0 / boughtMinor else null
    val avgPrice: Long get() = if (qty > 0) (costMinor.toDouble() / qty).roundToLong() else 0L
}

object StockAnalytics {
    fun holdings(rows: List<StockRow>, accountName: (String) -> String, merge: Boolean): List<Holding> {
        fun one(r: StockRow) = Holding(r.stock.id, r.stock.symbol, r.stock.name, listOf(accountName(r.stock.accountId)), listOf(r.stock.id),
            r.pos.qty, r.pos.costMinor, r.valueMinor, r.pos.realizedMinor, r.dividendsMinor, r.pos.boughtMinor)
        if (!merge) return rows.map { one(it) }
        return rows.groupBy { it.stock.symbol.uppercase() }.map { (sym, list) ->
            val hs = list.map { one(it) }
            Holding(sym, sym, hs.firstOrNull { it.name.isNotBlank() }?.name ?: "", hs.flatMap { it.accounts }.distinct(), hs.flatMap { it.stockIds },
                hs.sumOf { it.qty }, hs.sumOf { it.costMinor }, hs.sumOf { it.valueMinor }, hs.sumOf { it.realizedMinor },
                hs.sumOf { it.dividendsMinor }, hs.sumOf { it.boughtMinor })
        }
    }

    /** mode 0 = value, 1 = return %, 2 = symbol. */
    fun sort(h: List<Holding>, mode: Int): List<Holding> = when (mode) {
        1 -> h.sortedByDescending { it.returnPct ?: Double.NEGATIVE_INFINITY }
        2 -> h.sortedBy { it.symbol }
        else -> h.sortedByDescending { it.valueMinor }
    }

    fun ageDays(lastPriceDate: Long, now: Long = System.currentTimeMillis()): Int = ((now - lastPriceDate) / 86_400_000L).toInt()

    /** Best and worst performer by return on money bought (only holdings with purchases). */
    fun bestWorst(h: List<Holding>): Pair<Holding?, Holding?> {
        val r = h.filter { it.returnPct != null }
        return r.maxByOrNull { it.returnPct!! } to r.minByOrNull { it.returnPct!! }
    }

    /** Month-end portfolio value (recorded price, else cost) next to the cost of the shares held. Max 60 months. */
    fun portfolioSeries(trades: List<com.aajli.apeak.data.StockTradeEntity>, prices: List<com.aajli.apeak.data.PricePointEntity>,
                        now: java.time.LocalDate = java.time.LocalDate.now()): List<PortfolioPoint> {
        if (trades.isEmpty()) return emptyList()
        val zone = java.time.ZoneId.systemDefault()
        val first = java.time.YearMonth.from(java.time.Instant.ofEpochMilli(trades.minOf { it.dateMillis }).atZone(zone))
        val last = java.time.YearMonth.from(now)
        val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }.toList().takeLast(60)
        val byStock = trades.groupBy { it.stockId }
        val pByStock = prices.groupBy { it.stockId }
        return months.map { ym ->
            val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            var value = 0L; var cost = 0L
            for ((sid, ts) in byStock) {
                val pos = Calc.position(ts.filter { it.dateMillis < end })
                val price = PriceHistory.priceAt(pByStock[sid].orEmpty(), end)
                cost += pos.costMinor
                value += if (price != null) (pos.qty * price).roundToLong() else pos.costMinor
            }
            PortfolioPoint(ym, value, cost)
        }
    }
}

data class PortfolioPoint(val month: java.time.YearMonth, val value: Long, val cost: Long)
