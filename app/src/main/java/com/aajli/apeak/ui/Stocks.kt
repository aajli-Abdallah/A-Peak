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
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.*
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.Money
import kotlin.math.roundToLong

data class Hist(val date: Long, val stock: StockEntity, val trade: StockTradeEntity?, val div: DividendEntity?)

fun buildHistory(stocks: List<StockEntity>, trades: List<StockTradeEntity>, divs: List<DividendEntity>): List<Hist> {
    val m = stocks.associateBy { it.id }
    return (trades.mapNotNull { t -> m[t.stockId]?.let { Hist(t.dateMillis, it, t, null) } } +
        divs.mapNotNull { d -> m[d.stockId]?.let { Hist(d.dateMillis, it, null, d) } }).sortedByDescending { it.date }
}

private class TradeCtx(val stock: StockEntity, val existing: StockTradeEntity?, val type: String)
private class DivCtx(val stock: StockEntity, val existing: DividendEntity?)

@Composable
private fun HistoryRow(h: Hist, cur: String, showSymbol: Boolean, onClick: () -> Unit) {
    val d = h.div
    val icon: Ico; val label: String; val detail: String; val amount: Long; val outflow: Boolean
    if (d != null) {
        icon = Ico.Coin; label = stringResource(R.string.entry_dividend); detail = d.note; amount = d.amountMinor; outflow = false
    } else {
        val t = h.trade!!
        val buy = t.type == TradeType.BUY
        icon = if (buy) Ico.Add else Ico.Minus
        label = stringResource(if (buy) R.string.entry_buy else R.string.entry_sell)
        detail = "${Money.qtyInput(t.quantity)} × ${Money.format(t.priceMinor, cur)}" + if (t.note.isNotBlank()) " · ${t.note}" else ""
        amount = (t.quantity * t.priceMinor).roundToLong(); outflow = buy
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, if (outflow) expenseColor() else incomeColor())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((if (showSymbol) h.stock.symbol + " · " else "") + label)
            Text(listOf(fmtDate(h.date), detail).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text((if (outflow) "−" else "+") + Money.format(amount, cur), color = if (outflow) expenseColor() else incomeColor(), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TradeDialogs(vm: StocksViewModel, t: TradeCtx?, d: DivCtx?, trades: List<StockTradeEntity>, cur: String,
                         closeT: () -> Unit, closeD: () -> Unit) {
    if (t != null) TradeDialog(vm, t.stock, t.existing, t.type,
        Calc.position(trades.filter { it.stockId == t.stock.id && it.id != t.existing?.id }).qty, cur, closeT)
    if (d != null) DividendDialog(vm, d.stock, d.existing, cur, closeD)
}

@Composable
fun BrokerDetail(vm: StocksViewModel, id: String, onBack: () -> Unit, onOpenStock: (String) -> Unit) {
    val brokers by vm.brokers.collectAsState()
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val rawTrades by vm.rawTrades.collectAsState()
    val divs by vm.dividends.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var addStock by remember { mutableStateOf(false) }
    var tradeCtx by remember { mutableStateOf<TradeCtx?>(null) }
    var divCtx by remember { mutableStateOf<DivCtx?>(null) }
    val broker = brokers.find { it.id == id }
    if (broker == null) { LaunchedEffect(Unit) { onBack() }; return }
    val mine = stocks.filter { it.accountId == id }
    val rows = mine.map { Calc.stockRow(it, trades, divs) }
    val hist = buildHistory(mine, trades, divs)
    val unreal = rows.sumOf { it.unrealizedMinor }
    val realized = rows.sumOf { it.pos.realizedMinor }
    val dividends = rows.sumOf { it.dividendsMinor }
    val total = unreal + realized + dividends

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(broker.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            IconBtn(Ico.Edit, { edit = true })
            IconBtn(Ico.Delete, { del = true }, MaterialTheme.colorScheme.error)
        }
        HeroCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(broker.brokerName, style = MaterialTheme.typography.labelMedium)
                Text(Money.format(rows.sumOf { it.valueMinor }, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                KeyVal(stringResource(R.string.stocks_cost), Money.format(rows.sumOf { it.pos.costMinor }, cur))
                KeyVal(stringResource(R.string.unrealized_pl), signed(unreal, cur), gainColor(unreal))
                KeyVal(stringResource(R.string.realized_pl), signed(realized, cur), gainColor(realized))
                KeyVal(stringResource(R.string.dividends_received), signed(dividends, cur), gainColor(dividends))
                KeyVal(stringResource(R.string.total_return), signed(total, cur), gainColor(total))
                StackedBar(rows.filter { it.valueMinor > 0 }.map { it.stock.symbol to it.valueMinor }, cur)
            }
        }
        PortfolioChartCard(vm, mine.map { it.id }.toSet())
        Button(onClick = { addStock = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.add_stock)) }
        rows.forEach { r ->
            Card(Modifier.fillMaxWidth().clickable { onOpenStock(r.stock.id) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Ico.Stocks)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.stock.symbol, style = MaterialTheme.typography.titleMedium)
                        Text("${Money.qtyInput(r.pos.qty)} × ${Money.format(r.avgPrice, cur)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Money.format(r.valueMinor, cur), fontWeight = FontWeight.SemiBold)
                        Text(signed(r.totalReturn, cur), style = MaterialTheme.typography.bodySmall, color = gainColor(r.totalReturn))
                    }
                }
            }
        }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium)
        hist.forEach { h ->
            HistoryRow(h, cur, true) {
                val t = h.trade
                if (t != null) tradeCtx = TradeCtx(h.stock, rawTrades.find { it.id == t.id } ?: t, t.type) else divCtx = DivCtx(h.stock, h.div)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (edit) BrokerDialog(vm, broker) { edit = false }
    if (addStock) StockDialog(vm, id, null, cur) { addStock = false }
    TradeDialogs(vm, tradeCtx, divCtx, trades, cur, { tradeCtx = null }, { divCtx = null })
    if (del) ConfirmDialog(stringResource(R.string.confirm_broker), { vm.deleteBroker(id); del = false }, { del = false })
}

@Composable
fun StockDetail(vm: StocksViewModel, id: String, onBack: () -> Unit) {
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val rawTrades by vm.rawTrades.collectAsState()
    val divs by vm.dividends.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    var tradeCtx by remember { mutableStateOf<TradeCtx?>(null) }
    var divCtx by remember { mutableStateOf<DivCtx?>(null) }
    val stock = stocks.find { it.id == id }
    if (stock == null) { LaunchedEffect(Unit) { onBack() }; return }
    val row = Calc.stockRow(stock, trades, divs)
    val hist = buildHistory(listOf(stock), trades, divs)
    val pct = if (row.pos.costMinor > 0) " (${"%.1f".format(row.unrealizedMinor * 100.0 / row.pos.costMinor)}%)" else ""

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(stock.symbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1)
            IconBtn(Ico.Edit, { edit = true })
            IconBtn(Ico.Delete, { del = true }, MaterialTheme.colorScheme.error)
        }
        HeroCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stock.name, style = MaterialTheme.typography.labelMedium)
                Text(Money.format(row.valueMinor, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (stock.lastPriceMinor == 0L) Text(stringResource(R.string.no_price_hint), style = MaterialTheme.typography.bodySmall)
                KeyVal(stringResource(R.string.shares), Money.qtyInput(row.pos.qty))
                KeyVal(stringResource(R.string.avg_price), Money.format(row.avgPrice, cur))
                KeyVal(stringResource(R.string.stocks_cost), Money.format(row.pos.costMinor, cur))
                KeyVal(stringResource(R.string.last_price_on, fmtDate(stock.lastPriceDate)), Money.format(stock.lastPriceMinor, cur))
                KeyVal(stringResource(R.string.unrealized_pl), signed(row.unrealizedMinor, cur) + pct, gainColor(row.unrealizedMinor))
                KeyVal(stringResource(R.string.realized_pl), signed(row.pos.realizedMinor, cur), gainColor(row.pos.realizedMinor))
                KeyVal(stringResource(R.string.dividends_received), signed(row.dividendsMinor, cur), gainColor(row.dividendsMinor))
                KeyVal(stringResource(R.string.total_return), signed(row.totalReturn, cur), gainColor(row.totalReturn))
                if (stock.sector.isNotBlank()) KeyVal(stringResource(R.string.sector), stock.sector)
                if (stock.note.isNotBlank()) KeyVal(stringResource(R.string.stock_note), stock.note)
                if (stock.targetPriceMinor > 0) KeyVal(stringResource(R.string.target_price), Money.format(stock.targetPriceMinor, cur))
                if (stock.stopPriceMinor > 0) KeyVal(stringResource(R.string.stop_price), Money.format(stock.stopPriceMinor, cur))
            }
        }
        StockPriceChart(vm, stock.id, cur)
        SplitsCard(vm, stock)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { tradeCtx = TradeCtx(stock, null, TradeType.BUY) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.entry_buy), maxLines = 1) }
            Button(onClick = { tradeCtx = TradeCtx(stock, null, TradeType.SELL) }, enabled = row.pos.qty > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.entry_sell), maxLines = 1) }
            OutlinedButton(onClick = { divCtx = DivCtx(stock, null) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.entry_dividend), maxLines = 1) }
        }
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium)
        hist.forEach { h ->
            HistoryRow(h, cur, false) {
                val t = h.trade
                if (t != null) tradeCtx = TradeCtx(stock, rawTrades.find { it.id == t.id } ?: t, t.type) else divCtx = DivCtx(stock, h.div)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (edit) StockDialog(vm, stock.accountId, stock, cur) { edit = false }
    TradeDialogs(vm, tradeCtx, divCtx, trades, cur, { tradeCtx = null }, { divCtx = null })
    if (del) ConfirmDialog(stringResource(R.string.confirm_stock), { vm.deleteStock(id); del = false }, { del = false })
}
