# HSV Color Picker — dependency-free component

`shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/ColorPicker.kt`

A from-scratch HSV color picker with zero external dependencies, built on Compose
gestures and brushes. Used by both the custom accent flow and the palette editor.

## Public API

```kotlin
// Model + conversions
data class HsvColor(val hue: Float, val saturation: Float, val value: Float)
fun HsvColor.toColor(): Color
fun Color.toHsvColor(): HsvColor

// Hex helpers live in theme/Color.kt (shared with AppState):
fun Color.toHexString(): String          // "#RRGGBB"
fun parseHexColor(hex: String): Color?   // null on invalid input

// Controls
@Composable fun HueSlider(hue, onHueChanged, modifier)             // vertical strip
@Composable fun SvArea(hue, saturation, value, onChanged, modifier) // 2D pad
@Composable fun HsvColorPicker(initial, onColorChanged, modifier)   // full picker
@Composable fun ColorPickerSheet(title, initial, colors, onSelected, onDismiss)
```

## Math

- **HSV → RGB**: standard C = V·S, X = C·(1 − |(H/60) mod 2 − 1|), M = V − C, with
  the six hue-sector branches; result via `Color(r + m, g + m, b + m)`.
- **RGB → HSV**: max/min deltas; hue from the dominant channel with the ±360 wrap.
- Hue range 0..360, saturation/value 0..1.

## Interactions

- Both `HueSlider` and `SvArea` use `pointerInput(Unit) + detectDragGestures`:
  `onDragStart` picks up immediately on touch, `onDrag` follows `change.position`,
  always `change.consume()`, values coerced to range. No tap-to-position on release
  needed since drag handles touch-down too.
- `SvArea` maps x → saturation, y → value (inverted: top = value 1).
- Thumbs are positioned via `Modifier.offset { IntOffset(...) }` against sizes
  captured with `onSizeChanged` (hue: y = hue/360·h − 11; sv: x = s·w − 11,
  y = (1 − v)·h − 11).

## Rendering

- `HueSlider`: `Brush.verticalGradient` of 7 HSV colors (0°..360° in 60° steps),
  clipped to `RoundedCornerShape(10.dp)`, white ring thumb.
- `SvArea`: `Brush.horizontalGradient(White → hue color)` for saturation, plus an
  overlay `Box` with `Brush.verticalGradient(Transparent → Black)` for value;
  white-ring thumb filled with the current color.
- `HsvColorPicker` composes: `Row { SvArea(weight 1f, aspectRatio 1f) + HueSlider(fillMaxHeight, 26dp) }`
  then a preview swatch (44dp, rounded), a HEX `OutlinedTextField` (live-validated),
  and the current hex text.

## Sheet contract

`ColorPickerSheet` mirrors `TimePickerSheet`:

```kotlin
ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.background,
                 shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
    Column(padding(horizontal = 24.dp, bottom = 28.dp)) {
        SheetHeaderRow(icon = Icons.Rounded.Palette, title, "Pick a color", colors, onClose = onDismiss)
        HsvColorPicker(initial) { liveColor = it }
        AmazeButton("Choose Color") { onSelected(liveColor) }
    }
}
```

- Live drag updates the local `liveColor` (and the in-picker preview); the app-wide
  theme only changes on **Choose Color** — consistent with the TimePickerSheet pattern.
- Drag during a live palette re-apply is avoided by persisting only in `onSelected`.

## Callers

- Settings → Appearance → Custom accent swatch (`ColorPickerSheet("Custom Accent", ...)`).
- Onboarding → Personalize → Custom accent circle (same sheet).
- `PaletteEditorScreen` role rows (`ColorPickerSheet("<mode> · <role>", ...)`).