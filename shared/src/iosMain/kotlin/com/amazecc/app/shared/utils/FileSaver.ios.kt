package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFileSaver(): (fileName: String, bytes: ByteArray) -> Boolean = { _, _ -> false }
