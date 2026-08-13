# Custom Accent — "Pick a color, the app handles the rest"

## Spec

The user picks **any** color (HSV picker or hex input). The engine uses it exactly
like the four presets: it becomes `AmazeColors.accent`, and the derived tones
(`accentSurface`, `accentContainer`, pressed states, pill tints, gradients) are
computed automatically. No other color decisions are left to the user.

## Behavior

1. Choosing "Custom" in either picker sets `AccentTheme.CUSTOM` and persists the color.
2. The color is applied live to the running theme (no restart).
3. Switching back to a preset keeps the custom color stored, so returning to Custom
   restores the last picked color.
4. Logout resets to `AccentTheme.OCEAN` and clears the stored color (via `clearAll`).

## State & persistence

- `AppState.customAccentColor: StateFlow<Color>` (default `AccentOcean`).
- `AppState.setCustomAccent(color)` — sets color, switches accent to `CUSTOM`,
  writes `KEY_APP_ACCENT = "CUSTOM"` and `KEY_CUSTOM_ACCENT = color.toHexString()`.
- `AppState.changeAccent(accent)` — unchanged for presets; selecting the CUSTOM
  entry from UI always routes through `setCustomAccent` (or the swatch opens the picker).
- Loaded in `AppState.init` via `parseHexColor`.

## Theme.kt changes

```kotlin
enum class AccentTheme { OCEAN, FOREST, LAVENDER, SUNSET, CUSTOM }

fun AmazeTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    accentTheme: AccentTheme = AccentTheme.OCEAN,
    customAccent: Color = AccentOcean,   // NEW
    customPalette: CustomPalette? = null, // NEW, see palette-editor.md
    ...
) {
    val accent = when (accentTheme) {
        ...
        AccentTheme.CUSTOM -> customAccent
    }
```

`App.kt` collects `AppState.customAccentColor` and passes it through.

## UI

### Settings — Appearance & Feel → Accent Colors
- A fifth **Custom** swatch (rainbow-capable: shows the current custom color).
- Tapping it opens `ColorPickerSheet("Custom Accent", initial = customAccent, ...)`;
  `onSelected` calls `AppState.setCustomAccent`.
- `AccentSwatch` gained `customColor` and optional `onClick` params; the `when`
  has a `CUSTOM -> customColor` branch. Selected state (accent ring) works unchanged.

### Onboarding — Personalize → ACCENT COLOR
- A **Custom** circle (gradient of the custom color) after the four preset circles.
- Opens the same `ColorPickerSheet`; on confirm calls
  `AppState.setCustomAccent(color)` + `onAccentChange(AccentTheme.CUSTOM)` so the
  onboarding local state stays in sync.

### Labels
- `SettingsHub.accentLabel`: `CUSTOM -> "Custom"` (hub summary line).