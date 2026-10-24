package com.aajli.apeak.util

import com.aajli.apeak.data.BudgetEntity
import com.aajli.apeak.data.CategoryEntity
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import java.time.YearMonth

enum class BudgetLevel { OK, WARN, OVER }

data class BudgetAlert(val name: String, val level: BudgetLevel, val percent: Int, val spent: Long, val limit: Long)

data class BudgetProgress(val categoryId: String, val name: String, val icon: String, val limit: Long, val spent: Long) {
    val ratio: Float get() = if (limit > 0) spent.toFloat() / limit else 0f
    val level: BudgetLevel get() = Budgets.level(spent, limit)
}

object Budgets {
    fun key(ym: YearMonth): Int = ym.year * 100 + ym.monthValue

    /** Effective limit of a category in a month (0 = none). Past months keep the limit that applied then. */
    fun limitFor(rows: List<BudgetEntity>, categoryId: String, ym: YearMonth): Long =
        rows.filter { it.categoryId == categoryId && it.startMonth <= key(ym) }.maxByOrNull { it.startMonth }?.limitMinor ?: 0L

    /** OK below 80%, WARN from 80%, OVER from 100%. */
    fun level(spent: Long, limit: Long): BudgetLevel = when {
        limit <= 0 -> BudgetLevel.OK
        spent >= limit -> BudgetLevel.OVER
        spent * 100 >= limit * 80 -> BudgetLevel.WARN
        else -> BudgetLevel.OK
    }

    /** The level newly reached when spending moves from [before] to [after], or null if none. */
    fun crossed(before: Long, after: Long, limit: Long): BudgetLevel? {
        if (limit <= 0) return null
        val b = level(before, limit); val a = level(after, limit)
        return if (a.ordinal > b.ordinal) a else null
    }

    /** Budgeted categories of a month with their spending, worst first. Spending is matched by category id. */
    fun progress(budgets: List<BudgetEntity>, categories: List<CategoryEntity>, txs: List<TransactionEntity>, ym: YearMonth): List<BudgetProgress> {
        val spentBy = CashStats.inMonth(txs, ym).filter { it.type == Tx.EXPENSE }
            .groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        return budgets.map { it.categoryId }.distinct().mapNotNull { id ->
            val limit = limitFor(budgets, id, ym)
            if (limit <= 0) return@mapNotNull null
            val cat = categories.find { it.id == id }
            val snap = budgets.filter { it.categoryId == id }.maxByOrNull { it.startMonth }
            BudgetProgress(id, cat?.name ?: snap?.categoryName ?: "", cat?.icon ?: "📁", limit, spentBy[id] ?: 0L)
        }.sortedByDescending { it.ratio }
    }
}
