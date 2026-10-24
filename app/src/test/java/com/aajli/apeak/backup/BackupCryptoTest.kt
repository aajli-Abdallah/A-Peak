package com.aajli.apeak.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator

class BackupCryptoTest {
    private val plain = """{"app":"A-Peak","accounts":[{"id":"a","name":"Cash"}]}"""
    private fun pw(s: String) = s.toCharArray()
    private fun enc() = BackupCrypto.encryptWithPassword(plain, pw("correct horse"), "1.3.58", 1000)

    @Test fun passwordRoundTripHidesContent() {
        val e = enc()
        assertTrue(BackupCrypto.isEncrypted(e))
        assertFalse(e.contains("Cash"))
        assertEquals("1.3.58", JSONObject(e).getString("appVersion"))
        assertEquals(plain, BackupCrypto.decryptWithPassword(e, pw("correct horse")))
    }

    @Test fun wrongPasswordFails() {
        try { BackupCrypto.decryptWithPassword(enc(), pw("wrong")); fail() } catch (e: WrongPasswordException) { }
    }

    @Test fun modifiedHeaderFails() {
        val o = JSONObject(enc()); o.put("appVersion", "9.9.9")
        try { BackupCrypto.decryptWithPassword(o.toString(), pw("correct horse")); fail() } catch (e: WrongPasswordException) { }
    }

    @Test fun modifiedDataFails() {
        val o = JSONObject(enc())
        val bytes = Base64.getDecoder().decode(o.getString("data")); bytes[0] = (bytes[0].toInt() xor 1).toByte()
        o.put("data", Base64.getEncoder().encodeToString(bytes))
        try { BackupCrypto.decryptWithPassword(o.toString(), pw("correct horse")); fail() } catch (e: WrongPasswordException) { }
    }

    @Test fun keyRoundTripAndKind() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val e = BackupCrypto.encryptWithKey(plain, key, "1.3.58")
        assertEquals(BackupCrypto.KDF_KEYSTORE, BackupCrypto.kdfOf(e))
        assertEquals(plain, BackupCrypto.decryptWithKey(e, key))
    }

    @Test fun plainTextIsNotDetectedAsEncryptedAndOutputsDiffer() {
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertNotEquals(enc(), enc())
    }
}
