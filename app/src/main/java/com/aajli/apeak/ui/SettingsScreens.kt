package com.aajli.apeak.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aajli.apeak.BuildConfig
import com.aajli.apeak.BackupViewModel
import com.aajli.apeak.BanksViewModel
import com.aajli.apeak.ExpensesViewModel
import com.aajli.apeak.OverviewViewModel
import com.aajli.apeak.R
import com.aajli.apeak.data.Tx
import com.aajli.apeak.util.CURRENCIES

private fun launch(ctx: Context, i: Intent) { try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) { } }

@Composable
fun SettingsScreen(vm: BackupViewModel, onBack: () -> Unit, onManage: () -> Unit) {
    val ctx = LocalContext.current
    val currency by vm.settings.currency.collectAsState()
    val theme by vm.settings.theme.collectAsState()
    val dynamic by vm.settings.dynamic.collectAsState()
    val currencies = remember(currency) { if (currency in CURRENCIES) CURRENCIES else CURRENCIES + currency }
    val themes = listOf("SYSTEM", "LIGHT", "DARK")
    val email = stringResource(R.string.contact_email)
    val github = stringResource(R.string.contact_github)
    val site = stringResource(R.string.contact_website)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        PickerField(stringResource(R.string.currency), currency, currencies) { vm.settings.setCurrency(currencies[it]) }
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
        ToggleRow(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
            themes.indexOf(theme).coerceAtLeast(0)) { vm.settings.setTheme(themes[it]) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.dynamic_colors), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.dynamic_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = dynamic, onCheckedChange = { vm.settings.setDynamic(it) }, enabled = Build.VERSION.SDK_INT >= 31)
        }
        OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage)) }
        BackupSection(vm)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApeakMark(Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                LinkRow(stringResource(R.string.email_label), email) { launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))) }
                LinkRow(stringResource(R.string.github_label), github) { launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://$github"))) }
                LinkRow(stringResource(R.string.website_label), site) { launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://$site"))) }
            }
        }
        Text(stringResource(R.string.rights), style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
}

@Composable
private fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

private data class Item(val id: String, val name: String, val icon: String)

@Composable
fun ManageScreen(vm: ExpensesViewModel, startTab: Int, onBack: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val cats by vm.categories.collectAsState()
    var tab by remember { mutableIntStateOf(startTab) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var deleting by remember { mutableStateOf<Item?>(null) }
    val rows = when (tab) {
        0 -> accounts.map { Item(it.id, it.name, it.icon) }
        1 -> cats.filter { it.type == Tx.EXPENSE }.map { Item(it.id, it.name, it.icon) }
        else -> cats.filter { it.type == Tx.INCOME }.map { Item(it.id, it.name, it.icon) }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(stringResource(R.string.manage_title), onBack)
        ToggleRow(listOf(stringResource(R.string.tab_accounts), stringResource(R.string.tab_expense), stringResource(R.string.tab_income)),
            tab) { tab = it }
        LazyColumn(Modifier.weight(1f)) {
            items(rows, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().clickable { editing = r }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.icon, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    IconBtn(Ico.Close, { deleting = r }, MaterialTheme.colorScheme.error)
                }
            }
        }
        Button(onClick = { editing = Item("", "", "") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(stringResource(R.string.manage_new))
        }
        Spacer(Modifier.height(8.dp))
    }
    editing?.let { item ->
        ItemDialog(item, tab != 0 && item.id.isNotEmpty(), onDismiss = { editing = null }) { icon, name, past ->
            if (tab == 0) vm.saveAccount(item.id, name, icon)
            else vm.saveCategory(item.id, name, icon, if (tab == 1) Tx.EXPENSE else Tx.INCOME, past)
            editing = null
        }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    if (tab == 0) vm.deleteAccount(item.id) else vm.deleteCategory(item.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ItemDialog(item: Item, canUpdatePast: Boolean, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit) {
    var past by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf(item.name) }
    var icon by remember { mutableStateOf(item.icon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (item.id.isEmpty()) R.string.manage_add else R.string.manage_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(icon, { icon = it.take(4) }, label = { Text(stringResource(R.string.icon_emoji)) }, singleLine = true)
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                if (canUpdatePast) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = past, onCheckedChange = { past = it })
                    Text(stringResource(R.string.update_past), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(icon.trim().ifEmpty { "📁" }, name.trim(), past) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
