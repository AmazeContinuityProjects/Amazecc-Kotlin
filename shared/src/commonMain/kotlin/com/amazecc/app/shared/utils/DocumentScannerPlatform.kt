package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.domain.ScannedDocument

/**
 * D-174 — platform seam for automatic document scanning (edge detection, perspective
 * correction, deskew and cleanup).
 *
 * Like [rememberPhotoCapture] and [rememberGeotagCamera], this is a tiny `@Composable expect`
 * registered in the *host* composition so its ActivityResult launcher is never owned by a
 * dialog that may be disposed while the Activity saves state. The returned lambda starts the
 * scan; every actual reports failure honestly with `null` so the host keeps its manual
 * import/crop path instead of fabricating a processed page.
 */
@Composable
expect fun rememberDocumentScanner(onResult: (ScannedDocument?) -> Unit): () -> Unit

/**
 * D-174 — explicit, user-triggered OpenCV fallback on an image that is ALREADY loaded.
 *
 * This is deliberately separate from [rememberDocumentScanner]: ML Kit's structured result
 * Intent does not reliably carry a plain image URI, so an implicit "read data.data / EXTRA_STREAM
 * then run OpenCV" fallback was effectively unreachable. The host now offers a labelled control
 * that passes the loaded image bytes here instead. Callers run this off Main; on a platform with
 * no OpenCV bridge (desktop, iOS) it returns `null` honestly so the UI can say so. A `null` result
 * means no page was found — never a fabricated straightened image.
 */
expect suspend fun straightenDocumentWithOpenCV(bytes: ByteArray): ByteArray?
