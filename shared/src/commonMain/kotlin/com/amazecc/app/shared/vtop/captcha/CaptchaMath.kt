package com.amazecc.app.shared.vtop.captcha

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Pure maths for the VTOP captcha recogniser.
 *
 * Ported from the Flutter reference (`fkvit/lib/features/authentication/ocr_custom/`), which is a
 * single dense layer plus softmax over a 32-class character set. No ONNX or TFLite involved, so
 * the whole model is ~60 lines of Kotlin over a JSON weight file.
 *
 * Model provenance: `pratyush3124/VtopCaptchaSolver3.0` (>95% accuracy). The reference notes
 * that Chennai's own weights are corrupt and Vellore's are used instead
 * (`ocr_custom/constants.dart:60`).
 */
internal object CaptchaMath {

    const val IMAGE_WIDTH = 200
    const val IMAGE_HEIGHT = 40

    /** No `I`, `O`, `0` or `1` — VTOP's captcha alphabet excludes them. */
    const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val NUM_CLASSES = 32
    const val NUM_CHARACTERS = 6
    const val INPUT_SIZE = 528

    const val CONFIDENCE_THRESHOLD = 0.70
    const val HIGH_CONFIDENCE_THRESHOLD = 0.90

    /** Integer bounds of one character's block in the 200x40 image. */
    fun blockCoordinates(index: Int): IntArray {
        val x1 = (index + 1) * 25 + 2
        val y1 = 7 + 5 * (index % 2) + 1
        val x2 = (index + 2) * 25 + 1
        val y2 = 35 - 5 * ((index + 1) % 2)
        return intArrayOf(x1, y1, x2, y2)
    }

    /**
     * Extracts block [index] as normalised RGB, row-major, [width] x [height].
     *
     * [pixels] is RGB triples for the full 200x40 frame.
     */
    fun extractBlock(
        pixels: FloatArray,
        imageWidth: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int
    ): FloatArray {
        val width = x2 - x1
        val height = y2 - y1
        val out = FloatArray(width * height)
        var i = 0
        for (y in y1 until y2) {
            for (x in x1 until x2) {
                val src = (y * imageWidth + x) * 3
                if (src + 2 < pixels.size) {
                    out[i++] = pixels[src] / 255f
                    out[i++] = pixels[src + 1] / 255f
                    out[i++] = pixels[src + 2] / 255f
                }
            }
        }
        return out
    }

    /**
     * Converts RGB to the HSV saturation channel: `((max - min) * 255) / max`.
     *
     * Saturated ink on a light background separates cleanly here.
     */
    fun saturation(pixels: FloatArray): FloatArray {
        val out = FloatArray(pixels.size / 3)
        var o = 0
        var i = 0
        while (i + 2 < pixels.size) {
            val r = pixels[i]
            val g = pixels[i + 1]
            val b = pixels[i + 2]
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            out[o++] = if (mx <= 0f) 0f else ((mx - mn) * 255f) / mx
            i += 3
        }
        return out
    }

    /** Binarises against the block's own mean, then flattens. */
    fun binarize(block: FloatArray): FloatArray {
        if (block.isEmpty()) return block
        var sum = 0f
        for (v in block) sum += v
        val mean = sum / block.size
        val out = FloatArray(block.size)
        for (i in block.indices) out[i] = if (block[i] > mean) 1f else 0f
        return out
    }

    /**
     * Dense layer plus numerically stable softmax.
     *
     * [weights] is row-major [inputSize] x [numClasses].
     */
    fun forward(input: FloatArray, weights: FloatArray, biases: FloatArray, numClasses: Int): FloatArray {
        val logits = FloatArray(numClasses)
        for (c in 0 until numClasses) {
            var sum = biases[c]
            val offset = c
            for (i in input.indices) {
                sum += input[i] * weights[i * numClasses + offset]
            }
            logits[c] = sum
        }
        val maxLogit = logits.max()
        var total = 0f
        for (c in 0 until numClasses) {
            val e = exp(logits[c] - maxLogit)
            logits[c] = e
            total += e
        }
        for (c in 0 until numClasses) logits[c] /= total
        return logits
    }

    /** Argmax of a probability vector, as index and value. */
    fun argmax(probs: FloatArray): Pair<Int, Float> {
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        for (i in probs.indices) {
            if (probs[i] > bestValue) {
                bestValue = probs[i]
                best = i
            }
        }
        return best to bestValue
    }
}
