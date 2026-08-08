package com.amazecc.app.shared.security

// iOS fallback: plain storage until Keychain-backed crypto lands (roadmap Phase 2).
actual fun advancedEncrypt(plainText: String): String = plainText

actual fun advancedDecrypt(cipherText: String): String = cipherText
