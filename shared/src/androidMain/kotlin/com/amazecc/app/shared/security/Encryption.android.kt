package com.amazecc.app.shared.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "amazecc_credentials_v1"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
    generator.init(
        KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
    )
    return generator.generateKey()
}

actual fun advancedEncrypt(plainText: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val iv = cipher.iv
    val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
}

actual fun advancedDecrypt(cipherText: String): String {
    val decoded = Base64.decode(cipherText, Base64.NO_WRAP)
    val iv = decoded.copyOfRange(0, GCM_IV_BYTES)
    val encrypted = decoded.copyOfRange(GCM_IV_BYTES, decoded.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(encrypted), Charsets.UTF_8)
}
