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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.ui.m3e.components.M3ESurfaceCard
import com.amazecc.app.shared.ui.m3e.components.SurfaceContainerLevel
import com.amazecc.app.shared.ui.m3e.theme.AmazeM3ETheme
import com.amazecc.app.shared.ui.m3e.theme.M3EColorPalette
import com.amazecc.app.shared.ui.m3e.theme.M3ETheme
import com.amazecc.app.shared.utils.openDocumentBytes
import com.amazecc.app.shared.utils.rememberFileImporter
import com.amazecc.app.shared.utils.rememberFileSaver
import com.amazecc.app.shared.utils.toImageBitmap
import io.ktor.util.decodeBase64Bytes

private const val MIN_CROP = 0.08f

/**
 * D-009 — honest document scanner for common KMP.
 *
 * What IS real here: import an existing JPEG (as a data URI / base64 text file
 * or pasted base64), preview it, drag a crop rectangle over the image, edit the
 * document title/note, and export a standards-compliant PDF that embeds the
 * original JPEG and applies the crop as a PDF clipping path.
 *
 * What is NOT available in this codebase (reported, never faked): live camera
 * capture, automatic edge detection, and OCR. There is no CameraX/ML Kit
 * dependency, no CAMERA permission, and no intent-filter callback to receive a
 * camera result. The manual editor stands in for OCR text entry explicitly.
 */
@Composable
fun M3EDocumentScannerScreen(modifier: Modifier = Modifier) {
    AmazeM3ETheme {
        val colors = M3ETheme.colors
        var sourceBytes by remember { mutableStateOf<ByteArray?>(null) }
        var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
        var crop by remember { mutableStateOf(DocumentScannerPdf.Crop.FULL) }
        var boxSize by remember { mutableStateOf(IntSize.Zero) }
        var base64Input by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var status by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        val saveFile = rememberFileSaver()

        fun acceptSource(text: String) {
            val clean = text.trim()
                .substringAfter("base64,", missingDelimiterValue = text.trim())
                .filter { !it.isWhitespace() }
            if (clean.isBlank()) {
                error = "No image data found. Paste a data URI or base64 image."
                return
            }
            val bytes = runCatching { clean.decodeBase64Bytes() }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                error = "That does not look like valid base64 image data."
                return
            }
            val decoded = runCatching { bytes.toImageBitmap() }.getOrNull()
            if (decoded == null) {
                error = "The image could not be decoded on this device."
                return
            }
            sourceBytes = bytes
            bitmap = decoded
            crop = DocumentScannerPdf.Crop.FULL
            status = "Image loaded (${decoded.width}×${decoded.height}). Drag the handles to crop."
            error = if (DocumentScannerPdf.isJpeg(bytes)) null
            else "This image is not a JPEG. PDF export embeds the original JPEG bytes, so please re-save it as JPEG before exporting."
        }

        val importFile = rememberFileImporter { text -> if (text != null) acceptSource(text) }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    SectionTitle("DOCUMENT SCANNER", Icons.Rounded.Crop, colors)
                    Text(
                        "Import a photographed document, crop the page, add a title and note, then export a real PDF. " +
                            "Camera capture, automatic edge detection and OCR are not available in this Kotlin build — " +
                            "the text fields are a manual stand-in for OCR, not a simulated scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                    SectionTitle("1 · IMPORT IMAGE", Icons.Rounded.FileOpen, colors)
                    ElevatedButton(
                        onClick = { importFile() },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
                    ) {
                        Text("Import base64 / data-URI text file", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    OutlinedTextField(
                        value = base64Input,
                        onValueChange = { base64Input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Or paste a data:image/jpeg;base64,… string") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        colors = scannerFieldColors(colors),
                    )
                    ElevatedButton(
                        onClick = { acceptSource(base64Input) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = CircleShape,
                    ) { Text("Load pasted image") }
                }
            }

            val currentBitmap = bitmap
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
                                val l = crop.left * size.width
                                val t = crop.top * size.height
                                val r = crop.right * size.width
                                val b = crop.bottom * size.height
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
                                    .offset(x = (crop.left * boxSize.width).toInt().dp, y = (crop.top * boxSize.height).toInt().dp)
                                    .size((crop.width * boxSize.width).toInt().dp, (crop.height * boxSize.height).toInt().dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, amount ->
                                            val dx = amount.x / boxSize.width.coerceAtLeast(1)
                                            val dy = amount.y / boxSize.height.coerceAtLeast(1)
                                            crop = clampCrop(
                                                crop.copy(
                                                    left = crop.left + dx, right = crop.right + dx,
                                                    top = crop.top + dy, bottom = crop.bottom + dy,
                                                )
                                            )
                                        }
                                    },
                            )
                            CropHandle(x = crop.left, y = crop.top, boxSize = boxSize, colors = colors) { dx, dy ->
                                crop = clampCrop(crop.copy(left = crop.left + dx, top = crop.top + dy))
                            }
                            CropHandle(x = crop.right, y = crop.top, boxSize = boxSize, colors = colors) { dx, dy ->
                                crop = clampCrop(crop.copy(right = crop.right + dx, top = crop.top + dy))
                            }
                            CropHandle(x = crop.left, y = crop.bottom, boxSize = boxSize, colors = colors) { dx, dy ->
                                crop = clampCrop(crop.copy(left = crop.left + dx, bottom = crop.bottom + dy))
                            }
                            CropHandle(x = crop.right, y = crop.bottom, boxSize = boxSize, colors = colors) { dx, dy ->
                                crop = clampCrop(crop.copy(right = crop.right + dx, bottom = crop.bottom + dy))
                            }
                        }
                        ElevatedButton(onClick = { crop = DocumentScannerPdf.Crop.FULL }, shape = CircleShape) {
                            Text("Reset crop")
                        }
                    }
                }

                M3ESurfaceCard(containerLevel = SurfaceContainerLevel.LOW, cornerRadius = 22.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(16.dp)) {
                        SectionTitle("3 · EDIT & EXPORT", Icons.Rounded.PictureAsPdf, colors)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Document title (manual — no OCR on this platform)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = scannerFieldColors(colors),
                        )
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Note / transcription (optional)") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = scannerFieldColors(colors),
                        )
                        val source = sourceBytes
                        ElevatedButton(
                            onClick = {
                                if (source == null) {
                                    error = "Import an image first."
                                    return@ElevatedButton
                                }
                                if (!DocumentScannerPdf.isJpeg(source)) {
                                    error = "PDF export needs the original JPEG bytes. Re-save this image as JPEG and import it again."
                                    return@ElevatedButton
                                }
                                val pdf = DocumentScannerPdf.buildPdf(source, crop, currentBitmap.width, currentBitmap.height, title, note)
                                val name = "amazecc-scan.pdf"
                                val saved = saveFile(name, pdf)
                                if (saved) {
                                    status = "Saved $name (${pdf.size / 1024} KB)."
                                    error = null
                                    openDocumentBytes(pdf, name, "application/pdf")
                                } else {
                                    error = "The PDF could not be saved on this device."
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.elevatedButtonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export cropped PDF", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            status?.let { ScannerFeedback(it, false, colors) }
            error?.let { ScannerFeedback(it, true, colors) }
        }
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

private fun clampCrop(crop: DocumentScannerPdf.Crop): DocumentScannerPdf.Crop {
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
