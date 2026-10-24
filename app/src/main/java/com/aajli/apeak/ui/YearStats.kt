package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.R
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.util.CashStats
import com.aajli.apeak.util.MonthTotals
import com.aajli.apeak.util.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.util.Locale

@Composable
private fun TwoBars(items: List<Pair<String, MonthTotals>>) {
    val inc = incomeColor(); val exp = expenseColor()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val mx = items.maxOf { maxOf(it.second.income, it.second.expense) }.coerceAtLeast(1L).toFloat()
        val gw = size.width / items.size
        val bw = gw * 0.3f
        items.forEachIndexed { i, (_, m) ->
            val x0 = gw * i + gw * 0.17f
            val hi = size.height * (m.income / mx); val he = size.height * (m.expense / mx)
            drawRoundRect(inc, Offset(x0, size.height - hi), Size(bw, hi), CornerRadius(bw / 3f))
            drawRoundRect(exp, Offset(x0 + bw + gw * 0.05f, size.height - he), Size(bw, he), CornerRadius(bw / 3f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        items.forEach { Text(it.first, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = label) }
    }
}

@Composable
private fun WeekdayBars(values: List<Long>) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val mx = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val gw = size.width / 7f
        values.forEachIndexed { i, v ->
            val h = size.height * (v / mx)
            drawRoundRect(c, Offset(gw * i + gw * 0.2f, size.height - h), Size(gw * 0.6f, h), CornerRadius(gw * 0.15f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        (1..7).forEach {
            Text(DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()), Modifier.weight(1f),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun mName(m: Int) = Month.of(m).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

/** Expense tab, years page: year-by-year bars and all-time numbers. */
@Composable
fun YearsExtras(all: List<TransactionEntity>, cur: String) {
    val byYear = CashStats.byYear(all)
    if (byYear.isEmpty()) return
    val asc = byYear.toList().reversed().takeLast(8)
    if (asc.size >= 2) StatCard(stringResource(R.string.yby_title)) { TwoBars(asc.map { it.first.toString() to it.second }) }
    val total = CashStats.totals(all)
    val months = CashStats.activeMonths(all)
    StatCard(stringResource(R.string.all_time)) {
        if (total.income > 0) KeyVal(stringResource(R.string.savings_rate), "%.0f%%".format(total.balance * 100.0 / total.income), gainColor(total.balance))
        if (months > 0) KeyVal(stringResource(R.string.avg_expense), Money.format(total.expense / months, cur))
        val best = byYear.entries.maxByOrNull { it.value.balance }
        if (best != null) KeyVal(stringResource(R.string.best_year), "${best.key}  ${signed(best.value.balance, cur)}", gainColor(best.value.balance))
        val top = byYear.entries.maxByOrNull { it.value.expense }
        if (top != null && byYear.size >= 2) KeyVal(stringResource(R.string.top_spend_year), "${top.key}  ${Money.format(top.value.expense, cur)}")
    }
}

/** Year page: averages, forecast, biggest expenses, categories vs last year, weekday pattern, by account. */
@Composable
fun YearExtras2(all: List<TransactionEntity>, year: Int, months: List<MonthTotals>, cur: String) {
    val today = LocalDate.now()
    val y = MonthTotals(months.sumOf { it.income }, months.sumOf { it.expense })
    val activeIdx = months.indices.filter { months[it].income != 0L || months[it].expense != 0L }
    if (activeIdx.isNotEmpty()) StatCard(stringResource(R.string.year_averages)) {
        KeyVal(stringResource(R.string.avg_income), Money.format(y.income / activeIdx.size, cur))
        KeyVal(stringResource(R.string.avg_saved), signed(y.balance / activeIdx.size, cur), gainColor(y.balance))
        val worst = activeIdx.maxByOrNull { months[it].expense }
        if (worst != null && y.expense > 0) KeyVal(stringResource(R.string.top_spend_month), "${mName(worst + 1)}  ${Money.format(months[worst].expense, cur)}")
        if (year == today.year && today.dayOfYear >= 30 && y.expense > 0)
            KeyVal(stringResource(R.string.year_forecast), Money.format(CashStats.forecast(y.expense, today.dayOfYear, today.lengthOfYear()), cur), MaterialTheme.colorScheme.primary)
    }
    val top = CashStats.topExpenses(all, year, 5)
    if (top.isNotEmpty()) StatCard(stringResource(R.string.top_expenses)) {
        top.forEach { x ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${x.categoryIcon} ${x.categoryName} · ${fmtDate(x.dateMillis)}", modifier = Modifier.weight(1f), maxLines = 1)
                Text(Money.format(x.amountMinor, cur), color = expenseColor(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
    val cats = CashStats.spendingByCategory(all, year)
    val prevCats = CashStats.spendingByCategory(all, year - 1).toMap()
    if (cats.isNotEmpty() && prevCats.isNotEmpty()) StatCard(stringResource(R.string.cat_vs_prev, "${year - 1}")) {
        cats.take(8).forEach { (name, amt) ->
            val pc = prevCats[name]
            val delta = if (pc != null && pc > 0) "  (${"%+.0f%%".format(CashStats.pctChange(amt, pc)!!)})" else ""
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, modifier = Modifier.weight(1f), maxLines = 1)
                Text(Money.format(amt, cur) + delta,
                    color = if (pc == null || pc <= 0) Color.Unspecified else if (amt <= pc) incomeColor() else expenseColor())
            }
        }
    }
    val wd = CashStats.spendingByWeekday(all, year)
    if (wd.any { it > 0 }) StatCard(stringResource(R.string.weekday_title)) {
        WeekdayBars(wd)
        val best = wd.indices.maxByOrNull { wd[it] } ?: 0
        KeyVal(stringResource(R.string.most_on), DayOfWeek.of(best + 1).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()))
    }
    val accs = CashStats.spendingByAccount(all, year)
    if (accs.size >= 2) StatCard(stringResource(R.string.by_account)) { StackedBar(accs.take(5), cur) }
}
