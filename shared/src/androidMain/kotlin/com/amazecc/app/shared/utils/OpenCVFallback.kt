package com.amazecc.app.shared.utils

import com.amazecc.app.shared.domain.ScannedDocument
import com.amazecc.app.shared.domain.ScanSource
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal const val OPENCV_NOTICE = "Detected edges and straightened with OpenCV fallback"

@Volatile
private var openCvReady = false

/** Native libs are optional at runtime; never let a missing .so crash the scanner host. */
private fun ensureOpenCv(): Boolean = runCatching {
    if (!openCvReady) openCvReady = OpenCVLoader.initLocal()
    openCvReady
}.getOrDefault(false)

/**
 * D-174 — real on-device document detection from JPEG bytes:
 * grayscale -> Gaussian blur -> Canny -> morphological close -> largest convex quad
 * -> perspective rectification -> adaptive-threshold cleanup -> JPEG re-encode.
 *
 * Returns `null` (never a fabricated page) when OpenCV is unavailable, no valid quad is found,
 * or encoding fails. Callers must invoke this OFF the Main thread; they then keep the manual
 * crop path when it returns null.
 *
 * Renamed from `straightenDocumentWithOpenCV` so the public `suspend fun
 * straightenDocumentWithOpenCV(bytes): ByteArray?` expect/actual seam can reuse this pipeline —
 * Kotlin forbids a suspend and non-suspend overload with the same name and parameter types.
 */
fun scanDocumentWithOpenCV(jpegBytes: ByteArray): ScannedDocument? {
    if (jpegBytes.isEmpty() || !ensureOpenCv()) return null
    return runCatching { detectStraightenAndClean(jpegBytes) }.getOrNull()
}

private fun detectStraightenAndClean(jpegBytes: ByteArray): ScannedDocument? {
    // Decode directly from memory — no temp files and no Bitmap on the caller's thread.
    val encodedIn = Mat(1, jpegBytes.size, CvType.CV_8U)
    encodedIn.put(0, 0, jpegBytes)
    val original = Imgcodecs.imdecode(encodedIn, Imgcodecs.IMREAD_COLOR)
    encodedIn.release()
    if (original.empty()) return null

    val gray = Mat()
    val blurred = Mat()
    val edges = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
    val closed = Mat()
    val hierarchy = Mat()
    val warped = Mat()
    val warpedGray = Mat()
    val cleaned = Mat()
    val encodedOut = MatOfByte()
    val contours = mutableListOf<MatOfPoint>()
    val candidates = mutableListOf<MatOfPoint2f>()

    try {
        Imgproc.cvtColor(original, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 75.0, 200.0)
        // Bridge small gaps in the page border so the quad survives approxPolyDP.
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

        Imgproc.findContours(
            closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
        )
        if (contours.isEmpty()) return null

        val imageArea = original.width().toDouble() * original.height().toDouble()
        val quad = contours
            .filter { Imgproc.contourArea(it) >= imageArea * 0.08 }
            .mapNotNull { contour -> approxQuad(contour, candidates) }
            .maxByOrNull { Imgproc.contourArea(it) }
            ?: return null

        val ordered = orderPoints(quad.toArray())
        val (width, height) = quadDimensions(ordered)
        if (width < 40 || height < 40) return null
        val aspect = width.toDouble() / height.toDouble()
        if (aspect < 0.15 || aspect > 8.0) return null

        val src = MatOfPoint2f(*ordered)
        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((width - 1).toDouble(), 0.0),
            Point((width - 1).toDouble(), (height - 1).toDouble()),
            Point(0.0, (height - 1).toDouble()),
        )
        val transform = Imgproc.getPerspectiveTransform(src, dst)
        try {
            src.release()
            dst.release()
            // warpPerspective (not point-wise perspectiveTransform) rectifies a whole image.
            Imgproc.warpPerspective(original, warped, transform, Size(width.toDouble(), height.toDouble()))
        } finally {
            transform.release()
        }

        Imgproc.cvtColor(warped, warpedGray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.adaptiveThreshold(
            warpedGray, cleaned, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY,
            31, 15.0,
        )
        if (!Imgcodecs.imencode(".jpg", cleaned, encodedOut)) return null
        val jpeg = encodedOut.toArray()
        if (jpeg.isEmpty()) return null

        return ScannedDocument(
            imageBytes = jpeg,
            source = ScanSource.OPENCV,
            notice = OPENCV_NOTICE,
        )
    } finally {
        candidates.forEach { runCatching { it.release() } }
        contours.forEach { runCatching { it.release() } }
        encodedOut.release()
        cleaned.release()
        warpedGray.release()
        warped.release()
        hierarchy.release()
        kernel.release()
        closed.release()
        edges.release()
        blurred.release()
        gray.release()
        original.release()
    }
}

/** Keep only a genuinely convex four-point polygon; register it for guaranteed release. */
private fun approxQuad(contour: MatOfPoint, candidates: MutableList<MatOfPoint2f>): MatOfPoint2f? {
    val curve = MatOfPoint2f()
    val approx = MatOfPoint2f()
    try {
        contour.convertTo(curve, CvType.CV_32FC2)
        Imgproc.approxPolyDP(curve, approx, 0.02 * Imgproc.arcLength(curve, true), true)
        if (approx.total() != 4L) {
            approx.release()
            return null
        }
        val asPoints = MatOfPoint(*approx.toArray())
        val convex = try {
            Imgproc.isContourConvex(asPoints)
        } finally {
            asPoints.release()
        }
        if (!convex) {
            approx.release()
            return null
        }
        candidates.add(approx)
        return approx
    } finally {
        curve.release()
    }
}

/** Canonical document order: top-left, top-right, bottom-right, bottom-left. */
private fun orderPoints(points: Array<Point>): Array<Point> {
    val bySum = points.sortedBy { it.x + it.y }
    val byDiff = points.sortedBy { it.x - it.y }
    return arrayOf(bySum.first(), byDiff.first(), bySum.last(), byDiff.last())
}

private fun quadDimensions(ordered: Array<Point>): Pair<Int, Int> {
    val width = max(hypot(ordered[0].x - ordered[1].x, ordered[0].y - ordered[1].y),
        hypot(ordered[2].x - ordered[3].x, ordered[2].y - ordered[3].y))
    val height = max(hypot(ordered[1].x - ordered[2].x, ordered[1].y - ordered[2].y),
        hypot(ordered[3].x - ordered[0].x, ordered[3].y - ordered[0].y))
    return abs(width).toInt() to abs(height).toInt()
}
