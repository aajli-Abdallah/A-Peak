package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.util.CashStats
import com.aajli.apeak.util.Money
import java.time.Month
import java.util.Locale

/** Year page: spending per month, stacked by category (top 5 + other). */
@Composable
fun CategoryTrendCard(all: List<TransactionEntity>, year: Int) {
    val other = stringResource(R.string.cat_other)
    val series = CashStats.categoryByMonth(all, year, 5, other)
    if (series.isEmpty()) return
    val pal = if (LocalIsDark.current) LightPalette else Palette
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    StatCard(stringResource(R.string.cat_trend_title)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val totals = (0 until 12).map { m -> series.sumOf { it.second[m] } }
            val mx = totals.max().coerceAtLeast(1L).toFloat()
            val gw = size.width / 12f
            for (m in 0 until 12) {
                var y = size.height
                series.forEachIndexed { i, s ->
                    val h = size.height * (s.second[m] / mx)
                    if (h > 0f) { y -= h; drawRect(pal[i % pal.size], Offset(gw * m + gw * 0.15f, y), Size(gw * 0.7f, h)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            (1..12).forEach {
                Text(Month.of(it).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = label)
            }
        }
        series.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(pal[i % pal.size], CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(s.first, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Stock page: recorded prices over time (points are evenly spaced). */
@Composable
fun StockPriceChart(vm: StocksViewModel, stockId: String, cur: String) {
    val prices by vm.prices.collectAsState()
    val pts = prices.filter { it.stockId == stockId }.sortedBy { it.dateMillis }
    if (pts.size >= 2) StatCard(stringResource(R.string.price_history)) {
        LineChart(pts.map { it.priceMinor })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtDate(pts.first().dateMillis), style = MaterialTheme.typography.bodySmall)
            Text(fmtDate(pts.last().dateMillis), style = MaterialTheme.typography.bodySmall)
        }
        KeyVal(stringResource(R.string.price_range), Money.format(pts.minOf { it.priceMinor }, cur) + " – " + Money.format(pts.maxOf { it.priceMinor }, cur))
    }
}
