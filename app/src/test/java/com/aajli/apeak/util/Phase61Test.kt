package com.aajli.apeak.util

import com.aajli.apeak.backup.BackupCodec
import com.aajli.apeak.backup.BackupData
import com.aajli.apeak.backup.BackupMerge
import com.aajli.apeak.data.LookupItemEntity
import com.aajli.apeak.data.LookupKind
import com.aajli.apeak.data.SecurityEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class Phase61Test {
    @Test fun backupKeepsDetails() {
        val d = BackupData("1.3.61", 1L, "A",
            lookups = listOf(LookupItemEntity("l1", LookupKind.BROKER, "Trade Republic", "traderepublic.com", "low fees"), LookupItemEntity("l2", LookupKind.SECTOR, "Tech")),
            securities = listOf(SecurityEntity("s1", "AAPL", "Apple", "Tech", "long term")))
        assertEquals(d, BackupCodec.decode(BackupCodec.encode(d)))
    }

    @Test fun oldBackupWithoutDetailsStillImports() {
        val d = BackupCodec.decode("""{"app":"A-Peak","lookups":[{"id":"a","kind":"BROKER","name":"TR"}],"securities":[{"id":"s","symbol":"AAPL","name":"Apple"}]}""")
        assertEquals("", d.lookups[0].website); assertEquals("", d.securities[0].sector)
    }

    @Test fun changedDetailsCountAsUpdated() {
        val cur = BackupData(lookups = listOf(LookupItemEntity("a", LookupKind.BROKER, "TR", "", "")), securities = listOf(SecurityEntity("s", "AAPL", "Apple", "", "")))
        val inc = BackupData(lookups = listOf(LookupItemEntity("a", LookupKind.BROKER, "TR", "tr.com", "")), securities = listOf(SecurityEntity("s", "AAPL", "Apple", "Tech", "")))
        val r = BackupMerge.merge(cur, inc)
        assertEquals(1, r.report.first { it.key == "lookups" }.updated)
        assertEquals(1, r.report.first { it.key == "securities" }.updated)
    }
}
