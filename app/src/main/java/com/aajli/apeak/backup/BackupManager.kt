package com.aajli.apeak.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.aajli.apeak.BuildConfig
import com.aajli.apeak.R
import com.aajli.apeak.data.AppDatabase
import com.aajli.apeak.data.SettingsStore
import com.aajli.apeak.util.PriceHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class ImportPreview(val data: BackupData, val merge: MergeResult, val source: String, val currentCount: Int, val encrypted: Boolean = false)

/** An encrypted file waiting for its password. */
data class PendingImport(val text: String, val source: String)

class BackupManager(private val ctx: Context, private val db: AppDatabase, private val settings: SettingsStore) {
    private val dir: File get() = File(ctx.filesDir, "backups").also { it.mkdirs() }

    suspend fun snapshot(): BackupData {
        val d = db.backup()
        return BackupData(
            BuildConfig.VERSION_NAME, System.currentTimeMillis(), ctx.getString(R.string.author_name),
            mapOf("currency" to settings.currency.value, "theme" to settings.theme.value, "dynamic" to settings.dynamic.value.toString()),
            d.accounts(), d.categories(), d.transactions(), d.bankAccounts(), d.bankEntries(), d.brokers(), d.stocks(), d.trades(), d.dividends(), d.budgets(), d.prices(), d.splits(), d.lookups(), d.securities()
        )
    }

    /** One local copy per day, last 7 kept. Skipped when only the default accounts/categories exist. */
    suspend fun autoDaily() {
        protectLocalFiles()
        val f = File(dir, "apeak-auto-${LocalDate.now()}.json")
        if (f.exists()) return
        val s = snapshot()
        if (s.recordCount - s.accounts.size - s.categories.size <= 0) return
        val text = try { BackupCrypto.encryptWithKey(BackupCodec.encode(s), KeystoreKey.get(), BuildConfig.VERSION_NAME) } catch (e: Exception) { return }
        f.writeText(text)
        autoFiles().drop(7).forEach { it.delete() }
    }

    fun autoFiles(): List<File> =
        dir.listFiles { x -> x.name.startsWith("apeak-auto-") }?.sortedByDescending { it.name } ?: emptyList()

    /** password = null shares a plain file. Encryption (slow on purpose) runs off the main thread. */
    suspend fun shareUri(password: CharArray?): Uri {
        val s = snapshot()
        return withContext(Dispatchers.IO) {
            purgeShared()
            val d = File(ctx.cacheDir, "backups").also { it.mkdirs() }
            val plain = BackupCodec.encode(s)
            val f = File(d, "A-Peak-backup-${LocalDate.now()}" + (if (password != null) "-encrypted" else "") + ".json")
            f.writeText(if (password != null) BackupCrypto.encryptWithPassword(plain, password, BuildConfig.VERSION_NAME) else plain)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
        }
    }

    /** Merge writes only new/updated records. Replace first saves a safety copy, then wipes and restores everything. */
    suspend fun apply(p: ImportPreview, replace: Boolean) {
        val b = db.backup()
        if (replace) writeLocalCopy("apeak-before-replace-${System.currentTimeMillis()}.json", BackupCodec.encode(snapshot()))
        val x = if (replace) p.data else p.merge.data
        db.withTransaction {
            if (replace) {
                b.clearAccounts(); b.clearCategories(); b.clearTransactions(); b.clearBankAccounts(); b.clearBankEntries()
                b.clearBrokers(); b.clearStocks(); b.clearTrades(); b.clearDividends(); b.clearBudgets(); b.clearPrices(); b.clearSplits(); b.clearLookups(); b.clearSecurities()
            }
            b.putAccounts(x.accounts); b.putCategories(x.categories); b.putTransactions(x.transactions)
            b.putBankAccounts(x.bankAccounts); b.putBankEntries(x.bankEntries); b.putBrokers(x.brokers)
            b.putStocks(x.stocks); b.putTrades(x.trades); b.putDividends(x.dividends); b.putBudgets(x.budgets); b.putPrices(x.prices); b.putSplits(x.splits); b.putLookups(x.lookups); b.putSecurities(x.securities)
        }
        if (replace) {
            p.data.settings["currency"]?.let { settings.setCurrency(it) }
            p.data.settings["theme"]?.let { settings.setTheme(it) }
            p.data.settings["dynamic"]?.let { settings.setDynamic(it == "true") }
        }
        if (x.prices.isEmpty()) {
            val w = db.wealth()
            x.trades.forEach { w.insertPriceIfAbsent(PriceHistory.point(it.stockId, it.dateMillis, it.priceMinor)) }
            x.stocks.filter { it.lastPriceMinor > 0 }.forEach { w.upsertPrice(PriceHistory.point(it.id, it.lastPriceDate, it.lastPriceMinor)) }
        }
        settings.seeded = true
    }

    /** Writes a text file (for example CSV) to the share folder and returns a content uri for sharing. */
    suspend fun shareText(fileName: String, text: String): Uri = withContext(Dispatchers.IO) {
        val d = File(ctx.cacheDir, "backups").also { it.mkdirs() }
        val f = File(d, fileName)
        f.writeText(text)
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
    }

    /** Deletes files left in the share folder (shared backups and CSV exports). */
    fun purgeShared() { File(ctx.cacheDir, "backups").listFiles()?.forEach { it.delete() } }

    /** Local copy encrypted with the Keystore key; if the Keystore fails it stays a plain file in private app storage so a Replace is never unprotected. */
    private fun writeLocalCopy(name: String, plain: String) {
        val text = try { BackupCrypto.encryptWithKey(plain, KeystoreKey.get(), BuildConfig.VERSION_NAME) } catch (e: Exception) { plain }
        File(dir, name).writeText(text)
    }

    /** Encrypts local copies made by older versions. */
    private fun protectLocalFiles() {
        val files = dir.listFiles() ?: return
        for (f in files) {
            try {
                val t = f.readText()
                if (!BackupCrypto.isEncrypted(t)) f.writeText(BackupCrypto.encryptWithKey(t, KeystoreKey.get(), BuildConfig.VERSION_NAME))
            } catch (e: Exception) { }
        }
    }

    companion object {
        @Volatile private var inst: BackupManager? = null
        fun get(ctx: Context): BackupManager = inst ?: synchronized(this) {
            inst ?: BackupManager(ctx.applicationContext, AppDatabase.get(ctx), SettingsStore.get(ctx)).also { inst = it }
        }
    }
}
