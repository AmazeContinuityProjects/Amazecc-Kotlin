package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

/**
 * D-009 — real media capture / sharing seams for app features that need them.
 *
 * These are deliberately tiny `@Composable expect`s so every caller registers its
 * ActivityResult launcher in the *host* composition, never inside a `Dialog`'s
 * subcomposition. Launchers registered inside `M3EDismissibleSheet`'s `Dialog`
 * can be disposed while the Activity is already saving state, which is the
 * crash this seam exists to avoid.
 *
 * Each launcher reports failure honestly (null / false); none of them fake a
 * captured image or a successful share.
 */

/** Opens the system image picker for image files and delivers the chosen file's raw bytes. */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit

/** Opens the device camera and delivers the captured photo's raw bytes. */
@Composable
expect fun rememberPhotoCapture(onResult: (ByteArray?) -> Unit): () -> Unit

/** Shares a byte payload through the platform's share surface; false when unavailable. */
@Composable
expect fun rememberFileSharer(): (fileName: String, bytes: ByteArray, mimeType: String) -> Boolean
