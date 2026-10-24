package com.aajli.apeak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aajli.apeak.R
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.NetPoint
import com.aajli.apeak.util.OverviewState
import java.util.Locale

private fun label(p: NetPoint) =
    "${p.month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())} ${p.month.year}"

@Composable
fun StatCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Extra statistics of the Overview page. Everything comes precomputed in [OverviewState]. */
@Composable
fun OverviewExtras(s: OverviewState, cur: String) {
    // net worth over time
    if (s.series.size >= 2) StatCard(stringResource(R.string.nw_title)) {
        LineChart(s.series.map { it.value })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label(s.series.first()), style = MaterialTheme.typography.bodySmall)
            Text(label(s.series.last()), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.nw_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    // own money vs growth
    if (s.net != 0L || s.contributions != 0L) StatCard(stringResource(R.string.contrib_title)) {
        KeyVal(stringResource(R.string.contributions), Money.format(s.contributions, cur))
        KeyVal(stringResource(R.string.growth), signed(s.growth, cur), gainColor(s.growth))
        if (s.contributions > 0) KeyVal(stringResource(R.string.growth_pct), "%+.1f%%".format(s.growth * 100.0 / s.contributions), gainColor(s.growth))
        StackedBar(listOf(stringResource(R.string.contributions) to s.contributions, stringResource(R.string.growth) to s.growth), cur)
    }

    // savings runway
    if (s.avgSpend > 0 && s.liquid > 0) StatCard(stringResource(R.string.runway_title)) {
        Text(stringResource(R.string.runway_fmt, "%.1f".format(s.liquid.toDouble() / s.avgSpend)),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        KeyVal(stringResource(R.string.runway_liquid), Money.format(s.liquid, cur))
        KeyVal(stringResource(R.string.avg_expense), Money.format(s.avgSpend, cur))
    }

    // passive income
    if (s.hasPassive) StatCard(stringResource(R.string.passive_title)) {
        KeyVal(stringResource(R.string.passive_total), Money.format(s.passiveTotal, cur), incomeColor())
        KeyVal(stringResource(R.string.passive_year), Money.format(s.passiveYear, cur))
        KeyVal(stringResource(R.string.passive_12m), Money.format(s.passive12, cur))
        KeyVal(stringResource(R.string.passive_avg), Money.format(s.passiveAvg, cur))
    }

    // return per stock
    if (s.perf.isNotEmpty()) StatCard(stringResource(R.string.perf_title)) {
        Text(stringResource(R.string.perf_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.perf.take(8).forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.symbol, fontWeight = FontWeight.SemiBold)
                    if (r.costMinor > 0 && r.dividendsMinor > 0)
                        Text(stringResource(R.string.yield_fmt, "%.1f%%".format(r.dividendsMinor * 100.0 / r.costMinor)), style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%+.1f%%".format(r.totalReturn * 100.0 / r.boughtMinor), color = gainColor(r.totalReturn), fontWeight = FontWeight.SemiBold)
                    Text(signed(r.totalReturn, cur), style = MaterialTheme.typography.bodySmall, color = gainColor(r.totalReturn))
                }
            }
        }
    }

    // concentration
    if (s.parts.isNotEmpty()) StatCard(stringResource(R.string.conc_title)) {
        StackedBar(s.parts, cur)
        if (s.topSymbol != null && s.topSharePct > 30) Text(stringResource(R.string.conc_warn, s.topSymbol, s.topSharePct.toInt()), color = MaterialTheme.colorScheme.error)
    }
}
