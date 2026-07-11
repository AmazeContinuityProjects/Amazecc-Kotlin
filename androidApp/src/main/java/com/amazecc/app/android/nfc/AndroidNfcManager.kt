package com.amazecc.app.android.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.util.Log
import com.amazecc.app.shared.nfc.NfcManager
import java.nio.charset.Charset

class AndroidNfcManager(private val activity: Activity) : NfcManager {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var isListening = false
    private var onDataReceivedCallback: ((String) -> Unit)? = null

    override fun startSharing(data: String) {
        if (nfcAdapter == null) return
        try {
            val record = NdefRecord.createMime(
                "application/vnd.com.amazecc.app",
                data.toByteArray(Charset.forName("US-ASCII"))
            )
            val msg = NdefMessage(arrayOf(record))
            
            // Note: setNdefPushMessage is removed in API 34. 
            // Real peer-to-peer sharing on Android 14+ requires Nearby Connections API or HCE.
            Log.d("NfcManager", "Sharing data via NFC is unsupported on API 34+ without HCE.")
        } catch (e: Exception) {
            Log.e("NfcManager", "Error in startSharing", e)
        }
    }

    override fun stopSharing() {
        if (nfcAdapter == null) return
        try {
            Log.d("NfcManager", "Stopped sharing data via NFC")
        } catch (e: Exception) {
            Log.e("NfcManager", "Error stopping sharing", e)
        }
    }

    override fun startListening(onDataReceived: (String) -> Unit) {
        if (nfcAdapter == null) return
        isListening = true
        onDataReceivedCallback = onDataReceived

        val options = android.os.Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)

        nfcAdapter.enableReaderMode(
            activity,
            { tag ->
                // Try to read NDEF message from tag
                val ndef = android.nfc.tech.Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    val ndefMessage = ndef.cachedNdefMessage
                    if (ndefMessage != null) {
                        for (record in ndefMessage.records) {
                            val payload = String(record.payload, Charset.forName("US-ASCII"))
                            activity.runOnUiThread {
                                onDataReceivedCallback?.invoke(payload)
                            }
                        }
                    }
                    ndef.close()
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            options
        )
        Log.d("NfcManager", "Started listening for NFC tags")
    }

    override fun stopListening() {
        if (nfcAdapter == null) return
        isListening = false
        onDataReceivedCallback = null
        nfcAdapter.disableReaderMode(activity)
    }
}
