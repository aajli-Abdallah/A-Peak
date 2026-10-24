package com.aajli.apeak.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Currency
import java.util.Locale

object Tx {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
}

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String, val name: String, val icon: String, val sortOrder: Int)

@Entity(tableName = "categories")
data class CategoryEntity(@PrimaryKey val id: String, val name: String, val icon: String, val type: String, val sortOrder: Int)

/** Each transaction keeps a snapshot of the account and category name/icon. */
@Entity(tableName = "transactions", indices = [Index("dateMillis")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val dateMillis: Long,
    val note: String,
    val accountId: String,
    val accountName: String,
    val accountIcon: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val createdAt: Long
)

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, name") fun observeAll(): Flow<List<AccountEntity>>
    @Upsert suspend fun upsert(item: AccountEntity)
    @Upsert suspend fun upsertAll(items: List<AccountEntity>)
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name") fun observeAll(): Flow<List<CategoryEntity>>
    @Upsert suspend fun upsert(item: CategoryEntity)
    @Upsert suspend fun upsertAll(items: List<CategoryEntity>)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE dateMillis >= :fromMillis AND dateMillis < :toMillis ORDER BY dateMillis DESC, createdAt DESC")
    fun observeBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionEntity>>
    @Upsert suspend fun upsert(item: TransactionEntity)
    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("UPDATE transactions SET categoryName = :name, categoryIcon = :icon WHERE categoryId = :id") suspend fun renameCategory(id: String, name: String, icon: String)
    @Delete suspend fun delete(item: TransactionEntity)
}

@Database(entities = [AccountEntity::class, CategoryEntity::class, TransactionEntity::class, BankAccountEntity::class, BankEntryEntity::class, BrokerAccountEntity::class, StockEntity::class, StockTradeEntity::class, DividendEntity::class, BudgetEntity::class, PricePointEntity::class, StockSplitEntity::class, LookupItemEntity::class, SecurityEntity::class], version = 7, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun wealth(): WealthDao
    abstract fun backup(): BackupDao
    abstract fun budgetDao(): BudgetDao
    abstract fun lists(): ListsDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "apeak.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { inst = it }
        }
    }
}

class SettingsStore private constructor(ctx: Context) {
    private val p = ctx.getSharedPreferences("apeak_settings", Context.MODE_PRIVATE)
    private val _currency = MutableStateFlow(p.getString("currency", null) ?: defaultCurrency())
    private val _theme = MutableStateFlow(p.getString("theme", "SYSTEM") ?: "SYSTEM")
    private val _dynamic = MutableStateFlow(p.getBoolean("dynamic", false))
    val currency: StateFlow<String> = _currency
    val theme: StateFlow<String> = _theme
    val dynamic: StateFlow<Boolean> = _dynamic
    private val _lastShared = MutableStateFlow(p.getLong("lastShared", 0L))
    private val _remindAfter = MutableStateFlow(p.getLong("remindAfter", 0L))
    val lastShared: StateFlow<Long> = _lastShared
    val remindAfter: StateFlow<Long> = _remindAfter
    fun markShared() { val n = System.currentTimeMillis(); p.edit().putLong("lastShared", n).apply(); _lastShared.value = n }
    fun snooze() { val n = System.currentTimeMillis() + 3 * 86_400_000L; p.edit().putLong("remindAfter", n).apply(); _remindAfter.value = n }

    fun setCurrency(v: String) { p.edit().putString("currency", v).apply(); _currency.value = v }
    fun setTheme(v: String) { p.edit().putString("theme", v).apply(); _theme.value = v }
    fun setDynamic(v: Boolean) { p.edit().putBoolean("dynamic", v).apply(); _dynamic.value = v }
    var seeded: Boolean
        get() = p.getBoolean("seeded", false)
        set(v) { p.edit().putBoolean("seeded", v).apply() }

    private fun defaultCurrency() = try { Currency.getInstance(Locale.getDefault()).currencyCode } catch (e: Exception) { "USD" }

    companion object {
        @Volatile private var inst: SettingsStore? = null
        /** One shared instance, so every view model sees the same settings flows. */
        fun get(ctx: Context): SettingsStore = inst ?: synchronized(this) { inst ?: SettingsStore(ctx.applicationContext).also { inst = it } }
    }
}

// ---------------- Version 1.0.1: banks, savings, stock accounts ----------------
object BankKind { const val CURRENT = "CURRENT"; const val SAVINGS = "SAVINGS" }
object BankType { const val DEPOSIT = "DEPOSIT"; const val WITHDRAW = "WITHDRAW"; const val INTEREST = "INTEREST" }
object TradeType { const val BUY = "BUY"; const val SELL = "SELL" }

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey val id: String, val name: String, val bankName: String, val kind: String,
    val interestRate: Double, val openingMinor: Long, val note: String, val createdAt: Long
)

@Entity(tableName = "bank_entries", indices = [Index("accountId")])
data class BankEntryEntity(
    @PrimaryKey val id: String, val accountId: String, val type: String,
    val amountMinor: Long, val dateMillis: Long, val note: String
)

@Entity(tableName = "broker_accounts")
data class BrokerAccountEntity(
    @PrimaryKey val id: String, val name: String, val brokerName: String, val note: String, val createdAt: Long
)

@Entity(tableName = "stocks", indices = [Index("accountId")])
data class StockEntity(
    @PrimaryKey val id: String, val accountId: String, val symbol: String, val name: String,
    val lastPriceMinor: Long, val lastPriceDate: Long, val createdAt: Long,
    val sector: String = "", val note: String = "", val targetPriceMinor: Long = 0L, val stopPriceMinor: Long = 0L
)

@Entity(tableName = "stock_trades", indices = [Index("stockId")])
data class StockTradeEntity(
    @PrimaryKey val id: String, val stockId: String, val type: String, val quantity: Double,
    val priceMinor: Long, val feesMinor: Long, val dateMillis: Long, val note: String
)

@Entity(tableName = "dividends", indices = [Index("stockId")])
data class DividendEntity(
    @PrimaryKey val id: String, val stockId: String, val amountMinor: Long, val dateMillis: Long, val note: String
)

@Dao
interface WealthDao {
    @Query("SELECT * FROM bank_accounts ORDER BY createdAt") fun banks(): Flow<List<BankAccountEntity>>
    @Query("SELECT * FROM bank_entries ORDER BY dateMillis DESC") fun bankEntries(): Flow<List<BankEntryEntity>>
    @Query("SELECT * FROM broker_accounts ORDER BY createdAt") fun brokers(): Flow<List<BrokerAccountEntity>>
    @Query("SELECT * FROM stocks ORDER BY symbol") fun stocks(): Flow<List<StockEntity>>
    @Query("SELECT * FROM stock_trades ORDER BY dateMillis DESC") fun trades(): Flow<List<StockTradeEntity>>
    @Query("SELECT * FROM dividends ORDER BY dateMillis DESC") fun dividends(): Flow<List<DividendEntity>>

    @Upsert suspend fun upsertBank(item: BankAccountEntity)
    @Upsert suspend fun upsertBankEntry(item: BankEntryEntity)
    @Upsert suspend fun upsertBroker(item: BrokerAccountEntity)
    @Upsert suspend fun upsertStock(item: StockEntity)
    @Upsert suspend fun upsertTrade(item: StockTradeEntity)
    @Upsert suspend fun upsertDividend(item: DividendEntity)

    @Query("DELETE FROM bank_accounts WHERE id = :id") suspend fun deleteBank(id: String)
    @Query("DELETE FROM bank_entries WHERE accountId = :id") suspend fun deleteBankEntriesOf(id: String)
    @Query("DELETE FROM bank_entries WHERE id = :id") suspend fun deleteBankEntry(id: String)
    @Query("DELETE FROM broker_accounts WHERE id = :id") suspend fun deleteBroker(id: String)
    @Query("SELECT id FROM stocks WHERE accountId = :id") suspend fun stockIdsOf(id: String): List<String>
    @Query("DELETE FROM stocks WHERE id = :id") suspend fun deleteStock(id: String)
    @Query("DELETE FROM stock_trades WHERE stockId = :id") suspend fun deleteTradesOfStock(id: String)
    @Query("DELETE FROM dividends WHERE stockId = :id") suspend fun deleteDividendsOfStock(id: String)
    @Query("DELETE FROM stock_trades WHERE id = :id") suspend fun deleteTrade(id: String)
    @Query("DELETE FROM dividends WHERE id = :id") suspend fun deleteDividend(id: String)
    @Query("SELECT * FROM price_points ORDER BY dateMillis") fun prices(): Flow<List<PricePointEntity>>
    @Upsert suspend fun upsertPrice(item: PricePointEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPriceIfAbsent(item: PricePointEntity)
    @Query("DELETE FROM price_points WHERE stockId = :id") suspend fun deletePricesOfStock(id: String)
    @Query("SELECT * FROM stock_splits ORDER BY dateMillis") fun splits(): Flow<List<StockSplitEntity>>
    @Upsert suspend fun upsertSplit(item: StockSplitEntity)
    @Query("DELETE FROM stock_splits WHERE id = :id") suspend fun deleteSplit(id: String)
    @Query("DELETE FROM stock_splits WHERE stockId = :id") suspend fun deleteSplitsOfStock(id: String)
    @Query("UPDATE broker_accounts SET brokerName = :newName WHERE brokerName = :oldName") suspend fun renameBrokerCompany(oldName: String, newName: String)
    @Query("UPDATE broker_accounts SET name = :newName WHERE name = :oldName") suspend fun renameAccountName(oldName: String, newName: String)
    @Query("UPDATE stocks SET sector = :newName WHERE sector = :oldName") suspend fun renameSectorInStocks(oldName: String, newName: String)
    @Query("UPDATE stocks SET symbol = :newSymbol, name = :newName WHERE symbol = :oldSymbol") suspend fun renameStockSymbol(oldSymbol: String, newSymbol: String, newName: String)
    @Query("UPDATE stocks SET sector = :newSector WHERE symbol = :symbol") suspend fun setSectorOfSymbol(symbol: String, newSector: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "CREATE TABLE IF NOT EXISTS `bank_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `bankName` TEXT NOT NULL, `kind` TEXT NOT NULL, `interestRate` REAL NOT NULL, `openingMinor` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `bank_entries` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_bank_entries_accountId` ON `bank_entries` (`accountId`)",
            "CREATE TABLE IF NOT EXISTS `broker_accounts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brokerName` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `stocks` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `lastPriceMinor` INTEGER NOT NULL, `lastPriceDate` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_stocks_accountId` ON `stocks` (`accountId`)",
            "CREATE TABLE IF NOT EXISTS `stock_trades` (`id` TEXT NOT NULL, `stockId` TEXT NOT NULL, `type` TEXT NOT NULL, `quantity` REAL NOT NULL, `priceMinor` INTEGER NOT NULL, `feesMinor` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_stock_trades_stockId` ON `stock_trades` (`stockId`)",
            "CREATE TABLE IF NOT EXISTS `dividends` (`id` TEXT NOT NULL, `stockId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `dateMillis` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_dividends_stockId` ON `dividends` (`stockId`)"
        ).forEach { db.execSQL(it) }
    }
}

// ---------------- Version 1.1.10: backup access ----------------
@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM bank_accounts") suspend fun bankAccounts(): List<BankAccountEntity>
    @Query("SELECT * FROM bank_entries") suspend fun bankEntries(): List<BankEntryEntity>
    @Query("SELECT * FROM broker_accounts") suspend fun brokers(): List<BrokerAccountEntity>
    @Query("SELECT * FROM stocks") suspend fun stocks(): List<StockEntity>
    @Query("SELECT * FROM stock_trades") suspend fun trades(): List<StockTradeEntity>
    @Query("SELECT * FROM dividends") suspend fun dividends(): List<DividendEntity>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM price_points") suspend fun prices(): List<PricePointEntity>
    @Query("SELECT * FROM stock_splits") suspend fun splits(): List<StockSplitEntity>
    @Query("SELECT * FROM lookup_items") suspend fun lookups(): List<LookupItemEntity>
    @Query("SELECT * FROM securities") suspend fun securities(): List<SecurityEntity>

    @Upsert suspend fun putAccounts(items: List<AccountEntity>)
    @Upsert suspend fun putCategories(items: List<CategoryEntity>)
    @Upsert suspend fun putTransactions(items: List<TransactionEntity>)
    @Upsert suspend fun putBankAccounts(items: List<BankAccountEntity>)
    @Upsert suspend fun putBankEntries(items: List<BankEntryEntity>)
    @Upsert suspend fun putBrokers(items: List<BrokerAccountEntity>)
    @Upsert suspend fun putStocks(items: List<StockEntity>)
    @Upsert suspend fun putTrades(items: List<StockTradeEntity>)
    @Upsert suspend fun putDividends(items: List<DividendEntity>)
    @Upsert suspend fun putBudgets(items: List<BudgetEntity>)
    @Upsert suspend fun putPrices(items: List<PricePointEntity>)
    @Upsert suspend fun putSplits(items: List<StockSplitEntity>)
    @Upsert suspend fun putLookups(items: List<LookupItemEntity>)
    @Upsert suspend fun putSecurities(items: List<SecurityEntity>)

    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM bank_accounts") suspend fun clearBankAccounts()
    @Query("DELETE FROM bank_entries") suspend fun clearBankEntries()
    @Query("DELETE FROM broker_accounts") suspend fun clearBrokers()
    @Query("DELETE FROM stocks") suspend fun clearStocks()
    @Query("DELETE FROM stock_trades") suspend fun clearTrades()
    @Query("DELETE FROM dividends") suspend fun clearDividends()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM price_points") suspend fun clearPrices()
    @Query("DELETE FROM stock_splits") suspend fun clearSplits()
    @Query("DELETE FROM lookup_items") suspend fun clearLookups()
    @Query("DELETE FROM securities") suspend fun clearSecurities()
}

// ---------------- Version 1.2.0: budgets ----------------
/** A monthly limit for one category. Rows are versioned: the newest row starting on or before a month applies to it; limit 0 = no budget from that month. */
@Entity(tableName = "budgets", indices = [Index("categoryId")])
data class BudgetEntity(
    @PrimaryKey val id: String, val categoryId: String, val categoryName: String, val startMonth: Int, val limitMinor: Long
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets") fun observeAll(): Flow<List<BudgetEntity>>
    @Upsert suspend fun upsert(item: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteById(id: String)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `categoryName` TEXT NOT NULL, `startMonth` INTEGER NOT NULL, `limitMinor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_categoryId` ON `budgets` (`categoryId`)")
    }
}

// ---------------- Version 1.3.0: price history ----------------
/** One observed price of a stock on a day. id = stockId:dayStart, so one point per stock per day. */
@Entity(tableName = "price_points", indices = [Index("stockId")])
data class PricePointEntity(@PrimaryKey val id: String, val stockId: String, val dateMillis: Long, val priceMinor: Long)

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `price_points` (`id` TEXT NOT NULL, `stockId` TEXT NOT NULL, `dateMillis` INTEGER NOT NULL, `priceMinor` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_price_points_stockId` ON `price_points` (`stockId`)")
        // Backfill from what is already stored: every past trade price and every stock's last price.
        db.execSQL("INSERT OR IGNORE INTO price_points (id, stockId, dateMillis, priceMinor) SELECT stockId || ':' || dateMillis, stockId, dateMillis, priceMinor FROM stock_trades WHERE priceMinor > 0")
        db.execSQL("INSERT OR REPLACE INTO price_points (id, stockId, dateMillis, priceMinor) SELECT id || ':' || lastPriceDate, id, lastPriceDate, lastPriceMinor FROM stocks WHERE lastPriceMinor > 0")
    }
}

// ---------------- Version 1.3.55: stock splits (+ new stock fields) ----------------
/** numerator new shares for denominator old shares (2 for 1 = 2/1, reverse 1 for 10 = 1/10). */
@Entity(tableName = "stock_splits", indices = [Index("stockId")])
data class StockSplitEntity(@PrimaryKey val id: String, val stockId: String, val dateMillis: Long, val numerator: Int, val denominator: Int)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `stock_splits` (`id` TEXT NOT NULL, `stockId` TEXT NOT NULL, `dateMillis` INTEGER NOT NULL, `numerator` INTEGER NOT NULL, `denominator` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_splits_stockId` ON `stock_splits` (`stockId`)")
        db.execSQL("ALTER TABLE `stocks` ADD COLUMN `sector` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `stocks` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `stocks` ADD COLUMN `targetPriceMinor` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `stocks` ADD COLUMN `stopPriceMinor` INTEGER NOT NULL DEFAULT 0")
    }
}

// ---------------- Version 1.3.56: pick lists (brokers, account names, stocks) ----------------
object LookupKind { const val BROKER = "BROKER"; const val ACCOUNT = "ACCOUNT"; const val SECTOR = "SECTOR" }

/** A name offered in a picker: a broker company or a stock account name. Only a source for pickers; accounts keep their own text. */
@Entity(tableName = "lookup_items", indices = [Index("kind")])
data class LookupItemEntity(@PrimaryKey val id: String, val kind: String, val name: String, val website: String = "", val note: String = "")

/** A stock offered in the stock picker. */
@Entity(tableName = "securities")
data class SecurityEntity(@PrimaryKey val id: String, val symbol: String, val name: String, val sector: String = "", val note: String = "")

@Dao
interface ListsDao {
    @Query("SELECT * FROM lookup_items ORDER BY name COLLATE NOCASE") fun lookups(): Flow<List<LookupItemEntity>>
    @Upsert suspend fun upsertLookup(item: LookupItemEntity)
    @Query("DELETE FROM lookup_items WHERE id = :id") suspend fun deleteLookup(id: String)
    @Query("SELECT * FROM securities ORDER BY symbol") fun securities(): Flow<List<SecurityEntity>>
    @Upsert suspend fun upsertSecurity(item: SecurityEntity)
    @Query("DELETE FROM securities WHERE id = :id") suspend fun deleteSecurity(id: String)
    @Query("UPDATE securities SET sector = :newName WHERE sector = :oldName") suspend fun renameSectorInSecurities(oldName: String, newName: String)
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `lookup_items` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lookup_items_kind` ON `lookup_items` (`kind`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `securities` (`id` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))")
        // Fill the lists from what you already entered.
        db.execSQL("INSERT INTO lookup_items (id, kind, name) SELECT lower(hex(randomblob(16))), 'BROKER', brokerName FROM (SELECT DISTINCT brokerName FROM broker_accounts WHERE trim(brokerName) <> '')")
        db.execSQL("INSERT INTO lookup_items (id, kind, name) SELECT lower(hex(randomblob(16))), 'ACCOUNT', name FROM (SELECT DISTINCT name FROM broker_accounts WHERE trim(name) <> '')")
        db.execSQL("INSERT INTO securities (id, symbol, name) SELECT lower(hex(randomblob(16))), symbol, MAX(name) FROM stocks GROUP BY symbol")
    }
}

// ---------------- Version 1.3.61: details for brokers, stocks and sectors ----------------
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `lookup_items` ADD COLUMN `website` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `lookup_items` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `securities` ADD COLUMN `sector` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `securities` ADD COLUMN `note` TEXT NOT NULL DEFAULT ''")
        // Existing sectors become list entries, and each listed stock gets the sector of its holdings.
        db.execSQL("UPDATE securities SET sector = COALESCE((SELECT MAX(s.sector) FROM stocks s WHERE s.symbol = securities.symbol), '')")
        db.execSQL("INSERT INTO lookup_items (id, kind, name) SELECT lower(hex(randomblob(16))), 'SECTOR', sector FROM (SELECT DISTINCT sector FROM stocks WHERE trim(sector) <> '')")
    }
}
