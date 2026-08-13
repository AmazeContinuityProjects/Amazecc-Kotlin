package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

@Composable
expect fun rememberFileSaver(): (fileName: String, bytes: ByteArray) -> Boolean

@Composable
expect fun rememberPdfOpener(): (fileName: String, bytes: ByteArray) -> Boolean
