package com.amazecc.app.shared.utils

import java.security.MessageDigest

actual suspend fun hashStringSha256(str: String): String {
    val bytes = str.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
}
