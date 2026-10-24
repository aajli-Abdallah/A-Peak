package com.aajli.apeak.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.data.Tx
import com.aajli.apeak.util.CashStats
import com.aajli.apeak.util.CatTotal
import com.aajli.apeak.util.Money

@Composable
private fun CatRows(rows: List<CatTotal>, cur: String, color: Color) {
    val total = rows.sumOf { it.amount }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${r.icon} ${r.name}", Modifier.weight(1f), maxLines = 1, color = if (r.amount == 0L) dim else Color.Unspecified)
            Text(if (r.amount == 0L) "–" else Money.format(r.amount, cur), color = if (r.amount == 0L) dim else color, fontWeight = FontWeight.Medium)
        }
        if (r.amount > 0 && total > 0) LinearProgressIndicator(progress = { r.amount.toFloat() / total }, modifier = Modifier.fillMaxWidth())
    }
    KeyVal(stringResource(R.string.col_total), Money.format(total, cur))
}

@Composable
private fun CategoryYearTable(rows: List<Pair<CatTotal, List<Long>>>, years: List<Int>, cur: String) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        Row {
            Spacer(Modifier.width(130.dp))
            years.forEach { Text("$it", Modifier.width(100.dp), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge) }
        }
        rows.forEach { (c, vals) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${c.icon} ${c.name}", Modifier.width(130.dp), maxLines = 1)
                vals.forEach { v ->
                    Text(if (v == 0L) "–" else Money.format(v, cur), Modifier.width(100.dp), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall, color = if (v == 0L) dim else Color.Unspecified)
                }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Text(stringResource(R.string.col_total), Modifier.width(130.dp), fontWeight = FontWeight.Bold)
            years.indices.forEach { i ->
                Text(Money.format(rows.sumOf { it.second[i] }, cur), Modifier.width(100.dp), textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Expense tab, years page: every category, one column per year, expense or income. */
@Composable
fun CategoriesByYear(vm: ExpensesViewModel, all: List<TransactionEntity>, cur: String, onManage: () -> Unit) {
    val cats by vm.categories.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val years = CashStats.byYear(all).keys.sorted().takeLast(6)
    if (years.isEmpty()) return
    val rows = CashStats.categoryByYear(all, years, if (tab == 0) Tx.EXPENSE else Tx.INCOME, cats)
    StatCard(stringResource(R.string.cats_year_title)) {
        ToggleRow(listOf(stringResource(R.string.home_expense), stringResource(R.string.home_income)), tab) { tab = it }
        CategoryYearTable(rows, years, cur)
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_categories)) }
    }
}

/** Year page: total per category for the year, all categories listed (also those with 0). */
@Composable
fun CategoriesOfYear(vm: ExpensesViewModel, all: List<TransactionEntity>, year: Int, cur: String, onManage: () -> Unit) {
    val cats by vm.categories.collectAsState()
    StatCard(stringResource(R.string.cats_expense_title)) { CatRows(CashStats.categoryTotals(all, year, Tx.EXPENSE, cats), cur, expenseColor()) }
    StatCard(stringResource(R.string.cats_income_title)) { CatRows(CashStats.categoryTotals(all, year, Tx.INCOME, cats), cur, incomeColor()) }
    OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_categories)) }
}
