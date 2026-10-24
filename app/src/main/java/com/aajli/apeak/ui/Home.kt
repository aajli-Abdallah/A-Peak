package com.aajli.apeak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.Tx
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.util.Money
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HomeScreen(vm: ExpensesViewModel, onAdd: () -> Unit, onEdit: (TransactionEntity) -> Unit, onSettings: () -> Unit, onBack: (() -> Unit)? = null, onBudgets: () -> Unit = {}) {
    val month by vm.month.collectAsState()
    val txs by vm.transactions.collectAsState()
    val currency by vm.settings.currency.collectAsState()
    val deleted by vm.lastDeleted.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val msg = stringResource(R.string.tx_deleted)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val r = snackbar.showSnackbar(msg, actionLabel = undo, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearDeleted()
    }
    val income = txs.filter { it.type == Tx.INCOME }.sumOf { it.amountMinor }
    val expense = txs.filter { it.type == Tx.EXPENSE }.sumOf { it.amountMinor }
    val groups = remember(txs) { txs.groupBy { toDate(it.dateMillis) } }
    val dayFmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Scaffold(
        contentWindowInsets = if (onBack != null) WindowInsets.systemBars else WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd) { Text(stringResource(R.string.add_fab)) } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconBtn(Ico.Back, onBack) else ApeakMark(Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconBtn(Ico.Settings, onSettings)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ico.Prev, { vm.month.value = month.minusMonths(1) })
                Text(
                    month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { vm.month.value = YearMonth.now() }
                )
                IconBtn(Ico.Next, { vm.month.value = month.plusMonths(1) })
            }
            HeroCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.home_balance), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(income - expense, currency), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.home_income), style = MaterialTheme.typography.labelMedium)
                            Text(Money.format(income, currency), color = incomeColor(), fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.home_expense), style = MaterialTheme.typography.labelMedium)
                            Text(Money.format(expense, currency), color = expenseColor(), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (txs.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.empty_hint), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    item { MonthStats(vm, onBudgets) }
                    groups.forEach { (day, list) ->
                        item(key = "h$day") {
                            Text(day.format(dayFmt), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
                        }
                        items(list, key = { it.id }) { t -> TxRow(t, currency) { onEdit(t) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun TxRow(t: TransactionEntity, currency: String, onClick: () -> Unit) {
    val income = t.type == Tx.INCOME
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Text(t.categoryIcon)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.categoryName, style = MaterialTheme.typography.bodyLarge)
            val sub = listOf(t.note, t.accountName).filter { it.isNotBlank() }.joinToString(" · ")
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text((if (income) "+" else "−") + Money.format(t.amountMinor, currency),
            color = if (income) incomeColor() else expenseColor(), fontWeight = FontWeight.SemiBold)
    }
}
