package com.aajli.apeak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.*
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.Money

@Composable
fun OverviewScreen(vm: OverviewViewModel, backupVm: BackupViewModel, onSettings: () -> Unit) {
    val s by vm.state.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ApeakMark(Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.nav_overview), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconBtn(Ico.Settings, onSettings)
        }
        if (s.ready) {
            if (s.noAccounts) Text(stringResource(R.string.overview_empty))
            BackupReminder(backupVm, s.hasData)
            HeroCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.net_worth), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(s.net, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    StackedBar(listOf(stringResource(R.string.kind_current) to s.current, stringResource(R.string.kind_savings) to s.savings,
                        stringResource(R.string.nav_stocks) to s.stockValue), cur)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.gains), style = MaterialTheme.typography.titleMedium)
                    KeyVal(stringResource(R.string.interest_earned), signed(s.interest, cur), gainColor(s.interest))
                    KeyVal(stringResource(R.string.dividends_received), signed(s.dividends, cur), gainColor(s.dividends))
                    KeyVal(stringResource(R.string.realized_pl), signed(s.realized, cur), gainColor(s.realized))
                    KeyVal(stringResource(R.string.unrealized_pl), signed(s.unrealized, cur), gainColor(s.unrealized))
                    KeyVal(stringResource(R.string.total_gains), signed(s.gains, cur), gainColor(s.gains))
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium)
                    KeyVal(stringResource(R.string.kind_current), Money.format(s.current, cur))
                    KeyVal(stringResource(R.string.kind_savings), Money.format(s.savings, cur))
                    KeyVal(stringResource(R.string.stocks_value), Money.format(s.stockValue, cur))
                    KeyVal(stringResource(R.string.stocks_cost), Money.format(s.stocksCost, cur))
                    KeyVal(stringResource(R.string.deposits_total), Money.format(s.deposits, cur))
                    KeyVal(stringResource(R.string.withdrawals_total), Money.format(s.withdrawals, cur))
                }
            }
            OverviewExtras(s, cur)
            if (s.years.isNotEmpty()) {
                Text(stringResource(R.string.by_year), style = MaterialTheme.typography.titleMedium)
                s.years.forEach { y ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${y.year}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            KeyVal(stringResource(R.string.interest_earned), signed(y.interest, cur))
                            KeyVal(stringResource(R.string.dividends_received), signed(y.dividends, cur))
                            KeyVal(stringResource(R.string.realized_pl), signed(y.realized, cur))
                            KeyVal(stringResource(R.string.total_gains), signed(y.total, cur), gainColor(y.total))
                            val py = s.years.find { it.year == y.year - 1 }
                            if (py != null) DeltaRow(stringResource(R.string.vs_prev_year), y.total, py.total, true, cur)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun BanksScreen(vm: BanksViewModel, onOpen: (String) -> Unit) {
    val banks by vm.banks.collectAsState()
    val entries by vm.bankEntries.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val byAcc = entries.groupBy { it.accountId }
    fun bal(b: BankAccountEntity): Long = Calc.bankBalance(b, byAcc[b.id].orEmpty())
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }) { Text(stringResource(R.string.add_account_fab)) } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.nav_banks), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.banks_total), style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(banks.sumOf { bal(it) }, cur), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
            if (banks.isEmpty()) Text(stringResource(R.string.no_banks))
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(banks, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(b.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Ico.Bank)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                val kind = stringResource(if (b.kind == BankKind.SAVINGS) R.string.kind_savings else R.string.kind_current)
                                val rate = if (b.kind == BankKind.SAVINGS) " · ${b.interestRate}%" else ""
                                Text(listOf(b.bankName, kind).filter { it.isNotBlank() }.joinToString(" · ") + rate, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(Money.format(bal(b), cur), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    if (adding) BankAccountDialog(vm, null, cur) { adding = false }
}

@Composable
fun BankDetail(vm: BanksViewModel, id: String, onBack: () -> Unit) {
    val banks by vm.banks.collectAsState()
    val allEntries by vm.bankEntries.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BankEntryEntity?>(null) }
    val acc = banks.find { it.id == id }
    if (acc == null) { LaunchedEffect(Unit) { onBack() }; return }
    val entries = allEntries.filter { it.accountId == id }
    val balance = Calc.bankBalance(acc, entries)
    val interestByYear = entries.filter { it.type == BankType.INTEREST }.groupBy { toDate(it.dateMillis).year }.toSortedMap(reverseOrder())

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(acc.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            IconBtn(Ico.Edit, { edit = true })
            IconBtn(Ico.Delete, { del = true }, MaterialTheme.colorScheme.error)
        }
        HeroCard {
            Column(Modifier.padding(16.dp)) {
                Text(listOf(acc.bankName, stringResource(if (acc.kind == BankKind.SAVINGS) R.string.kind_savings else R.string.kind_current))
                    .filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(balance, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                KeyVal(stringResource(R.string.deposits_total), Money.format(entries.filter { it.type == BankType.DEPOSIT }.sumOf { it.amountMinor }, cur))
                KeyVal(stringResource(R.string.withdrawals_total), Money.format(entries.filter { it.type == BankType.WITHDRAW }.sumOf { it.amountMinor }, cur))
                KeyVal(stringResource(R.string.interest_earned), Money.format(entries.filter { it.type == BankType.INTEREST }.sumOf { it.amountMinor }, cur))
                if (acc.kind == BankKind.SAVINGS) {
                    KeyVal(stringResource(R.string.interest_rate), "${acc.interestRate}%")
                    KeyVal(stringResource(R.string.expected_interest), Money.format((balance * acc.interestRate / 100.0).toLong(), cur))
                }
            }
        }
        LineChart(Calc.balanceSeries(acc, entries))
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.add_entry)) }
        if (interestByYear.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.interest_by_year), style = MaterialTheme.typography.titleMedium)
                    interestByYear.forEach { (y, l) -> KeyVal("$y", Money.format(l.sumOf { it.amountMinor }, cur)) }
                }
            }
        }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium)
        entries.sortedByDescending { it.dateMillis }.forEach { e -> BankEntryRow(e, cur) { editing = e } }
        Spacer(Modifier.height(16.dp))
    }
    if (edit) BankAccountDialog(vm, acc, cur) { edit = false }
    if (adding) BankEntryDialog(vm, id, null, cur) { adding = false }
    editing?.let { e -> BankEntryDialog(vm, id, e, cur) { editing = null } }
    if (del) ConfirmDialog(stringResource(R.string.confirm_account), { vm.deleteBank(id); del = false }, { del = false })
}

@Composable
private fun BankEntryRow(e: BankEntryEntity, cur: String, onClick: () -> Unit) {
    val out = e.type == BankType.WITHDRAW
    val (icon, label) = when (e.type) {
        BankType.DEPOSIT -> Ico.Up to R.string.entry_deposit
        BankType.WITHDRAW -> Ico.Down to R.string.entry_withdraw
        else -> Ico.Percent to R.string.entry_interest
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, if (out) expenseColor() else incomeColor())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(label))
            Text(listOf(fmtDate(e.dateMillis), e.note).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text((if (out) "−" else "+") + Money.format(e.amountMinor, cur), color = if (out) expenseColor() else incomeColor(), fontWeight = FontWeight.SemiBold)
    }
}
