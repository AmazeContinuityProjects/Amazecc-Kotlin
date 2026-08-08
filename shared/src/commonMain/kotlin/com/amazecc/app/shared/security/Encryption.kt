package com.amazecc.app.shared.security

expect fun advancedEncrypt(plainText: String): String

expect fun advancedDecrypt(cipherText: String): String

object Encryption {
    fun encryptOrPlain(plainText: String): String =
        try {
            advancedEncrypt(plainText)
        } catch (_: Exception) {
            plainText
        }

    fun decryptOrPlain(cipherText: String): String =
        try {
            advancedDecrypt(cipherText)
        } catch (_: Exception) {
            cipherText
        }
}
