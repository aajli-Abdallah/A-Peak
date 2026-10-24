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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.DividendStats
import com.aajli.apeak.util.Money
import java.time.LocalDate
import java.util.Locale

@Composable
private fun SimpleBars(values: List<Long>, labels: List<String>) {
    val c = incomeColor(); val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val mx = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val gw = size.width / values.size
        values.forEachIndexed { i, v ->
            val h = size.height * (v / mx)
            drawRoundRect(c, Offset(gw * i + gw * 0.15f, size.height - h), Size(gw * 0.7f, h), CornerRadius(gw * 0.2f))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = dim) }
    }
}

/** All dividends: last 12 months, projection, yields, by year and by stock. */
@Composable
fun DividendsScreen(vm: StocksViewModel, onBack: () -> Unit, onOpenStock: (String) -> Unit) {
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val divs by vm.dividends.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val today = LocalDate.now()
    val rows = stocks.map { Calc.stockRow(it, trades, divs) }
    val heldIds = rows.filter { it.pos.qty > 0 }.map { it.stock.id }.toSet()
    val months = DividendStats.trailingMonths(divs, today)
    val last12 = months.sumOf { it.second }
    val projected = DividendStats.last12(divs.filter { it.stockId in heldIds }, today)
    val cost = rows.filter { it.pos.qty > 0 }.sumOf { it.pos.costMinor }
    val value = rows.filter { it.pos.qty > 0 }.sumOf { it.valueMinor }
    val yoc = DividendStats.yieldPct(projected, cost)
    val yov = DividendStats.yieldPct(projected, value)
    val perStock = DividendStats.byStock(divs).entries.sortedByDescending { it.value }

    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(stringResource(R.string.div_title), style = MaterialTheme.typography.titleLarge)
        }
        if (divs.isEmpty()) Text(stringResource(R.string.dividends_empty))
        HeroCard {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.div_last12), style = MaterialTheme.typography.labelMedium)
                Text(Money.format(last12, cur), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                KeyVal(stringResource(R.string.div_projected), Money.format(projected, cur))
                KeyVal(stringResource(R.string.div_yield_cost), if (yoc != null) "%.1f%%".format(yoc) else "–")
                KeyVal(stringResource(R.string.div_yield_value), if (yov != null) "%.1f%%".format(yov) else "–")
            }
        }
        if (divs.isNotEmpty()) StatCard(stringResource(R.string.div_monthly_title)) {
            SimpleBars(months.map { it.second }, months.map { it.first.month.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) })
        }
        val years = DividendStats.byYear(divs)
        if (years.isNotEmpty()) StatCard(stringResource(R.string.div_by_year)) {
            years.forEach { (y, total) -> KeyVal("$y", Money.format(total, cur), incomeColor()) }
            KeyVal(stringResource(R.string.div_all_time), Money.format(divs.sumOf { it.amountMinor }, cur))
        }
        if (perStock.isNotEmpty()) StatCard(stringResource(R.string.div_by_stock)) {
            perStock.forEach { (id, total) ->
                val s = stocks.find { it.id == id }
                if (s != null) {
                    val row = rows.find { it.stock.id == id }
                    val l12 = DividendStats.last12(divs.filter { it.stockId == id }, today)
                    val y = if (row != null && row.pos.qty > 0) DividendStats.yieldPct(l12, row.pos.costMinor) else null
                    val sub = Money.format(l12, cur) + " / 12m" + (if (y != null) " · " + stringResource(R.string.yield_fmt, "%.1f%%".format(y)) else "")
                    Column(Modifier.fillMaxWidth().clickable { onOpenStock(id) }.padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.symbol, fontWeight = FontWeight.SemiBold)
                            Text(Money.format(total, cur), color = incomeColor(), fontWeight = FontWeight.SemiBold)
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
