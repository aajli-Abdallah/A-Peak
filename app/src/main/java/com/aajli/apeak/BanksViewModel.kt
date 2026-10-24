package com.aajli.apeak

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aajli.apeak.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Bank and savings screens. */
class BanksViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BanksRepository.get(app)
    val settings = SettingsStore.get(app)
    val banks = repo.banks
    val bankEntries = repo.bankEntries

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    fun saveBank(item: BankAccountEntity) = io { repo.saveBank(item) }
    fun deleteBank(id: String) = io { repo.deleteBank(id) }
    fun saveBankEntry(item: BankEntryEntity) = io { repo.saveBankEntry(item) }
    fun deleteBankEntry(id: String) = io { repo.deleteBankEntry(id) }
}
