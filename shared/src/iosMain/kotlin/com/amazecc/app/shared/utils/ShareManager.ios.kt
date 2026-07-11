package com.amazecc.app.shared.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.components.AmazeButton

@Composable
actual fun ShareIcsButton(icsContent: String) {
    val clipboardManager = LocalClipboardManager.current
    AmazeButton(
        text = "Copy ICS to Clipboard",
        onClick = {
            clipboardManager.setText(AnnotatedString(icsContent))
        },
        modifier = Modifier.padding(16.dp)
    )
}
