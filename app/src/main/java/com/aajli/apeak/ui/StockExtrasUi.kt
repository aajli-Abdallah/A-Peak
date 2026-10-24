package com.aajli.apeak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.StockEntity
import com.aajli.apeak.data.StockSplitEntity
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.PriceAlertKind
import com.aajli.apeak.util.PriceAlerts
import com.aajli.apeak.util.StockRow
import com.aajli.apeak.util.newId

@Composable
fun SplitDialog(vm: StocksViewModel, stock: StockEntity, existing: StockSplitEntity?, onDismiss: () -> Unit) {
    var date by remember { mutableLongStateOf(existing?.dateMillis ?: todayStart()) }
    var num by remember { mutableStateOf(existing?.numerator?.toString() ?: "2") }
    var den by remember { mutableStateOf(existing?.denominator?.toString() ?: "1") }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    FormDialog(stringResource(R.string.split_title, stock.symbol), onDismiss, onSave = {
        val n = num.toIntOrNull(); val d = den.toIntOrNull()
        if (n == null || d == null || n <= 0 || d <= 0 || n == d) err = true
        else { vm.saveSplit(StockSplitEntity(existing?.id ?: newId(), stock.id, date, n, d)); onDismiss() }
    }, onDelete = if (existing != null) ({ confirm = true }) else null) {
        Text(stringResource(R.string.split_hint), style = MaterialTheme.typography.bodySmall)
        DateButton(stringResource(R.string.date), date) { date = it }
        TextF(stringResource(R.string.split_new), num) { num = it.filter { c -> c.isDigit() } }
        TextF(stringResource(R.string.split_old), den) { den = it.filter { c -> c.isDigit() } }
        if (err) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error)
    }
    if (confirm && existing != null) ConfirmDialog(stringResource(R.string.confirm_entry), { vm.deleteSplit(existing.id); onDismiss() }, { confirm = false })
}

/** Stock page: list of splits, or just an "add split" link when there is none. */
@Composable
fun SplitsCard(vm: StocksViewModel, stock: StockEntity) {
    val all by vm.splits.collectAsState()
    val mine = all.filter { it.stockId == stock.id }.sortedByDescending { it.dateMillis }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StockSplitEntity?>(null) }
    if (mine.isEmpty()) TextButton(onClick = { adding = true }) { Text(stringResource(R.string.add_split)) }
    else StatCard(stringResource(R.string.splits_title)) {
        mine.forEach { s ->
            Row(Modifier.fillMaxWidth().clickable { editing = s }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtDate(s.dateMillis))
                Text(stringResource(R.string.split_fmt, s.numerator, s.denominator))
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_split)) }
    }
    if (adding) SplitDialog(vm, stock, null) { adding = false }
    editing?.let { e -> SplitDialog(vm, stock, e) { editing = null } }
}

/** Holdings tab: value by sector / tag, shown once at least one stock has a sector. */
@Composable
fun SectorCard(rows: List<StockRow>, cur: String) {
    val un = stringResource(R.string.unassigned)
    if (rows.none { it.stock.sector.isNotBlank() }) return
    val parts = rows.filter { it.valueMinor > 0 }.groupBy { it.stock.sector.trim().ifBlank { un } }
        .map { (k, v) -> k to v.sumOf { it.valueMinor } }.sortedByDescending { it.second }
    if (parts.isNotEmpty()) StatCard(stringResource(R.string.sector_title)) { StackedBar(parts, cur) }
}

/** Stocks tab: held stocks currently at or beyond their target or stop price. */
@Composable
fun PriceAlertsCard(rows: List<StockRow>, cur: String) {
    val hits = rows.filter { it.pos.qty > 0 }.mapNotNull { r ->
        PriceAlerts.status(r.stock.lastPriceMinor, r.stock.targetPriceMinor, r.stock.stopPriceMinor)?.let { r to it }
    }
    if (hits.isNotEmpty()) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.price_alert_title), style = MaterialTheme.typography.titleSmall)
            hits.forEach { (r, k) ->
                val target = k == PriceAlertKind.TARGET
                Text(stringResource(if (target) R.string.alert_target_fmt else R.string.alert_stop_fmt, r.stock.symbol,
                    Money.format(r.stock.lastPriceMinor, cur), Money.format(if (target) r.stock.targetPriceMinor else r.stock.stopPriceMinor, cur)),
                    color = if (target) incomeColor() else expenseColor())
            }
        }
    }
}

/** Dialog shown when saving a price reaches a target or stop price. */
@Composable
fun PriceAlertHost(vm: StocksViewModel) {
    val list by vm.priceAlerts.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    if (list.isNotEmpty()) AlertDialog(
        onDismissRequest = { vm.priceAlerts.value = emptyList() },
        title = { Text(stringResource(R.string.price_alert_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { a ->
                    val target = a.kind == PriceAlertKind.TARGET
                    Text(stringResource(if (target) R.string.alert_target_fmt else R.string.alert_stop_fmt, a.symbol, Money.format(a.price, cur), Money.format(a.level, cur)))
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.priceAlerts.value = emptyList() }) { Text(stringResource(R.string.ok)) } }
    )
}
