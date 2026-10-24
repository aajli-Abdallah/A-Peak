package com.aajli.apeak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aajli.apeak.StocksViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.LookupItemEntity
import com.aajli.apeak.data.LookupKind
import com.aajli.apeak.data.SecurityEntity
import com.aajli.apeak.util.newId

/** A picker with an extra last entry to add a new item. */
@Composable
fun PickOrAdd(label: String, value: String, options: List<String>, addLabel: String, onPick: (Int) -> Unit, onAdd: () -> Unit) {
    PickerField(label, value.ifBlank { stringResource(R.string.pick_hint) }, options + addLabel) { i -> if (i >= options.size) onAdd() else onPick(i) }
}

/** Sector picker: a "no sector" entry, the listed sectors and "add new". */
@Composable
fun SectorPicker(value: String, sectors: List<String>, onChange: (String) -> Unit, onAdd: () -> Unit) {
    val none = stringResource(R.string.sector_none)
    PickOrAdd(stringResource(R.string.sector), value, listOf(none) + sectors, stringResource(R.string.add_new_item),
        { onChange(if (it == 0) "" else sectors[it - 1]) }, onAdd)
}

/** Quick add of a name (inline from a picker). Details can be added later in the Lists screen. */
@Composable
fun AddNameDialog(title: String, label: String, initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var err by remember { mutableStateOf(false) }
    FormDialog(title, onDismiss, onSave = { if (text.isBlank()) err = true else onSave(text.trim()) }) {
        TextF(label, text) { text = it }
        if (err) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun UpdateExistingRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(stringResource(R.string.update_existing), style = MaterialTheme.typography.bodySmall)
    }
}

/** Add or edit a broker, an account name or a sector together with its own details. */
@Composable
fun LookupDialog(vm: StocksViewModel, kind: String, existing: LookupItemEntity?, onDismiss: () -> Unit) {
    val all by vm.lookups.collectAsState()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var website by remember { mutableStateOf(existing?.website ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var propagate by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf(false) }
    val (titleRes, labelRes) = when (kind) {
        LookupKind.BROKER -> R.string.add_broker_name to R.string.broker_company
        LookupKind.ACCOUNT -> R.string.add_account_name to R.string.account_name
        else -> R.string.add_sector_name to R.string.sector
    }
    FormDialog(stringResource(titleRes), onDismiss, onSave = {
        val n = name.trim()
        val dup = all.any { it.kind == kind && it.name.equals(n, true) && it.id != existing?.id }
        if (n.isEmpty() || dup) err = true
        else {
            vm.saveLookup(LookupItemEntity(existing?.id ?: newId(), kind, n, website.trim(), note.trim()), existing?.name, propagate)
            onDismiss()
        }
    }) {
        TextF(stringResource(labelRes), name) { name = it }
        if (kind == LookupKind.BROKER) TextF(stringResource(R.string.lookup_website), website) { website = it }
        if (kind != LookupKind.SECTOR) TextF(stringResource(R.string.lookup_note), note) { note = it }
        if (existing != null) UpdateExistingRow(propagate) { propagate = it }
        if (err) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error)
    }
}

/** Add or edit a stock of the stock list with its details. onSaved gets the saved item (an existing one if the symbol is already listed). */
@Composable
fun SecurityDialog(vm: StocksViewModel, existing: SecurityEntity?, onSaved: (SecurityEntity) -> Unit = {}, onDismiss: () -> Unit) {
    val secs by vm.securities.collectAsState()
    val lookups by vm.lookups.collectAsState()
    var symbol by remember { mutableStateOf(existing?.symbol ?: "") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sector by remember { mutableStateOf(existing?.sector ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var propagate by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf(false) }
    var addSector by remember { mutableStateOf(false) }
    val sectors = lookups.filter { it.kind == LookupKind.SECTOR }.map { it.name }
    FormDialog(stringResource(if (existing == null) R.string.new_security else R.string.edit_security), onDismiss, onSave = {
        val sym = symbol.trim().uppercase()
        if (sym.isEmpty()) err = true
        else {
            val dup = secs.find { it.symbol.equals(sym, true) && it.id != existing?.id }
            val item = if (dup != null) dup.copy(name = name.trim().ifBlank { dup.name }, sector = sector.trim().ifBlank { dup.sector }, note = note.trim().ifBlank { dup.note })
                else SecurityEntity(existing?.id ?: newId(), sym, name.trim(), sector.trim(), note.trim())
            vm.saveSecurity(item, existing, propagate)
            onSaved(item)
            onDismiss()
        }
    }) {
        TextF(stringResource(R.string.symbol), symbol) { symbol = it }
        TextF(stringResource(R.string.stock_name), name) { name = it }
        SectorPicker(sector, sectors, { sector = it }, { addSector = true })
        TextF(stringResource(R.string.security_note), note) { note = it }
        if (existing != null) UpdateExistingRow(propagate) { propagate = it }
        if (err) Text(stringResource(R.string.check_values), color = MaterialTheme.colorScheme.error)
    }
    if (addSector) AddNameDialog(stringResource(R.string.add_sector_name), stringResource(R.string.sector), onDismiss = { addSector = false }) { n ->
        vm.addLookup(LookupKind.SECTOR, n)
        sector = n
        addSector = false
    }
}

/** Manage brokers, account names, stocks and sectors. Each keeps its own details, entered once and reused by the pickers. */
@Composable
fun ListsScreen(vm: StocksViewModel, startTab: Int, onBack: () -> Unit) {
    val lookups by vm.lookups.collectAsState()
    val secs by vm.securities.collectAsState()
    var tab by remember { mutableIntStateOf(startTab) }
    var newLookup by remember { mutableStateOf(false) }
    var editingLookup by remember { mutableStateOf<LookupItemEntity?>(null) }
    var newSec by remember { mutableStateOf(false) }
    var editingSec by remember { mutableStateOf<SecurityEntity?>(null) }
    val kind = when (tab) { 0 -> LookupKind.BROKER; 1 -> LookupKind.ACCOUNT; else -> LookupKind.SECTOR }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(stringResource(R.string.lists_title), onBack)
        ToggleRow(listOf(stringResource(R.string.list_brokers), stringResource(R.string.list_accounts), stringResource(R.string.list_stocks), stringResource(R.string.list_sectors)), tab) { tab = it }
        Text(stringResource(R.string.lists_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            if (tab == 2) items(secs, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().clickable { editingSec = s }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.symbol, fontWeight = FontWeight.SemiBold)
                        val info = listOf(s.name, s.sector, s.note).filter { it.isNotBlank() }.joinToString(" · ")
                        if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconBtn(Ico.Close, { vm.deleteSecurity(s.id) }, MaterialTheme.colorScheme.error)
                }
            } else items(lookups.filter { it.kind == kind }, key = { it.id }) { l ->
                Row(Modifier.fillMaxWidth().clickable { editingLookup = l }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, style = MaterialTheme.typography.bodyLarge)
                        val info = listOf(l.website, l.note).filter { it.isNotBlank() }.joinToString(" · ")
                        if (info.isNotBlank()) Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconBtn(Ico.Close, { vm.deleteLookup(l.id) }, MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(onClick = { if (tab == 2) newSec = true else newLookup = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.list_add))
        }
        Spacer(Modifier.height(8.dp))
    }
    if (newLookup) LookupDialog(vm, kind, null) { newLookup = false }
    editingLookup?.let { l -> LookupDialog(vm, l.kind, l) { editingLookup = null } }
    if (newSec) SecurityDialog(vm, null) { newSec = false }
    editingSec?.let { s -> SecurityDialog(vm, s) { editingSec = null } }
}
