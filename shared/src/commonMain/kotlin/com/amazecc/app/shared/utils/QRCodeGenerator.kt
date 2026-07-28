@file:Suppress("MagicNumber", "LocalVariableName")
package com.amazecc.app.shared.utils

import kotlin.math.*

object QRCodeGenerator {

    fun generate(text: String): List<BooleanArray>? {
        val dataBytes = text.encodeToByteArray()
        val overheadBits = 4 + 8 + 4 // byte mode (0100) + char count (8 bits for v1-9) + max terminator
        val totalBitsNeeded = overheadBits + dataBytes.size * 8
        val totalBytesNeeded = (totalBitsNeeded + 7) / 8

        val version = selectVersion(totalBytesNeeded)
        val dataCodewords = totalDataCodewords(version)
        if (totalBytesNeeded > dataCodewords) return null

        val encoded = IntArray(dataCodewords)
        var bitPos = 0

        fun writeBits(value: Int, numBits: Int) {
            for (i in numBits - 1 downTo 0) {
                if (bitPos >= dataCodewords * 8) break
                val bit = (value shr i) and 1
                if (bit == 1) {
                    val byteIdx = bitPos / 8
                    val bitIdx = 7 - (bitPos % 8)
                    encoded[byteIdx] = encoded[byteIdx] or (1 shl bitIdx)
                }
                bitPos++
            }
        }

        writeBits(0b0100, 4)
        writeBits(dataBytes.size, 8)
        for (b in dataBytes) writeBits(b.toInt() and 0xFF, 8)

        val remainingBits = dataCodewords * 8 - bitPos
        if (remainingBits > 0) writeBits(0, minOf(4, remainingBits))

        var padByte = 0b1110_1100
        while (bitPos < dataCodewords * 8) {
            writeBits(padByte, 8)
            padByte = if (padByte == 0b1110_1100) 0b0001_0001 else 0b1110_1100
        }

        val ecBytes = ecBytesPerBlock(version)
        val blocks = blockCount(version)
        val ecData = rsEncode(encoded, ecBytes)
        val interleaved = interleave(encoded, ecData, blocks)

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }

        placeFinderPatterns(matrix, size)
        placeTimingPatterns(matrix, size)
        matrix[size - 8][8] = true
        placeData(matrix, interleaved, version, size)
        applyMask(matrix, size)
        placeFormatInfo(matrix, size, 0b010)

        return matrix.map { it.clone() }
    }

    private fun selectVersion(dataLen: Int): Int = when {
        dataLen <= 16 -> 1
        dataLen <= 28 -> 2
        dataLen <= 44 -> 3
        dataLen <= 64 -> 4
        dataLen <= 86 -> 5
        else -> 6
    }

    private fun ecBytesPerBlock(version: Int): Int = when (version) {
        1 -> 10; 2 -> 16; 3 -> 26; 4 -> 18; 5 -> 24; else -> 16
    }

    private fun blockCount(version: Int): Int = when (version) {
        1, 2, 3, 6 -> 1; 4, 5 -> 2; else -> 1
    }

    private fun totalDataCodewords(version: Int): Int = when (version) {
        1 -> 16; 2 -> 28; 3 -> 44; 4 -> 64; 5 -> 86; else -> 108
    }

    private fun rsEncode(data: IntArray, ecBytes: Int): IntArray {
        val rs = ReedSolomon(ecBytes)
        return rs.encode(data)
    }

    private fun interleave(data: IntArray, ec: IntArray, blocks: Int): IntArray {
        val total = data.size + ec.size
        val result = IntArray(total)
        val blockSize = data.size / blocks
        val ecBlockSize = ec.size / blocks
        var pos = 0
        for (b in 0 until blockSize) {
            for (blk in 0 until blocks) {
                result[pos++] = data[blk * blockSize + b]
            }
        }
        for (b in 0 until ecBlockSize) {
            for (blk in 0 until blocks) {
                result[pos++] = ec[blk * ecBlockSize + b]
            }
        }
        return result
    }

    private fun placeFinderPatterns(matrix: Array<BooleanArray>, size: Int) {
        for (fx in 0..size - 1 step size - 7) {
            val fy = 0
            for (i in -1..7) for (j in -1..7) {
                val x = fx + i; val y = fy + j
                if (x < 0 || x >= size || y < 0 || y >= size) continue
                matrix[x][y] = when {
                    i == -1 || i == 7 || j == -1 || j == 7 -> true
                    i in 1..5 && j in 1..5 -> false
                    else -> true
                }
            }
        }
        for (i in -1..7) for (j in -1..7) {
            val x = i; val y = size - 7 + j
            if (x < 0 || x >= size || y < 0 || y >= size) continue
            matrix[x][y] = when {
                i == -1 || i == 7 || j == -1 || j == 7 -> true
                i in 1..5 && j in 1..5 -> false
                else -> true
            }
        }
        for (i in 0 until 8) {
            if (i < size) { matrix[7][i] = false; matrix[i][7] = false }
            if (size - 1 - i >= 0) { matrix[7][size - 1 - i] = false; matrix[size - 1 - i][7] = false }
        }
        for (i in 0 until 8) {
            if (size - 1 - i >= 0) matrix[size - 1 - i][size - 1 - 7] = false
        }
    }

    private fun placeTimingPatterns(matrix: Array<BooleanArray>, size: Int) {
        for (i in 8 until size - 8) {
            matrix[i][6] = i % 2 == 0
            matrix[6][i] = i % 2 == 0
        }
    }

    private fun placeData(matrix: Array<BooleanArray>, data: IntArray, version: Int, size: Int) {
        var bitIdx = 0
        var col = size - 1
        var direction = -1

        while (col > 0 && bitIdx < data.size * 8) {
            if (col == 6) { col--; continue }
            val rows = if (direction == -1) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (offset in 0..1) {
                    val cx = col - offset
                    if (cx < 0 || isFunctionModule(matrix, row, cx, size)) continue
                    val bitVal = (data[bitIdx / 8] shr (7 - (bitIdx % 8))) and 1 == 1
                    if (bitIdx < data.size * 8) {
                        matrix[row][cx] = bitVal
                        bitIdx++
                    }
                }
            }
            col -= 2
            direction *= -1
        }
    }

    private fun isFunctionModule(matrix: Array<BooleanArray>, row: Int, col: Int, size: Int): Boolean {
        if (row < 9 && col < 9) return true
        if (row < 9 && col >= size - 8) return true
        if (row >= size - 8 && col < 9) return true
        if (row == 6 || col == 6) return true
        return false
    }

    private fun applyMask(matrix: Array<BooleanArray>, size: Int) {
        for (i in 0 until size) for (j in 0 until size) {
            if (isFunctionModule(matrix, i, j, size)) continue
            if ((i + j) % 2 == 0) matrix[i][j] = !matrix[i][j]
        }
    }

    private fun placeFormatInfo(matrix: Array<BooleanArray>, size: Int, maskPattern: Int) {
        val ecBits = 0b00 // M
        val dataBits = (ecBits shl 3) or (maskPattern and 0b111)
        var codeword = dataBits shl 10
        val generator = 0b10100110111
        for (i in 14 downTo 10) {
            if ((codeword shr i) and 1 == 1) {
                codeword = codeword xor (generator shl (i - 10))
            }
        }
        val formatVal = ((dataBits shl 10) or (codeword and 0x3FF)) xor 0b101010000010010
        val bits = BooleanArray(15) { ((formatVal shr (14 - it)) and 1) == 1 }

        for (i in 0..5) { matrix[8][i] = bits[i]; matrix[size - 1 - i][8] = bits[i] }
        matrix[8][7] = bits[6]; matrix[8][8] = bits[7]; matrix[7][8] = bits[8]
        for (i in 9..14) { matrix[14 - i][8] = bits[i] }
        matrix[8][size - 8] = true
        for (i in 0..6) { matrix[8][size - 7 + i] = bits[14 - i] }
    }
}

private class ReedSolomon(private val ecBytes: Int) {

    fun encode(data: IntArray): IntArray {
        val result = data.copyOf(data.size + ecBytes)
        for (i in data.indices) {
            if (result[i] != 0) {
                val factor = gfLog(result[i])
                for (j in generator.indices) {
                    result[i + j] = result[i + j] xor gfExp((factor + generator[j]) % 255)
                }
            }
        }
        return result.copyOfRange(data.size, result.size)
    }

    private val generator by lazy { buildGenerator() }

    private fun buildGenerator(): IntArray {
        var gen = intArrayOf(1)
        for (i in 0 until ecBytes) {
            val term = intArrayOf(1, gfExp(i))
            gen = gfMultiplyPoly(gen, term)
        }
        return gen
    }

    private fun gfMultiplyPoly(a: IntArray, b: IntArray): IntArray {
        val result = IntArray(a.size + b.size - 1)
        for (i in a.indices) for (j in b.indices) {
            result[i + j] = result[i + j] xor gfMul(a[i], b[j])
        }
        return result
    }

    companion object {
        private val LOG_TABLE = IntArray(256)
        private val EXP_TABLE = IntArray(256)

        init {
            var v = 1
            for (i in 0 until 255) {
                EXP_TABLE[i] = v
                LOG_TABLE[v] = i
                v = v shl 1
                if (v >= 256) v = v xor 0b100011101
            }
            EXP_TABLE[255] = EXP_TABLE[0]
        }

        fun gfLog(a: Int): Int = if (a == 0) 0 else LOG_TABLE[a]
        fun gfExp(a: Int): Int = EXP_TABLE[a % 255]
        fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else EXP_TABLE[(LOG_TABLE[a] + LOG_TABLE[b]) % 255]
    }
}
