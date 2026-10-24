package com.aajli.apeak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.util.Holding
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.StockAnalytics
import java.time.YearMonth
import java.util.Locale

private fun lbl(ym: YearMonth) = ym.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " " + ym.year

@Composable
fun TwoLineChart(a: List<Long>, b: List<Long>) {
    if (a.size < 2) return
    val ca = MaterialTheme.colorScheme.primary
    val cb = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val all = a + b
        val mn = all.min(); val mx = all.max()
        val range = (mx - mn).coerceAtLeast(1L).toFloat()
        fun path(v: List<Long>): Path {
            val p = Path()
            v.forEachIndexed { i, x ->
                val px = size.width * i / (v.size - 1)
                val py = size.height * 0.95f - size.height * 0.9f * ((x - mn).toFloat() / range)
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            return p
        }
        drawPath(path(b), cb, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path(a), ca, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Portfolio value vs money invested over time. stockIds = null means all stocks. */
@Composable
fun PortfolioChartCard(vm: StocksViewModel, stockIds: Set<String>?) {
    val trades by vm.trades.collectAsState()
    val prices by vm.prices.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val series = remember(trades, prices, stockIds) { StockAnalytics.portfolioSeries(trades.filter { stockIds == null || it.stockId in stockIds }, prices) }
    if (series.size >= 2) StatCard(stringResource(R.string.portfolio_chart_title)) {
        TwoLineChart(series.map { it.value }, series.map { it.cost })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lbl(series.first().month), style = MaterialTheme.typography.bodySmall)
            Text(lbl(series.last().month), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.legend_value), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.legend_invested), style = MaterialTheme.typography.bodySmall)
        }
        val g = series.last().value - series.last().cost
        KeyVal(stringResource(R.string.port_gain), signed(g, cur), gainColor(g))
    }
}

@Composable
fun PortfolioStatsCard(all: List<Holding>, fees: Long, cur: String) {
    if (all.isEmpty()) return
    val (best, worst) = StockAnalytics.bestWorst(all)
    StatCard(stringResource(R.string.stats_title)) {
        KeyVal(stringResource(R.string.stats_holdings), "${all.count { it.qty > 0 }}")
        if (best != null) KeyVal(stringResource(R.string.stats_best), "${best.symbol}  ${"%+.1f%%".format(best.returnPct!!)}", gainColor(best.totalReturn))
        if (worst != null && worst.key != best?.key) KeyVal(stringResource(R.string.stats_worst), "${worst.symbol}  ${"%+.1f%%".format(worst.returnPct!!)}", gainColor(worst.totalReturn))
        KeyVal(stringResource(R.string.stats_fees), Money.format(fees, cur))
    }
}
