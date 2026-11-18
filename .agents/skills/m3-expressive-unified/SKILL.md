---
name: m3-expressive-unified
description: >-
  Monolithic unified master skill for Material 3 Expressive (M3E) design, Jetpack Compose / Compose Multiplatform architecture, and fast headless JVM unit/UI testing. Synthesized word-for-word from mindcrafter129/m3e-skills, JusDots-Devs/m3e-skill, fumiya-kume/toy-poodle-love, and Chris Banes' Compose engineering practices. Trigger on Material 3, Material 3 Expressive, MUI3 Expressive, spring physics, MotionScheme, MaterialShapes, Compose UI redesign, headless unit testing, and component overhaul.
version: 2.0.0
---

# Material 3 Expressive (M3E) — Monolithic Unified Master Skill

This document is the **complete, unified master reference** for designing and building production applications with **Material 3 Expressive (M3E)** using **Jetpack Compose / Compose Multiplatform**. It unifies word-for-word documentation, token tables, API signatures, code recipes, and headless testing patterns.

---

# PART I: Material 3 Expressive (M3E) Core Foundations
*(Sourced directly from mindcrafter129/m3e-skills)*

M3 Expressive is **not** a new version of Material Design — it's an evolution of M3, introduced May 2025, focused on making UI feel more human, alive, and easy to scan. Design for emotion. Build for expression. Engage through experience.

> **Provenance note:** m3.material.io is a JS-rendered SPA that plain HTTP fetches can't read (returns an empty shell). The content below was extracted with **Playwright headless-browser scripts** that render each page and pull the real DOM text/tables — not secondhand summaries.

> **⚠️ Platform reality check — read this before writing any code.** M3 Expressive (springs, emphasized type, shape morph, new shapes, 5-size buttons, wavy progress, loading indicator, button groups, flexible app/nav bars) ships on **Jetpack Compose and Android Views (MDC-Android)**. **Flutter** has M3 but not most Expressive additions. **Material Web (MWC) is in MAINTENANCE MODE — M3 Expressive is NOT implemented on Web at all.** So if the person wants an M3E look on the web, you are **hand-rolling every expressive behavior in CSS/JS/SVG** (the wavy progress bar, the 7-shape morphing loader, shape-morph on press) — MWC's `<md-*>` components will give you *baseline* M3 only, not Expressive. Don't tell someone "just use Material Web" and expect the expressive look; say plainly that Web = baseline M3 components + custom code for the expressive parts. This single misunderstanding is behind most "the AI made bad M3E" results.

> **🎯 The two things AI gets wrong most (per real testing):** (1) **Progress bars.** In M3E the signature look is a **wavy/squiggly active track** — the filled portion of a linear indicator is a sine-wave squiggle, flattening to a plain line for the remaining track, with a small gap and a dot/stop indicator at the end. Not a flat bar. See §9 Progress indicators. (2) **Shapes.** People render generic rounded-rects or bad shapes instead of pulling from the actual 35-shape library, and they render the **loading indicator** as a spinner instead of what it actually is: a blob that **morphs through 7 different Material shapes** in a loop. See §2 and §9.

---

## 1. Color

The Material color system includes a built-in set of accessible color relationships, 26+ color roles mapped to Material Components, built-in dark theme colors, a static baseline scheme, and dynamic color (user-generated + content-based).

### Color roles — general concepts
- **Surface** — backgrounds and large, low-emphasis areas of the screen.
- **Primary / Secondary / Tertiary** — accent roles used to emphasize or de-emphasize foreground elements.
- **Container** — fill color for foreground elements like buttons. Never for text or icons.
- **On-** — text/icon color placed on top of its paired parent color (e.g. `on-primary` on `primary`).
- **-Variant** — a lower-emphasis alternative to its non-variant pair (e.g. `outline-variant` vs `outline`).
- Colors must be **paired/layered as intended** — mismatching roles (e.g. `primary` + `secondary-container` instead of `primary` + `on-primary`) can break contrast, especially under user-controlled contrast settings.

### Accent roles (primary / secondary / tertiary)
- **Primary** — the most prominent components: FAB, high-emphasis buttons, active states. 4 roles: `primary`, `on-primary`, `primary-container`, `on-primary-container`.
- **Secondary** — less prominent components, e.g. filter chips, the selected state of a nav icon, a dismissive button. Same 4-role shape.
- **Tertiary** — contrasting accents that balance primary/secondary, or bring heightened attention to something like an input field or a badge/notification. Applied at the designer's discretion — meant for broader color expression. Same 4-role shape.

### Error
- Static by default even in dynamic schemes (but still adapts light/dark). 4 roles: `error`, `on-error`, `error-container`, `on-error-container`.

### Surface roles
- `surface` (default background), `on-surface` (text/icons), `on-surface-variant` (lower-emphasis text/icons).
- **5 surface-container tiers** by emphasis: `surface-container-lowest`, `-low`, `surface-container` (default), `-high`, `-highest`. `surface-container` is the default; the others build hierarchy/nested containers, especially on expanded screens.
- Common pattern: `surface` for a body/background area, `surface-container` for a navigation area — and this mapping should **stay consistent across window size classes** (mobile vs tablet).
- **Inverse roles**: `inverse-surface`, `inverse-on-surface`, `inverse-primary` — for elements that should visually contrast against the surrounding UI (e.g. a snackbar).
- **Bright/dim add-ons**: `surface-dim` (dimmest in both themes) and `surface-bright` (brightest in both themes) — unlike default `surface`, which flips brightness between light/dark, these two **keep their relative brightness across both themes**.

### Outline
- `outline` — important boundaries, e.g. a text-field border.
- `outline-variant` — decorative elements like dividers, or borders on components (chips/buttons) whose *inner* content already provides 4.5:1 contrast.
- Don't use `outline` for dividers (wrong contrast requirement) or for multi-element containers like cards — use `outline-variant`. Don't use `outline-variant` to establish visual hierarchy or define a hard boundary — use `outline` or another 3:1-contrast color for that.

### Add-on roles (most products won't need these)
- **Fixed accent colors**: `primary-fixed` / `secondary-fixed` / `tertiary-fixed` (+ `-dim` variants) — fill colors that **stay the same tone across light and dark theme**, unlike regular containers which shift tone between themes. Use when you deliberately want a color that doesn't change with theme. `on-<x>-fixed` and `on-<x>-fixed-variant` are the paired text/icon roles. Caution: fixed colors don't adapt to theme, so they can cause contrast problems — don't use where contrast really matters.

### Choosing a scheme: static vs dynamic
**Static (baseline or custom-brand):**
- Pros: accessible colors, pre-made baseline scheme, won't break an M2 app, easy to upgrade to dynamic later.
- Cons: not personalized, doesn't respond to wallpaper/content, no user-controlled contrast.
- Use when: not ready for dynamic color yet; migrating from M2; building for enterprise users who wouldn't benefit from personalization; **building for iOS** (dynamic color is an Android-native concept).

**Dynamic:**
- Pros: accessible, personalized (responds to wallpaper or in-app content), supports advanced customization like color fidelity, supports user-controlled contrast.
- Cons: not the exact same UI colors across all devices.
- Two sources: **user-generated** (from the Android wallpaper) or **content-based** (from in-app content like an album cover, book cover, photo). You can combine both on the same screen (e.g. wallpaper-sourced scheme for the whole app + album-art-sourced scheme just for media controls) — but **limit a screen to two color-scheme source types** to avoid visual disarray, and never replace a semantic color (red error, green success) with dynamic content color.

### Baseline (default) hex values — light theme
```
Primary            #6750A4    On primary            #FFFFFF
Primary container  #EADDFF    On primary container  #4F378B
Secondary          #625B71    On secondary          #FFFFFF
Secondary container#E8DEF8    On secondary container#4A4458
Tertiary           #7D5260    On tertiary            #FFFFFF
Tertiary container #FFD8E4    On tertiary container  #633B48
Error              #B3261E    On error                #FFFFFF
Error container     #F9DEDC    On error container      #8C1D18
Surface             #FEF7FF    On surface              #1D1B20
Surface variant      #E7E0EC   On surface variant       #49454F
Surface container highest #E6E0E9   Surface container high #ECE6F0
Surface container          #F3EDF7   Surface container low  #F7F2FA
Surface container lowest   #FFFFFF
Inverse surface     #322F35    Inverse on surface       #F5EFF7
Surface tint         #6750A4
Outline              #79747E    Outline variant           #CAC4D0
Primary fixed        #EADDFF    On primary fixed          #21005D
Primary fixed dim    #D0BCFF    On primary fixed variant  #4F378B   Inverse primary #D0BCFF
Secondary fixed      #E8DEF8    On secondary fixed        #1D192B
Secondary fixed dim  #CCC2DC    On secondary fixed variant #4A4458
Tertiary fixed       #FFD8E4    On tertiary fixed         #31111D
Tertiary fixed dim   #EFB8C8    On tertiary fixed variant #633B48
Background           #FEF7FF    On background             #1D1B20
Surface bright        #FEF7FF   Surface dim                #DED8E1
Scrim / Shadow        #000000
```

---

## 2. Shape

M3E ships an **expanded shape library of 35 shapes** (see bundled `shapes-reference.png`). Categories: Basic (Circle, Square, Slanted, Arch, Semicircle, Oval, Pill, Triangle, Arrow, Fan), Geometric (Diamond, Clamshell, Pentagon, Gem, Very Sunny), Cookie/scalloped (Sunny, 4/6/7/9/12-sided cookie), Organic/clover (4-leaf clover, 8-leaf clover, Burst, Soft burst, Boom, Soft boom), Decorative (Flower, Puffy, Puffy diamond, Ghost-ish, Pixel circle, Pixel triangle, Bun, Heart).

### May 2025 changes
- Added the 35 new shapes + shape morphing to the Figma Design Kit and Jetpack Compose.
- New shape principles + refreshed art direction.
- Added corner-radius tokens: **large-increased (20dp)**, **extra-large-increased (32dp)**, **extra-extra-large (48dp)**.
- **Fully rounded corners now use a `full` token** — previously this was defined as 50% of the component size.

### Corner radius scale (10 steps, size-based)
| Style | Value |
|---|---|
| None | 0dp |
| Extra small | 4dp |
| Small | 8dp |
| Medium | 12dp |
| Large | 16dp |
| Large increased | 20dp |
| Extra large | 28dp |
| Extra large increased | 32dp |
| Extra extra large | 48dp |
| Full | fully rounded |

- Components can be **symmetric** (same value all corners) or **asymmetric** (different values per corner — "inner corners," used in components with closely-grouped items like menus and split buttons).
- **Optical roundness**: when nesting rounded objects, don't reuse the same corner radius for both — calculate `outer radius − padding = inner radius` (e.g. 48dp − 14dp = 34dp) so nested corners look proportional rather than unbalanced.

---

## 3. Typography

### M3 type scale — 30 styles total
The type scale has **one scale, two style sets**: **15 baseline** + **15 emphasized**, both spanning Display L/M/S → Headline L/M/S → Title L/M/S → Body L/M/S → Label L/M/S. Emphasized styles were added in the May 2025 expressive update — higher weight + minor adjustments vs baseline, meant for bold/selection/emphasis moments. **Use baseline and emphasized together**, not one or the other exclusively.

### Default typeface
**Google Sans** (Display/Headline/Title Large use "Google Sans"; Title Medium/Small, Body, and Label all use "Google Sans Text") is now the default in the current M3 type-scale tokens.

| Style | Font | Weight | Size | Tracking | Line height |
|---|---|---|---|---|---|
| Display Large | Google Sans | 400 | 57pt | 0 | 64pt |
| Display Medium | Google Sans | 400 | 45pt | 0 | 52pt |
| Display Small | Google Sans | 400 | 36pt | 0 | 44pt |
| Headline Large | Google Sans | 400 | 32pt | 0 | 40pt |
| Headline Medium | Google Sans | 400 | 28pt | 0 | 36pt |
| Headline Small | Google Sans | 400 | 24pt | 0 | 32pt |
| Title Large | Google Sans | 400 | 22pt | 0 | 28pt |
| Title Medium | Google Sans Text | 500 | 16pt | 0 | 24pt |
| Title Small | Google Sans Text | 500 | 14pt | 0 | 20pt |
| Body Large | Google Sans Text | 400 | 16pt | 0 | 24pt |
| Body Medium | Google Sans Text | 400 | 14pt | 0 | 20pt |
| Body Small | Google Sans Text | 400 | 12pt | 0.1pt | 16pt |
| Label Large | Google Sans Text | 500 (700 prominent) | 14pt | 0 | 20pt |
| Label Medium | Google Sans Text | 500 (700 prominent) | 12pt | 0.1pt | 16pt |
| Label Small | Google Sans Text | 500 | 11pt | 0.1pt | 16pt |

---

## 4. Motion Physics (Springs)

Material replaced its old easing/duration system with a **motion physics system** based on **springs**:
- **Expressive** — Material's default, opinionated scheme; **overshoots the final value to add bounce**. Use for most situations, especially hero moments and key interactions.
- **Standard** — more functional, minimal bounce, **eases into the final value** without overshoot. Use for utilitarian products.

Two spring **types**:
- **Spatial** — position, size, rotation, rounded-corner changes; **overshoots and bounces into place**.
- **Effects** — color, opacity; **no overshoot**.

Three **speeds** each:
| Speed | Spatial example | Effects example |
|---|---|---|
| Default | Animations covering part of the screen (bottom sheet, expanded nav rail) | Opacity of content inside a nav rail |
| Fast | Small components (switches, buttons) | Color change of a switch handle |
| Slow | Full-screen animations | Full-screen content refresh |

| Token | Damping | Stiffness |
|---|---|---|
| Fast spatial | 0.9 | 1400 |
| Fast effects | 1 | 3800 |
| Default spatial | 0.9 | 700 |
| Default effects | 1 | 1600 |
| Slow spatial | 0.9 | 300 |
| Slow effects | 1 | 800 |

---

## 5. Elevation

- M3 uses **tonal surface color instead of shadows by default**, reserving visible shadows for when they're actually needed (protection against a busy background, or encouraging interaction).
- **Component elevation levels**:
  - Level 0 (0dp): App bar (not scrolled), Buttons (filled/tonal/outlined), Button groups, Cards (filled/outlined), Carousel, Tabs.
  - Level 1 (1dp): Elevated Button, Elevated Card, Modal Bottom Sheet.
  - Level 2 (3dp): Scrolled App Bar, Navigation Bar, Floating Toolbar.
  - Level 3 (6dp): FAB, Extended FAB, FAB menu, Dialogs, Search.
  - Level 4 (8dp) & Level 5 (12dp): Hovered and dragged states only.

---

## 6. Icons & Spacing

- Base unit = 8dp (`space100` = 8dp).
- Material Symbols (Outlined, Rounded, Sharp).
- Standard size: 24dp × 24dp (live area: 20dp × 20dp centered, with 2dp padding).

---

## 7. Wavy Progress & 7-Shape Morphing Loading Indicator

### Progress Indicators:
- **Wavy linear determinate**: 3dp amplitude sine wave, 40dp wavelength, 4dp stroke, sitting in a 10dp-tall container, with a 4dp gap, then flat remaining track, 4dp gap, and 4dp stop dot.
- Active = `primary`, track = `secondary-container`, stop = `primary`.

### Loading Indicator (NOT a spinner):
- Active indicator is a **single blob morphing continuously through 7 Material 3 shapes on a loop**:
  `soft burst → 9-sided cookie → pentagon → pill → sunny → 4-sided cookie → oval → (repeat)`
- Continuously rotating, accelerating mid-morph, springy overshoot on each settle.
- Default size = 48dp.

---

# PART II: Jetpack Compose M3E Implementation Specifications
*(Sourced directly from JusDots-Devs and fumiya-kume)*

### Dependencies:
```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.4.0-alpha15")
    // or Compose Multiplatform material3 1.7+ / 1.11+
}
```

### Opt-in Annotation:
```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

### Theme Wrapping:
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive() // Bouncy, physics-driven motion
    ) {
        content()
    }
}
```

### ButtonGroup (Connected & Spaced):
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FilterButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            Button(
                onClick = { onSelect(index) },
                shapes = ButtonDefaults.shapes(),
                colors = if (isSelected) {
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                } else {
                    ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(option)
            }
        }
    }
}
```

### Floating Toolbar:
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingActionToolbar(
    onAction1: () -> Unit,
    onAction2: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.padding(bottom = 16.dp),
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        IconButton(onClick = onAction1) {
            Icon(Icons.Rounded.Search, contentDescription = "Search")
        }
        IconButton(onClick = onAction2) {
            Icon(Icons.Rounded.Tune, contentDescription = "Filter")
        }
    }
}
```

---

# PART III: Architecture Standards for Fast Headless Testing & Overhaul
*(Sourced from Chris Banes' Compose Engineering Practices)*

### 1. Strict Stateful / Stateless Split
Composables must NEVER read from singletons (like `AppState`), network clients, or disk directly.
- **State Data Class**: Pure, immutable Kotlin data class holding primitive types, lists, and enum flags.
- **Stateless Composable**: Accepts `(state: MyState, onAction: (Action) -> Unit, modifier: Modifier = Modifier)`.
- **Stateful Wrapper / Screen**: Gathers Flow / Coroutine state off-main-thread and passes it to the stateless composable.

### 2. Multi-Threading & Asynchronous Concurrency
- Network requests, JSON deserialization, and expensive list filtering/sorting MUST run on `Dispatchers.Default` / `Dispatchers.IO`.
- UI thread (`Dispatchers.Main`) only handles state rendering.
- Use `StateFlow` + `SharingStarted.WhileSubscribed(5000)` to prevent UI thread blocking.

### 3. Headless JVM Compose UI Testing (No Emulator / Device Needed!)
Run UI tests on your host computer using `runComposeUiTest` from `compose-ui-test-junit4` or `compose.ui.test` in `shared/src/jvmTest`. Tests execute in **2 to 8 seconds**, asserting:
- Correct text, semantics, and icons
- Proper colors and container roles
- Click callbacks, swipe gestures, and transitions
- Edge cases (empty data, network error, massive lists)
