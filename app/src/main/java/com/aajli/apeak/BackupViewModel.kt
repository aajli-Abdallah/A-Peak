package com.aajli.apeak

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aajli.apeak.backup.*
import com.aajli.apeak.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Backup, export and import. Also runs the start-up backup housekeeping. */
class BackupViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SettingsStore.get(app)
    val backups = BackupManager.get(app)

    init { io { backups.purgeShared(); backups.autoDaily() } }

    private fun io(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { block() } }

    // ---- Backup / import ----
    val importPreview = MutableStateFlow<ImportPreview?>(null)
    val importError = MutableStateFlow<Int?>(null)

    fun prepareImportFromUri(uri: Uri) = io {
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) { null }
        if (text == null) { importError.value = R.string.import_invalid } else { prepareImport(text, uri.lastPathSegment ?: "") }
    }

    fun prepareImportFromFile(f: File) = io { prepareImport(f.readText(), f.name) }

    val passwordRequest = MutableStateFlow<PendingImport?>(null)
    val passwordError = MutableStateFlow(false)

    private suspend fun prepareImport(text: String, source: String, encrypted: Boolean = false) {
        try {
            var plain = text
            var wasEncrypted = encrypted
            if (BackupCrypto.isEncrypted(text)) {
                if (BackupCrypto.kdfOf(text) == BackupCrypto.KDF_KEYSTORE) {
                    plain = BackupCrypto.decryptWithKey(text, KeystoreKey.get())
                    wasEncrypted = true
                } else {
                    passwordError.value = false
                    passwordRequest.value = PendingImport(text, source)
                    return
                }
            }
            val data = BackupCodec.decode(plain)
            if (Versions.compare(data.appVersion, BuildConfig.VERSION_NAME) > 0) { importError.value = R.string.import_newer; return }
            val cur = backups.snapshot()
            importPreview.value = ImportPreview(data, BackupMerge.merge(cur, data), source, cur.recordCount, wasEncrypted)
        } catch (e: Exception) { importError.value = R.string.import_invalid }
    }

    fun submitPassword(password: CharArray) = io {
        val p = passwordRequest.value ?: return@io
        try {
            val plain = BackupCrypto.decryptWithPassword(p.text, password)
            passwordRequest.value = null
            prepareImport(plain, p.source, true)
        } catch (e: WrongPasswordException) {
            passwordError.value = true
        } catch (e: Exception) {
            passwordRequest.value = null
            importError.value = R.string.import_invalid
        } finally {
            password.fill('\u0000')
        }
    }

    fun cancelPassword() { passwordRequest.value = null; passwordError.value = false }

    fun confirmImport(replace: Boolean) = io {
        val p = importPreview.value ?: return@io
        importPreview.value = null
        backups.apply(p, replace)
    }

    fun cancelImport() { importPreview.value = null }
}
