package com.aajli.apeak.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.CsvExport
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.Reports
import kotlinx.coroutines.launch

private suspend fun exportCsv(ctx: Context, vm: StocksViewModel, trades: Boolean, year: Int?) {
    val stocks = vm.stocks.value
    val brokers = vm.brokers.value
    val name = { id: String -> brokers.find { it.id == id }?.name ?: "" }
    val text = if (trades) CsvExport.trades(vm.rawTrades.value, stocks, name, year) else CsvExport.dividends(vm.dividends.value, stocks, name, year)
    val file = "A-Peak-" + (if (trades) "trades" else "dividends") + "-" + (year?.toString() ?: "all") + ".csv"
    val uri = vm.backups.shareText(file, text)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

/** Every sale with its profit or loss, by year, closed positions and CSV export. */
@Composable
fun RealizedGainsScreen(vm: StocksViewModel, onBack: () -> Unit, onOpenStock: (String) -> Unit) {
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val rawTrades by vm.rawTrades.collectAsState()
    val divs by vm.dividends.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportYear by remember { mutableStateOf<Int?>(null) }
    val sales = Reports.sales(trades)
    val years = Reports.byYear(sales)
    val symbolOf = { id: String -> stocks.find { it.id == id }?.symbol ?: "?" }
    val closed = stocks.map { Calc.stockRow(it, trades, divs) }.filter { it.pos.qty <= 0.0 && it.pos.boughtMinor > 0 }
    val totalGain = sales.sumOf { it.gainMinor }
    val exportYears = (rawTrades.map { toDate(it.dateMillis).year } + divs.map { toDate(it.dateMillis).year }).distinct().sortedDescending()
    val allLabel = stringResource(R.string.export_all_years)

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(stringResource(R.string.gains_title), style = MaterialTheme.typography.titleLarge)
        }
        if (sales.isEmpty()) Text(stringResource(R.string.gains_empty))
        HeroCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.gains_total), style = MaterialTheme.typography.labelMedium)
                Text(signed(totalGain, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                KeyVal(stringResource(R.string.gains_sales), "${sales.size}")
                KeyVal(stringResource(R.string.stats_fees), Money.format(rawTrades.sumOf { it.feesMinor }, cur))
            }
        }
        years.forEach { y ->
            StatCard("${y.year}") {
                KeyVal(stringResource(R.string.gains_realized), signed(y.gain, cur), gainColor(y.gain))
                KeyVal(stringResource(R.string.gains_proceeds), Money.format(y.proceeds, cur))
                KeyVal(stringResource(R.string.stats_fees), Money.format(y.fees, cur))
                sales.filter { toDate(it.dateMillis).year == y.year }.forEach { s ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenStock(s.stockId) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${symbolOf(s.stockId)} · ${Money.qtyInput(s.qty)}", fontWeight = FontWeight.Medium)
                            Text(fmtDate(s.dateMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(signed(s.gainMinor, cur), color = gainColor(s.gainMinor), fontWeight = FontWeight.SemiBold)
                            s.gainPct?.let { Text("%+.1f%%".format(it), style = MaterialTheme.typography.bodySmall, color = gainColor(s.gainMinor)) }
                        }
                    }
                }
            }
        }
        if (closed.isNotEmpty()) StatCard(stringResource(R.string.closed_title)) {
            closed.forEach { r ->
                Row(Modifier.fillMaxWidth().clickable { onOpenStock(r.stock.id) }.padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.stock.symbol, fontWeight = FontWeight.Medium)
                    Text(signed(r.totalReturn, cur), color = gainColor(r.totalReturn), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        StatCard(stringResource(R.string.export_title)) {
            Text(stringResource(R.string.export_csv_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PickerField(stringResource(R.string.year_label), exportYear?.toString() ?: allLabel, listOf(allLabel) + exportYears.map { it.toString() }) {
                exportYear = if (it == 0) null else exportYears[it - 1]
            }
            Button(onClick = { scope.launch { exportCsv(ctx, vm, true, exportYear) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_trades)) }
            OutlinedButton(onClick = { scope.launch { exportCsv(ctx, vm, false, exportYear) } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_dividends)) }
        }
        Spacer(Modifier.height(16.dp))
    }
}
