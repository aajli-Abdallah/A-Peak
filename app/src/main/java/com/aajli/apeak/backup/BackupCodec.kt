package com.aajli.apeak.backup

import com.aajli.apeak.data.*
import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.s(k: String) = optString(k, "")
private fun JSONObject.l(k: String) = optLong(k, 0L)
private fun JSONObject.i(k: String) = optInt(k, 0)
private fun JSONObject.d(k: String) = optDouble(k, 0.0)
private fun <T> JSONArray?.read(f: (JSONObject) -> T): List<T> = if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }
private fun <T> List<T>.write(f: (T) -> JSONObject): JSONArray { val a = JSONArray(); forEach { a.put(f(it)) }; return a }

/** Ordered upgrade hook. Add (versionThatChangedTheFormat, transform) here when the file format changes. Old backups must stay importable. */
object Upgrades {
    val steps: List<Pair<String, (JSONObject) -> Unit>> = emptyList()
    fun apply(root: JSONObject, from: String) {
        steps.sortedWith(Comparator { a, b -> Versions.compare(a.first, b.first) }).forEach { (v, f) -> if (Versions.compare(from, v) < 0) f(root) }
    }
}

object BackupCodec {
    const val APP = "A-Peak"

    fun encode(d: BackupData): String {
        val o = JSONObject()
        o.put("app", APP).put("formatVersion", 1).put("appVersion", d.appVersion).put("author", d.author).put("exportedAt", d.exportedAt)
        val st = JSONObject(); d.settings.forEach { (k, v) -> st.put(k, v) }; o.put("settings", st)
        o.put("accounts", d.accounts.write { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("sortOrder", it.sortOrder) })
        o.put("categories", d.categories.write { JSONObject().put("id", it.id).put("name", it.name).put("icon", it.icon).put("type", it.type).put("sortOrder", it.sortOrder) })
        o.put("transactions", d.transactions.write {
            JSONObject().put("id", it.id).put("type", it.type).put("amountMinor", it.amountMinor).put("dateMillis", it.dateMillis).put("note", it.note)
                .put("accountId", it.accountId).put("accountName", it.accountName).put("accountIcon", it.accountIcon)
                .put("categoryId", it.categoryId).put("categoryName", it.categoryName).put("categoryIcon", it.categoryIcon).put("createdAt", it.createdAt)
        })
        o.put("bankAccounts", d.bankAccounts.write {
            JSONObject().put("id", it.id).put("name", it.name).put("bankName", it.bankName).put("kind", it.kind)
                .put("interestRate", it.interestRate).put("openingMinor", it.openingMinor).put("note", it.note).put("createdAt", it.createdAt)
        })
        o.put("bankEntries", d.bankEntries.write {
            JSONObject().put("id", it.id).put("accountId", it.accountId).put("type", it.type).put("amountMinor", it.amountMinor)
                .put("dateMillis", it.dateMillis).put("note", it.note)
        })
        o.put("brokers", d.brokers.write { JSONObject().put("id", it.id).put("name", it.name).put("brokerName", it.brokerName).put("note", it.note).put("createdAt", it.createdAt) })
        o.put("stocks", d.stocks.write {
            JSONObject().put("id", it.id).put("accountId", it.accountId).put("symbol", it.symbol).put("name", it.name)
                .put("lastPriceMinor", it.lastPriceMinor).put("lastPriceDate", it.lastPriceDate).put("createdAt", it.createdAt)
                .put("sector", it.sector).put("note", it.note).put("targetPriceMinor", it.targetPriceMinor).put("stopPriceMinor", it.stopPriceMinor)
        })
        o.put("trades", d.trades.write {
            JSONObject().put("id", it.id).put("stockId", it.stockId).put("type", it.type).put("quantity", it.quantity)
                .put("priceMinor", it.priceMinor).put("feesMinor", it.feesMinor).put("dateMillis", it.dateMillis).put("note", it.note)
        })
        o.put("dividends", d.dividends.write {
            JSONObject().put("id", it.id).put("stockId", it.stockId).put("amountMinor", it.amountMinor).put("dateMillis", it.dateMillis).put("note", it.note)
        })
        o.put("budgets", d.budgets.write { JSONObject().put("id", it.id).put("categoryId", it.categoryId).put("categoryName", it.categoryName).put("startMonth", it.startMonth).put("limitMinor", it.limitMinor) })
        o.put("prices", d.prices.write { JSONObject().put("id", it.id).put("stockId", it.stockId).put("dateMillis", it.dateMillis).put("priceMinor", it.priceMinor) })
        o.put("splits", d.splits.write { JSONObject().put("id", it.id).put("stockId", it.stockId).put("dateMillis", it.dateMillis).put("numerator", it.numerator).put("denominator", it.denominator) })
        o.put("lookups", d.lookups.write { JSONObject().put("id", it.id).put("kind", it.kind).put("name", it.name).put("website", it.website).put("note", it.note) })
        o.put("securities", d.securities.write { JSONObject().put("id", it.id).put("symbol", it.symbol).put("name", it.name).put("sector", it.sector).put("note", it.note) })
        return o.toString()
    }

    /** Throws if the text is not an A-Peak backup. Missing tables (older backups) become empty lists. */
    fun decode(text: String): BackupData {
        val r = JSONObject(text)
        require(r.optString("app") == APP) { "not an A-Peak backup" }
        val version = r.optString("appVersion", "").ifBlank { Versions.DEFAULT }
        Upgrades.apply(r, version)
        val st = HashMap<String, String>()
        r.optJSONObject("settings")?.let { s -> s.keys().forEach { k -> st[k] = s.optString(k) } }
        return BackupData(
            version, r.l("exportedAt"), r.s("author"), st,
            r.optJSONArray("accounts").read { AccountEntity(it.s("id"), it.s("name"), it.s("icon"), it.i("sortOrder")) },
            r.optJSONArray("categories").read { CategoryEntity(it.s("id"), it.s("name"), it.s("icon"), it.s("type"), it.i("sortOrder")) },
            r.optJSONArray("transactions").read {
                TransactionEntity(it.s("id"), it.s("type"), it.l("amountMinor"), it.l("dateMillis"), it.s("note"), it.s("accountId"), it.s("accountName"),
                    it.s("accountIcon"), it.s("categoryId"), it.s("categoryName"), it.s("categoryIcon"), it.l("createdAt"))
            },
            r.optJSONArray("bankAccounts").read {
                BankAccountEntity(it.s("id"), it.s("name"), it.s("bankName"), it.s("kind"), it.d("interestRate"), it.l("openingMinor"), it.s("note"), it.l("createdAt"))
            },
            r.optJSONArray("bankEntries").read { BankEntryEntity(it.s("id"), it.s("accountId"), it.s("type"), it.l("amountMinor"), it.l("dateMillis"), it.s("note")) },
            r.optJSONArray("brokers").read { BrokerAccountEntity(it.s("id"), it.s("name"), it.s("brokerName"), it.s("note"), it.l("createdAt")) },
            r.optJSONArray("stocks").read { StockEntity(it.s("id"), it.s("accountId"), it.s("symbol"), it.s("name"), it.l("lastPriceMinor"), it.l("lastPriceDate"), it.l("createdAt"), it.s("sector"), it.s("note"), it.l("targetPriceMinor"), it.l("stopPriceMinor")) },
            r.optJSONArray("trades").read {
                StockTradeEntity(it.s("id"), it.s("stockId"), it.s("type"), it.d("quantity"), it.l("priceMinor"), it.l("feesMinor"), it.l("dateMillis"), it.s("note"))
            },
            r.optJSONArray("dividends").read { DividendEntity(it.s("id"), it.s("stockId"), it.l("amountMinor"), it.l("dateMillis"), it.s("note")) },
            r.optJSONArray("budgets").read { BudgetEntity(it.s("id"), it.s("categoryId"), it.s("categoryName"), it.i("startMonth"), it.l("limitMinor")) },
            r.optJSONArray("prices").read { PricePointEntity(it.s("id"), it.s("stockId"), it.l("dateMillis"), it.l("priceMinor")) },
            r.optJSONArray("splits").read { StockSplitEntity(it.s("id"), it.s("stockId"), it.l("dateMillis"), it.i("numerator"), it.i("denominator")) },
            r.optJSONArray("lookups").read { LookupItemEntity(it.s("id"), it.s("kind"), it.s("name"), it.s("website"), it.s("note")) },
            r.optJSONArray("securities").read { SecurityEntity(it.s("id"), it.s("symbol"), it.s("name"), it.s("sector"), it.s("note")) }
        )
    }
}
