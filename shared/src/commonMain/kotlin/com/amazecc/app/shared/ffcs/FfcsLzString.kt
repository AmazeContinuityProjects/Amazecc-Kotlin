package com.amazecc.app.shared.ffcs

/**
 * D-179 — byte-compatible Kotlin port of lz-string v1.4.5's
 * `compressToEncodedURIComponent` / `decompressFromEncodedURIComponent`.
 *
 * Source of truth (all verbatim algorithm, no fabricated compression):
 *  - `ffcs-planner/scripts/ts/node_modules/lz-string/libs/lz-string.js:9-104, 109-324, 332-492`
 *  - `ffcs-planner/scripts/python/ffcs_core/codec.py:28-254` (cross-checked port)
 *
 * The golden vectors in `assets/golden-share.json` were generated with the real
 * npm `lz-string` package. Any change to the bit writer, dictionary growth or
 * URI-safe alphabet below breaks byte equality with every shipped share link.
 *
 * Kotlin `Char` is a UTF-16 code unit, exactly what JS `charCodeAt` returns, so
 * the algorithm works directly on `String` indices — including surrogate pairs.
 */
object FfcsLzString {

    /** lz-string's URI-safe alphabet — note `+` and `$` instead of `+` and `/`. */
    private const val KEY_STR_URI_SAFE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+-$"
    private const val BITS_PER_CHAR = 6
    private const val RESET_VALUE = 32
    private val REVERSE: Map<Char, Int> = KEY_STR_URI_SAFE.withIndex().associate { it.value to it.index }

    /** `LZString.compressToEncodedURIComponent(input)`. */
    fun compressToEncodedUriComponent(input: String): String {
        val dictionary = HashMap<String, Int>()
        val dictionaryToCreate = HashMap<String, Boolean>()
        val data = StringBuilder()
        var dataVal = 0
        var dataPosition = 0
        var enlargeIn = 2
        var dictSize = 3
        var numBits = 2
        var w = ""

        /** Write one zero bit (the "new literal" marker prefix). */
        fun emitZero() {
            dataVal = dataVal shl 1
            if (dataPosition == BITS_PER_CHAR - 1) {
                data.append(KEY_STR_URI_SAFE[dataVal])
                dataVal = 0
                dataPosition = 0
            } else {
                dataPosition++
            }
        }

        /** Write `count` low bits of `value`, least significant first. */
        fun emitValue(valueInitial: Int, count: Int) {
            var value = valueInitial
            repeat(count) {
                dataVal = (dataVal shl 1) or (value and 1)
                if (dataPosition == BITS_PER_CHAR - 1) {
                    data.append(KEY_STR_URI_SAFE[dataVal])
                    dataVal = 0
                    dataPosition = 0
                } else {
                    dataPosition++
                }
                value = value shr 1
            }
        }

        /** Dictionary growth: after `enlargeIn` emissions, widen each code by one bit. */
        fun enlarge() {
            enlargeIn--
            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }

        /** Emit the code for a word: either a fresh literal or a dictionary entry. */
        fun emitWord(word: String) {
            if (dictionaryToCreate.containsKey(word)) {
                val first = word[0].code
                if (first < 256) {
                    repeat(numBits) { emitZero() }
                    emitValue(first, 8)
                } else {
                    emitValue(1, numBits)
                    emitValue(first, 16)
                }
                enlarge()
                dictionaryToCreate.remove(word)
            } else {
                emitValue(dictionary.getValue(word), numBits)
            }
            enlarge()
        }

        for (i in input.indices) {
            val c = input[i].toString()
            if (!dictionary.containsKey(c)) {
                dictionary[c] = dictSize++
                dictionaryToCreate[c] = true
            }
            val wc = w + c
            if (dictionary.containsKey(wc)) {
                w = wc
            } else {
                emitWord(w)
                dictionary[wc] = dictSize++
                w = c
            }
        }

        if (w.isNotEmpty()) emitWord(w)

        // End-of-stream marker (value 2), least significant bit first.
        emitValue(2, numBits)

        // Flush the last partial character by shifting in one final zero.
        while (true) {
            dataVal = dataVal shl 1
            if (dataPosition == BITS_PER_CHAR - 1) {
                data.append(KEY_STR_URI_SAFE[dataVal])
                break
            }
            dataPosition++
        }
        return data.toString()
    }

    /** `LZString.decompressFromEncodedURIComponent(encoded)`. */
    fun decompressFromEncodedUriComponent(encoded: String?): String? {
        if (encoded == null) return ""
        if (encoded.isEmpty()) return null

        // JS normalises a space back to '+' before reading the URI-safe alphabet.
        val input = encoded.replace(' ', '+')
        val length = input.length
        val dictionary = mutableListOf("", "", "") // 0..2 placeholders; entries start at 3
        var enlargeIn = 4
        var dictSize = 4
        var numBits = 3
        val result = StringBuilder()
        var value = REVERSE[input[0]] ?: 0
        var position = RESET_VALUE
        var index = 1

        /** Read `count` bits, refilling from the encoded alphabet as JS does. */
        fun readBits(count: Int): Int {
            var bits = 0
            var power = 1
            val maxPower = 1 shl count
            while (power != maxPower) {
                val resb = value and position
                position = position shr 1
                if (position == 0) {
                    position = RESET_VALUE
                    value = if (index < length) (REVERSE[input[index]] ?: 0) else 0
                    index++
                }
                if (resb > 0) bits = bits or power
                power = power shl 1
            }
            return bits
        }

        val first = readBits(2)
        var w: String
        when (first) {
            0 -> w = readBits(8).toChar().toString()
            1 -> w = readBits(16).toChar().toString()
            2 -> return ""
            else -> return null
        }
        dictionary.add(w) // dictionary[3]
        result.append(w)

        while (true) {
            if (index > length) return ""

            val code = readBits(numBits)
            val dictIndex: Int
            when (code) {
                0 -> {
                    dictionary.add(readBits(8).toChar().toString())
                    dictSize++
                    dictIndex = dictSize - 1
                    enlargeIn--
                }
                1 -> {
                    dictionary.add(readBits(16).toChar().toString())
                    dictSize++
                    dictIndex = dictSize - 1
                    enlargeIn--
                }
                2 -> return result.toString()
                else -> dictIndex = code
            }

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }

            val entry: String
            if (dictIndex >= 0 && dictIndex < dictionary.size && dictionary[dictIndex].isNotEmpty()) {
                entry = dictionary[dictIndex]
            } else if (dictIndex == dictSize) {
                entry = w + w[0]
            } else {
                return null
            }
            result.append(entry)

            // Add w + entry[0] to the dictionary.
            dictionary.add(w + entry[0])
            dictSize++
            enlargeIn--
            w = entry

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }
    }
}
