package com.amazecc.app.shared.ui.screens

import androidx.compose.runtime.Composable
import com.amazecc.app.shared.api.AmazeClient
import com.amazecc.app.shared.model.ArrearResponse

@Composable
fun DocumentsScreen() {
    TabbedKeyValueScreen(
        title = "Documents",
        description = "Bonafide, transcripts, and learning",
        loadingText = "Loading documents...",
        tabLabels = listOf("Bonafide", "E-Transcript", "Additional Learning"),
        endpointKeys = listOf("bonafide", "e-transcript", "additional-learning")
    ) { ep ->
        when (ep) {
            "bonafide" -> AmazeClient.getBonafide()
            "e-transcript" -> AmazeClient.getETranscript()
            "additional-learning" -> AmazeClient.getAdditionalLearning()
            else -> ArrearResponse(success = false, message = "Unknown")
        }
    }
}
