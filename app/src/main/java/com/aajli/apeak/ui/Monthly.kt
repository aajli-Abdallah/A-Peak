package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.util.CashStats
import com.aajli.apeak.util.MonthTotals
import com.aajli.apeak.util.Money
import java.time.LocalDate
import java.time.Month
import java.util.Locale

@Composable
private fun IncomeExpenseRow(income: Long, expense: Long, cur: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(stringResource(R.string.home_income), style = MaterialTheme.typography.labelMedium)
            Text(Money.format(income, cur), color = incomeColor(), fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.home_expense), style = MaterialTheme.typography.labelMedium)
            Text(Money.format(expense, cur), color = expenseColor(), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Monthly tab main page: all years. */
@Composable
fun MonthlyYearsScreen(vm: ExpensesViewModel, onYear: (Int) -> Unit, onAdd: () -> Unit, onManage: () -> Unit, onBudgets: () -> Unit, onSettings: () -> Unit) {
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val byYear = CashStats.byYear(all)
    val years = (byYear.keys + LocalDate.now().year).toSortedSet(reverseOrder())
    val total = CashStats.totals(all)
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd) { Text(stringResource(R.string.add_fab)) } }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApeakMark(Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nav_monthly), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconBtn(Ico.Settings, onSettings)
            }
            HeroCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.all_years), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(total.balance, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    IncomeExpenseRow(total.income, total.expense, cur)
                }
            }
            BudgetsSummaryCard(vm, onBudgets)
            Text(stringResource(R.string.monthly_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            YearsExtras(all, cur)
            CategoriesByYear(vm, all, cur, onManage)
            years.forEach { y ->
                val t = byYear[y] ?: MonthTotals(0, 0)
                Card(Modifier.fillMaxWidth().clickable { onYear(y) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Ico.Calendar)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$y", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row {
                                Text(Money.format(t.income, cur), color = incomeColor(), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(10.dp))
                                Text(Money.format(t.expense, cur), color = expenseColor(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(signed(t.balance, cur), color = gainColor(t.balance), fontWeight = FontWeight.SemiBold)
                        AppIcon(Ico.Next, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun MonthBars(months: List<MonthTotals>) {
    val inc = incomeColor(); val exp = expenseColor()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val mx = months.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1L).toFloat()
        val gw = size.width / 12f
        val bw = gw * 0.32f
        months.forEachIndexed { i, m ->
            val x0 = gw * i + gw * 0.14f
            val hi = size.height * (m.income / mx)
            val he = size.height * (m.expense / mx)
            drawRoundRect(inc, Offset(x0, size.height - hi), Size(bw, hi), CornerRadius(bw / 3f))
            drawRoundRect(exp, Offset(x0 + bw + gw * 0.04f, size.height - he), Size(bw, he), CornerRadius(bw / 3f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        (1..12).forEach {
            Text(Month.of(it).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = label)
        }
    }
}

@Composable
private fun MonthRow(month: Int, t: MonthTotals, cur: String, onClick: () -> Unit) {
    val empty = t.income == 0L && t.expense == 0L
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                if (!empty) Row {
                    Text(Money.format(t.income, cur), color = incomeColor(), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(10.dp))
                    Text(Money.format(t.expense, cur), color = expenseColor(), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!empty) Text(signed(t.balance, cur), color = gainColor(t.balance), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            AppIcon(Ico.Next, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Statistics of one year, with each month. */
@Composable
fun YearScreen(vm: ExpensesViewModel, year: Int, onBack: () -> Unit, onManage: () -> Unit, onMonth: (Int) -> Unit) {
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val other = stringResource(R.string.cat_other)
    val months = CashStats.byMonth(all, year)
    val y = MonthTotals(months.sumOf { it.income }, months.sumOf { it.expense })
    val active = months.count { it.income > 0 || it.expense > 0 }
    val cats = CashStats.spendingByCategory(all, year)
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text("$year", style = MaterialTheme.typography.titleLarge)
        }
        HeroCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_balance), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(y.balance, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                IncomeExpenseRow(y.income, y.expense, cur)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { MonthBars(months) }
        }
        if (y.income > 0 || active > 0) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.year_summary), style = MaterialTheme.typography.titleMedium)
                if (y.income > 0) KeyVal(stringResource(R.string.savings_rate), "%.0f%%".format(y.balance * 100.0 / y.income), gainColor(y.balance))
                if (active > 0) KeyVal(stringResource(R.string.avg_expense), Money.format(y.expense / active, cur))
            }
        }
        if (cats.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.spending_by_category), style = MaterialTheme.typography.titleMedium)
                val rest = cats.drop(5).sumOf { it.second }
                StackedBar(cats.take(5) + (if (rest > 0) listOf(other to rest) else emptyList()), cur)
            }
        }
        YearExtras(all, year, months, cur)
        YearExtras2(all, year, months, cur)
        CategoryTrendCard(all, year)
        CategoriesOfYear(vm, all, year, cur, onManage)
        Text(stringResource(R.string.months), style = MaterialTheme.typography.titleMedium)
        months.forEachIndexed { i, m -> MonthRow(i + 1, m, cur) { onMonth(i + 1) } }
        Spacer(Modifier.height(16.dp))
    }
}
