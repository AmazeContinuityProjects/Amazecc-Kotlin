package com.amazecc.app.shared.domain

/**
 * D-174 — a real auto-scanned page, or `null` when the platform scanner could not produce one.
 *
 * Pure common model: no Android/iOS types, no I/O, no fabrication. [imageBytes] is always a JPEG
 * payload (the scanner host's PDF engine embeds JPEG bytes directly), [pdfBytes] is the optional
 * machine-generated PDF, [source] is the engine that actually produced the page, and [notice] is
 * honest copy the UI may show. Unsupported platforms never construct one of these.
 */
enum class ScanSource { ML_KIT, OPENCV }

/** A page returned by an on-device scanner. Defaults describe an empty, unprocessed result. */
data class ScannedDocument(
    val imageBytes: ByteArray = ByteArray(0),
    val pdfBytes: ByteArray? = null,
    val source: ScanSource = ScanSource.ML_KIT,
    val notice: String? = null,
)
