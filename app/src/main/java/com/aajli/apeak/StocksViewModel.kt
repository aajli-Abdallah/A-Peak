package com.aajli.apeak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aajli.apeak.backup.BackupManager
import com.aajli.apeak.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** View model of every stock screen. Data and rules live in [StocksRepository]; this class only exposes them and runs the writes. */
class StocksViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = StocksRepository.get(app)
    val settings = SettingsStore.get(app)
    val backups = BackupManager.get(app)

    val brokers = repo.brokers
    val stocks = repo.stocks
    val rawTrades = repo.rawTrades
    val trades = repo.trades
    val dividends = repo.dividends
    val prices = repo.prices
    val splits = repo.splits
    val lookups = repo.lookups
    val securities = repo.securities
    val priceAlerts = repo.priceAlerts

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun saveBroker(item: BrokerAccountEntity) = io { repo.saveBroker(item) }
    fun deleteBroker(id: String) = io { repo.deleteBroker(id) }
    fun saveStock(item: StockEntity) = io { repo.saveStock(item) }
    fun deleteStock(id: String) = io { repo.deleteStock(id) }
    fun saveTrade(t: StockTradeEntity) = io { repo.saveTrade(t) }
    fun deleteTrade(id: String) = io { repo.deleteTrade(id) }
    fun saveDividend(item: DividendEntity) = io { repo.saveDividend(item) }
    fun deleteDividend(id: String) = io { repo.deleteDividend(id) }
    fun saveSplit(item: StockSplitEntity) = io { repo.saveSplit(item) }
    fun deleteSplit(id: String) = io { repo.deleteSplit(id) }
    fun addLookup(kind: String, name: String) = io { repo.addLookup(kind, name) }
    fun renameLookup(item: LookupItemEntity, name: String) = io { repo.renameLookup(item, name) }
    fun deleteLookup(id: String) = io { repo.deleteLookup(id) }
    fun saveSecurity(item: SecurityEntity) = io { repo.saveSecurity(item) }
    fun deleteSecurity(id: String) = io { repo.deleteSecurity(id) }

    fun saveLookup(item: LookupItemEntity, oldName: String?, propagate: Boolean) = io { repo.saveLookup(item, oldName, propagate) }
    fun saveSecurity(item: SecurityEntity, old: SecurityEntity?, propagate: Boolean) = io { repo.saveSecurity(item, old, propagate) }
}
