package com.amazecc.app.shared.utils

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.components.AmazeButton

@Composable
actual fun ShareIcsButton(icsContent: String) {
    val context = LocalContext.current
    AmazeButton(
        text = "Export ICS",
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_TEXT, icsContent)
            }
            context.startActivity(Intent.createChooser(intent, "Share Calendar"))
        },
        modifier = Modifier.padding(16.dp)
    )
}
