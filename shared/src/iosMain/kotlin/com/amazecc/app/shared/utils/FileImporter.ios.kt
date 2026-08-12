package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFileImporter(onResult: (String?) -> Unit): () -> Unit = {
    onResult(null)
}
