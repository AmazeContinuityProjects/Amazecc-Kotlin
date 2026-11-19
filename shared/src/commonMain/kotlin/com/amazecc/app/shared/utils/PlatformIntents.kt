package com.amazecc.app.shared.utils

/**
 * Small platform hand-offs for UI actions. Each function returns `true` when the platform
 * successfully handled the action and `false` otherwise; none of them throw.
 */
expect fun openExternalUrl(url: String): Boolean

expect fun dialPhoneNumber(number: String): Boolean

expect fun openDocumentBytes(bytes: ByteArray, fileName: String, mimeType: String): Boolean

expect fun shareText(text: String): Boolean
