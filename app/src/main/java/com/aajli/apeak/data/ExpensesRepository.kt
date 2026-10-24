package com.aajli.apeak.data

import android.app.Application
import com.aajli.apeak.R
import com.aajli.apeak.util.BudgetAlert
import com.aajli.apeak.util.Budgets
import com.aajli.apeak.util.CashStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

private fun uid() = UUID.randomUUID().toString()

/** Single source of truth for expenses: accounts, categories, transactions and budgets, with the budget alert rule. */
class ExpensesRepository private constructor(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun <T> Flow<List<T>>.hot(): StateFlow<List<T>> = stateIn(scope, SharingStarted.Eagerly, emptyList())

    val accounts = db.accounts().observeAll().hot()
    val categories = db.categories().observeAll().hot()
    val allTransactions = db.transactions().observeAll().hot()
    val budgets = db.budgetDao().observeAll().hot()
    val budgetAlert = MutableStateFlow<BudgetAlert?>(null)

    fun transactionsOfMonth(m: YearMonth): Flow<List<TransactionEntity>> {
        val z = ZoneId.systemDefault()
        return db.transactions().observeBetween(
            m.atDay(1).atStartOfDay(z).toInstant().toEpochMilli(),
            m.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        )
    }

    suspend fun seedIfNeeded(a: Application, settings: SettingsStore) {
        if (settings.seeded) return
        fun s(id: Int) = a.getString(id)
        db.accounts().upsertAll(listOf(
            AccountEntity(uid(), s(R.string.acc_cash), "💵", 0),
            AccountEntity(uid(), s(R.string.acc_bank), "🏦", 1),
            AccountEntity(uid(), s(R.string.acc_card), "💳", 2)
        ))
        val exp = listOf(R.string.cat_food to "🍽️", R.string.cat_transport to "🚌", R.string.cat_home to "🏠",
            R.string.cat_bills to "💡", R.string.cat_health to "💊", R.string.cat_shopping to "🛍️",
            R.string.cat_fun to "🎬", R.string.cat_other to "📦")
        val inc = listOf(R.string.cat_salary to "💼", R.string.cat_gift to "🎁", R.string.cat_other_income to "💰")
        db.categories().upsertAll(
            exp.mapIndexed { i, c -> CategoryEntity(uid(), s(c.first), c.second, Tx.EXPENSE, i) } +
            inc.mapIndexed { i, c -> CategoryEntity(uid(), s(c.first), c.second, Tx.INCOME, i) }
        )
        settings.seeded = true
    }

    suspend fun saveTransaction(existing: TransactionEntity?, type: String, amountMinor: Long, date: Long,
                                note: String, acc: AccountEntity, cat: CategoryEntity) {
        db.transactions().upsert(TransactionEntity(
            existing?.id ?: uid(), type, amountMinor, date, note,
            acc.id, acc.name, acc.icon, cat.id, cat.name, cat.icon,
            existing?.createdAt ?: System.currentTimeMillis()
        ))
        checkBudget(existing, type, amountMinor, date, cat)
    }

    suspend fun deleteTransaction(t: TransactionEntity) { db.transactions().delete(t) }
    suspend fun restoreTransaction(t: TransactionEntity) { db.transactions().upsert(t) }

    suspend fun saveAccount(id: String, name: String, icon: String) {
        val cur = accounts.value
        val order = cur.find { it.id == id }?.sortOrder ?: cur.size
        db.accounts().upsert(AccountEntity(id.ifEmpty { uid() }, name, icon, order))
    }
    suspend fun deleteAccount(id: String) { db.accounts().deleteById(id) }

    suspend fun saveCategory(id: String, name: String, icon: String, type: String, updatePast: Boolean) {
        val cur = categories.value
        val order = cur.find { it.id == id }?.sortOrder ?: cur.count { it.type == type }
        db.categories().upsert(CategoryEntity(id.ifEmpty { uid() }, name, icon, type, order))
        if (updatePast && id.isNotEmpty()) db.transactions().renameCategory(id, name, icon)
    }
    suspend fun deleteCategory(id: String) { db.categories().deleteById(id) }

    /** Sets the limit of a category from month [ym] onward. 0 removes the budget from that month. */
    suspend fun setBudget(categoryId: String, name: String, ym: YearMonth, limit: Long) {
        val key = Budgets.key(ym)
        val rows = budgets.value.filter { it.categoryId == categoryId }
        val same = rows.find { it.startMonth == key }
        val previous = Budgets.limitFor(rows.filter { it.startMonth < key }, categoryId, ym.minusMonths(1))
        if (limit == 0L && previous == 0L) { same?.let { db.budgetDao().deleteById(it.id) } }
        else { db.budgetDao().upsert(BudgetEntity(same?.id ?: uid(), categoryId, name, key, limit)) }
    }

    private fun checkBudget(existing: TransactionEntity?, type: String, amount: Long, date: Long, cat: CategoryEntity) {
        if (type != Tx.EXPENSE) return
        val ym = YearMonth.from(java.time.Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()))
        val limit = Budgets.limitFor(budgets.value, cat.id, ym)
        if (limit <= 0) return
        val before = CashStats.inMonth(allTransactions.value, ym)
            .filter { it.id != existing?.id && it.type == Tx.EXPENSE && it.categoryId == cat.id }.sumOf { it.amountMinor }
        val level = Budgets.crossed(before, before + amount, limit) ?: return
        budgetAlert.value = BudgetAlert(cat.name, level, ((before + amount) * 100 / limit).toInt(), before + amount, limit)
    }

    companion object {
        @Volatile private var inst: ExpensesRepository? = null
        fun get(ctx: android.content.Context): ExpensesRepository = inst ?: synchronized(this) {
            inst ?: ExpensesRepository(AppDatabase.get(ctx)).also { inst = it }
        }
    }
}
