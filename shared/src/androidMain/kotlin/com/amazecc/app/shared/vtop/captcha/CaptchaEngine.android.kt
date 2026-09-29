package com.amazecc.app.shared.vtop.captcha

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.amazecc.app.shared.services.AndroidApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream

/**
 * Android implementation: decode with `Bitmap`, resize bilinearly, then run the shared maths.
 *
 * The weight file is `assets/vellore_weights.json` (see
 * `docs/sep-29-2026/vtop-porting-notes.md` section 5). Shape is `{"biases": [32], "weights":
 * [[32] x 528]}`, row-major.
 */
actual class CaptchaEngine actual constructor() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var cached: Weights? = null

    actual val isReady: Boolean
        get() = loadWeights() != null

    private class Weights(val flat: FloatArray, val biases: FloatArray, val inputSize: Int)

    private fun context(): Context? = AndroidApp.context

    private fun loadWeights(): Weights? {
        cached?.let { return it }
        val ctx = context() ?: return null
        val raw = try {
            ctx.assets.open(WEIGHTS_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            val biasArray = root.getValue("biases").jsonArray
            val weightArray = root.getValue("weights").jsonArray
            val biases = FloatArray(biasArray.size) { biasArray[it].jsonPrimitive.content.toFloat() }
            val flat = FloatArray(weightArray.size * CaptchaMath.NUM_CLASSES)
            for (row in weightArray.indices) {
                val cols = weightArray[row].jsonArray
                for (col in cols.indices) {
                    flat[row * CaptchaMath.NUM_CLASSES + col] = cols[col].jsonPrimitive.content.toFloat()
                }
            }
            Weights(flat, biases, weightArray.size).also { cached = it }
        } catch (_: Exception) {
            null
        }
    }

    actual suspend fun solve(dataUri: String): CaptchaPrediction? {
        val weights = loadWeights() ?: return null
        val decoded = withContext(Dispatchers.Default) { decodeToRgb(dataUri) } ?: return null

        // Resize first, then take the saturation channel — matching the reference order.
        val resized = bilinearResize(
            decoded.pixels,
            decoded.width,
            decoded.height,
            CaptchaMath.IMAGE_WIDTH,
            CaptchaMath.IMAGE_HEIGHT
        )
        val sat = CaptchaMath.saturation(resized)

        val chars = StringBuilder()
        val confidences = ArrayList<Float>(CaptchaMath.NUM_CHARACTERS)

        for (i in 0 until CaptchaMath.NUM_CHARACTERS) {
            val (x1, y1, x2, y2) = CaptchaMath.blockCoordinates(i).let {
                intArrayOf(it[0], it[1], it[2], it[3])
            }
            val block = FloatArray((x2 - x1) * (y2 - y1)) { idx ->
                val y = y1 + idx / (x2 - x1)
                val x = x1 + idx % (x2 - x1)
                sat.getOrElse(y * CaptchaMath.IMAGE_WIDTH + x) { 0f }
            }
            val input = CaptchaMath.binarize(block)
            if (input.size != weights.inputSize) return null

            val probs = CaptchaMath.forward(input, weights.flat, weights.biases, CaptchaMath.NUM_CLASSES)
            val (index, confidence) = CaptchaMath.argmax(probs)
            chars.append(CaptchaMath.CHARSET.getOrElse(index) { '?' })
            confidences.add(confidence)
        }

        val mean = if (confidences.isEmpty()) 0f else confidences.sum() / confidences.size
        return CaptchaPrediction(chars.toString(), mean, confidences)
    }

    private class Decoded(val pixels: FloatArray, val width: Int, val height: Int)

    private fun decodeToRgb(dataUri: String): Decoded? {
        val comma = dataUri.indexOf(',')
        val payload = if (comma >= 0) dataUri.substring(comma + 1) else dataUri
        val bytes = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }
        val bitmap = try {
            BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
        } catch (_: Throwable) {
            null
        } ?: return null

        val w = bitmap.width
        val h = bitmap.height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        bitmap.recycle()

        val out = FloatArray(w * h * 3)
        var o = 0
        for (px in argb) {
            out[o++] = ((px shr 16) and 0xFF).toFloat()
            out[o++] = ((px shr 8) and 0xFF).toFloat()
            out[o++] = (px and 0xFF).toFloat()
        }
        return Decoded(out, w, h)
    }

    private fun bilinearResize(
        pixels: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        val total = pixels.size / 3
        if (srcW <= 0 || srcH <= 0 || srcW * srcH != total) return pixels

        val out = FloatArray(dstW * dstH * 3)
        val xRatio = srcW.toFloat() / dstW
        val yRatio = srcH.toFloat() / dstH

        for (y in 0 until dstH) {
            val fy = (y + 0.5f) * yRatio - 0.5f
            val y0 = fy.toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceAtMost(srcH - 1)
            val wy = fy - y0

            for (x in 0 until dstW) {
                val fx = (x + 0.5f) * xRatio - 0.5f
                val x0 = fx.toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceAtMost(srcW - 1)
                val wx = fx - x0

                for (c in 0 until 3) {
                    val p00 = pixels[(y0 * srcW + x0) * 3 + c]
                    val p01 = pixels[(y0 * srcW + x1) * 3 + c]
                    val p10 = pixels[(y1 * srcW + x0) * 3 + c]
                    val p11 = pixels[(y1 * srcW + x1) * 3 + c]
                    val top = p00 + (p01 - p00) * wx
                    val bottom = p10 + (p11 - p10) * wx
                    out[(y * dstW + x) * 3 + c] = top + (bottom - top) * wy
                }
            }
        }
        return out
    }

    private companion object {
        const val WEIGHTS_ASSET = "vellore_weights.json"
    }
}
