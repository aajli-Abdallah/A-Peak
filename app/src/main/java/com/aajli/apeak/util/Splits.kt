package com.aajli.apeak.util

import com.aajli.apeak.data.PricePointEntity
import com.aajli.apeak.data.StockSplitEntity
import com.aajli.apeak.data.StockTradeEntity
import com.aajli.apeak.data.TradeType
import kotlin.math.roundToLong

/**
 * Stock splits are applied when reading, never written into your trades.
 * A trade dated before a split is shown in post-split terms: quantity x ratio, price / ratio.
 * Rounding of the price is folded into the fees, so the cost basis and proceeds stay exact.
 */
object Splits {
    fun factor(splits: List<StockSplitEntity>, stockId: String, date: Long): Double =
        splits.filter { it.stockId == stockId && it.dateMillis > date && it.denominator > 0 }.fold(1.0) { a, s -> a * s.numerator / s.denominator }

    fun adjustTrades(trades: List<StockTradeEntity>, splits: List<StockSplitEntity>): List<StockTradeEntity> {
        if (splits.isEmpty()) return trades
        return trades.map { t ->
            val f = factor(splits, t.stockId, t.dateMillis)
            if (f == 1.0) t else {
                val q = t.quantity * f
                val p = (t.priceMinor / f).roundToLong()
                val resid = (t.quantity * t.priceMinor - q * p).roundToLong()
                t.copy(quantity = q, priceMinor = p, feesMinor = t.feesMinor + if (t.type == TradeType.BUY) resid else -resid)
            }
        }
    }

    fun adjustPrices(prices: List<PricePointEntity>, splits: List<StockSplitEntity>): List<PricePointEntity> {
        if (splits.isEmpty()) return prices
        return prices.map { p ->
            val f = factor(splits, p.stockId, p.dateMillis)
            if (f == 1.0) p else p.copy(priceMinor = (p.priceMinor / f).roundToLong())
        }
    }

    /** Shares held on [date], counted in the units valid on that date (only splits up to that date apply). Pass one stock's raw trades and splits. */
    fun heldAt(trades: List<StockTradeEntity>, splits: List<StockSplitEntity>, date: Long): Double =
        Calc.position(adjustTrades(trades.filter { it.dateMillis <= date }, splits.filter { it.dateMillis <= date })).qty
}
