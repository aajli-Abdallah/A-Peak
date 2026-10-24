package com.aajli.apeak.backup

import com.aajli.apeak.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun tx(id: String, acc: String) = TransactionEntity(id, Tx.EXPENSE, 100, 1, "", acc, "Cash", "", "c", "Food", "", 0)

    @Test fun versionCompare() {
        assertTrue(Versions.compare("1.1.10", "1.1.9") > 0)
        assertTrue(Versions.compare("1.0.12", "1.1.0") < 0)
        assertEquals(0, Versions.compare("1.4.0", "1.4.0"))
    }

    @Test fun roundTrip() {
        val d = BackupData("1.1.10", 5L, "A", mapOf("currency" to "EUR"),
            accounts = listOf(AccountEntity("a", "Cash", "x", 0)), transactions = listOf(tx("t", "a")),
            bankAccounts = listOf(BankAccountEntity("b", "B", "Bank", BankKind.SAVINGS, 3.5, 1000, "n", 1)),
            trades = listOf(StockTradeEntity("tr", "s", TradeType.BUY, 1.5, 100, 5, 2, "")))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
    }

    @Test fun oldBackupWithoutVersionOrTables() {
        val d = BackupCodec.decode("""{"app":"A-Peak","accounts":[{"id":"a","name":"Cash","icon":"x","sortOrder":0}]}""")
        assertEquals(Versions.DEFAULT, d.appVersion)
        assertEquals(1, d.accounts.size)
        assertEquals(0, d.transactions.size)
    }

    @Test fun mergeMatchesByNameAndRemaps() {
        val cur = BackupData(accounts = listOf(AccountEntity("L", "Cash", "x", 0)))
        val inc = BackupData(accounts = listOf(AccountEntity("X", "cash", "x", 0)), transactions = listOf(tx("t1", "X")))
        val r = BackupMerge.merge(cur, inc)
        assertEquals("L", r.data.transactions[0].accountId)
        assertEquals(1, r.report.first { it.key == "accounts" }.updated)
        assertEquals(1, r.report.first { it.key == "transactions" }.new)
    }

    @Test fun mergeIdenticalIsUnchanged() {
        val d = BackupData(accounts = listOf(AccountEntity("a", "Cash", "x", 0)), transactions = listOf(tx("t", "a")))
        val r = BackupMerge.merge(d, d)
        assertEquals(0, r.data.recordCount)
        assertEquals(1, r.report.first { it.key == "transactions" }.unchanged)
    }
}
