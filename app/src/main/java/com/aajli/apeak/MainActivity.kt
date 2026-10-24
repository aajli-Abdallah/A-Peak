package com.aajli.apeak

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aajli.apeak.data.TransactionEntity
import com.aajli.apeak.ui.*
import java.time.YearMonth

sealed interface Screen {
    data class Tab(val i: Int) : Screen
    data class Form(val tx: TransactionEntity?, val date: Long? = null) : Screen
    data object Settings : Screen
    data class Manage(val tab: Int = 0) : Screen
    data class BankDetail(val id: String) : Screen
    data class BrokerDetail(val id: String) : Screen
    data class StockDetail(val id: String) : Screen
    data class Year(val year: Int) : Screen
    data class Month(val year: Int, val month: Int) : Screen
    data object Budgets : Screen
    data object Prices : Screen
    data object Dividends : Screen
    data object Gains : Screen
    data class Lists(val tab: Int = 0) : Screen
}

/** New transactions from a month page start in that month (today if it is the current month). */
private fun defaultDate(ym: YearMonth): Long = if (ym == YearMonth.now()) todayStart() else dayStart(ym.atDay(1))

class MainActivity : ComponentActivity() {
    private val expensesVm: ExpensesViewModel by viewModels()
    private val banksVm: BanksViewModel by viewModels()
    private val stocksVm: StocksViewModel by viewModels()
    private val backupVm: BackupViewModel by viewModels()
    private val overviewVm: OverviewViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by expensesVm.settings.theme.collectAsState()
            val dynamic by expensesVm.settings.dynamic.collectAsState()
            APeakTheme(theme, dynamic) { AppRoot(expensesVm, banksVm, stocksVm, backupVm, overviewVm) }
        }
    if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) i.data?.let { backupVm.prepareImportFromUri(it) }
    }
}

@Composable
fun AppRoot(expensesVm: ExpensesViewModel, banksVm: BanksViewModel, stocksVm: StocksViewModel, backupVm: BackupViewModel, overviewVm: OverviewViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Tab(0)) }
    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { pop() }
    ImportHost(backupVm)
    BudgetAlertHost(expensesVm)
    PriceAlertHost(stocksVm)
    val tabs = listOf(Ico.Overview to R.string.nav_overview, Ico.Bank to R.string.nav_banks, Ico.Stocks to R.string.nav_stocks, Ico.Calendar to R.string.nav_monthly)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = stack.last()) {
            is Screen.Tab -> Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, (icon, label) ->
                            NavigationBarItem(
                                selected = s.i == i,
                                onClick = { stack.clear(); stack.add(Screen.Tab(i)) },
                                icon = { AppIcon(icon) },
                                label = { Text(stringResource(label)) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(Modifier.padding(pad)) {
                    when (s.i) {
                        0 -> OverviewScreen(overviewVm, backupVm) { push(Screen.Settings) }
                        1 -> BanksScreen(banksVm) { push(Screen.BankDetail(it)) }
                        2 -> BrokersScreen(stocksVm, { push(Screen.BrokerDetail(it)) }, { push(Screen.StockDetail(it)) }, { push(Screen.Prices) }, { push(Screen.Dividends) }, { push(Screen.Gains) }, { push(Screen.Lists()) })
                        else -> MonthlyYearsScreen(expensesVm, { push(Screen.Year(it)) }, { push(Screen.Form(null)) }, { push(Screen.Manage(1)) }, { push(Screen.Budgets) }) { push(Screen.Settings) }
                    }
                }
            }
            is Screen.Form -> TransactionForm(expensesVm, s.tx, s.date) { pop() }
            Screen.Settings -> SettingsScreen(backupVm, { pop() }, { push(Screen.Manage()) })
            is Screen.Manage -> ManageScreen(expensesVm, s.tab) { pop() }
            is Screen.BankDetail -> BankDetail(banksVm, s.id) { pop() }
            is Screen.BrokerDetail -> BrokerDetail(stocksVm, s.id, { pop() }, { push(Screen.StockDetail(it)) })
            is Screen.StockDetail -> StockDetail(stocksVm, s.id) { pop() }
            Screen.Budgets -> BudgetsScreen(expensesVm) { pop() }
            Screen.Prices -> UpdatePricesScreen(stocksVm) { pop() }
            Screen.Dividends -> DividendsScreen(stocksVm, { pop() }) { push(Screen.StockDetail(it)) }
            Screen.Gains -> RealizedGainsScreen(stocksVm, { pop() }) { push(Screen.StockDetail(it)) }
            is Screen.Lists -> ListsScreen(stocksVm, s.tab) { pop() }
            is Screen.Year -> YearScreen(expensesVm, s.year, { pop() }, { push(Screen.Manage(1)) }) { push(Screen.Month(s.year, it)) }
            is Screen.Month -> {
                remember(s) { expensesVm.month.value = YearMonth.of(s.year, s.month) }
                HomeScreen(expensesVm, { push(Screen.Form(null, defaultDate(expensesVm.month.value))) }, { push(Screen.Form(it)) }, { push(Screen.Settings) }, { pop() }, { push(Screen.Budgets) })
            }
        }
    }
}
