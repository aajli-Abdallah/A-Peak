package com.aajli.apeak.ui

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
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.StockAnalytics

/** Update the price of all held stocks in one screen. Oldest prices first. */
@Composable
fun UpdatePricesScreen(vm: StocksViewModel, onBack: () -> Unit) {
    val stocks by vm.stocks.collectAsState()
    val trades by vm.trades.collectAsState()
    val brokers by vm.brokers.collectAsState()
    val cur by vm.settings.currency.collectAsState()
    val inputs = remember { mutableStateMapOf<String, String>() }
    val held = stocks.filter { s -> s.lastPriceMinor == 0L || Calc.position(trades.filter { it.stockId == s.id }).qty > 0 }.sortedBy { it.lastPriceDate }
    val changes = held.mapNotNull { s ->
        val p = Money.parse(inputs[s.id] ?: "")
        if (p != null && p != s.lastPriceMinor) s to p else null
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ico.Back, onBack)
            Text(stringResource(R.string.update_prices), style = MaterialTheme.typography.titleLarge)
        }
        if (held.isEmpty()) Text(stringResource(R.string.prices_empty))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            held.forEach { s ->
                val age = StockAnalytics.ageDays(s.lastPriceDate)
                val old = s.lastPriceMinor == 0L || age >= 7
                val badge = when {
                    s.lastPriceMinor == 0L -> stringResource(R.string.price_none)
                    age <= 0 -> stringResource(R.string.price_today)
                    else -> stringResource(R.string.price_age, age)
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.symbol, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(s.name, brokers.find { it.id == s.accountId }?.name ?: "").filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(badge, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                                color = if (old) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.lastPriceMinor > 0) Text(stringResource(R.string.prices_last, Money.format(s.lastPriceMinor, cur)), style = MaterialTheme.typography.bodySmall)
                        AmountField(stringResource(R.string.prices_new, cur), inputs[s.id] ?: "") { inputs[s.id] = it }
                        val p = Money.parse(inputs[s.id] ?: "")
                        if (p != null && s.lastPriceMinor > 0 && p != s.lastPriceMinor) {
                            val d = p - s.lastPriceMinor
                            Text("%+.1f%%".format(d * 100.0 / s.lastPriceMinor), color = gainColor(d), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                changes.forEach { (s, p) -> vm.saveStock(s.copy(lastPriceMinor = p, lastPriceDate = System.currentTimeMillis())) }
                onBack()
            },
            enabled = changes.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(stringResource(R.string.prices_save, changes.size)) }
        Spacer(Modifier.height(4.dp))
    }
}
