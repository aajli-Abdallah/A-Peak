package com.aajli.apeak.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import kotlinx.coroutines.launch

private suspend fun shareBackup(ctx: Context, vm: BackupViewModel, password: CharArray?) {
    val uri = vm.backups.shareUri(password)
    password?.fill('\u0000')
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null))
    vm.settings.markShared()
}

private fun tableLabel(key: String) = when (key) {
    "accounts" -> R.string.tbl_accounts
    "categories" -> R.string.tbl_categories
    "transactions" -> R.string.tbl_transactions
    "bank_accounts" -> R.string.tbl_bank_accounts
    "bank_entries" -> R.string.tbl_bank_entries
    "broker_accounts" -> R.string.tbl_brokers
    "stocks" -> R.string.tbl_stocks
    "budgets" -> R.string.tbl_budgets
    "prices" -> R.string.tbl_prices
    "splits" -> R.string.tbl_splits
    "lookups" -> R.string.tbl_lookups
    "securities" -> R.string.tbl_securities
    "trades" -> R.string.tbl_trades
    else -> R.string.tbl_dividends
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Asks for a password and shares an encrypted backup. Sharing without a password is possible but not recommended. */
@Composable
fun ExportDialog(vm: BackupViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ok = pw.length >= 8 && pw == pw2
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.export_protect_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_protect_hint), style = MaterialTheme.typography.bodySmall)
                PasswordField(stringResource(R.string.password), pw) { pw = it }
                PasswordField(stringResource(R.string.password_repeat), pw2) { pw2 = it }
                if (pw.isNotEmpty() && pw.length < 8) Text(stringResource(R.string.password_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else if (pw2.isNotEmpty() && pw != pw2) Text(stringResource(R.string.password_mismatch), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.export_lost_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = ok && !busy, onClick = { scope.launch { busy = true; shareBackup(ctx, vm, pw.toCharArray()); onDismiss() } }) {
                Text(stringResource(R.string.export_encrypt_share))
            }
        },
        dismissButton = {
            Row {
                TextButton(enabled = !busy, onClick = { scope.launch { busy = true; shareBackup(ctx, vm, null); onDismiss() } }) {
                    Text(stringResource(R.string.export_plain), color = MaterialTheme.colorScheme.error)
                }
                TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** Settings card: share, import, automatic backups. */
@Composable
fun BackupSection(vm: BackupViewModel) {
    val last by vm.settings.lastShared.collectAsState()
    val files = remember { vm.backups.autoFiles() }
    var showExport by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.prepareImportFromUri(uri) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_hint), style = MaterialTheme.typography.bodySmall)
            Text(if (last == 0L) stringResource(R.string.backup_last_never) else stringResource(R.string.backup_last, fmtDate(last)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { showExport = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.backup_share))
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_import))
            }
            if (files.isNotEmpty()) {
                Text(stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.labelLarge)
                files.forEach { f ->
                    TextButton(onClick = { vm.prepareImportFromFile(f) }, modifier = Modifier.fillMaxWidth()) {
                        Text(f.name.removePrefix("apeak-auto-").removeSuffix(".json"))
                    }
                }
            }
        }
    }
    if (showExport) ExportDialog(vm) { showExport = false }
}

/** Banner on the Overview when no backup was shared for 14 days (or ever). */
@Composable
fun BackupReminder(vm: BackupViewModel, hasData: Boolean) {
    val last by vm.settings.lastShared.collectAsState()
    val after by vm.settings.remindAfter.collectAsState()
    var showExport by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val days = if (last == 0L) null else (now - last) / 86_400_000L
    val due = hasData && now >= after && (days == null || days >= 14)
    if (due) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (days == null) stringResource(R.string.reminder_never) else stringResource(R.string.reminder_days, days.toInt()))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showExport = true }) { Text(stringResource(R.string.reminder_share)) }
                TextButton(onClick = { vm.settings.snooze() }) { Text(stringResource(R.string.reminder_later)) }
            }
        }
    }
    if (showExport) ExportDialog(vm) { showExport = false }
}

/** Import dialogs: password, preview, errors. Lives at the app root so a file opened from WhatsApp works from any screen. */
@Composable
fun ImportHost(vm: BackupViewModel) {
    val preview by vm.importPreview.collectAsState()
    val error by vm.importError.collectAsState()
    val pending by vm.passwordRequest.collectAsState()
    val pwError by vm.passwordError.collectAsState()
    var confirmReplace by remember { mutableStateOf(false) }
    var pw by remember(pending) { mutableStateOf("") }
    pending?.let {
        AlertDialog(
            onDismissRequest = { vm.cancelPassword() },
            title = { Text(stringResource(R.string.import_password_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.import_password_hint), style = MaterialTheme.typography.bodySmall)
                    PasswordField(stringResource(R.string.password), pw) { pw = it }
                    if (pwError) Text(stringResource(R.string.wrong_password), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { vm.submitPassword(pw.toCharArray()) }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { vm.cancelPassword() }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    error?.let { res ->
        AlertDialog(
            onDismissRequest = { vm.importError.value = null },
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(res)) },
            confirmButton = { TextButton(onClick = { vm.importError.value = null }) { Text(stringResource(R.string.ok)) } }
        )
    }
    preview?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text(stringResource(R.string.import_preview)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.import_from, p.data.appVersion, p.data.recordCount), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(if (p.encrypted) R.string.import_encrypted_note else R.string.import_plain_note), style = MaterialTheme.typography.bodySmall,
                        color = if (p.encrypted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    p.merge.report.filter { it.new + it.updated + it.unchanged > 0 }.forEach { r ->
                        KeyVal(stringResource(tableLabel(r.key)), stringResource(R.string.import_counts, r.new, r.updated, r.unchanged))
                    }
                    Text(stringResource(R.string.import_replace_note, p.currentCount, p.data.recordCount), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport(false) }) { Text(stringResource(R.string.import_merge)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmReplace = true }) { Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { vm.cancelImport() }) { Text(stringResource(R.string.cancel)) }
                }
            }
        )
        if (confirmReplace) AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.import_replace)) },
            text = { Text(stringResource(R.string.import_replace_confirm)) },
            confirmButton = { TextButton(onClick = { confirmReplace = false; vm.confirmImport(true) }) { Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
