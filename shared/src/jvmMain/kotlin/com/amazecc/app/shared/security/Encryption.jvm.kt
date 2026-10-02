package com.amazecc.app.shared.security

/**
 * Desktop stand-in for the Android Keystore-backed credential encryption.
 *
 * The Android actual uses `AndroidKeyStore` + AES-GCM, which has no desktop equivalent. Desktop
 * is a development target, so this keeps credentials obfuscated at rest without pretending to
 * the same threat model: a fixed key is extractable. Anything shipped to a user must go through
 * the Android actual.
 */
private const val ALGO = "AES/CBC/PKCS5Padding"
private const val KEY_ALIAS = "amazecc_desktop_v1"

private fun key(): javax.crypto.SecretKey {
    val spec = javax.crypto.spec.SecretKeySpec(
        "amazecc-desktop-only!".toByteArray(Charsets.UTF_8).copyOf(16),
        "AES"
    )
    return spec
}

private fun iv(): javax.crypto.spec.IvParameterSpec =
    javax.crypto.spec.IvParameterSpec(ByteArray(16) { it.toByte() })

actual fun advancedEncrypt(plainText: String): String {
    val cipher = javax.crypto.Cipher.getInstance(ALGO)
    cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key(), iv())
    val out = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out)
}

actual fun advancedDecrypt(cipherText: String): String {
    val raw = java.util.Base64.getUrlDecoder().decode(cipherText)
    val cipher = javax.crypto.Cipher.getInstance(ALGO)
    cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key(), iv())
    return String(cipher.doFinal(raw), Charsets.UTF_8)
}
