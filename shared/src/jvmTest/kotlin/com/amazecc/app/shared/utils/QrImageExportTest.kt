package com.amazecc.app.shared.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-145: the QR PNG writer must emit a real, decodable image — never a stub or fake success. */
class QrImageExportTest {

    private val checkerboard = listOf(
        booleanArrayOf(true, false, true),
        booleanArrayOf(false, true, false),
        booleanArrayOf(true, false, true)
    )

    @Test
    fun pngHasMagicIhdrDimensionsAndIend() {
        val png = encodeQrPng(checkerboard, moduleSize = 2, quietZone = 1)
        val decoded = decodePng(png)

        assertTrue(png.copyOfRange(0, 8).contentEquals(PNG_MAGIC), "PNG magic bytes must lead the file")
        val expectedSide = 3 * 2 + 2 * 1 * 2
        assertEquals(expectedSide, decoded.width)
        assertEquals(expectedSide, decoded.height)
        // The decode helper above also verifies every chunk's CRC32.
        assertTrue(png.copyOfRange(png.size - 8, png.size - 4).contentEquals("IEND".toByteArray()), "IEND must be last")
    }

    @Test
    fun rendersBlackModulesAndWhiteQuietZoneAtExpectedCoordinates() {
        // moduleSize=2, quietZone=1: module (c,r) covers pixels [(c+1)*2..+1, (r+1)*2..+1].
        val decoded = decodePng(encodeQrPng(checkerboard, moduleSize = 2, quietZone = 1))

        assertEquals(BLACK, decoded.pixel(3, 3), "module (0,0) is true -> black")
        assertEquals(WHITE, decoded.pixel(5, 3), "module (1,0) is false -> white")
        assertEquals(BLACK, decoded.pixel(5, 5), "module (1,1) is true -> black")
        assertEquals(WHITE, decoded.pixel(0, 0), "quiet zone must stay white")
        assertEquals(WHITE, decoded.pixel(decoded.width - 1, decoded.height - 1), "bottom-right quiet zone white")
    }

    @Test
    fun emptyMatrixProducesSmallestValidPng() {
        val decoded = decodePng(encodeQrPng(emptyList()))

        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
        assertEquals(WHITE, decoded.pixel(0, 0))
    }

    @Test
    fun nullShapedRowsAlsoProduceValidPng() {
        val decoded = decodePng(encodeQrPng(listOf(BooleanArray(0), BooleanArray(0))))
        assertEquals(1, decoded.width)
        assertEquals(1, decoded.height)
    }

    @Test
    fun largeMatrixSpansMultipleDeflateBlocks() {
        // 150 * (1 + 150*3) = 67,650 raw bytes > 65,535, so the writer must emit two stored blocks.
        val big = List(150) { BooleanArray(150) { true } }
        val png = encodeQrPng(big, moduleSize = 1, quietZone = 0)
        val decoded = decodePng(png)

        assertEquals(150, decoded.width)
        assertEquals(150, decoded.height)
        assertEquals(BLACK, decoded.pixel(0, 0))
        assertEquals(BLACK, decoded.pixel(149, 149))
        assertEquals(2, countStoredBlocks(png))
    }

    private data class DecodedPng(val width: Int, val height: Int, val rgb: ByteArray) {
        fun pixel(x: Int, y: Int): Triple<Int, Int, Int> {
            val offset = (y * width + x) * 3
            return Triple(
                rgb[offset].toInt() and 0xFF,
                rgb[offset + 1].toInt() and 0xFF,
                rgb[offset + 2].toInt() and 0xFF
            )
        }
    }

    /** Parses the real PNG: verifies every chunk CRC, unfilters stored DEFLATE rows itself. */
    private fun decodePng(png: ByteArray): DecodedPng {
        assertTrue(png.copyOfRange(0, 8).contentEquals(PNG_MAGIC), "must start with PNG signature")
        var offset = 8
        var ihdr: ByteArray? = null
        val idat = mutableListOf<Byte>()
        var sawIend = false
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val data = png.copyOfRange(offset + 8, offset + 8 + length)
            val expectedCrc = readInt(png, offset + 8 + length)
            val actualCrc = crc32(png.copyOfRange(offset + 4, offset + 8 + length))
            assertEquals(expectedCrc, actualCrc, "CRC32 must be valid for chunk $type")
            when (type) {
                "IHDR" -> {
                    ihdr = data
                    assertEquals(8, data[8].toInt(), "bit depth 8")
                    assertEquals(2, data[9].toInt(), "truecolour RGB")
                }
                "IDAT" -> idat.addAll(data.asIterable())
                "IEND" -> sawIend = true
            }
            offset += 12 + length
            if (sawIend) break
        }
        assertTrue(sawIend, "IEND chunk must be present")
        val header = checkNotNull(ihdr) { "IHDR chunk must be present" }
        val width = readInt(header, 0)
        val height = readInt(header, 4)
        assertTrue(width > 0 && height > 0, "dimensions must be positive")

        val raw = inflateStored(idat.toByteArray())
        val rgb = ByteArray(width * height * 3)
        for (row in 0 until height) {
            val src = row * (1 + width * 3)
            assertEquals(0, raw[src].toInt(), "filter type must be None")
            raw.copyInto(rgb, row * width * 3, src + 1, src + 1 + width * 3)
        }
        return DecodedPng(width, height, rgb)
    }

    private fun countStoredBlocks(png: ByteArray): Int {
        var offset = 8
        var blocks = 0
        var idat: ByteArray? = null
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") idat = png.copyOfRange(offset + 8, offset + 8 + length)
            offset += 12 + length
            if (type == "IEND") break
        }
        val zlib = checkNotNull(idat)
        var pos = 2 // zlib header
        while (pos < zlib.size - 4) { // exclude adler32 trailer
            val header = zlib[pos++].toInt() and 0xFF
            assertEquals(0, header and 0x06, "BTYPE 00 (stored)")
            val len = (zlib[pos].toInt() and 0xFF) or ((zlib[pos + 1].toInt() and 0xFF) shl 8)
            pos += 4 + len // length + inverse + payload
            blocks++
            if (header and 1 == 1) break
        }
        return blocks
    }

    private fun inflateStored(zlib: ByteArray): ByteArray {
        assertEquals(0x78, zlib[0].toInt() and 0xFF, "zlib header")
        var pos = 2
        val out = mutableListOf<Byte>()
        while (true) {
            val header = zlib[pos++].toInt() and 0xFF
            assertEquals(0, header and 0x06, "only stored DEFLATE blocks are expected")
            val final = header and 1
            val len = (zlib[pos].toInt() and 0xFF) or ((zlib[pos + 1].toInt() and 0xFF) shl 8)
            val inverse = (zlib[pos + 2].toInt() and 0xFF) or ((zlib[pos + 3].toInt() and 0xFF) shl 8)
            assertEquals(len.inv() and 0xFFFF, inverse, "stored block length complement")
            pos += 4
            repeat(len) { out.add(zlib[pos++]) }
            if (final == 1) break
        }
        return out.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (byte in data) {
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc xor 0xFFFFFFFF.toInt()
    }

    private companion object {
        val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val BLACK = Triple(0, 0, 0)
        val WHITE = Triple(255, 255, 255)
    }
}
