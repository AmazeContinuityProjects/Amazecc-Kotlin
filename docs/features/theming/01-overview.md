# Custom Theming Engine — Overview

The AmazeCC theming engine lets users go beyond the four preset accents. It has two
layers, both built on the existing single-source-of-truth architecture:

| Layer | What it does | Entry points |
|---|---|---|
| **Custom Accent** | Pick any color; the engine derives surfaces, containers, pressed states, etc. from it | Settings → Appearance & Feel → Accent Colors → Custom; Onboarding → Personalize → accent row |
| **Custom Palette** | Per-mode (Light/Dark) per-role overrides for every `AmazeColors` field | Settings → Appearance & Feel → Custom Palette → Edit Palette |

## Architecture

```
App.kt (collects flows)                     state/AppState.kt (source of truth)
  ├─ theme: AppTheme          ──────────►   _theme            (KEY_APP_THEME)
  ├─ accent: AccentTheme      ──────────►   _accent           (KEY_APP_ACCENT)
  ├─ customAccent: Color      ──────────►   _customAccentColor (KEY_CUSTOM_ACCENT, hex)
  └─ customPalette: CustomPalette ──────►   _customPalette     (KEY_CUSTOM_PALETTE, JSON)
                        │
                        ▼
              theme/Theme.kt  AmazeTheme(...)
                        │
      accent = when(accentTheme) { CUSTOM -> customAccent }
      baseColors = when(resolvedTheme) { LIGHT/DARK/AMOLED }
      colors = paletteOverrides?.applyTo(baseColors) ?: baseColors
                        │
                        ▼
              AmazeColors (CompositionLocal LocalAmazeColors)
```

- `AmazeColors` is a `@Stable` class with `mutableStateOf` properties; `AmazeTheme`
  keeps one remembered instance and calls `updateWith(colors)` on every recomposition
  (Theme.kt:372), so any change recolors the whole app instantly with no restart.
- The accent color is injected into `AmazeColors` as-is; `accentSurface` and
  `accentContainer` are derived via `accent.copy(alpha = ...)` — this is why
  "pick a color, let the app handle the rest" is nearly free.
- The custom palette is applied with the existing `AmazeColors.copy(...)` after the
  base palette is built, so it layers on top of any theme/accent combination.

## Persistence keys (SettingsManager)

| Key | Type | Purpose |
|---|---|---|
| `KEY_APP_THEME` ("app_theme") | enum name | LIGHT / DARK / AMOLED / SYSTEM |
| `KEY_APP_ACCENT` ("app_accent") | enum name | OCEAN / FOREST / LAVENDER / SUNSET / CUSTOM |
| `KEY_CUSTOM_ACCENT` ("app_accent_custom") | hex `#RRGGBB` | the picked custom accent color |
| `KEY_CUSTOM_PALETTE` ("custom_palette") | JSON | serialized `CustomPalette` |

All four are wiped by `AppState.logout()` (which now uses `SettingsManager.clearAll()`),
and the in-memory flows are reset to defaults.

## Extension points

- New roles: add an entry to `PaletteRole` and one line to `PaletteOverrides.applyTo`
  and `PaletteRole.currentOf` — the map-backed `PaletteOverrides` needs no schema change.
- New modes: add to `PaletteMode` and mirror in `AppTheme`/`Theme.kt` resolution.
- Contrast checks / a11y warnings on low-contrast text colors: not implemented yet;
  the natural hook is `ColorPickerSheet.onSelected` or a validation pass in
  `PaletteEditorScreen` before persisting.

## Files

- `shared/src/commonMain/kotlin/com/amazecc/app/shared/theme/Theme.kt` — `AccentTheme.CUSTOM`, `customAccent`, `customPalette` params
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/theme/Color.kt` — hex helpers (`parseHexColor`, `Color.toHexString`)
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/theme/CustomPalette.kt` — `CustomPalette`, `PaletteOverrides`, `PaletteMode`, `PaletteRole`, `applyTo`, `currentOf`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/state/AppState.kt` — flows, persistence, setters
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/App.kt` — flow collection → `AmazeTheme`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/components/ColorPicker.kt` — HSV picker + `ColorPickerSheet`
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/settings/PaletteEditorScreen.kt` — full palette editor
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/settings/SettingsPages.kt` — Appearance page (Custom swatch, palette toggle/entry)
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/settings/SettingsScreen.kt` / `SettingsModels.kt` — `SettingsSubScreen.PALETTE` route
- `shared/src/commonMain/kotlin/com/amazecc/app/shared/ui/screens/onboarding/OnboardingScreen.kt` — Personalize custom accent circle
