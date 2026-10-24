package com.aajli.apeak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aajli.apeak.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Expense screens: month page, years, categories, accounts and budgets. */
class ExpensesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ExpensesRepository.get(app)
    val settings = SettingsStore.get(app)
    val month = MutableStateFlow(YearMonth.now())
    val lastDeleted = MutableStateFlow<TransactionEntity?>(null)

    val accounts = repo.accounts
    val categories = repo.categories
    val allTransactions = repo.allTransactions
    val budgets = repo.budgets
    val budgetAlert = repo.budgetAlert

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions = month.flatMapLatest { repo.transactionsOfMonth(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { io { repo.seedIfNeeded(app, settings) } }

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun saveTransaction(existing: TransactionEntity?, type: String, amountMinor: Long, date: Long,
                        note: String, acc: AccountEntity, cat: CategoryEntity) = io { repo.saveTransaction(existing, type, amountMinor, date, note, acc, cat) }

    fun deleteTransaction(t: TransactionEntity) { lastDeleted.value = t; io { repo.deleteTransaction(t) } }
    fun undoDelete() { val t = lastDeleted.value ?: return; lastDeleted.value = null; io { repo.restoreTransaction(t) } }
    fun clearDeleted() { lastDeleted.value = null }

    fun saveAccount(id: String, name: String, icon: String) = io { repo.saveAccount(id, name, icon) }
    fun deleteAccount(id: String) = io { repo.deleteAccount(id) }
    fun saveCategory(id: String, name: String, icon: String, type: String, updatePast: Boolean = false) = io { repo.saveCategory(id, name, icon, type, updatePast) }
    fun deleteCategory(id: String) = io { repo.deleteCategory(id) }
    fun setBudget(categoryId: String, name: String, ym: YearMonth, limit: Long) = io { repo.setBudget(categoryId, name, ym, limit) }
}
