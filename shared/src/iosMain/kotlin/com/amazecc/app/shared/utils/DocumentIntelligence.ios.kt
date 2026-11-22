package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.PaperInference

/**
 * D-166 — iOS has no on-device OCR/Gemini Nano bridge wired up in this build.
 * These are honest no-ops: they return empty/null so callers report the feature
 * as unavailable rather than displaying a fabricated inference.
 */
actual suspend fun recognizeDocumentText(bytes: ByteArray): List<String> = emptyList()

actual suspend fun inferPaperDetails(text: String): PaperInference? = null
