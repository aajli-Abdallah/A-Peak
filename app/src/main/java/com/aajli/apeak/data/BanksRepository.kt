package com.aajli.apeak.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Single source of truth for bank and savings accounts and their entries. */
class BanksRepository private constructor(private val db: AppDatabase) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val w = db.wealth()
    private fun <T> Flow<List<T>>.hot(): StateFlow<List<T>> = stateIn(scope, SharingStarted.Eagerly, emptyList())

    val banks = w.banks().hot()
    val bankEntries = w.bankEntries().hot()

    suspend fun saveBank(item: BankAccountEntity) { w.upsertBank(item) }
    suspend fun deleteBank(id: String) { db.withTransaction { w.deleteBankEntriesOf(id); w.deleteBank(id) } }
    suspend fun saveBankEntry(item: BankEntryEntity) { w.upsertBankEntry(item) }
    suspend fun deleteBankEntry(id: String) { w.deleteBankEntry(id) }

    companion object {
        @Volatile private var inst: BanksRepository? = null
        fun get(ctx: Context): BanksRepository = inst ?: synchronized(this) {
            inst ?: BanksRepository(AppDatabase.get(ctx)).also { inst = it }
        }
    }
}
