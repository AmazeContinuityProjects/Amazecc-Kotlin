package com.amazecc.app.shared.nfc

import androidx.compose.runtime.staticCompositionLocalOf

interface NfcManager {
    fun startSharing(data: String)
    fun stopSharing()
    fun startListening(onDataReceived: (String) -> Unit)
    fun stopListening()
}

val LocalNfcManager = staticCompositionLocalOf<NfcManager?> { null }
