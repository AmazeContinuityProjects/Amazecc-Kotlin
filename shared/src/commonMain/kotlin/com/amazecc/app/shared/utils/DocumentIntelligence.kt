package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.PaperInference

/**
 * D-166 — on-device document intelligence.
 *
 * These are real platform capabilities, not simulations. Android implements
 * them with ML Kit Text Recognition v2 and the Gemini Nano Prompt API; every
 * other target deliberately returns an empty/null result instead of faking an
 * inference. Callers must surface that as "not available on this device".
 */
expect suspend fun recognizeDocumentText(bytes: ByteArray): List<String>

/** Returns `null` whenever the model is unavailable or the output cannot be parsed. */
expect suspend fun inferPaperDetails(text: String): PaperInference?

private const val UNSPECIFIED_NAME = "Scanned-Document"
private const val MAX_NAME_LENGTH = 80
private val ABSENT_TOKENS = setOf("null", "none", "n/a", "na", "unknown", "not available")
private val SEPARATOR_RUN = Regex("[-_]{2,}")

/**
 * Pure, deterministic filename builder. Uses only facts already present in
 * [inference]; it never guesses a subject, code, date or assessment type.
 *
 * Preference order: subject code, then subject name, then assessment type,
 * then date. The model's own `suggestedName` is used only as a last resort
 * because it is still extracted data. The result is ASCII-safe, contains no
 * path separators, is never blank, and falls back to "Scanned-Document".
 */
fun suggestedDocumentName(inference: PaperInference?): String {
    if (inference == null) return UNSPECIFIED_NAME

    val identifier = inference.subjectCode.fileNamePart() ?: inference.subject.fileNamePart()
    val assessment = inference.assessmentType.fileNamePart()
    val date = inference.documentDate.fileNamePart()
    val parts = listOfNotNull(identifier, assessment, date)

    val candidate = (if (parts.isEmpty()) {
        inference.suggestedName.fileNamePart()
    } else {
        parts.joinToString("-")
    }) ?: return UNSPECIFIED_NAME

    val bounded = if (candidate.length > MAX_NAME_LENGTH) {
        candidate.take(MAX_NAME_LENGTH).trim('-', '_')
    } else {
        candidate
    }
    return bounded.takeIf { it.isNotEmpty() } ?: UNSPECIFIED_NAME
}

/** Maps arbitrary model text to a single filename-safe token, or null if absent. */
private fun String?.fileNamePart(): String? {
    val source = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (source.lowercase() in ABSENT_TOKENS) return null

    val builder = StringBuilder(source.length)
    for (ch in source) {
        builder.append(
            when (ch) {
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_' -> ch
                else -> '-' // whitespace, illegal filename chars, control chars, non-ASCII
            }
        )
    }
    return builder.toString()
        .replace(SEPARATOR_RUN, "-")
        .trim('-', '_')
        .takeIf { it.isNotEmpty() }
}
