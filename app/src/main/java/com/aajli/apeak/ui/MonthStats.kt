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
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import com.aajli.apeak.util.CashStats
import com.aajli.apeak.util.MonthTotals
import com.aajli.apeak.util.Money
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale

private fun monthName(m: Int, style: java.time.format.TextStyle = java.time.format.TextStyle.FULL) =
    Month.of(m).getDisplayName(style, Locale.getDefault())

/** One line "label: +/-diff (pct)". Green when the change is good for you. */
@Composable
fun DeltaRow(label: String, cur: Long, prev: Long, higherIsGood: Boolean, currency: String) {
    val diff = cur - prev
    val pct = CashStats.pctChange(cur, prev)
    val good = if (higherIsGood) diff >= 0 else diff <= 0
    val txt = signed(diff, currency) + (if (pct != null) "  (${"%+.0f".format(pct)}%)" else "")
    KeyVal(label, txt, if (diff == 0L) Color.Unspecified else if (good) incomeColor() else expenseColor())
}

@Composable
private fun DayBars(values: List<Long>) {
    val c = expenseColor()
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val mx = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val gw = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / mx)
            drawRoundRect(c, Offset(gw * i + gw * 0.15f, size.height - h), Size(gw * 0.7f, h), CornerRadius(gw * 0.2f))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("1", style = MaterialTheme.typography.labelSmall)
        Text("${values.size}", style = MaterialTheme.typography.labelSmall)
    }
}

/** Statistics shown at the top of the month list. */
@Composable
fun MonthStats(vm: ExpensesViewModel, onBudgets: () -> Unit) {
    val month by vm.month.collectAsState()
    val txs by vm.transactions.collectAsState()
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val today = LocalDate.now()
    val t = CashStats.totals(txs)
    val prevYm = month.minusMonths(1)
    val prevTxs = CashStats.inMonth(all, prevYm)
    val prev = CashStats.totals(prevTxs)
    val hasData = t.income != 0L || t.expense != 0L
    val isNow = month == YearMonth.from(today)

    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(4.dp))
        BudgetCard(vm, onBudgets)
        // vs previous month
        if (hasData && (prev.income != 0L || prev.expense != 0L)) StatCard(stringResource(R.string.compare_with, monthName(prevYm.monthValue))) {
            DeltaRow(stringResource(R.string.home_income), t.income, prev.income, true, cur)
            DeltaRow(stringResource(R.string.home_expense), t.expense, prev.expense, false, cur)
            DeltaRow(stringResource(R.string.home_balance), t.balance, prev.balance, true, cur)
        }
        // forecast
        if (isNow && t.expense > 0 && today.dayOfMonth >= 3) {
            val f = CashStats.forecast(t.expense, today.dayOfMonth, month.lengthOfMonth())
            StatCard(stringResource(R.string.forecast_title)) {
                KeyVal(stringResource(R.string.spent_so_far), Money.format(t.expense, cur))
                KeyVal(stringResource(R.string.forecast_pace), Money.format(f, cur), MaterialTheme.colorScheme.primary)
                if (prev.expense > 0) DeltaRow(stringResource(R.string.vs_last_month), f, prev.expense, false, cur)
            }
        }
        // day by day
        if (t.expense > 0) StatCard(stringResource(R.string.day_by_day)) {
            DayBars(CashStats.dailyExpense(txs, month))
            val elapsed = if (isNow) today.dayOfMonth else month.lengthOfMonth()
            KeyVal(stringResource(R.string.avg_per_day), Money.format(t.expense / elapsed, cur))
            Text(stringResource(R.string.top_expenses), style = MaterialTheme.typography.labelLarge)
            txs.filter { it.type == Tx.EXPENSE }.sortedByDescending { it.amountMinor }.take(3).forEach { x ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${x.categoryIcon} ${x.categoryName} · ${fmtDate(x.dateMillis)}", modifier = Modifier.weight(1f), maxLines = 1)
                    Text(Money.format(x.amountMinor, cur), color = expenseColor(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        // category breakdown
        val cats = CashStats.categoryBreakdown(txs)
        if (cats.isNotEmpty()) StatCard(stringResource(R.string.spending_by_category)) {
            val total = cats.sumOf { it.second }
            val prevCats = CashStats.categoryBreakdown(prevTxs).toMap()
            cats.take(6).forEach { (name, amt) ->
                val pc = prevCats[name]
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name)
                        Text(Money.format(amt, cur), fontWeight = FontWeight.Medium)
                    }
                    LinearProgressIndicator(progress = { amt.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${amt * 100 / total}%", style = MaterialTheme.typography.bodySmall)
                        if (pc != null && pc > 0) Text("%+.0f%%".format(CashStats.pctChange(amt, pc)!!),
                            style = MaterialTheme.typography.bodySmall, color = if (amt <= pc) incomeColor() else expenseColor())
                    }
                }
            }
        }
    }
}

/** Extra statistics on the year page. */
@Composable
fun YearExtras(all: List<TransactionEntity>, year: Int, months: List<MonthTotals>, cur: String) {
    val y = MonthTotals(months.sumOf { it.income }, months.sumOf { it.expense })
    val p = CashStats.totals(all.filter { toDate(it.dateMillis).year == year - 1 })
    if (p.income != 0L || p.expense != 0L) StatCard(stringResource(R.string.compare_with, "${year - 1}")) {
        DeltaRow(stringResource(R.string.home_income), y.income, p.income, true, cur)
        DeltaRow(stringResource(R.string.home_expense), y.expense, p.expense, false, cur)
        DeltaRow(stringResource(R.string.home_balance), y.balance, p.balance, true, cur)
    }
    val active = months.indices.filter { months[it].income != 0L || months[it].expense != 0L }
    if (active.size >= 2) {
        val last = active.last()
        val best = active.maxByOrNull { months[it].balance }!!
        val worst = active.minByOrNull { months[it].balance }!!
        StatCard(stringResource(R.string.balance_curve)) {
            LineChart(CashStats.runningBalance(months.take(last + 1)))
            KeyVal(stringResource(R.string.best_month), "${monthName(best + 1)}  ${signed(months[best].balance, cur)}", gainColor(months[best].balance))
            KeyVal(stringResource(R.string.worst_month), "${monthName(worst + 1)}  ${signed(months[worst].balance, cur)}", gainColor(months[worst].balance))
        }
    }
}
