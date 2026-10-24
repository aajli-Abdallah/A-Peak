package com.aajli.apeak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aajli.apeak.data.*
import com.aajli.apeak.util.OverviewCalc
import com.aajli.apeak.util.OverviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

private data class BankBundle(val banks: List<BankAccountEntity>, val entries: List<BankEntryEntity>, val brokers: List<BrokerAccountEntity>)
private data class StockBundle(val stocks: List<StockEntity>, val trades: List<StockTradeEntity>, val dividends: List<DividendEntity>, val prices: List<PricePointEntity>)

/**
 * The Overview numbers are computed once per data change on a background thread and only while the page is shown,
 * instead of on every recomposition on the main thread.
 */
class OverviewViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore.get(app)
    private val banks = BanksRepository.get(app)
    private val stocks = StocksRepository.get(app)
    private val expenses = ExpensesRepository.get(app)

    val state: StateFlow<OverviewState> = combine(
        combine(banks.banks, banks.bankEntries, stocks.brokers) { b, e, br -> BankBundle(b, e, br) },
        combine(stocks.stocks, stocks.trades, stocks.dividends, stocks.prices) { s, t, d, p -> StockBundle(s, t, d, p) },
        expenses.allTransactions
    ) { a, b, tx -> OverviewCalc.compute(a.banks, a.entries, a.brokers, b.stocks, b.trades, b.dividends, b.prices, tx, LocalDate.now()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState.EMPTY)
}
