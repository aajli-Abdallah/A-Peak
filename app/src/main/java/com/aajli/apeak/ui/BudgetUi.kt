package com.aajli.apeak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.Tx
import com.aajli.apeak.util.BudgetLevel
import com.aajli.apeak.util.BudgetProgress
import com.aajli.apeak.util.Budgets
import com.aajli.apeak.util.Money
import java.time.YearMonth
import java.util.Locale

@Composable
private fun levelColor(l: BudgetLevel): Color = when (l) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARN -> Color(0xFFF59E0B)
    BudgetLevel.OVER -> expenseColor()
}

private fun monthLabel(ym: YearMonth) = ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + ym.year

/** Progress bar for one budget: amber from 80%, red from 100%. */
@Composable
fun BudgetBar(p: BudgetProgress, cur: String) {
    val c = levelColor(p.level)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${p.icon} ${p.name}", Modifier.weight(1f), maxLines = 1)
            Text("${Money.format(p.spent, cur)} / ${Money.format(p.limit, cur)}", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { p.ratio.coerceIn(0f, 1f) }, color = c, modifier = Modifier.fillMaxWidth().height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${(p.ratio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = c, fontWeight = FontWeight.SemiBold)
            Text(if (p.spent > p.limit) stringResource(R.string.budget_over, Money.format(p.spent - p.limit, cur))
                else stringResource(R.string.budget_left, Money.format(p.limit - p.spent, cur)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Month page: budgets of the month being viewed. */
@Composable
fun BudgetCard(vm: ExpensesViewModel, onBudgets: () -> Unit) {
    val month by vm.month.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState()
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val list = Budgets.progress(budgets, cats, all, month)
    StatCard(stringResource(R.string.budgets_title)) {
        if (list.isEmpty()) Text(stringResource(R.string.budgets_none_hint), style = MaterialTheme.typography.bodySmall)
        list.forEach { BudgetBar(it, cur) }
        OutlinedButton(onClick = onBudgets, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.budgets_manage)) }
    }
}

/** Expense years page: this month's budgets, alerts first. */
@Composable
fun BudgetsSummaryCard(vm: ExpensesViewModel, onBudgets: () -> Unit) {
    val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState()
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val now = YearMonth.now()
    val list = Budgets.progress(budgets, cats, all, now)
    val alerts = list.filter { it.level != BudgetLevel.OK }
    StatCard(stringResource(R.string.budgets_title) + " · " + monthLabel(now)) {
        if (list.isEmpty()) Text(stringResource(R.string.budgets_none_hint), style = MaterialTheme.typography.bodySmall)
        else {
            KeyVal(stringResource(R.string.budgets_used), Money.format(list.sumOf { it.spent }, cur) + " / " + Money.format(list.sumOf { it.limit }, cur))
            if (alerts.isEmpty()) Text(stringResource(R.string.budgets_all_ok), style = MaterialTheme.typography.bodySmall, color = incomeColor())
            alerts.forEach { BudgetBar(it, cur) }
        }
        OutlinedButton(onClick = onBudgets, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.budgets_manage)) }
    }
}

private class Target(val id: String, val name: String, val icon: String, val limit: Long)

/** Set, change or remove the monthly limit of each expense category. */
@Composable
fun BudgetsScreen(vm: ExpensesViewModel, onBack: () -> Unit) {
    val budgets by vm.budgets.collectAsState()
    val cats by vm.categories.collectAsState()
    val all by vm.allTransactions.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<Target?>(null) }
    val progress = Budgets.progress(budgets, cats, all, ym)
    val byId = progress.associateBy { it.categoryId }
    val expenseCats = cats.filter { it.type == Tx.EXPENSE }
    val extra = progress.filter { p -> expenseCats.none { it.id == p.categoryId } }
    val sumLimit = progress.sumOf { it.limit }
    val sumSpent = progress.sumOf { it.spent }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(stringResource(R.string.budgets_title), style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Prev, { ym = ym.minusMonths(1) })
            Text(monthLabel(ym), style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable { ym = YearMonth.now() })
            IconBtn(Ico.Next, { ym = ym.plusMonths(1) })
        }
        HeroCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.budgets_total), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(sumLimit - sumSpent, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                KeyVal(stringResource(R.string.budgets_used), Money.format(sumSpent, cur) + " / " + Money.format(sumLimit, cur))
                if (sumLimit > 0) LinearProgressIndicator(progress = { (sumSpent.toFloat() / sumLimit).coerceIn(0f, 1f) },
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth().height(8.dp))
            }
        }
        Text(stringResource(R.string.budgets_apply_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val rows = expenseCats.map { Target(it.id, it.name, it.icon, byId[it.id]?.limit ?: 0L) } + extra.map { Target(it.categoryId, it.name, it.icon, it.limit) }
        rows.forEach { t ->
            Card(Modifier.fillMaxWidth().clickable { editing = t }) {
                Column(Modifier.padding(16.dp)) {
                    val p = byId[t.id]
                    if (p != null) BudgetBar(p, cur)
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.icon} ${t.name}")
                        Text(stringResource(R.string.budget_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    editing?.let { t ->
        var text by remember(t) { mutableStateOf(if (t.limit > 0) Money.toInput(t.limit) else "") }
        var err by remember(t) { mutableStateOf(false) }
        FormDialog(stringResource(R.string.budget_for, t.name), { editing = null }, onSave = {
            val m = Money.parseOpt(text)
            if (m == null) err = true else { vm.setBudget(t.id, t.name, ym, m); editing = null }
        }, onDelete = if (t.limit > 0) ({ vm.setBudget(t.id, t.name, ym, 0L); editing = null }) else null) {
            AmountField(stringResource(R.string.budget_limit, cur), text) { text = it }
            Text(stringResource(R.string.budget_applies, monthLabel(ym)), style = MaterialTheme.typography.bodySmall)
            if (err) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Shown when saving an expense reaches 80% or 100% of a category budget. */
@Composable
fun BudgetAlertHost(vm: ExpensesViewModel) {
    val a by vm.budgetAlert.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    a?.let { al ->
        AlertDialog(
            onDismissRequest = { vm.budgetAlert.value = null },
            title = { Text(stringResource(R.string.budget_alert_title)) },
            text = {
                Text(if (al.level == BudgetLevel.OVER)
                    stringResource(R.string.budget_alert_over, al.name, Money.format(al.spent, cur), Money.format(al.limit, cur))
                else stringResource(R.string.budget_alert_warn, al.name, al.percent, Money.format(al.spent, cur), Money.format(al.limit, cur)))
            },
            confirmButton = { TextButton(onClick = { vm.budgetAlert.value = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
}
