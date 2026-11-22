package com.amazecc.app.shared.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.amazecc.app.shared.domain.PaperInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream

/**
 * D-166 — real on-device OCR. Width/height come from JPEG bounds and rotation
 * from EXIF, both read defensively. ML Kit's `fromByteArray` only accepts NV21
 * or YV12 (there is no IMAGE_FORMAT_JPEG constant), so the decoded JPEG is
 * converted to packed NV21 before being handed over. Unreadable input yields
 * an empty list, never invented text.
 */
actual suspend fun recognizeDocumentText(bytes: ByteArray): List<String> = withContext(Dispatchers.Default) {
    if (bytes.isEmpty()) return@withContext emptyList()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds) }
    val rawWidth = bounds.outWidth
    val rawHeight = bounds.outHeight
    if (rawWidth <= 1 || rawHeight <= 1) return@withContext emptyList()

    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    // Cap the working bitmap: OCR only needs ~16px per character, and the
    // ARGB + NV21 buffers otherwise cost ~100 MB for a full-resolution photo.
    var sampleSize = 1
    while (maxOf(rawWidth / sampleSize, rawHeight / sampleSize) > 2048) sampleSize *= 2
    val decoded = runCatching {
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
        )
    }.getOrNull() ?: return@withContext emptyList()

    // NV21 chroma is subsampled 2x2, so both dimensions must be even.
    val width = decoded.width - decoded.width % 2
    val height = decoded.height - decoded.height % 2
    if (width <= 1 || height <= 1) return@withContext emptyList()

    val image = runCatching {
        val cropped = if (decoded.width == width && decoded.height == height) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, width, height)
        }
        InputImage.fromByteArray(
            toNv21(cropped, width, height),
            width,
            height,
            rotation,
            InputImage.IMAGE_FORMAT_NV21
        )
    }.getOrNull() ?: return@withContext emptyList()

    val recognizer = runCatching { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }.getOrNull()
        ?: return@withContext emptyList()
    try {
        runCatching {
            recognizer.process(image).await().textBlocks
                .flatMap { block -> block.lines.map { it.text } }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    } finally {
        runCatching { recognizer.close() }
    }
}

/** Packs an ARGB_8888 bitmap into BT.601 NV21 (full luma plane, interleaved V/U). */
private fun toNv21(bitmap: Bitmap, width: Int, height: Int): ByteArray {
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val nv21 = ByteArray(width * height * 3 / 2)
    val frameSize = width * height

    var luma = 0
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val color = pixels[row + x]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            nv21[luma++] = ((77 * r + 150 * g + 29 * b + 128) shr 8).coerceIn(0, 255).toByte()
        }
    }

    var chroma = frameSize
    for (blockY in 0 until height step 2) {
        for (blockX in 0 until width step 2) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in 0..1) {
                for (dx in 0..1) {
                    val color = pixels[(blockY + dy) * width + (blockX + dx)]
                    r += (color shr 16) and 0xFF
                    g += (color shr 8) and 0xFF
                    b += color and 0xFF
                }
            }
            r /= 4
            g /= 4
            b /= 4
            val u = ((-43 * r - 85 * g + 128 * b + 128) shr 8) + 128
            val v = ((128 * r - 107 * g - 21 * b + 128) shr 8) + 128
            nv21[chroma++] = v.coerceIn(0, 255).toByte()
            nv21[chroma++] = u.coerceIn(0, 255).toByte()
        }
    }
    return nv21
}

private const val MAX_OCR_CHARS = 8_000

/**
 * D-166 — real on-device inference with Gemini Nano via the ML Kit Prompt API.
 *
 * Structured output is intentionally NOT used: the project has no KSP plugin,
 * the required KSP version is incompatible with Kotlin 2.4.21, release builds
 * minify without an editable keep-rules file, and `isStructuredOutputFeatureAvailable()`
 * is not guaranteed on every device. A plain prompt plus defensive JSON parsing
 * keeps the same data contract everywhere. When the model is unavailable or the
 * response cannot be parsed we return null rather than fabricating a result.
 */
actual suspend fun inferPaperDetails(text: String): PaperInference? = withContext(Dispatchers.Default) {
    val source = text.trim().takeIf { it.isNotEmpty() } ?: return@withContext null

    val model = runCatching { Generation.getClient() }.getOrNull() ?: return@withContext null
    try {
        val status = runCatching { model.checkStatus() }.getOrNull()
        if (status != FeatureStatus.AVAILABLE) return@withContext null

        val prompt = buildPrompt(source.take(MAX_OCR_CHARS))
        val rawResponse = runCatching { model.generateContent(prompt).candidates.firstOrNull()?.text }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext null

        val parsed = parsePaperInference(rawResponse) ?: return@withContext null
        parsed.copy(suggestedName = suggestedDocumentName(parsed))
    } finally {
        runCatching { model.close() }
    }
}

private fun buildPrompt(ocrText: String): String = """
You extract facts from text OCR'd from an academic document (question paper, exam, CIA, assignment, circular).
Return ONLY one JSON object - no prose, no markdown fences. Use null for absent single values and [] for absent lists. Never guess or invent anything.
Keys:
- assessmentType: string or null, e.g. "CIA-1", "CIA-2", "ESE", "Midterm", "Assignment", "Quiz".
- subject: string or null, the full subject name as printed.
- subjectCode: string or null, the alphanumeric course code as printed.
- classCodes: array of strings, class or section codes as printed.
- faculties: array of strings, faculty names as printed.
- documentDate: string or null, the date on the document in YYYY-MM-DD when possible, otherwise as printed.
- insights: array of 2 to 4 short factual observations (max 12 words each), or [] if none.
- suggestedName: string or null, a short filename-safe title made ONLY from the facts above.
If a fact is not printed in the document, return null or []. Do not use general knowledge.
---DOCUMENT START---
$ocrText
---DOCUMENT END---
""".trimIndent()

private val parserJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val fencedJson = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

/** Accepts a bare object, a fenced ```json block, or an object embedded in prose. */
private fun extractJsonObject(raw: String): JsonObject? {
    val trimmed = raw.trim()
    val candidates = mutableListOf(trimmed)
    fencedJson.findAll(trimmed).forEach { candidates += it.groupValues[1].trim() }
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start >= 0 && end > start) candidates += trimmed.substring(start, end + 1)

    for (candidate in candidates) {
        if (candidate.isEmpty()) continue
        val obj = runCatching { parserJson.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
        if (obj != null) return obj
    }
    return null
}

private fun parsePaperInference(raw: String): PaperInference? {
    val obj = extractJsonObject(raw) ?: return null
    val assessment = obj.optString("assessmentType")
    val subject = obj.optString("subject")
    val subjectCode = obj.optString("subjectCode")
    val documentDate = obj.optString("documentDate")
    val classCodes = obj.optStringList("classCodes")
    val faculties = obj.optStringList("faculties")
    val suggested = obj.optString("suggestedName")

    val hasAnyFact = listOf(assessment, subject, subjectCode, documentDate).any { it != null } ||
        classCodes.isNotEmpty() || faculties.isNotEmpty()
    if (!hasAnyFact && suggested == null) return null

    return PaperInference(
        assessmentType = assessment,
        subject = subject,
        subjectCode = subjectCode,
        classCodes = classCodes.take(8),
        faculties = faculties.take(8),
        documentDate = documentDate,
        suggestedName = suggested.orEmpty(),
        insights = obj.optStringList("insights").take(4).map { it.take(160) },
        confidence = obj.optConfidence()
    )
}

private fun JsonObject.optString(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("null", ignoreCase = true)
    }

private fun JsonObject.optStringList(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull {
        (it as? JsonPrimitive)?.content?.trim()?.takeIf { s ->
            s.isNotEmpty() && !s.equals("null", ignoreCase = true)
        }
    }
    is JsonPrimitive -> value.content.split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    else -> emptyList()
}

private fun JsonObject.optConfidence(): Float? {
    val content = (this["confidence"] as? JsonPrimitive)?.content ?: return null
    val value = content.toFloatOrNull() ?: return null
    return value.takeIf { it.isFinite() && it in 0f..1f }
}
