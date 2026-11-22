package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.domain.ScannedDocument

/**
 * D-174 — no native document-scanner bridge is wired into this KMP iOS build.
 *
 * Fail closed: the launcher immediately reports "nothing scanned", so the host shows its honest
 * "you can still import or photograph a page" copy. It never fabricates a JPEG or claims a source.
 */
@Composable
actual fun rememberDocumentScanner(onResult: (ScannedDocument?) -> Unit): () -> Unit = { onResult(null) }

/** No OpenCV native bridge is wired into this KMP iOS build — report no result, never fabricate. */
actual suspend fun straightenDocumentWithOpenCV(bytes: ByteArray): ByteArray? = null
