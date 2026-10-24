package com.aajli.apeak.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.*
import com.aajli.apeak.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TransactionForm(vm: ExpensesViewModel, existing: TransactionEntity?, defaultDate: Long?, onClose: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val categories by vm.categories.collectAsState()
    val currency by vm.settings.currency.collectAsState()
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(existing?.type ?: Tx.EXPENSE) }
    var amount by remember { mutableStateOf(existing?.let { Money.toInput(it.amountMinor) } ?: "") }
    var date by remember { mutableLongStateOf(existing?.dateMillis ?: defaultDate ?: todayStart()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var catId by remember { mutableStateOf(existing?.categoryId) }
    var accId by remember { mutableStateOf(existing?.accountId) }
    var showError by remember { mutableStateOf(false) }

    // Keep the saved snapshot selectable even if the category/account was deleted later.
    val catOptions = remember(categories, type, existing) {
        val l = categories.filter { it.type == type }
        if (existing != null && existing.type == type && l.none { it.id == existing.categoryId })
            l + CategoryEntity(existing.categoryId, existing.categoryName, existing.categoryIcon, type, 0) else l
    }
    val accOptions = remember(accounts, existing) {
        if (existing != null && accounts.none { it.id == existing.accountId })
            accounts + AccountEntity(existing.accountId, existing.accountName, existing.accountIcon, 0) else accounts
    }
    val cat = catOptions.find { it.id == catId } ?: catOptions.firstOrNull()
    val acc = accOptions.find { it.id == accId } ?: accOptions.firstOrNull()
    val d = toDate(date)
    val fmt = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val minor = Money.parse(amount)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(stringResource(if (existing == null) R.string.new_tx else R.string.edit_tx), onClose)
        ToggleRow(listOf(stringResource(R.string.home_expense), stringResource(R.string.home_income)),
            if (type == Tx.EXPENSE) 0 else 1) { type = if (it == 0) Tx.EXPENSE else Tx.INCOME }
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
            label = { Text(stringResource(R.string.amount)) },
            suffix = { Text(currency) },
            singleLine = true,
            isError = showError && minor == null,
            supportingText = { if (showError && minor == null) Text(stringResource(R.string.invalid_amount)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        if (cat == null) Text(stringResource(R.string.no_category), color = MaterialTheme.colorScheme.error)
        else PickerField(stringResource(R.string.category), "${cat.icon} ${cat.name}", catOptions.map { "${it.icon} ${it.name}" }) { catId = catOptions[it].id }
        if (acc == null) Text(stringResource(R.string.no_account), color = MaterialTheme.colorScheme.error)
        else PickerField(stringResource(R.string.account), "${acc.icon} ${acc.name}", accOptions.map { "${it.icon} ${it.name}" }) { accId = accOptions[it].id }
        OutlinedButton(
            onClick = {
                DatePickerDialog(ctx, { _, y, m, day -> date = dayStart(LocalDate.of(y, m + 1, day)) },
                    d.year, d.monthValue - 1, d.dayOfMonth).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("${stringResource(R.string.date)}: ${d.format(fmt)}") }
        OutlinedTextField(
            value = note, onValueChange = { note = it.take(100) },
            label = { Text(stringResource(R.string.note)) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (minor == null || cat == null || acc == null) showError = true
                else { vm.saveTransaction(existing, type, minor, date, note.trim(), acc, cat); onClose() }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(stringResource(R.string.save)) }
        if (existing != null) {
            TextButton(onClick = { vm.deleteTransaction(existing); onClose() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
