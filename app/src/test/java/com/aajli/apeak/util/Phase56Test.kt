package com.aajli.apeak.util

import com.aajli.apeak.backup.BackupCodec
import com.aajli.apeak.backup.BackupData
import com.aajli.apeak.backup.BackupMerge
import com.aajli.apeak.data.LookupItemEntity
import com.aajli.apeak.data.LookupKind
import com.aajli.apeak.data.SecurityEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase56Test {
    @Test fun backupKeepsLists() {
        val d = BackupData("1.3.56", 1L, "A",
            lookups = listOf(LookupItemEntity("l1", LookupKind.BROKER, "Trade Republic")),
            securities = listOf(SecurityEntity("s1", "AAPL", "Apple")))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
    }

    @Test fun mergeMatchesListsByNameOrSymbol() {
        val cur = BackupData(lookups = listOf(LookupItemEntity("a", LookupKind.BROKER, "Trade Republic")), securities = listOf(SecurityEntity("x", "AAPL", "Apple")))
        val inc = BackupData(lookups = listOf(LookupItemEntity("b", LookupKind.BROKER, "trade republic"), LookupItemEntity("c", LookupKind.ACCOUNT, "PEA")),
            securities = listOf(SecurityEntity("y", "aapl", "Apple Inc"), SecurityEntity("z", "MSFT", "Microsoft")))
        val r = BackupMerge.merge(cur, inc)
        assertEquals(1, r.report.first { it.key == "lookups" }.new)
        assertEquals(1, r.report.first { it.key == "securities" }.new)
        assertEquals(1, r.report.first { it.key == "securities" }.updated)
    }
}
