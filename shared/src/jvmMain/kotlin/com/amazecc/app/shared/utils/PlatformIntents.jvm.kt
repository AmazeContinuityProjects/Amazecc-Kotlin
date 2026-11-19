package com.amazecc.app.shared.utils

import java.awt.Desktop
import java.net.URI

actual fun openExternalUrl(url: String): Boolean {
    val trimmed = url.trim()
    val scheme = trimmed.lowercase()
    if (!scheme.startsWith("http://") && !scheme.startsWith("https://")) return false
    return runCatching {
        val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            desktop.browse(URI(trimmed))
            true
        } else {
            false
        }
    }.getOrDefault(false)
}

/** No telephony on desktop/JVM. */
actual fun dialPhoneNumber(number: String): Boolean = false

/** There is no external document viewer wired up on desktop/JVM. */
actual fun openDocumentBytes(bytes: ByteArray, fileName: String, mimeType: String): Boolean = false

/** There is no system share sheet on desktop/JVM. */
actual fun shareText(text: String): Boolean = false
