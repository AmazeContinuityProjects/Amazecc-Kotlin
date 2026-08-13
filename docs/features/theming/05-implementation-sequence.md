# Theming Feature — Implementation Sequence

How the feature was built, in dependency order, so each step compiled green and
could be shipped independently.

## Part A — Custom Accent (one color, app derives the rest)

1. **Theme.kt**: add `AccentTheme.CUSTOM`; add `customAccent: Color = AccentOcean`
   param to `AmazeTheme`; add CUSTOM branch to the accent `when`.
2. **Color.kt**: `Color.toHexString()` and `parseHexColor(hex)` (hex helpers live
   here, not in the UI layer, so `AppState` can import them cleanly).
3. **SettingsManager.kt**: `KEY_CUSTOM_ACCENT = "app_accent_custom"`.
4. **AppState.kt**: `_customAccentColor = MutableStateFlow(AccentOcean)` +
   `customAccentColor` flow; load hex in `init`; `setCustomAccent(color)` (sets
   CUSTOM + persists name + hex); reset in `logout()`.
5. **App.kt**: collect `customAccent` → pass to `AmazeTheme`.
6. **SettingsPages.kt**: fifth Custom swatch on the accent row (`AccentSwatch`
   gained `customColor`/`onClick` + CUSTOM branch) → `ColorPickerSheet`.
7. **SettingsHub.kt**: `accentLabel` CUSTOM → "Custom".
8. **OnboardingScreen.kt**: custom gradient circle after the presets → same sheet,
   then `setCustomAccent` + `onAccentChange(CUSTOM)`.
9. Gate: commonMain + Android + iOS compile.

## Part B — Palette Editor (per-role, per-mode)

1. **theme/CustomPalette.kt** (new): `PaletteMode`, `PaletteRole` (19 roles,
   `EDITABLE = entries`), `PaletteOverrides` (hex map, `applyTo(AmazeColors)` via
   `AmazeColors.copy`), `CustomPalette` (`enabled`/`light`/`dark`),
   `PaletteRole.currentOf(colors)` accessor for the editor's effective-color display.
2. **Theme.kt**: split `baseColors` (per appTheme) from the final `colors` line:
   `paletteOverrides?.applyTo(baseColors) ?: baseColors`.
3. **AppState.kt**: `customPalette` flow, JSON persistence
   (`jsonFormat.decodeFromString<CustomPalette>` / `encodeToString`),
   `setPaletteEnabled` / `setPaletteRole` / `clearPaletteRole` /
   `resetCustomPalette`, logout reset.
4. **SettingsManager.kt**: `KEY_CUSTOM_PALETTE = "custom_palette"`.
5. **App.kt**: collect `customPalette` → pass through.
6. **SettingsModels.kt**: `SettingsSubScreen.PALETTE("Custom Palette", ...)`.
7. **PaletteEditorScreen.kt** (new): mode pills, role rows (swatch + hex +
   overridden chip + undo), Reset All, `ColorPickerSheet` per role.
8. **SettingsPages.kt**: "Custom Palette" group — enable `SettingsSwitchRow` +
   "Edit Palette" row (override count + chevron) → `onOpenSubScreen(PALETTE)`.
9. **SettingsScreen.kt**: route `PALETTE -> PaletteEditorScreen()`;
   `AppearancePage(onOpenSubScreen = ...)`.
10. Gate: commonMain (hit `when` exhaustiveness in `SettingsHub.valueFor` — add
    `PALETTE -> null`), then Android + iOS.

## Part C — HSV Picker (dependency-free)

1. **ui/components/ColorPicker.kt** (new): `HsvColor` + both conversions,
   `HueSlider`, `SvArea`, `HsvColorPicker`, `ColorPickerSheet`.
2. Reuse: `SheetHeaderRow` (internal, ui.components) + `AmazeButton`.
3. Gate: commonMain + Android + iOS compile.

## Part D — Docs

- `docs/features/theming/01-overview.md` … `05-implementation-sequence.md`.

## Build gates used

```powershell
.\gradlew :shared:compileCommonMainKotlinMetadata --console=plain 2>&1 | Select-String -Pattern "^e:|error:|BUILD|FAILED"
.\gradlew :shared:compileAndroidMain :shared:compileKotlinIosSimulatorArm64 --console=plain 2>&1 | Select-String -Pattern "^e:|error:|BUILD|FAILED"
```

Known pre-existing Android deprecation warnings live in `AlarmReceiver.kt` /
`NotificationsUtils.android.kt` — unrelated, untouched.

## Sequence notes

- Parts A → B → C → D were done in this order so the picker (C) was ready before
  the palette editor (B's step 7) needed it; in practice A, C and the B data model
  are independent and could be merged in any order.
- Theme changes (steps 1–2 in each part) compile on their own because defaults
  (`customAccent = AccentOcean`, `customPalette = null`) preserve behavior.
- `animateColorAsState` lives in `androidx.compose.animation`, not
  `androidx.compose.animation.core` (not used in the final picker — thumbs are
  plain offsets).