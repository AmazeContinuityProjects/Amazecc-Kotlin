package com.amazecc.app.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.parseHexColor
import com.amazecc.app.shared.theme.toHexString
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HSV color model used by the dependency-free color picker.
 * hue: 0..360 (degrees), saturation: 0..1, value: 0..1.
 */
data class HsvColor(val hue: Float, val saturation: Float, val value: Float) {
    fun toColor(): Color {
        val h = ((hue % 360f) + 360f) % 360f
        val c = value * saturation
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = value - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }
}

fun Color.toHsvColor(): HsvColor {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val d = max - min
    val hue = when {
        d == 0f -> 0f
        max == red -> 60f * (((green - blue) / d) % 6f)
        max == green -> 60f * ((blue - red) / d + 2f)
        else -> 60f * ((red - green) / d + 4f)
    }
    return HsvColor(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = if (max == 0f) 0f else d / max,
        value = max
    )
}

/**
 * Vertical hue strip (0..360). Drag anywhere on it to change hue.
 */
@Composable
fun HueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val brush = remember {
        Brush.verticalGradient(List(7) { i -> HsvColor(i * 60f, 1f, 1f).toColor() })
    }
    var heightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .clip(RoundedCornerShape(10.dp))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> onHueChanged((offset.y / size.height).coerceIn(0f, 1f) * 360f) },
                    onDrag = { change, _ ->
                        change.consume()
                        onHueChanged((change.position.y / size.height).coerceIn(0f, 1f) * 360f)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(0, (hue / 360f * heightPx - 10f).roundToInt()) }
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/**
 * 2D saturation (x) / value (y) pad for a given hue.
 */
@Composable
fun SvArea(
    hue: Float,
    saturation: Float,
    value: Float,
    onChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val hueColor = remember(hue) { HsvColor(hue, 1f, 1f).toColor() }
    var widthPx by remember { mutableStateOf(0f) }
    var heightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat(); heightPx = it.height.toFloat() }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onChanged((offset.x / size.width).coerceIn(0f, 1f), (1f - offset.y / size.height).coerceIn(0f, 1f))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onChanged((change.position.x / size.width).coerceIn(0f, 1f), (1f - change.position.y / size.height).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        )
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (saturation * widthPx - 11f).roundToInt(),
                        ((1f - value) * heightPx - 11f).roundToInt()
                    )
                }
                .size(22.dp)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .background(HsvColor(hue, saturation, value).toColor())
        )
    }
}

/**
 * Dependency-free HSV color picker: saturation/value pad + hue strip + hex input + preview.
 * Used by the custom accent and palette editor flows.
 */
@Composable
fun HsvColorPicker(
    initial: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AmazeTheme.colors
    var hsv by remember(initial) { mutableStateOf(initial.toHsvColor()) }
    var hexText by remember(initial) { mutableStateOf(initial.toHexString()) }
    val current = hsv.toColor()

    fun applyColor(color: Color) {
        hsv = color.toHsvColor()
        hexText = color.toHexString()
        onColorChanged(color)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SvArea(
                hue = hsv.hue,
                saturation = hsv.saturation,
                value = hsv.value,
                onChanged = { s, v -> applyColor(HsvColor(hsv.hue, s, v).toColor()) },
                modifier = Modifier.weight(1f).aspectRatio(1f)
            )
            HueSlider(
                hue = hsv.hue,
                onHueChanged = { h -> applyColor(HsvColor(h, hsv.saturation, hsv.value).toColor()) },
                modifier = Modifier.fillMaxHeight().width(26.dp)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(current)
                    .border(1.dp, colors.textMuted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            )
            OutlinedTextField(
                value = hexText,
                onValueChange = { text ->
                    hexText = text
                    parseHexColor(text)?.let { applyColor(it) }
                },
                singleLine = true,
                label = { Text("HEX", style = AmazeTheme.typography.smallLabel.copy(color = colors.textMuted)) },
                textStyle = AmazeTheme.typography.body.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.textMuted.copy(alpha = 0.3f),
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    cursorColor = colors.accent
                )
            )
            Text(
                current.toHexString(),
                style = AmazeTheme.typography.caption.copy(color = colors.textSecondary),
                maxLines = 1
            )
        }
    }
}

/**
 * Bottom-sheet wrapper for the HSV picker, mirroring the TimePickerSheet pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Color,
    colors: AmazeColors,
    onSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var liveColor by remember { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeaderRow(
                icon = Icons.Rounded.Palette,
                title = title,
                subtitle = "Pick a color",
                colors = colors,
                onClose = onDismiss
            )
            Spacer(Modifier.height(16.dp))
            HsvColorPicker(
                initial = initial,
                onColorChanged = { liveColor = it }
            )
            Spacer(Modifier.height(20.dp))
            AmazeButton(
                text = "Save Color",
                onClick = { onSelected(liveColor) },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            )
        }
    }
}
