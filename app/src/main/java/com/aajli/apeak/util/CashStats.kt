package com.aajli.apeak.util

import com.aajli.apeak.data.CategoryEntity
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import java.time.Instant
import java.time.YearMonth
import kotlin.math.abs
import java.time.ZoneId

data class MonthTotals(val income: Long, val expense: Long) {
    val balance: Long get() = income - expense
}

data class CatTotal(val name: String, val icon: String, val amount: Long)

object CashStats {
    private fun zdt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())

    fun totals(txs: List<TransactionEntity>) = MonthTotals(
        txs.filter { it.type == Tx.INCOME }.sumOf { it.amountMinor },
        txs.filter { it.type == Tx.EXPENSE }.sumOf { it.amountMinor }
    )

    /** Years with data, newest first. */
    fun byYear(txs: List<TransactionEntity>): Map<Int, MonthTotals> =
        txs.groupBy { zdt(it.dateMillis).year }.mapValues { totals(it.value) }.toSortedMap(reverseOrder())

    /** Always 12 entries, January to December. */
    fun byMonth(txs: List<TransactionEntity>, year: Int): List<MonthTotals> {
        val g = txs.filter { zdt(it.dateMillis).year == year }.groupBy { zdt(it.dateMillis).monthValue }
        return (1..12).map { totals(g[it].orEmpty()) }
    }

    fun spendingByCategory(txs: List<TransactionEntity>, year: Int): List<Pair<String, Long>> =
        txs.filter { it.type == Tx.EXPENSE && zdt(it.dateMillis).year == year }
            .groupBy { it.categoryName }.map { it.key to it.value.sumOf { t -> t.amountMinor } }
            .sortedByDescending { it.second }

    fun inMonth(txs: List<TransactionEntity>, ym: YearMonth): List<TransactionEntity> =
        txs.filter { YearMonth.from(zdt(it.dateMillis)) == ym }

    /** Percent change from prev to cur. Null when prev is zero. */
    fun pctChange(cur: Long, prev: Long): Double? = if (prev == 0L) null else (cur - prev) * 100.0 / abs(prev)

    /** Expenses per day, one entry per day of the month. */
    fun dailyExpense(monthTxs: List<TransactionEntity>, ym: YearMonth): List<Long> {
        val days = LongArray(ym.lengthOfMonth())
        monthTxs.filter { it.type == Tx.EXPENSE }.forEach { days[zdt(it.dateMillis).dayOfMonth - 1] += it.amountMinor }
        return days.toList()
    }

    fun categoryBreakdown(monthTxs: List<TransactionEntity>): List<Pair<String, Long>> =
        monthTxs.filter { it.type == Tx.EXPENSE }.groupBy { it.categoryName }
            .map { it.key to it.value.sumOf { t -> t.amountMinor } }.sortedByDescending { it.second }

    fun forecast(expenseSoFar: Long, day: Int, daysInMonth: Int): Long =
        if (day <= 0) expenseSoFar else expenseSoFar * daysInMonth / day

    fun runningBalance(months: List<MonthTotals>): List<Long> {
        var b = 0L
        return months.map { b += it.balance; b }
    }

    fun topExpenses(txs: List<TransactionEntity>, year: Int, n: Int): List<TransactionEntity> =
        txs.filter { it.type == Tx.EXPENSE && zdt(it.dateMillis).year == year }.sortedByDescending { it.amountMinor }.take(n)

    /** Monday first (index 0) to Sunday (index 6). */
    fun spendingByWeekday(txs: List<TransactionEntity>, year: Int): List<Long> {
        val a = LongArray(7)
        txs.filter { it.type == Tx.EXPENSE && zdt(it.dateMillis).year == year }.forEach { a[zdt(it.dateMillis).dayOfWeek.value - 1] += it.amountMinor }
        return a.toList()
    }

    fun spendingByAccount(txs: List<TransactionEntity>, year: Int): List<Pair<String, Long>> =
        txs.filter { it.type == Tx.EXPENSE && zdt(it.dateMillis).year == year }.groupBy { it.accountName }
            .map { it.key to it.value.sumOf { t -> t.amountMinor } }.sortedByDescending { it.second }

    fun activeMonths(txs: List<TransactionEntity>): Int = txs.map { YearMonth.from(zdt(it.dateMillis)) }.distinct().size

    /** One row per known category of this type (also those with 0), plus names that only exist in history (deleted/renamed). year = null means all years. */
    fun categoryTotals(txs: List<TransactionEntity>, year: Int?, type: String, known: List<CategoryEntity>): List<CatTotal> {
        val scope = txs.filter { it.type == type && (year == null || zdt(it.dateMillis).year == year) }
        val sums = scope.groupBy { it.categoryName }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        val mine = known.filter { it.type == type }
        val names = mine.map { it.name }.toSet()
        val rows = mine.map { CatTotal(it.name, it.icon, sums[it.name] ?: 0L) } +
            sums.filterKeys { it !in names }.map { (n, a) -> CatTotal(n, scope.first { it.categoryName == n }.categoryIcon, a) }
        return rows.sortedWith(compareByDescending<CatTotal> { it.amount }.thenBy { it.name })
    }

    /** Every category with one amount per year, in the order of [years]. */
    fun categoryByYear(txs: List<TransactionEntity>, years: List<Int>, type: String, known: List<CategoryEntity>): List<Pair<CatTotal, List<Long>>> {
        val sums = txs.filter { it.type == type }.groupBy { it.categoryName to zdt(it.dateMillis).year }.mapValues { e -> e.value.sumOf { it.amountMinor } }
        return categoryTotals(txs, null, type, known).map { r -> r to years.map { y -> sums[r.name to y] ?: 0L } }
    }

    /** Spending per month (12 values) for the [top] biggest categories of the year; the rest is grouped under [otherLabel]. */
    fun categoryByMonth(txs: List<TransactionEntity>, year: Int, top: Int, otherLabel: String): List<Pair<String, List<Long>>> {
        val exp = txs.filter { it.type == Tx.EXPENSE && zdt(it.dateMillis).year == year }
        val byCat = exp.groupBy { it.categoryName }
        val topNames = byCat.map { it.key to it.value.sumOf { t -> t.amountMinor } }.sortedByDescending { it.second }.take(top).map { it.first }
        fun monthly(list: List<TransactionEntity>): List<Long> {
            val a = LongArray(12)
            list.forEach { a[zdt(it.dateMillis).monthValue - 1] += it.amountMinor }
            return a.toList()
        }
        val rows = topNames.map { it to monthly(byCat[it].orEmpty()) }
        val rest = exp.filter { it.categoryName !in topNames }
        return if (rest.isEmpty()) rows else rows + (otherLabel to monthly(rest))
    }
}
