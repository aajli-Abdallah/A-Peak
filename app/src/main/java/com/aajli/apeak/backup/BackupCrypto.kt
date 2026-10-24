package com.aajli.apeak.backup

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception()

/**
 * Backup encryption with standard JCE only: AES-256-GCM. Password backups derive the key with PBKDF2-HMAC-SHA256
 * (random salt, iterations stored in the file). Local copies use a key kept in the Android Keystore.
 * The file is a JSON envelope; appVersion stays readable and, with kdf, is authenticated (GCM additional data),
 * so changing the header makes decryption fail.
 */
object BackupCrypto {
    const val DEFAULT_ITERATIONS = 600_000
    const val KDF_PASSWORD = "PBKDF2WithHmacSHA256"
    const val KDF_KEYSTORE = "keystore"
    private const val FORMAT = 1
    private const val APP = "A-Peak"
    private val enc = Base64.getEncoder()
    private val dec = Base64.getDecoder()

    private fun aad(appVersion: String, kdf: String) = "$APP|$FORMAT|$appVersion|$kdf".toByteArray(Charsets.UTF_8)

    fun isEncrypted(text: String): Boolean =
        try { val o = JSONObject(text); o.optString("app") == APP && o.optString("format") == "encrypted" } catch (e: Exception) { false }

    fun kdfOf(text: String): String = JSONObject(text).optString("kdf")

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = try { SecretKeyFactory.getInstance(KDF_PASSWORD).generateSecret(spec).encoded } finally { spec.clearPassword() }
        return SecretKeySpec(bytes, "AES")
    }

    private fun envelope(appVersion: String, kdf: String, iter: Int?, salt: ByteArray?, iv: ByteArray, ct: ByteArray): String {
        val o = JSONObject().put("app", APP).put("format", "encrypted").put("formatVersion", FORMAT).put("appVersion", appVersion).put("kdf", kdf)
        if (iter != null) o.put("iter", iter)
        if (salt != null) o.put("salt", enc.encodeToString(salt))
        o.put("iv", enc.encodeToString(iv)).put("data", enc.encodeToString(ct))
        return o.toString()
    }

    fun encryptWithPassword(plain: String, password: CharArray, appVersion: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(128, iv))
        c.updateAAD(aad(appVersion, KDF_PASSWORD))
        return envelope(appVersion, KDF_PASSWORD, iterations, salt, iv, c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** Throws WrongPasswordException for a wrong password or a damaged/modified file. */
    fun decryptWithPassword(text: String, password: CharArray): String {
        val o = JSONObject(text)
        require(o.optString("kdf") == KDF_PASSWORD)
        val iter = o.getInt("iter")
        require(iter in 1_000..2_000_000)
        return open(o, derive(password, dec.decode(o.getString("salt")), iter))
    }

    /** The cipher creates the IV (required by the Android Keystore). */
    fun encryptWithKey(plain: String, key: SecretKey, appVersion: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        c.updateAAD(aad(appVersion, KDF_KEYSTORE))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return envelope(appVersion, KDF_KEYSTORE, null, null, c.iv, ct)
    }

    fun decryptWithKey(text: String, key: SecretKey): String {
        val o = JSONObject(text)
        require(o.optString("kdf") == KDF_KEYSTORE)
        return open(o, key)
    }

    private fun open(o: JSONObject, key: SecretKey): String {
        val iv = dec.decode(o.getString("iv"))
        val ct = dec.decode(o.getString("data"))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        c.updateAAD(aad(o.optString("appVersion"), o.optString("kdf")))
        return try { String(c.doFinal(ct), Charsets.UTF_8) } catch (e: BadPaddingException) { throw WrongPasswordException() }
    }
}
