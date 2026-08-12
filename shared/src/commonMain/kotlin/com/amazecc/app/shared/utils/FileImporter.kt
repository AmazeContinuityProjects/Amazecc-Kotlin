package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

/**
 * Returns a launcher that opens a file picker and delivers the picked file's text
 * content via [onResult] (null when cancelled or unreadable).
 */
@Composable
expect fun rememberFileImporter(onResult: (String?) -> Unit): () -> Unit
