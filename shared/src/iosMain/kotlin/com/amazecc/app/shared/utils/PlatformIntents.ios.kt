package com.amazecc.app.shared.utils

/*
 * All four actions report `false` on iOS. Wiring them up requires Objective-C interop
 * (UIApplication.openURL for http/https and tel:, and UIActivityViewController for sharing
 * / document preview via a UIDocumentInteractionController). That interop is out of scope for
 * this change, so the helpers degrade gracefully instead of throwing or linking new frameworks.
 */

actual fun openExternalUrl(url: String): Boolean = false

actual fun dialPhoneNumber(number: String): Boolean = false

actual fun openDocumentBytes(bytes: ByteArray, fileName: String, mimeType: String): Boolean = false

actual fun shareText(text: String): Boolean = false
