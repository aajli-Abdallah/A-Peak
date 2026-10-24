package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.aajli.apeak.util.Holding
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.StockAnalytics

@Composable
private fun HoldingCard(h: Holding, portfolio: Long, cur: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(enabled = h.stockIds.size == 1, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Ico.Stocks)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.symbol, style = MaterialTheme.typography.titleMedium)
                val detail = if (h.qty > 0) "${Money.qtyInput(h.qty)} × ${Money.format(h.avgPrice, cur)}" else stringResource(R.string.closed)
                Text(listOf(h.accounts.joinToString(", "), detail).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (h.qty > 0 && portfolio > 0) Text(stringResource(R.string.weight_fmt, "%.1f".format(h.valueMinor * 100.0 / portfolio)), style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(h.valueMinor, cur), fontWeight = FontWeight.SemiBold)
                Text(signed(h.totalReturn, cur) + (h.returnPct?.let { "  (%+.1f%%)".format(it) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = gainColor(h.totalReturn))
            }
        }
    }
}

/** Round icon tile with a label, used inside the hero card. A badge shows a count (for example stale prices). */
@Composable
private fun ActionTile(ico: Ico, label: String, badge: Int, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                AppIcon(ico, Modifier.size(26.dp), Color.White)
            }
            if (badge > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(20.dp).background(Color(0xFFF59E0B), CircleShape),
                contentAlignment = Alignment.Center) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = Color(0xFF0B1F3A), fontWeight = FontWeight.Bold)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun Sparkline(values: List<Long>) {
    if (values.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(52.dp)) {
        val mn = values.min(); val mx = values.max()
        val range = (mx - mn).coerceAtLeast(1L).toFloat()
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height * 0.9f - size.height * 0.8f * ((v - mn).toFloat() / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Stocks tab: one scrolling page. Hero with value, return, trend and quick tiles; then accounts or holdings. */
@Composable
fun BrokersScreen(vm: StocksViewModel, onOpen: (String) -> Unit, onOpenStock: (String) -> Unit = {}, onPrices: () -> Unit = {},
                  onDividends: () -> Unit = {}, onGains: () -> Unit = {}, onLists: () -> Unit = {}) {
    val brokers by vm.brokers.collectAsState()
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val rawTrades by vm.rawTrades.collectAsState()
    val prices by vm.prices.collectAsState()
    val divs by vm.dividends.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var addingStock by remember { mutableStateOf(false) }
    var addingAccount by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var sort by remember { mutableIntStateOf(0) }
    var hideClosed by remember { mutableStateOf(true) }
    var merge by remember { mutableStateOf(false) }
    val rows = stocks.map { Calc.stockRow(it, trades, divs) }
    val stale = rows.count { it.pos.qty > 0 && (it.stock.lastPriceMinor == 0L || StockAnalytics.ageDays(it.stock.lastPriceDate) >= 7) }
    val nameOf = { id: String -> brokers.find { it.id == id }?.name ?: "" }
    val all = StockAnalytics.holdings(rows, nameOf, merge)
    val holdings = StockAnalytics.sort(all.filter { !hideClosed || it.qty > 0 }, sort)
    val portfolioValue = rows.sumOf { it.valueMinor }
    val totalReturn = rows.sumOf { it.totalReturn }
    val invested = rows.sumOf { it.pos.boughtMinor }
    val series = remember(trades, prices) { StockAnalytics.portfolioSeries(trades, prices).map { it.value } }
    val open = rows.count { it.pos.qty > 0 }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { addingStock = true }) { Text(stringResource(R.string.add_stock_fab)) } }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.nav_stocks), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.stocks_subtitle, open, brokers.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                HeroCard {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.stocks_value), style = MaterialTheme.typography.labelMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Money.format(portfolioValue, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(signed(totalReturn, cur) + (if (invested > 0) "  %+.1f%%".format(totalReturn * 100.0 / invested) else ""),
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = gainColor(totalReturn))
                            }
                        }
                        Sparkline(series)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ActionTile(Ico.Refresh, stringResource(R.string.tile_prices), stale, onPrices, Modifier.weight(1f))
                            ActionTile(Ico.Coin, stringResource(R.string.tile_dividends), 0, onDividends, Modifier.weight(1f))
                            ActionTile(Ico.Chart, stringResource(R.string.tile_gains), 0, onGains, Modifier.weight(1f))
                            ActionTile(Ico.List, stringResource(R.string.tile_lists), 0, onLists, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (stale > 0) item {
                Text(stringResource(R.string.prices_stale, stale), style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B),
                    modifier = Modifier.clickable(onClick = onPrices).padding(vertical = 4.dp))
            }
            item { PriceAlertsCard(rows, cur) }
            item { ToggleRow(listOf(stringResource(R.string.tab_accounts), stringResource(R.string.tab_holdings)), tab) { tab = it } }
            if (tab == 0) {
                item { OutlinedButton(onClick = { addingAccount = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_broker_fab)) } }
                if (brokers.isEmpty()) item { Text(stringResource(R.string.no_brokers)) }
                items(brokers, key = { it.id }) { b ->
                    val mine = rows.filter { it.stock.accountId == b.id }
                    Card(Modifier.fillMaxWidth().clickable { onOpen(b.id) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Ico.Overview)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.name, style = MaterialTheme.typography.titleMedium)
                                Text(b.brokerName, style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(mine.sumOf { it.valueMinor }, cur), fontWeight = FontWeight.SemiBold)
                                val tr = mine.sumOf { it.totalReturn }
                                Text(signed(tr, cur), style = MaterialTheme.typography.bodySmall, color = gainColor(tr))
                            }
                        }
                    }
                }
            } else {
                item { ToggleRow(listOf(stringResource(R.string.sort_value), stringResource(R.string.sort_return), stringResource(R.string.sort_name)), sort) { sort = it } }
                item {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = hideClosed, onCheckedChange = { hideClosed = it })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.hide_closed))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = merge, onCheckedChange = { merge = it })
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.merge_ticker))
                        }
                    }
                }
                item { PortfolioChartCard(vm, null) }
                item { PortfolioStatsCard(all, rawTrades.sumOf { it.feesMinor }, cur) }
                item { SectorCard(rows, cur) }
                if (holdings.isEmpty()) item { Text(stringResource(R.string.no_holdings)) }
                items(holdings, key = { it.key }) { h -> HoldingCard(h, portfolioValue, cur) { h.stockIds.singleOrNull()?.let(onOpenStock) } }
            }
        }
    }
    if (addingStock) StockDialog(vm, null, null, cur) { addingStock = false }
    if (addingAccount) BrokerDialog(vm, null) { addingAccount = false }
}
