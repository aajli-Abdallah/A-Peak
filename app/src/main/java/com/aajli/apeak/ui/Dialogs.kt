package com.aajli.apeak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.*
import com.aajli.apeak.util.Money
import com.aajli.apeak.util.newId
import com.aajli.apeak.util.Calc
import com.aajli.apeak.util.Splits
import com.aajli.apeak.util.DividendStats

@Composable
private fun ErrLine(show: Boolean) { if (show) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error) }

@Composable
fun BankAccountDialog(vm: BanksViewModel, existing: BankAccountEntity?, cur: String, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var bank by remember { mutableStateOf(existing?.bankName ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: BankKind.CURRENT) }
    var rate by remember { mutableStateOf(existing?.let { if (it.interestRate == 0.0) "" else it.interestRate.toString() } ?: "") }
    var opening by remember { mutableStateOf(existing?.let { Money.toInput(it.openingMinor) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }
    FormDialog(stringResource(if (existing == null) R.string.new_bank else R.string.edit_bank), onDismiss, onSave = {
        val open = Money.parseOpt(opening)
        val r = if (rate.isBlank()) 0.0 else Money.parseRate(rate)
        if (name.isBlank() || open == null || r == null) err = true
        else {
            vm.saveBank(BankAccountEntity(existing?.id ?: newId(), name.trim(), bank.trim(), kind,
                if (kind == BankKind.SAVINGS) r else 0.0, open, note.trim(), existing?.createdAt ?: System.currentTimeMillis()))
            onDismiss()
        }
    }) {
        TextF(stringResource(R.string.account_name), name) { name = it }
        TextF(stringResource(R.string.bank_name), bank) { bank = it }
        ToggleRow(listOf(stringResource(R.string.kind_current), stringResource(R.string.kind_savings)),
            if (kind == BankKind.SAVINGS) 1 else 0) { kind = if (it == 1) BankKind.SAVINGS else BankKind.CURRENT }
        if (kind == BankKind.SAVINGS) AmountField(stringResource(R.string.interest_rate), rate) { rate = it }
        AmountField(stringResource(R.string.opening_balance, cur), opening) { opening = it }
        TextF(stringResource(R.string.note), note) { note = it }
        ErrLine(err)
    }
}

@Composable
fun BankEntryDialog(vm: BanksViewModel, accountId: String, existing: BankEntryEntity?, cur: String, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: BankType.DEPOSIT) }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.amountMinor) } ?: "") }
    var date by remember { mutableLongStateOf(existing?.dateMillis ?: todayStart()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val types = listOf(BankType.DEPOSIT, BankType.WITHDRAW, BankType.INTEREST)
    FormDialog(stringResource(if (existing == null) R.string.new_entry else R.string.edit_entry), onDismiss, onSave = {
        val m = Money.parse(amount)
        if (m == null) err = true
        else { vm.saveBankEntry(BankEntryEntity(existing?.id ?: newId(), accountId, type, m, date, note.trim())); onDismiss() }
    }, onDelete = if (existing != null) ({ confirm = true }) else null) {
        ToggleRow(listOf(stringResource(R.string.entry_deposit), stringResource(R.string.entry_withdraw), stringResource(R.string.entry_interest)),
            types.indexOf(type)) { type = types[it] }
        AmountField(stringResource(R.string.amount_cur, cur), amount) { amount = it }
        DateButton(stringResource(R.string.date), date) { date = it }
        TextF(stringResource(R.string.note), note) { note = it }
        ErrLine(err)
    }
    if (confirm && existing != null) ConfirmDialog(stringResource(R.string.confirm_entry),
        { vm.deleteBankEntry(existing.id); onDismiss() }, { confirm = false })
}

@Composable
fun BrokerDialog(vm: StocksViewModel, existing: BrokerAccountEntity?, onSaved: (String) -> Unit = {}, onDismiss: () -> Unit) {
    val lookups by vm.lookups.collectAsState()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var broker by remember { mutableStateOf(existing?.brokerName ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }
    var addKind by remember { mutableStateOf<String?>(null) }
    val accountNames = lookups.filter { it.kind == LookupKind.ACCOUNT }.map { it.name }
    val brokerNames = lookups.filter { it.kind == LookupKind.BROKER }.map { it.name }
    val brokerInfo = lookups.find { it.kind == LookupKind.BROKER && it.name.equals(broker, true) }
        ?.let { listOf(it.website, it.note).filter { s -> s.isNotBlank() }.joinToString(" · ") } ?: ""
    FormDialog(stringResource(if (existing == null) R.string.new_broker else R.string.edit_broker), onDismiss, onSave = {
        if (name.isBlank() || broker.isBlank()) err = true
        else {
            val id = existing?.id ?: newId()
            vm.saveBroker(BrokerAccountEntity(id, name.trim(), broker.trim(), note.trim(), existing?.createdAt ?: System.currentTimeMillis()))
            onSaved(id)
            onDismiss()
        }
    }) {
        PickOrAdd(stringResource(R.string.account_name), name, accountNames, stringResource(R.string.add_new_item), { name = accountNames[it] }, { addKind = LookupKind.ACCOUNT })
        PickOrAdd(stringResource(R.string.broker_company), broker, brokerNames, stringResource(R.string.add_new_item), { broker = brokerNames[it] }, { addKind = LookupKind.BROKER })
        if (brokerInfo.isNotBlank()) Text(brokerInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextF(stringResource(R.string.note), note) { note = it }
        ErrLine(err)
    }
    addKind?.let { k ->
        val isAccount = k == LookupKind.ACCOUNT
        AddNameDialog(stringResource(if (isAccount) R.string.add_account_name else R.string.add_broker_name),
            stringResource(if (isAccount) R.string.account_name else R.string.broker_company), onDismiss = { addKind = null }) { n ->
            vm.addLookup(k, n)
            if (isAccount) name = n else broker = n
            addKind = null
        }
    }
}

@Composable
fun StockDialog(vm: StocksViewModel, accountId: String?, existing: StockEntity?, cur: String, onDismiss: () -> Unit) {
    val brokers by vm.brokers.collectAsState()
    val allStocks by vm.stocks.collectAsState()
    val secs by vm.securities.collectAsState()
    val lookups by vm.lookups.collectAsState()
    var accId by remember { mutableStateOf(accountId ?: existing?.accountId) }
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.let { if (it.lastPriceMinor > 0) Money.toInput(it.lastPriceMinor) else "" } ?: "") }
    var sector by remember { mutableStateOf(existing?.sector ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var target by remember { mutableStateOf(existing?.let { if (it.targetPriceMinor > 0) Money.toInput(it.targetPriceMinor) else "" } ?: "") }
    var stop by remember { mutableStateOf(existing?.let { if (it.stopPriceMinor > 0) Money.toInput(it.stopPriceMinor) else "" } ?: "") }
    var err by remember { mutableStateOf(false) }
    var exists by remember { mutableStateOf(false) }
    var addAccount by remember { mutableStateOf(false) }
    var addSecurity by remember { mutableStateOf(false) }
    var addSector by remember { mutableStateOf(false) }
    val accLabels = brokers.map { "${it.name} · ${it.brokerName}" }
    val chosen = brokers.find { it.id == accId }
    val sectors = lookups.filter { it.kind == LookupKind.SECTOR }.map { it.name }
    FormDialog(stringResource(if (existing == null) R.string.new_stock else R.string.edit_stock), onDismiss, onSave = {
        val p = Money.parseOpt(price); val tp = Money.parseOpt(target); val sp = Money.parseOpt(stop)
        val acc = accId
        exists = false
        if (acc == null || symbol.isBlank() || p == null || tp == null || sp == null) err = true
        else if (existing == null && allStocks.any { it.accountId == acc && it.symbol.equals(symbol.trim(), true) }) exists = true
        else {
            val changed = existing == null || p != existing.lastPriceMinor
            vm.saveStock(StockEntity(existing?.id ?: newId(), acc, symbol.trim().uppercase(), name.trim(), p,
                if (changed) System.currentTimeMillis() else existing!!.lastPriceDate, existing?.createdAt ?: System.currentTimeMillis(),
                sector.trim(), note.trim(), tp, sp))
            onDismiss()
        }
    }) {
        if (accountId == null && existing == null)
            PickOrAdd(stringResource(R.string.account), chosen?.let { "${it.name} · ${it.brokerName}" } ?: "", accLabels,
                stringResource(R.string.add_new_account), { accId = brokers[it].id }, { addAccount = true })
        PickOrAdd(stringResource(R.string.pick_stock), if (symbol.isBlank()) "" else "$symbol — $name", secs.map { "${it.symbol} — ${it.name}" },
            stringResource(R.string.add_new_stock), { symbol = secs[it].symbol; name = secs[it].name; if (sector.isBlank()) sector = secs[it].sector }, { addSecurity = true })
        AmountField(stringResource(R.string.last_price_cur, cur), price) { price = it }
        SectorPicker(sector, sectors, { sector = it }, { addSector = true })
        TextF(stringResource(R.string.note), note) { note = it }
        AmountField(stringResource(R.string.target_price_cur, cur), target) { target = it }
        AmountField(stringResource(R.string.stop_price_cur, cur), stop) { stop = it }
        if (exists) Text(stringResource(R.string.stock_exists), color = MaterialTheme.colorScheme.error)
        ErrLine(err)
    }
    if (addAccount) BrokerDialog(vm, null, onSaved = { accId = it }) { addAccount = false }
    if (addSecurity) SecurityDialog(vm, null, onSaved = { s -> symbol = s.symbol; name = s.name; if (sector.isBlank()) sector = s.sector }) { addSecurity = false }
    if (addSector) AddNameDialog(stringResource(R.string.add_sector_name), stringResource(R.string.sector), onDismiss = { addSector = false }) { n ->
        vm.addLookup(LookupKind.SECTOR, n)
        sector = n
        addSector = false
    }
}

@Composable
fun TradeDialog(vm: StocksViewModel, stock: StockEntity, existing: StockTradeEntity?, preset: String, heldQty: Double,
                cur: String, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: preset) }
    var qty by remember { mutableStateOf(existing?.let { Money.qtyInput(it.quantity) } ?: "") }
    var price by remember {
        mutableStateOf(existing?.let { Money.toInput(it.priceMinor) } ?: (if (stock.lastPriceMinor > 0) Money.toInput(stock.lastPriceMinor) else ""))
    }
    var fees by remember { mutableStateOf(existing?.let { if (it.feesMinor > 0) Money.toInput(it.feesMinor) else "" } ?: "") }
    var date by remember { mutableLongStateOf(existing?.dateMillis ?: todayStart()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val rawAll by vm.rawTrades.collectAsState()
    val splitAll by vm.splits.collectAsState()
    val held = Splits.heldAt(rawAll.filter { it.stockId == stock.id && it.id != existing?.id }, splitAll.filter { it.stockId == stock.id }, date)
    FormDialog(stringResource(R.string.trade_title, stock.symbol), onDismiss, onSave = {
        val q = Money.parseQty(qty); val p = Money.parse(price); val f = Money.parseOpt(fees)
        if (q == null || p == null || f == null || (type == TradeType.SELL && q > held + 1e-9)) err = true
        else { vm.saveTrade(StockTradeEntity(existing?.id ?: newId(), stock.id, type, q, p, f, date, note.trim())); onDismiss() }
    }, onDelete = if (existing != null) ({ confirm = true }) else null) {
        ToggleRow(listOf(stringResource(R.string.entry_buy), stringResource(R.string.entry_sell)),
            if (type == TradeType.BUY) 0 else 1) { type = if (it == 0) TradeType.BUY else TradeType.SELL }
        if (type == TradeType.SELL) Text(stringResource(R.string.shares_on_date, Money.qtyInput(held)), style = MaterialTheme.typography.bodySmall)
        AmountField(stringResource(R.string.quantity), qty) { qty = it }
        AmountField(stringResource(R.string.price_per_share, cur), price) { price = it }
        AmountField(stringResource(R.string.fees, cur), fees) { fees = it }
        DateButton(stringResource(R.string.date), date) { date = it }
        TextF(stringResource(R.string.note), note) { note = it }
        ErrLine(err)
    }
    if (confirm && existing != null) ConfirmDialog(stringResource(R.string.confirm_entry),
        { vm.deleteTrade(existing.id); onDismiss() }, { confirm = false })
}

@Composable
fun DividendDialog(vm: StocksViewModel, stock: StockEntity, existing: DividendEntity?, cur: String, onDismiss: () -> Unit) {
    val trades by vm.rawTrades.collectAsState()
    val splitList by vm.splits.collectAsState()
    var perShare by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.amountMinor) } ?: "") }
    var date by remember { mutableLongStateOf(existing?.dateMillis ?: todayStart()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var auto by remember { mutableStateOf(existing == null) }
    var err by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun heldAt(d: Long) = Splits.heldAt(trades.filter { it.stockId == stock.id }, splitList.filter { it.stockId == stock.id }, d)
    fun fill(ps: String, d: Long) {
        val v = Money.parseQty(ps)
        val q = heldAt(d)
        if (auto && v != null && q > 0) amount = Money.toInput(DividendStats.perShareTotal(q, v))
    }
    FormDialog(stringResource(R.string.dividend_title, stock.symbol), onDismiss, onSave = {
        val m = Money.parse(amount)
        if (m == null) err = true
        else { vm.saveDividend(DividendEntity(existing?.id ?: newId(), stock.id, m, date, note.trim())); onDismiss() }
    }, onDelete = if (existing != null) ({ confirm = true }) else null) {
        Text(stringResource(R.string.shares_on_date, Money.qtyInput(heldAt(date))), style = MaterialTheme.typography.bodySmall)
        AmountField(stringResource(R.string.dividend_per_share, cur), perShare) { perShare = it; fill(it, date) }
        AmountField(stringResource(R.string.dividend_amount, cur), amount) { amount = it; auto = false }
        DateButton(stringResource(R.string.date), date) { date = it; fill(perShare, it) }
        TextF(stringResource(R.string.note), note) { note = it }
        ErrLine(err)
    }
    if (confirm && existing != null) ConfirmDialog(stringResource(R.string.confirm_entry),
        { vm.deleteDividend(existing.id); onDismiss() }, { confirm = false })
}
