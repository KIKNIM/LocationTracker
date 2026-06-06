package com.locationtracker.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.locationtracker.App
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 密钥管家
 *
 * 三层防护：
 * 1. Keystore 内生成 AES-256 密钥（硬件不可导出）
 * 2. 用 Keystore 密钥加密 SQLCipher 的随机 passphrase
 * 3. 加密后的 passphrase 存入 EncryptedSharedPreferences
 *
 * → 即使 root 也拿不到原始密钥材料
 */
@Singleton
class KeystoreHelper @Inject constructor() {

    private val alias = "tracker_db_master_key"
    private val prefsKeyIv = "db_iv"
    private val prefsKeyData = "db_enc"
    private val app = App.instance

    fun obtainKey(): ByteArray {
        val ivB64 = app.securePrefs.getString(prefsKeyIv, null)
        val dataB64 = app.securePrefs.getString(prefsKeyData, null)

        if (ivB64 != null && dataB64 != null) {
            return decrypt(
                Base64.decode(dataB64, Base64.NO_WRAP),
                Base64.decode(ivB64, Base64.NO_WRAP)
            )
        }

        // 首次：生成随机 passphrase，用 Keystore 密钥加密后保存
        val raw = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val masterKey = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)

        val enc = cipher.doFinal(raw)
        app.securePrefs.edit()
            .putString(prefsKeyIv, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(prefsKeyData, Base64.encodeToString(enc, Base64.NO_WRAP))
            .apply()

        Timber.i("Database encryption key generated + stored in Keystore")
        return raw
    }

    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }
}
