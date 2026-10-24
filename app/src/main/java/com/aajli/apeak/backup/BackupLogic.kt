package com.aajli.apeak.backup

import com.aajli.apeak.data.*

data class BackupData(
    val appVersion: String = Versions.DEFAULT,
    val exportedAt: Long = 0L,
    val author: String = "",
    val settings: Map<String, String> = emptyMap(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val bankAccounts: List<BankAccountEntity> = emptyList(),
    val bankEntries: List<BankEntryEntity> = emptyList(),
    val brokers: List<BrokerAccountEntity> = emptyList(),
    val stocks: List<StockEntity> = emptyList(),
    val trades: List<StockTradeEntity> = emptyList(),
    val dividends: List<DividendEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val prices: List<PricePointEntity> = emptyList(),
    val splits: List<StockSplitEntity> = emptyList(),
    val lookups: List<LookupItemEntity> = emptyList(),
    val securities: List<SecurityEntity> = emptyList()
) {
    val recordCount: Int get() = accounts.size + categories.size + transactions.size + bankAccounts.size +
        bankEntries.size + brokers.size + stocks.size + trades.size + dividends.size + budgets.size + prices.size + splits.size + lookups.size + securities.size
}

object Versions {
    /** Backups without an appVersion are treated as made by this version (the first release). */
    const val DEFAULT = "1.0.0"

    fun compare(a: String, b: String): Int {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
    private fun parts(v: String) = v.split('.').map { p -> p.filter { it.isDigit() }.toIntOrNull() ?: 0 }
}

data class TableReport(val key: String, val new: Int, val updated: Int, val unchanged: Int)
data class MergeResult(val data: BackupData, val report: List<TableReport>)

/**
 * Merge rules (incoming = backup file, current = database):
 * - every table: match by id first; same id and different content = the backup version wins ("updated").
 * - accounts, categories, bank accounts, stock accounts: if no id matches, match by name (default accounts get random ids on each phone).
 *   categories also match on type, bank accounts on name + bank, stocks on account + symbol.
 * - records that point to a matched parent are re-pointed to the local parent id.
 * - trades, dividends, entries, transactions: id only.
 * Settings are not touched by Merge.
 */
object BackupMerge {
    private fun <T> plan(key: String, incoming: List<T>, current: List<T>, idOf: (T) -> String, match: (T) -> T?,
                         withId: (T, String) -> T, idMap: MutableMap<String, String>, report: MutableList<TableReport>): List<T> {
        var n = 0; var u = 0; var s = 0
        val write = mutableListOf<T>()
        for (item in incoming) {
            val local = current.find { idOf(it) == idOf(item) } ?: match(item)
            val target = withId(item, if (local != null) idOf(local) else idOf(item))
            idMap[idOf(item)] = idOf(target)
            when {
                local == null -> { n++; write += target }
                local == target -> s++
                else -> { u++; write += target }
            }
        }
        report += TableReport(key, n, u, s)
        return write
    }

    fun merge(cur: BackupData, inc: BackupData): MergeResult {
        val rep = mutableListOf<TableReport>()
        val accMap = HashMap<String, String>(); val catMap = HashMap<String, String>()
        val bankMap = HashMap<String, String>(); val brokerMap = HashMap<String, String>(); val stockMap = HashMap<String, String>()
        val none = HashMap<String, String>()

        val accounts = plan("accounts", inc.accounts, cur.accounts, { it.id },
            { a -> cur.accounts.find { it.name.equals(a.name, true) } }, { a, id -> a.copy(id = id) }, accMap, rep)
        val categories = plan("categories", inc.categories, cur.categories, { it.id },
            { c -> cur.categories.find { it.type == c.type && it.name.equals(c.name, true) } }, { c, id -> c.copy(id = id) }, catMap, rep)
        val txIn = inc.transactions.map { it.copy(accountId = accMap[it.accountId] ?: it.accountId, categoryId = catMap[it.categoryId] ?: it.categoryId) }
        val transactions = plan("transactions", txIn, cur.transactions, { it.id }, { null }, { t, id -> t.copy(id = id) }, none, rep)

        val bankAccounts = plan("bank_accounts", inc.bankAccounts, cur.bankAccounts, { it.id },
            { b -> cur.bankAccounts.find { it.name.equals(b.name, true) && it.bankName.equals(b.bankName, true) } }, { b, id -> b.copy(id = id) }, bankMap, rep)
        val entIn = inc.bankEntries.map { it.copy(accountId = bankMap[it.accountId] ?: it.accountId) }
        val bankEntries = plan("bank_entries", entIn, cur.bankEntries, { it.id }, { null }, { e, id -> e.copy(id = id) }, none, rep)

        val brokers = plan("broker_accounts", inc.brokers, cur.brokers, { it.id },
            { b -> cur.brokers.find { it.name.equals(b.name, true) } }, { b, id -> b.copy(id = id) }, brokerMap, rep)
        val stIn = inc.stocks.map { it.copy(accountId = brokerMap[it.accountId] ?: it.accountId) }
        val stocks = plan("stocks", stIn, cur.stocks, { it.id },
            { s -> cur.stocks.find { it.accountId == s.accountId && it.symbol.equals(s.symbol, true) } }, { s, id -> s.copy(id = id) }, stockMap, rep)
        val trIn = inc.trades.map { it.copy(stockId = stockMap[it.stockId] ?: it.stockId) }
        val trades = plan("trades", trIn, cur.trades, { it.id }, { null }, { t, id -> t.copy(id = id) }, none, rep)
        val dvIn = inc.dividends.map { it.copy(stockId = stockMap[it.stockId] ?: it.stockId) }
        val dividends = plan("dividends", dvIn, cur.dividends, { it.id }, { null }, { d, id -> d.copy(id = id) }, none, rep)

        val budIn = inc.budgets.map { it.copy(categoryId = catMap[it.categoryId] ?: it.categoryId) }
        val budgets = plan("budgets", budIn, cur.budgets, { it.id },
            { b -> cur.budgets.find { it.categoryId == b.categoryId && it.startMonth == b.startMonth } }, { b, id -> b.copy(id = id) }, none, rep)

        val prIn = inc.prices.map { it.copy(stockId = stockMap[it.stockId] ?: it.stockId) }
        val prices = plan("prices", prIn, cur.prices, { it.id },
            { p -> cur.prices.find { it.stockId == p.stockId && it.dateMillis == p.dateMillis } }, { p, id -> p.copy(id = id) }, none, rep)

        val spIn = inc.splits.map { it.copy(stockId = stockMap[it.stockId] ?: it.stockId) }
        val splits = plan("splits", spIn, cur.splits, { it.id },
            { s -> cur.splits.find { it.stockId == s.stockId && it.dateMillis == s.dateMillis } }, { s, id -> s.copy(id = id) }, none, rep)

        val lookups = plan("lookups", inc.lookups, cur.lookups, { it.id },
            { l -> cur.lookups.find { it.kind == l.kind && it.name.equals(l.name, true) } }, { l, id -> l.copy(id = id) }, none, rep)
        val securities = plan("securities", inc.securities, cur.securities, { it.id },
            { s -> cur.securities.find { it.symbol.equals(s.symbol, true) } }, { s, id -> s.copy(id = id) }, none, rep)

        return MergeResult(
            BackupData(inc.appVersion, inc.exportedAt, inc.author, emptyMap(), accounts, categories, transactions,
                bankAccounts, bankEntries, brokers, stocks, trades, dividends, budgets, prices, splits, lookups, securities), rep)
    }
}
