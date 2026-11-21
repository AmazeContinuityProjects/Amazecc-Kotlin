@file:Suppress("MagicNumber")
package com.amazecc.app.shared.utils

/**
 * D-145: dependency-free QR image export for commonMain.
 *
 * Web's ShareHandleSheet downloads a PNG of the invite QR. The shared module has no image codec,
 * so this writer emits a standards-compliant PNG 1.4 directly from the same boolean matrix the
 * Compose canvas renders: 8-bit truecolour RGB, filter type None, stored (uncompressed) DEFLATE
 * blocks inside a zlib wrapper, and a CRC32 on every chunk. No file I/O and no platform API here —
 * the caller owns the bytes and decides where they go.
 */

/** Renders [matrix] (true = black module) to a PNG byte array.
 *
 *  White [quietZone] modules surround the symbol and each module is [moduleSize] pixels square.
 *  Negative/too-small sizing arguments are clamped so the result is always a valid image. An
 *  empty or null-shaped matrix yields the smallest valid PNG (one white pixel) instead of throwing.
 */
fun encodeQrPng(
    matrix: List<BooleanArray>,
    moduleSize: Int = 8,
    quietZone: Int = 4
): ByteArray {
    val scale = maxOf(1, moduleSize)
    val quiet = maxOf(0, quietZone)
    val moduleCount = matrix.size

    if (moduleCount == 0 || matrix.all { it.isEmpty() }) {
        // A blank input still produces a real, decodable image — never an exception or fake bytes.
        return encodePng(1, 1, byteArrayOf(255.toByte(), 255.toByte(), 255.toByte()))
    }

    val side = (moduleCount + 2 * quiet) * scale
    val rgb = ByteArray(side * side * 3)
    // White background, including the quiet zone.
    for (i in rgb.indices step 3) {
        rgb[i] = 255.toByte()
        rgb[i + 1] = 255.toByte()
        rgb[i + 2] = 255.toByte()
    }
    // Black modules. Ragged/short rows are tolerated: a missing cell stays white.
    for (row in 0 until moduleCount) {
        val cells = matrix[row]
        for (col in 0 until moduleCount) {
            if (cells.getOrNull(col) != true) continue
            val x0 = (col + quiet) * scale
            val y0 = (row + quiet) * scale
            for (dx in 0 until scale) {
                for (dy in 0 until scale) {
                    val offset = ((y0 + dy) * side + (x0 + dx)) * 3
                    rgb[offset] = 0
                    rgb[offset + 1] = 0
                    rgb[offset + 2] = 0
                }
            }
        }
    }
    return encodePng(side, side, rgb)
}

// ---- PNG plumbing (adapted from ui/screens/m3e/M3EExamScheduleExports.kt; those helpers are private) ----

private fun encodePng(width: Int, height: Int, rgb: ByteArray): ByteArray {
    val raw = ByteArray(height * (1 + width * 3))
    var cursor = 0
    for (row in 0 until height) {
        raw[cursor++] = 0 // filter type: None
        val rowStart = row * width * 3
        rgb.copyInto(raw, destinationOffset = cursor, startIndex = rowStart, endIndex = rowStart + width * 3)
        cursor += width * 3
    }
    val zlib = byteArrayOf(0x78, 0x01) + deflateStored(raw) + adler32(raw)
    val out = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    val ihdr = ByteArray(13)
    writeInt(ihdr, 0, width)
    writeInt(ihdr, 4, height)
    ihdr[8] = 8 // bit depth
    ihdr[9] = 2 // truecolour RGB
    ihdr[10] = 0 // compression
    ihdr[11] = 0 // filter
    ihdr[12] = 0 // interlace
    return out + chunk("IHDR", ihdr) + chunk("IDAT", zlib) + chunk("IEND", ByteArray(0))
}

/** Stored (uncompressed) DEFLATE blocks — a valid zlib payload with no platform dependency. */
private fun deflateStored(raw: ByteArray): ByteArray {
    val out = mutableListOf<Byte>()
    var offset = 0
    while (offset < raw.size) {
        val length = minOf(65_535, raw.size - offset)
        val final = offset + length == raw.size
        out.add(if (final) 1 else 0) // BFINAL=1/0, BTYPE=00
        out.add((length and 0xFF).toByte())
        out.add(((length shr 8) and 0xFF).toByte())
        val inverse = length.inv() and 0xFFFF
        out.add((inverse and 0xFF).toByte())
        out.add(((inverse shr 8) and 0xFF).toByte())
        for (i in offset until offset + length) out.add(raw[i])
        offset += length
    }
    return out.toByteArray()
}

private fun adler32(data: ByteArray): ByteArray {
    var a = 1
    var b = 0
    data.forEach { byte ->
        a = (a + (byte.toInt() and 0xFF)) % 65_521
        b = (b + a) % 65_521
    }
    val value = (b shl 16) or a
    return byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )
}

private fun chunk(type: String, data: ByteArray): ByteArray {
    val out = ByteArray(12 + data.size)
    writeInt(out, 0, data.size)
    val typeBytes = type.toByteArray()
    typeBytes.copyInto(out, 4)
    data.copyInto(out, 8)
    val crcInput = ByteArray(4 + data.size)
    typeBytes.copyInto(crcInput, 0)
    data.copyInto(crcInput, 4)
    writeInt(out, 8 + data.size, crc32(crcInput))
    return out
}

private val crcTable = IntArray(256).also { table ->
    for (n in 0 until 256) {
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        table[n] = c
    }
}

private fun crc32(data: ByteArray): Int {
    var crc = 0xFFFFFFFF.toInt()
    data.forEach { byte -> crc = crcTable[(crc xor (byte.toInt() and 0xFF)) and 0xFF] xor (crc ushr 8) }
    return crc xor 0xFFFFFFFF.toInt()
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value ushr 24).toByte()
    target[offset + 1] = (value ushr 16).toByte()
    target[offset + 2] = (value ushr 8).toByte()
    target[offset + 3] = value.toByte()
}
