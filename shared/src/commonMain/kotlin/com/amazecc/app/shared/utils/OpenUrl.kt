package com.amazecc.app.shared.utils

import androidx.compose.runtime.Composable

@Composable
expect fun rememberUrlOpener(): (url: String) -> Unit
