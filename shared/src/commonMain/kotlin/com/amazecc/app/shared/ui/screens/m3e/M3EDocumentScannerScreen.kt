package com.amazecc.app.shared.ui.screens.m3e

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.domain.PaperInference
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme

private const val MIN_CROP = 0.08f

internal const val SCANNER_BASE64_FIELD_TAG = "scanner_base64_field"
internal const val SCANNER_TITLE_FIELD_TAG = "scanner_title_field"
internal const val SCANNER_NOTE_FIELD_TAG = "scanner_note_field"
internal const val SCANNER_EXPORT_TAG = "scanner_export_pdf"
internal const val SCANNER_SHARE_TAG = "scanner_share_pdf"
internal const val SCANNER_READING_TAG = "scanner_on_device_reading"
internal const val SCANNER_ANALYSIS_PROGRESS_TAG = "scanner_analysis_progress"
internal const val SCANNER_ANALYZE_TAG = "scanner_reanalyse"

/** Immutable snapshot handed to [M3EDocumentScannerScreen] by [M3EDocumentScannerHost]. */
data class M3EDocumentScannerState(
    val imageBytes: ByteArray? = null,
    val image: ImageBitmap? = null,
    val crop: DocumentScannerPdf.Crop = DocumentScannerPdf.Crop.FULL,
    val title: String = "",
    val note: String = "",
    val base64Input: String = "",
    val status: String? = null,
    val errorMessage: String? = null,
    val isExporting: Boolean = false,
    val shareNotice: String? = null,
    // D-171 — on-device reading. Every field is optional: the platform may be unable to read
    // text or to infer details, and the UI must report that instead of inventing values.
    val inference: PaperInference? = null,
    val ocrText: String? = null,
    val isAnalyzing: Boolean = false,
    val analysisNotice: String? = null,
)

/** Every state change the stateless screen can request. Validation stays in the host. */
sealed interface M3EDocumentScannerAction {
    data object PickFromGallery : M3EDocumentScannerAction
    data object TakePhoto : M3EDocumentScannerAction
    data class UpdateBase64(val value: String) : M3EDocumentScannerAction
    data object LoadPasted : M3EDocumentScannerAction
    data class SetCrop(val crop: DocumentScannerPdf.Crop) : M3EDocumentScannerAction
    data class SetTitle(val value: String) : M3EDocumentScannerAction
    data class SetNote(val value: String) : M3EDocumentScannerAction
    data object ExportPdf : M3EDocumentScannerAction
    data object SharePdf : M3EDocumentScannerAction
    /** Re-run on-device reading on the currently loaded image bytes. */
    data object Analyze : M3EDocumentScannerAction
    data object Clear : M3EDocumentScannerAction
}

/**
 * D-009 — honest, **stateless** document scanner surface.
 *
 * What IS real here: gallery/camera capture (Android), preview, drag a crop rectangle over the
 * image, on-device text recognition and paper-detail inference (supported Android devices),
 * edit title/note, and export/share a standards-compliant PDF that embeds the original JPEG
 * with the crop applied as a clipping path.
 *
 * What is NOT shown here (reported, never faked): automatic edge detection. OCR/inference are
 * real platform capabilities now, but they return empty/null on unsupported devices and the
 * surface says so. Camera/gallery picking is unavailable on desktop and iOS, where the base64
 * fallback is the real import path. Title/note are user-editable; an inferred title is only
 * prefilled into a blank field and remains editable.
 *
 * This composable deliberately has no theme wrapper and no ActivityResult launcher. It is
 * composed inside `M3EDismissibleSheet`'s `Dialog` content, so all launchers live in the host
 * composed outside that `Dialog`, and the palette comes from the surrounding theme.
 */
@Composable
fun M3EDocumentScannerScreen(
    state: M3EDocumentScannerState,
    onAction: (M3EDocumentScannerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = M3ETheme.colors
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Drag needs an accumulating value between pointer events; keep that tiny interaction
    // draft here and publish every change to the host through SetCrop. It is resynced from
    // the authoritative state whenever the host changes the crop (e.g. Reset crop).
    var draftCrop by remember { mutableStateOf(state.crop) }
    LaunchedEffect(state.crop) { draftCrop = state.crop }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                SectionTitle("DOCUMENT SCANNER", Icons.Rounded.Crop, colors)
                Text(
                    "Choose a photo from the gallery or take one with the camera (Android), then crop the page, " +
                        "read it on-device, add a title and note, and export or share a real PDF. On desktop and " +
                        "iOS, capture is not wired up — paste a base64 JPEG instead. Automatic edge detection is " +
                        "unavailable. On-device text recognition and paper-detail inference ARE available on " +
                        "supported Android devices; elsewhere the scanner reports that reading is unavailable " +
                        "rather than inventing results. Title and note stay user-editable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                SectionTitle("1 · IMPORT IMAGE", Icons.Rounded.FileOpen, colors)
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.PickFromGallery) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Choose photo from gallery", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.TakePhoto) },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                ) {
                    Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Take photo", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                OutlinedTextField(
                    value = state.base64Input,
                    onValueChange = { onAction(M3EDocumentScannerAction.UpdateBase64(it)) },
                    modifier = Modifier.fillMaxWidth().testTag(SCANNER_BASE64_FIELD_TAG),
                    label = { Text("Or paste a data:image/jpeg;base64,… string") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = scannerFieldColors(colors),
                )
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.LoadPasted) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = CircleShape,
                ) { Text("Load pasted image") }
            }
        }

        val currentBitmap = state.image
        if (currentBitmap != null) {
            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    SectionTitle("2 · CROP", Icons.Rounded.Crop, colors)
                    val aspect = currentBitmap.width.toFloat() / currentBitmap.height.coerceAtLeast(1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspect.coerceIn(0.3f, 3.5f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .onSizeChanged { boxSize = it },
                    ) {
                        Image(
                            bitmap = currentBitmap,
                            contentDescription = "Imported document image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds,
                        )
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val l = draftCrop.left * size.width
                            val t = draftCrop.top * size.height
                            val r = draftCrop.right * size.width
                            val b = draftCrop.bottom * size.height
                            val dim = Color.Black.copy(alpha = 0.48f)
                            drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, t))
                            drawRect(dim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                            drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
                            drawRect(dim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))
                            drawRect(colors.primary, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                        }
                        // Drag inside the crop to move it.
                        Box(
                            modifier = Modifier
                                .offset(x = (draftCrop.left * boxSize.width).toInt().dp, y = (draftCrop.top * boxSize.height).toInt().dp)
                                .size((draftCrop.width * boxSize.width).toInt().dp, (draftCrop.height * boxSize.height).toInt().dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { _, amount ->
                                        val dx = amount.x / boxSize.width.coerceAtLeast(1)
                                        val dy = amount.y / boxSize.height.coerceAtLeast(1)
                                        draftCrop = clampCrop(
                                            draftCrop.copy(
                                                left = draftCrop.left + dx, right = draftCrop.right + dx,
                                                top = draftCrop.top + dy, bottom = draftCrop.bottom + dy,
                                            )
                                        )
                                        onAction(M3EDocumentScannerAction.SetCrop(draftCrop))
                                    }
                                },
                        )
                        CropHandle(x = draftCrop.left, y = draftCrop.top, boxSize = boxSize, colors = colors) { dx, dy ->
                            draftCrop = clampCrop(draftCrop.copy(left = draftCrop.left + dx, top = draftCrop.top + dy))
                            onAction(M3EDocumentScannerAction.SetCrop(draftCrop))
                        }
                        CropHandle(x = draftCrop.right, y = draftCrop.top, boxSize = boxSize, colors = colors) { dx, dy ->
                            draftCrop = clampCrop(draftCrop.copy(right = draftCrop.right + dx, top = draftCrop.top + dy))
                            onAction(M3EDocumentScannerAction.SetCrop(draftCrop))
                        }
                        CropHandle(x = draftCrop.left, y = draftCrop.bottom, boxSize = boxSize, colors = colors) { dx, dy ->
                            draftCrop = clampCrop(draftCrop.copy(left = draftCrop.left + dx, bottom = draftCrop.bottom + dy))
                            onAction(M3EDocumentScannerAction.SetCrop(draftCrop))
                        }
                        CropHandle(x = draftCrop.right, y = draftCrop.bottom, boxSize = boxSize, colors = colors) { dx, dy ->
                            draftCrop = clampCrop(draftCrop.copy(right = draftCrop.right + dx, bottom = draftCrop.bottom + dy))
                            onAction(M3EDocumentScannerAction.SetCrop(draftCrop))
                        }
                    }
                    ElevatedButton(onClick = { onAction(M3EDocumentScannerAction.SetCrop(DocumentScannerPdf.Crop.FULL)) }, shape = CircleShape) {
                        Text("Reset crop")
                    }
                }
            }
        }

        // D-171 — real on-device reading, between crop and edit/export. Only facts returned by
        // the platform are rendered; absent fields are skipped rather than shown as placeholders.
        M3ESurfaceCard(
            containerLevel = SurfaceContainerLevel.LOW,
            cornerRadius = 22.dp,
            modifier = Modifier.fillMaxWidth().testTag(SCANNER_READING_TAG),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                SectionTitle("ON-DEVICE READING", Icons.Rounded.Insights, colors)
                if (state.isAnalyzing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.primaryContainer)
                            .padding(12.dp)
                            .testTag(SCANNER_ANALYSIS_PROGRESS_TAG),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.HourglassTop, null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Text(
                            "Reading this document on-device…",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onPrimaryContainer,
                        )
                    }
                }
                val reading = state.inference
                if (reading != null) {
                    // Scalars and joined list values become real, labelled chips (max two per row).
                    val chips = mutableListOf<Pair<String, String>>().apply {
                        reading.assessmentType?.takeIf { it.isNotBlank() }?.let { add("Assessment" to it) }
                        reading.subject?.takeIf { it.isNotBlank() }?.let { add("Subject" to it) }
                        reading.subjectCode?.takeIf { it.isNotBlank() }?.let { add("Subject code" to it) }
                        reading.documentDate?.takeIf { it.isNotBlank() }?.let { add("Document date" to it) }
                        reading.classCodes.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                            ?.let { add("Class codes" to it.joinToString(", ")) }
                        reading.faculties.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
                            ?.let { add("Faculties" to it.joinToString(", ")) }
                        reading.confidence?.let { add("Confidence" to "${(it * 100).toInt()}%") }
                    }
                    chips.chunked(2).forEach { rowChips ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowChips.forEach { (label, value) ->
                                ScannerInfoChip(label, value, colors, Modifier.weight(1f))
                            }
                        }
                    }
                    reading.insights.filter { it.isNotBlank() }.forEach { insight ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(Icons.Rounded.Lightbulb, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                            Text(insight, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                        }
                    }
                }
                // The real OCR text is kept even when structured inference is unavailable.
                state.ocrText?.takeIf { it.isNotBlank() }?.let { text ->
                    val preview = if (text.length > 160) "${text.take(160)}…" else text
                    Text("Read text: $preview", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                // Every analysisNotice is a failure/validation message (no text, unavailable,
                // or no image) — never paint it with the success checkmark.
                state.analysisNotice?.let { ScannerFeedback(it, isError = true, colors = colors) }
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.Analyze) },
                    enabled = state.imageBytes != null && !state.isAnalyzing,
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag(SCANNER_ANALYZE_TAG),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Re-analyse", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                SectionTitle("3 · EDIT & EXPORT", Icons.Rounded.PictureAsPdf, colors)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = { onAction(M3EDocumentScannerAction.SetTitle(it)) },
                    modifier = Modifier.fillMaxWidth().testTag(SCANNER_TITLE_FIELD_TAG),
                    label = { Text("Document title (editable)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = scannerFieldColors(colors),
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onAction(M3EDocumentScannerAction.SetNote(it)) },
                    modifier = Modifier.fillMaxWidth().testTag(SCANNER_NOTE_FIELD_TAG),
                    label = { Text("Note / transcription (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = scannerFieldColors(colors),
                )
                val source = state.imageBytes
                if (source != null && !DocumentScannerPdf.isJpeg(source)) {
                    ScannerFeedback(
                        "This image is not a JPEG. PDF export embeds the original JPEG bytes, so please re-save it as JPEG before exporting.",
                        isError = true,
                        colors = colors,
                    )
                }
                // D-149: JPEG embedding + xref serialization is real work. The host runs it
                // off the UI thread and reports only what actually happened.
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.ExportPdf) },
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SCANNER_EXPORT_TAG),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export cropped PDF", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                ElevatedButton(
                    onClick = { onAction(M3EDocumentScannerAction.SharePdf) },
                    enabled = !state.isExporting,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag(SCANNER_SHARE_TAG),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                ) {
                    Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share PDF", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
                TextButton(onClick = { onAction(M3EDocumentScannerAction.Clear) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Clear document")
                }
            }
        }

        state.status?.let { ScannerFeedback(it, false, colors) }
        state.shareNotice?.let { ScannerFeedback(it, false, colors) }
        state.errorMessage?.let { ScannerFeedback(it, true, colors) }
    }
}

/** One labelled fact from a real [PaperInference]; never renders a placeholder value. */
@Composable
private fun ScannerInfoChip(label: String, value: String, colors: M3EColorPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
private fun CropHandle(
    x: Float,
    y: Float,
    boxSize: IntSize,
    colors: M3EColorPalette,
    onDrag: (Float, Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                x = (x * boxSize.width).toInt().dp - 12.dp,
                y = (y * boxSize.height).toInt().dp - 12.dp,
            )
            .size(24.dp)
            .clip(CircleShape)
            .background(colors.primary)
            .pointerInput(Unit) {
                detectDragGestures { _, amount ->
                    onDrag(amount.x / boxSize.width.coerceAtLeast(1), amount.y / boxSize.height.coerceAtLeast(1))
                }
            },
    )
}

internal fun clampCrop(crop: DocumentScannerPdf.Crop): DocumentScannerPdf.Crop {
    var left = crop.left.coerceIn(0f, 1f - MIN_CROP)
    var right = crop.right.coerceIn(left + MIN_CROP, 1f)
    if (right - left < MIN_CROP) {
        left = (left - MIN_CROP).coerceAtLeast(0f)
        right = (left + MIN_CROP).coerceAtMost(1f)
    }
    var top = crop.top.coerceIn(0f, 1f - MIN_CROP)
    var bottom = crop.bottom.coerceIn(top + MIN_CROP, 1f)
    if (bottom - top < MIN_CROP) {
        top = (top - MIN_CROP).coerceAtLeast(0f)
        bottom = (top + MIN_CROP).coerceAtMost(1f)
    }
    return DocumentScannerPdf.Crop(left, top, right, bottom)
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: M3EColorPalette) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = colors.onSurface)
    }
}

@Composable
private fun ScannerFeedback(text: String, isError: Boolean, colors: M3EColorPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isError) colors.errorContainer else colors.primaryContainer)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (isError) colors.error else colors.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (isError) colors.error else colors.onPrimaryContainer)
    }
}

@Composable
private fun scannerFieldColors(colors: M3EColorPalette) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = colors.onSurface,
    unfocusedTextColor = colors.onSurface,
    focusedContainerColor = colors.surfaceContainerHigh,
    unfocusedContainerColor = colors.surfaceContainerLow,
    focusedBorderColor = colors.primary,
    unfocusedBorderColor = colors.outlineVariant,
    focusedLabelColor = colors.primary,
    unfocusedLabelColor = colors.onSurfaceVariant,
)
