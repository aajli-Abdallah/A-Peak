package com.aajli.apeak.data

import android.content.Context
import androidx.room.withTransaction
import com.aajli.apeak.util.PriceAlert
import com.aajli.apeak.util.PriceAlertKind
import com.aajli.apeak.util.PriceAlerts
import com.aajli.apeak.util.PriceHistory
import com.aajli.apeak.util.Splits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import kotlin.math.roundToLong

private fun uid() = UUID.randomUUID().toString()

/**
 * Single source of truth for brokers, stocks, trades, dividends, prices, splits and the pick lists,
 * with the rules that go with them (split adjustment, price history, price alerts).
 * One instance per process, shared by every view model.
 */
class StocksRepository private constructor(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val w = db.wealth()
    private val lists = db.lists()
    private fun <T> Flow<List<T>>.hot(): StateFlow<List<T>> = stateIn(scope, SharingStarted.Eagerly, emptyList())

    val splits = w.splits().hot()
    private val rawPrices = w.prices().hot()
    /** Price points with splits applied (for charts and values). */
    val prices = combine(rawPrices, splits) { p, s -> Splits.adjustPrices(p, s) }.hot()
    val brokers = w.brokers().hot()
    val stocks = w.stocks().hot()
    /** Trades exactly as you entered them (use for editing and CSV). */
    val rawTrades = w.trades().hot()
    /** Trades with splits applied (use for quantities, costs and values). */
    val trades = combine(rawTrades, splits) { t, s -> Splits.adjustTrades(t, s) }.hot()
    val dividends = w.dividends().hot()
    val lookups = lists.lookups().hot()
    val securities = lists.securities().hot()
    val priceAlerts = MutableStateFlow<List<PriceAlert>>(emptyList())

    suspend fun saveBroker(item: BrokerAccountEntity) { w.upsertBroker(item) }

    suspend fun deleteBroker(id: String) {
        db.withTransaction {
            w.stockIdsOf(id).forEach { s -> w.deleteTradesOfStock(s); w.deleteDividendsOfStock(s); w.deletePricesOfStock(s); w.deleteSplitsOfStock(s); w.deleteStock(s) }
            w.deleteBroker(id)
        }
    }

    suspend fun saveStock(item: StockEntity) {
        val old = stocks.value.find { it.id == item.id }
        w.upsertStock(item)
        if (item.lastPriceMinor > 0) w.upsertPrice(PriceHistory.point(item.id, item.lastPriceDate, item.lastPriceMinor))
        if (old != null) PriceAlerts.crossed(old.lastPriceMinor, item.lastPriceMinor, item.targetPriceMinor, item.stopPriceMinor)?.let { k ->
            priceAlerts.value = priceAlerts.value + PriceAlert(item.symbol, k, item.lastPriceMinor,
                if (k == PriceAlertKind.TARGET) item.targetPriceMinor else item.stopPriceMinor)
        }
    }

    suspend fun deleteStock(id: String) {
        db.withTransaction { w.deleteTradesOfStock(id); w.deleteDividendsOfStock(id); w.deletePricesOfStock(id); w.deleteSplitsOfStock(id); w.deleteStock(id) }
    }

    suspend fun saveTrade(t: StockTradeEntity) {
        w.upsertTrade(t)
        w.insertPriceIfAbsent(PriceHistory.point(t.stockId, t.dateMillis, t.priceMinor))
        val s = stocks.value.find { it.id == t.stockId }
        if (s != null && s.lastPriceMinor == 0L) w.upsertStock(s.copy(lastPriceMinor = t.priceMinor, lastPriceDate = t.dateMillis))
    }

    suspend fun deleteTrade(id: String) { w.deleteTrade(id) }
    suspend fun saveDividend(item: DividendEntity) { w.upsertDividend(item) }
    suspend fun deleteDividend(id: String) { w.deleteDividend(id) }

    suspend fun saveSplit(item: StockSplitEntity) {
        w.upsertSplit(item)
        // A price recorded before the split date is converted so the value stays continuous.
        val s = stocks.value.find { it.id == item.stockId }
        if (s != null && s.lastPriceMinor > 0 && s.lastPriceDate < item.dateMillis && item.dateMillis <= System.currentTimeMillis() && item.numerator > 0)
            w.upsertStock(s.copy(lastPriceMinor = (s.lastPriceMinor.toDouble() * item.denominator / item.numerator).roundToLong(), lastPriceDate = item.dateMillis))
    }

    suspend fun deleteSplit(id: String) { w.deleteSplit(id) }

    suspend fun addLookup(kind: String, name: String) {
        val n = name.trim()
        if (n.isNotEmpty() && lookups.value.none { it.kind == kind && it.name.equals(n, true) }) lists.upsertLookup(LookupItemEntity(uid(), kind, n))
    }
    suspend fun renameLookup(item: LookupItemEntity, name: String) { lists.upsertLookup(item.copy(name = name.trim())) }
    suspend fun deleteLookup(id: String) { lists.deleteLookup(id) }
    suspend fun saveSecurity(item: SecurityEntity) { lists.upsertSecurity(item) }
    suspend fun deleteSecurity(id: String) { lists.deleteSecurity(id) }

    companion object {
        @Volatile private var inst: StocksRepository? = null
        fun get(ctx: Context): StocksRepository = inst ?: synchronized(this) {
            inst ?: StocksRepository(AppDatabase.get(ctx)).also { inst = it }
        }
    }

    /** Saves a broker, account name or sector entry. With [propagate] a rename is applied to every account or stock that uses the old name. */
    suspend fun saveLookup(item: LookupItemEntity, oldName: String?, propagate: Boolean) {
        lists.upsertLookup(item)
        if (propagate && oldName != null && oldName != item.name) when (item.kind) {
            LookupKind.BROKER -> w.renameBrokerCompany(oldName, item.name)
            LookupKind.ACCOUNT -> w.renameAccountName(oldName, item.name)
            LookupKind.SECTOR -> { w.renameSectorInStocks(oldName, item.name); lists.renameSectorInSecurities(oldName, item.name) }
        }
    }

    /** Saves a listed stock. With [propagate] a change of symbol, name or sector is applied to the holdings of that stock. */
    suspend fun saveSecurity(item: SecurityEntity, old: SecurityEntity?, propagate: Boolean) {
        lists.upsertSecurity(item)
        if (propagate && old != null) {
            if (old.symbol != item.symbol || old.name != item.name) w.renameStockSymbol(old.symbol, item.symbol, item.name)
            if (old.sector != item.sector) w.setSectorOfSymbol(item.symbol, item.sector)
        }
    }
}
