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

        val blocks = blockCount(version)
        val ecPerBlock = ecBytesPerBlock(version)
        val dataPerBlock = encoded.size / blocks
        val ecStream = rsEncodePerBlock(encoded, dataPerBlock, blocks, ecPerBlock)
        val sequence = interleave(encoded, ecStream, blocks, dataPerBlock, ecPerBlock)

        val size = version * 4 + 17
        val matrix = Array(size) { BooleanArray(size) }
        val function = mutableSetOf<Long>()

        placeFinderPatterns(matrix, size, function)
        placeTimingPatterns(matrix, size, function)
        placeAlignmentPatterns(matrix, version, size, function)
        matrix[size - 8][8] = true
        function.add((size - 8).toLong() * size + 8)

        placeFormatInfo(matrix, size, 0b010, function)
        placeData(matrix, sequence, size, function)
        applyMask(matrix, size, function)
        placeFormatBits(matrix, size, 0b010)

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
        1, 2, 3 -> 1; 4, 5 -> 2; else -> 4
    }

    private fun totalDataCodewords(version: Int): Int = when (version) {
        1 -> 16; 2 -> 28; 3 -> 44; 4 -> 64; 5 -> 86; else -> 108
    }

    private fun rsEncodePerBlock(data: IntArray, dataPerBlock: Int, blocks: Int, ecPerBlock: Int): List<IntArray> {
        val result = mutableListOf<IntArray>()
        for (blk in 0 until blocks) {
            val block = data.copyOfRange(blk * dataPerBlock, (blk + 1) * dataPerBlock)
            result.add(ReedSolomon(ecPerBlock).encode(block))
        }
        return result
    }

    private fun interleave(data: IntArray, ecBlocks: List<IntArray>, blocks: Int, dataSize: Int, ecSize: Int): IntArray {
        val result = IntArray(dataSize * blocks + ecSize * blocks)
        var pos = 0
        for (b in 0 until dataSize) {
            for (blk in 0 until blocks) {
                result[pos++] = data[blk * dataSize + b]
            }
        }
        for (b in 0 until ecSize) {
            for (blk in 0 until blocks) {
                result[pos++] = ecBlocks[blk][b]
            }
        }
        return result
    }

    private fun placeFinderPatterns(matrix: Array<BooleanArray>, size: Int, fn: MutableSet<Long>) {
        fun drawPattern(tx: Int, ty: Int) {
            for (i in -1..7) for (j in -1..7) {
                val x = tx + i; val y = ty + j
                if (x < 0 || x >= size || y < 0 || y >= size) continue
                matrix[x][y] = when {
                    i == -1 || i == 7 || j == -1 || j == 7 -> true
                    i in 1..5 && j in 1..5 -> false
                    else -> true
                }
                fn.add(x.toLong() * size + y)
            }
        }
        drawPattern(0, 0)
        drawPattern(size - 7, 0)
        drawPattern(0, size - 7)
        for (i in 0 until 8) {
            if (i < size) {
                matrix[7][i] = false; matrix[i][7] = false
                fn.add(7L * size + i); fn.add(i.toLong() * size + 7)
            }
            if (size - 1 - i >= 0) {
                matrix[7][size - 1 - i] = false
                matrix[size - 1 - i][7] = false
                fn.add(7L * size + (size - 1 - i))
                fn.add((size - 1 - i).toLong() * size + 7)
            }
        }
        for (i in 0 until 8) {
            if (size - 1 - i >= 0) {
                matrix[size - 1 - i][size - 1 - 7] = false
                fn.add((size - 1 - i).toLong() * size + (size - 1 - 7))
            }
        }
    }

    private fun placeTimingPatterns(matrix: Array<BooleanArray>, size: Int, fn: MutableSet<Long>) {
        for (i in 8 until size - 8) {
            matrix[i][6] = i % 2 == 0
            matrix[6][i] = i % 2 == 0
            fn.add(i.toLong() * size + 6)
            fn.add(6L * size + i)
        }
    }

    private fun placeAlignmentPatterns(matrix: Array<BooleanArray>, version: Int, size: Int, fn: MutableSet<Long>) {
        if (version == 1) return
        val last = 4 * version + 10
        val coords = listOf(6, last)
        fun overlapsFinder(r: Int, c: Int): Boolean =
            (r in 0..8 && c in 0..8) || (r in 0..8 && c in (size - 9) until size) || (r in (size - 9) until size && c in 0..8)
        for (r in coords) for (c in coords) {
            if (overlapsFinder(r, c)) continue
            for (dr in -2..2) for (dc in -2..2) {
                val x = r + dr; val y = c + dc
                if (x < 0 || x >= size || y < 0 || y >= size) continue
                matrix[x][y] = (abs(dr) == 2 || abs(dc) == 2) || (dr == 0 && dc == 0)
                fn.add(x.toLong() * size + y)
            }
        }
    }

    private fun placeData(matrix: Array<BooleanArray>, data: IntArray, size: Int, fn: MutableSet<Long>) {
        var bitIdx = 0
        var col = size - 1
        var direction = -1

        while (col > 0 && bitIdx < data.size * 8) {
            if (col == 6) { col--; continue }
            val rows = if (direction == -1) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (offset in 0..1) {
                    val cx = col - offset
                    if (cx < 0 || fn.contains(row.toLong() * size + cx)) continue
                    val bitVal = (data[bitIdx / 8] shr (7 - (bitIdx % 8))) and 1 == 1
                    matrix[row][cx] = bitVal
                    bitIdx++
                }
            }
            col -= 2
            direction *= -1
        }
    }

    private fun applyMask(matrix: Array<BooleanArray>, size: Int, fn: MutableSet<Long>) {
        for (i in 0 until size) for (j in 0 until size) {
            if (fn.contains(i.toLong() * size + j)) continue
            if ((i + j) % 2 == 0) matrix[i][j] = !matrix[i][j]
        }
    }

    private fun formatBits(maskPattern: Int): Int {
        val ecBits = 0b00 // M
        val dataBits = (ecBits shl 3) or (maskPattern and 0b111)
        var codeword = dataBits shl 10
        val generator = 0b10100110111
        for (i in 14 downTo 10) {
            if ((codeword shr i) and 1 == 1) {
                codeword = codeword xor (generator shl (i - 10))
            }
        }
        return ((dataBits shl 10) or (codeword and 0x3FF)) xor 0b101010000010010
    }

    private fun placeFormatInfo(matrix: Array<BooleanArray>, size: Int, maskPattern: Int, fn: MutableSet<Long>) {
        val formatVal = formatBits(maskPattern)
        val bits = BooleanArray(15) { ((formatVal shr (14 - it)) and 1) == 1 }

        for (i in 0..5) {
            matrix[8][i] = bits[i]; fn.add(8L * size + i)
            matrix[size - 1 - i][8] = bits[i]; fn.add((size - 1 - i).toLong() * size + 8)
        }
        matrix[8][7] = bits[6]; fn.add(8L * size + 7)
        matrix[8][8] = bits[7]; fn.add(8L * size + 8)
        matrix[7][8] = bits[8]; fn.add(7L * size + 8)
        for (i in 9..14) {
            matrix[14 - i][8] = bits[i]
            fn.add((14 - i).toLong() * size + 8)
        }
        matrix[8][size - 8] = true
        fn.add(8L * size + (size - 8))
        for (i in 0..6) {
            matrix[8][size - 7 + i] = bits[14 - i]
            fn.add(8L * size + (size - 7 + i))
        }
    }

    private fun placeFormatBits(matrix: Array<BooleanArray>, size: Int, maskPattern: Int) {
        val formatVal = formatBits(maskPattern)
        val bits = BooleanArray(15) { ((formatVal shr (14 - it)) and 1) == 1 }

        for (i in 0..5) {
            matrix[8][i] = bits[i]
            matrix[size - 1 - i][8] = bits[i]
        }
        matrix[8][7] = bits[6]
        matrix[8][8] = bits[7]
        matrix[7][8] = bits[8]
        for (i in 9..14) {
            matrix[14 - i][8] = bits[i]
        }
        matrix[8][size - 8] = true
        for (i in 0..6) {
            matrix[8][size - 7 + i] = bits[14 - i]
        }
    }
}

private class ReedSolomon(private val ecBytes: Int) {

    fun encode(data: IntArray): IntArray {
        val result = data.copyOf(data.size + ecBytes)
        for (i in data.indices) {
            if (result[i] != 0) {
                val factor = gfLog(result[i])
                for (j in generator.indices) {
                    val coeffLog = if (generator[j] != 0) gfLog(generator[j]) else -1
                    if (coeffLog >= 0) {
                        result[i + j] = result[i + j] xor gfExp((factor + coeffLog) % 255)
                    }
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