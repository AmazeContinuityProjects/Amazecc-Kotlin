# Custom Palette Editor — per-role, per-mode color overrides

## Spec

A full theming editor: the user can override **every color role** of
`AmazeColors`, separately for **Light** and **Dark** modes (Dark overrides also
apply to AMOLED). Any unset role inherits the base palette, so the user can
tweak one role or remix the entire theme.

## Data model (theme/CustomPalette.kt)

```kotlin
enum class PaletteMode(val label: String) { LIGHT("Light"), DARK("Dark") }

enum class PaletteRole(val label: String) {
    BACKGROUND, SURFACE, ELEVATED_SURFACE, BORDER,
    TEXT_PRIMARY, TEXT_SECONDARY, TEXT_MUTED,
    ACCENT, SUCCESS, WARNING, DANGER, INFO,
    CHART1..CHART5, NAV_BACKGROUND, NAV_BORDER
}

@Serializable data class PaletteOverrides(val values: Map<String, String> = emptyMap())
// role name -> hex; null/absent = inherit base

@Serializable data class CustomPalette(
    val enabled: Boolean = false,
    val light: PaletteOverrides = PaletteOverrides(),
    val dark: PaletteOverrides = PaletteOverrides()
)
```

- Values are **hex strings** (`#RRGGBB`) for serializability.
- Backed by a `Map` so adding roles never requires a schema migration.
- Helpers: `PaletteOverrides.color/set/clear/isEmpty`,
  `CustomPalette.withEnabled/withRole/clearRole/resetAll`,
  `PaletteOverrides.applyTo(AmazeColors)` (uses existing `AmazeColors.copy`),
  `PaletteRole.currentOf(AmazeColors)` (reads the effective, already-applied color).

## Application (Theme.kt)

```kotlin
val colors = customPalette
    ?.takeIf { it.enabled }
    ?.let { palette -> if (resolvedTheme == AppTheme.LIGHT) palette.light else palette.dark }
    ?.takeIf { !it.isEmpty }
    ?.applyTo(baseColors)
    ?: baseColors
```

Order of precedence: preset accent → custom accent (CUSTOM) → palette accent
override wins. Everything updates live via `rememberColors.updateWith(...)`.

## State & persistence (AppState.kt)

- `customPalette: StateFlow<CustomPalette>` loaded from `KEY_CUSTOM_PALETTE` JSON
  in `init` (ignoreUnknownKeys).
- `setPaletteEnabled(Boolean)`, `setPaletteRole(mode, role, color)`,
  `clearPaletteRole(mode, role)`, `resetCustomPalette()` — each persists instantly.
- Logout resets to a fresh `CustomPalette()`.

## Editor UI (PaletteEditorScreen.kt)

Full screen routed as `SettingsSubScreen.PALETTE` from the Appearance page.

- **Mode pills** (Light / Dark) — `AmazePill` selector.
- **Role list**: one card per role showing a live swatch dot (effective color),
  role label, effective hex, and an "overridden" indicator + undo button when the
  role has a custom value. Tapping a row opens `ColorPickerSheet` seeded with the
  current value; "Choose Color" persists via `setPaletteRole`.
- **Reset All** (danger-tinted, enabled when any override exists).
- Footer shows the override count for the active mode.
- Dark mode note: "Dark overrides also apply to AMOLED mode."

## Appearance page entry

A "Custom Palette" group with:

- `SettingsSwitchRow` — **Enable Custom Palette** (`setPaletteEnabled`).
- **Edit Palette** row (chevron + override count) → opens the editor.

## Scope notes / known limits

- No contrast enforcement — low-contrast text colors are the user's choice.
- AMOLED inherits the Dark overrides (by design, keeps scope sane).
- The editor is settings-only; onboarding exposes only the one-color custom accent.