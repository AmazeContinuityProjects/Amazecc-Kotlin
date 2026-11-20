---
name: AmazeCC
description: Attendance-first student OS in Material 3 Expressive on a pitch-black, spring-driven canvas.
colors:
  periwinkle-signal: "#A8C7FA"
  periwinkle-on: "#003062"
  periwinkle-container: "#0842A0"
  periwinkle-container-on: "#D3E3FD"
  sky-signal: "#7FCFFF"
  sky-on: "#00344F"
  sky-container: "#004C70"
  sky-container-on: "#C2E7FF"
  blush-signal: "#EFB8C8"
  blush-on: "#492532"
  blush-container: "#633B48"
  blush-container-on: "#FFD8E4"
  ink-background: "#000000"
  ink-surface: "#0B0B0E"
  ink-lowest: "#000000"
  ink-low: "#0F1014"
  ink-container: "#16171D"
  ink-high: "#1E2028"
  ink-highest: "#282B36"
  ink-on: "#E3E2E6"
  ink-on-variant: "#C4C6D0"
  outline: "#8E9099"
  outline-variant: "#33353E"
  mint-safe: "#6DD58C"
  mint-safe-on: "#003919"
  mint-safe-container: "#0F5228"
  mint-safe-container-on: "#B9F0C8"
  amber-caution: "#FFBA28"
  amber-caution-on: "#422C00"
  amber-caution-container: "#5F4100"
  amber-caution-container-on: "#FFE1A6"
  coral-alert: "#FFB4AB"
  coral-alert-on: "#690005"
  coral-alert-container: "#93000A"
  coral-alert-container-on: "#FFDAD6"
  light-primary: "#0B57D0"
  light-on-primary: "#FFFFFF"
  light-primary-container: "#D3E3FD"
  light-primary-container-on: "#041E49"
  light-secondary: "#00639B"
  light-on-secondary: "#FFFFFF"
  light-secondary-container: "#C2E7FF"
  light-secondary-container-on: "#001D32"
  light-tertiary: "#7D5260"
  light-on-tertiary: "#FFFFFF"
  light-tertiary-container: "#FFD8E4"
  light-tertiary-container-on: "#31111D"
  light-background: "#F8F9FA"
  light-on-background: "#1F1F1F"
  light-surface: "#FFFFFF"
  light-on-surface: "#1F1F1F"
  light-on-surface-variant: "#444746"
  light-lowest: "#FFFFFF"
  light-low: "#F3F4F6"
  light-container: "#EDEEF2"
  light-high: "#E7E8EC"
  light-highest: "#E1E3E8"
  light-outline: "#74777F"
  light-outline-variant: "#C4C7C5"
  light-mint-safe: "#146C2E"
  light-mint-safe-on: "#FFFFFF"
  light-mint-safe-container: "#CEEAD6"
  light-mint-safe-container-on: "#0A3D1B"
  light-amber-caution: "#E37400"
  light-amber-caution-on: "#2E1C00"
  light-amber-caution-container: "#FEEFC3"
  light-amber-caution-container-on: "#422C00"
  light-coral-alert: "#BA1A1A"
  light-coral-alert-on: "#FFFFFF"
  light-coral-alert-container: "#FFDAD6"
  light-coral-alert-container-on: "#410002"
typography:
  # All sizes are scale-independent sp (never px) so system text scaling holds.
  # `materialRole` is the source of truth; numeric values are the local override.
  headline:
    fontFamily: "Roboto, sans-serif"
    materialRole: "displaySmall"
    fontSize: "24sp"
    fontWeight: 900
    letterSpacing: "-0.5sp"
  title:
    fontFamily: "Roboto, sans-serif"
    materialRole: "titleMedium"
    fontSize: "18sp"
    fontWeight: 700
  body:
    fontFamily: "Roboto, sans-serif"
    materialRole: "bodySmall"
    fontSize: "14sp"
    fontWeight: 400
  label:
    fontFamily: "Roboto, sans-serif"
    materialRole: "labelSmall"
    fontSize: "10sp"
    fontWeight: 700
    letterSpacing: "1sp"
rounded:
  inner: "8px"
  chip: "12px"
  card: "20px"
  pill: "24px"
  sculpt: "26px"
  sheet: "28px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
components:
  surface-card:
    backgroundColor: "{colors.ink-container}"
    textColor: "{colors.ink-on}"
    rounded: "{rounded.card}"
    padding: "16px"
  surface-card-interactive:
    backgroundColor: "{colors.ink-container}"
    textColor: "{colors.ink-on}"
    rounded: "{rounded.card}"
    padding: "16px"
  connected-group:
    backgroundColor: "{colors.ink-low}"
    textColor: "{colors.ink-on-variant}"
    rounded: "{rounded.pill}"
    padding: "4px"
  connected-group-selected:
    backgroundColor: "{colors.periwinkle-signal}"
    textColor: "{colors.periwinkle-on}"
    rounded: "{rounded.card}"
    padding: "16px 9px"
  stat-card:
    backgroundColor: "{colors.ink-low}"
    textColor: "{colors.periwinkle-signal}"
    rounded: "{rounded.sculpt}"
    padding: "14px 12px"
  nav-pill:
    backgroundColor: "{colors.ink-highest}"
    textColor: "{colors.periwinkle-signal}"
    rounded: "{rounded.pill}"
    padding: "10px 8px"
  floating-toolbar:
    backgroundColor: "{colors.ink-highest}"
    textColor: "{colors.ink-on}"
    rounded: "{rounded.pill}"
    padding: "12px 6px"
  light-surface-card:
    backgroundColor: "{colors.light-container}"
    textColor: "{colors.light-on-surface}"
    rounded: "{rounded.card}"
    padding: "16px"
  light-stat-card:
    backgroundColor: "{colors.light-low}"
    textColor: "{colors.light-primary}"
    rounded: "{rounded.sculpt}"
    padding: "14px 12px"
  light-connected-group-selected:
    backgroundColor: "{colors.light-primary}"
    textColor: "{colors.light-on-primary}"
    rounded: "{rounded.card}"
    padding: "16px 9px"
  light-nav-pill:
    backgroundColor: "{colors.light-highest}"
    textColor: "{colors.light-primary}"
    rounded: "{rounded.pill}"
    padding: "10px 8px"
  light-floating-toolbar:
    backgroundColor: "{colors.light-highest}"
    textColor: "{colors.light-on-surface}"
    rounded: "{rounded.pill}"
    padding: "12px 6px"
---

# Design System: AmazeCC

## Overview

**Creative North Star: "The Living Ledger"**

AmazeCC's Material 3 Expressive language treats the screen as a ledger that is quietly alive. On an absolute-black canvas, records are laid out with accounting clarity — five tonal strata, hairline separators, uppercase micro-labels — and then made tactile: cards compress under a finger, connected tabs crossfade into a selected pill with a checkmark, and the wavy progress meter gives attendance and marks a visible pulse. The system is precise and calm at rest, sculptural and optimistic in motion.

Color is scarce and semantic. Periwinkle, sky, and blush are roles for primary, secondary, and tertiary actions; mint, amber, and coral carry success, caution, and error. They never decorate. Depth comes first from the tonal ladder and only then from a controlled pair of lift shadows. Type stays inside Material 3 roles, with local overrides only for the two moments that matter: the uppercase micro-label and the oversized metric.

The migration plan retires the legacy `AmazeTheme`/`AccentTheme` pill-and-glow interface and forbids `amazeui`, `shadcn`, and Tailwind web styles. Material 3 governs structure, navigation, and interaction; iOS/Cupertino controls are not part of this language, and the same design carries across the KMP targets without an OS-specific redesign. Midnight OLED and Classic Light are two first-class appearances of the same language with their own role values; neither is an inversion or a reduced version of the other.

**Key Characteristics:**
- Pitch-black, five-tier tonal canvas with hairline borders instead of generalized shadows.
- Spring physics on every interactive element; a 3dp sine wave is the signature meter.
- Sculptural asymmetric corners on stat cards, regular large radii on containers, full pills on navigation.
- Semantic-only color: status and selection are the only reasons an accent appears.

## Colors

A low-chroma ink ladder carries the structure; three cool signal accents and three status hues carry meaning.

### Primary
- **Periwinkle Signal** (`#A8C7FA`): selected tabs, active navigation, hero metric values, and the active wavy meter. Its dark on-color is `#003062`; its container is `#0842A0`.

### Secondary
- **Sky Signal** (`#7FCFFF`): supporting actions and the wavy meter's flat track. Its container is `#004C70`.

### Tertiary
- **Blush Signal** (`#EFB8C8`): a third, restrained accent for distinct but non-status categories. Its container is `#633B48`.

### Neutral
- **Midnight Ink Background** (`#000000`): the canvas. Never lightened to grey for a "softer" look.
- **Midnight Ink Surface** (`#0B0B0E`): the base surface above the canvas.
- **Ink Tonal Tiers** (`#000000`, `#0F1014`, `#16171D`, `#1E2028`, `#282B36`): `surfaceContainerLowest` through `surfaceContainerHighest`, the primary depth mechanism.
- **Ink On** (`#E3E2E6`) and **Ink On Variant** (`#C4C6D0`): primary and secondary text.
- **Outline** (`#8E9099`) and **Outline Variant** (`#33353E`): borders; the variant is used at 0.4–0.5 alpha on cards and navigation.

### Status
- **Mint Safe** (`#6DD58C`): attendance ≥75% and successful states; on-color `#003919` (7.22:1), container `#0F5228`, on-container `#B9F0C8` (7.25:1).
- **Amber Caution** (`#FFBA28`): warnings and near-threshold states; on-color `#422C00` (7.73:1), container `#5F4100`, on-container `#FFE1A6` (7.39:1).
- **Coral Alert** (`#FFB4AB`): critical attendance and errors; on-color `#690005` (7.72:1), container `#93000A`, on-container `#FFDAD6` (7.24:1).

### Classic Light (Parallel Scheme)

Classic Light keeps the same roles with their own values; it is not an inversion of Midnight.

- **Primary:** Light Periwinkle `#0B57D0`; on-color `#FFFFFF`; container `#D3E3FD`; on-container `#041E49`.
- **Secondary:** Light Sky `#00639B`; on-color `#FFFFFF`; container `#C2E7FF`; on-container `#001D32`.
- **Tertiary:** Light Blush `#7D5260`; on-color `#FFFFFF`; container `#FFD8E4`; on-container `#31111D`.
- **Neutral:** background `#F8F9FA`; on-background `#1F1F1F`; surface `#FFFFFF`; on-surface `#1F1F1F`; on-surface-variant `#444746`.
- **Ink Tonal Tiers (light):** `#FFFFFF`, `#F3F4F6`, `#EDEEF2`, `#E7E8EC`, `#E1E3E8` from lowest to highest.
- **Outline:** `#74777F`; outline variant `#C4C7C5`.
- **Status:** Mint Safe `#146C2E` — on-color `#FFFFFF` (6.53:1), container `#CEEAD6`, on-container `#0A3D1B` (9.65:1). Amber Caution `#E37400` — on-color `#2E1C00` (5.28:1), container `#FEEFC3`, on-container `#422C00` (11.53:1). Coral Alert `#BA1A1A` — on-color `#FFFFFF` (6.46:1), container `#FFDAD6`, on-container `#410002` (13.26:1). The old mint fill `#1E8E3E` and white-on-amber pair failed AA (4.21:1 and 3.10:1); the measured values above all meet WCAG AA.

Init records Midnight as the incumbent identity, so the dark tokens remain the canonical set in frontmatter; the `light-*` keys are the complete parallel record.

**The Two Appearances Rule.** Midnight and Classic Light are parallel schemes with their own role values. Never derive one by inverting the other, and never let a component hard-code a value that only works in one.

**The Signal Rule.** An accent appears only to show state, selection, or amount. A decorative accent is a bug.

**The Ink Rule.** The canvas is pure black. Depth is earned through tonal tiers, never by tinting the background.

## Typography

**Display Font:** Roboto through Material 3's default type scale (system face on Android)
**Body Font:** Roboto — the M3E screens use `MaterialTheme.typography` roles rather than the legacy Outfit/Geist pair.
**Label/Mono Font:** none defined.

**Character:** Neutral and legible; personality comes from weight, tracking, and case rather than a bespoke typeface. Micro-labels are loud and wide; metrics are heavy and tight.

### Hierarchy
All type sizes are `sp` (scale-independent), never `px`; the Material role is authoritative and the numeric value is only the local override.
- **Headline** (weight 900, 24sp / `displaySmall`, line-height proportional, tracking -0.5sp): the oversized stat value — often an attendance percentage or CGPA — in the tone's accent color.
- **Title** (weight 700, 18sp / `titleMedium`): section and card titles.
- **Body** (weight 400, 14sp / `bodySmall`): supporting copy, locally set to 11sp with a 13sp line-height inside stat cards.
- **Label** (weight 700, 10sp / `labelSmall`, tracking 1sp, uppercase): category and stat labels; navigation labels use `labelLarge` at 14sp.

**The Two-Voice Rule.** Give every number a Black-weight headline with tight tracking and every label an uppercase, wide-tracked micro-size. Everything in between stays quiet.

## Layout

Content sits on a 16px horizontal and top inset (start/end/top 16, bottom 0) with a 16px vertical rhythm between major blocks and 12px inside groups. Cards carry 16px internal padding; connected groups use 4px of outer padding around their segments; stat cards use 12px vertical and 14px horizontal padding. The floating navigation is a centered pill inset 16px horizontally and 8px vertically from the bottom, with 10/8px internal padding and an 8px gap between destinations. A floating toolbar uses a wider 24/16px inset. Bottom sheets take 28px top corners and 20px horizontal padding.

Stat cards live in a horizontally scrollable carousel; connected button groups scroll horizontally rather than truncating labels on narrow screens. Information-dense grids (timetables, week strips) use 12px gaps and 10–12px inner radii.

## Elevation & Depth

Depth is tonal first: the five `surfaceContainer` tiers separate layers, and a 1px `outlineVariant` border at 0.4–0.5 alpha defines the edge of a card. Shadows are reserved for two lifted surfaces — the bottom navigation pill at 8px elevation and the sync notification sheet at 16px. The floating toolbar deliberately has no shadow; it is a flat pill on the highest tonal tier with a hairline border.

**The Tonal First Rule.** If a layer can be separated with a tonal step and a hairline border, do not add a shadow.

## Shapes

Large, soft containers are the default: 20px for surface cards, 24px for connected groups and navigation pills, 28px for the top corners of bottom sheets. Inner chips and list items use 8–12px; timetable cells use 10–12px. Circles are reserved for icons, avatars, and active navigation indicators. Stat cards are the exception and the signature: each tone uses an asymmetric radius (for example 26px/10px/26px/10px), and the selected segment of a connected group becomes an 18px pill inside a 24px container.

**The Asymmetry Rule.** Never normalize a stat card to a uniform radius. Its uneven corners are what make the ledger feel sculptural rather than generic.

## Components

### Connected Button Group
A 24px pill of `ink-low` holding 4px-padded segments. Unselected segments keep asymmetric outer corners and `ink-on-variant` text; the selected segment becomes an 18px pill filled with `periwinkle-signal`, `periwinkle-on` text, and a 15dp check icon. Selection crossfades with `effectsDefault`; labels are 13sp and never wrap.

### Surface Card
A 20px, tonally-filled container with a 1px `outlineVariant` border at half alpha and 16px internal padding. Interactive cards scale to 0.97 on press with `spatialDefault` and expose an explicit button role, focus, and Enter/Space activation through `clickableA11y`.

### Stat Card
A sculptural, asymmetric tile on `ink-low`. The tone sets the accent for a 24sp Black-weight metric and a circular icon container; the label is a 10sp uppercase micro-line, and the subtitle is 11sp on `ink-on-variant`.

### Wavy Linear Progress
The signature component: a 14dp-high canvas with a 4dp round stroke, 3dp sine amplitude, 40dp wavelength, 4dp gap before the flat track, and a 4dp stop dot. The active wave is `periwinkle-signal`; the remaining track is `sky-container` at half alpha.

### Navigation
A centered 8dp-elevation pill of `ink-highest` at 0.98 alpha with a 1.5px half-alpha border. The selected destination grows from a 16dp to a 22dp circular indicator; labels use `labelLarge` at 14sp. The floating toolbar is the flatter sibling: same tonal base at 0.95 alpha, 1px border, no shadow.

### Bottom Sheet
A 28px-top-corner surface with a short drag handle, 20px horizontal padding, and a 120dp swipe-to-dismiss threshold. Sync feedback uses a 16dp-elevation notification card above it.

### Skeleton and Sync Feedback
Loading states mirror the real card geometry: pill-clipped bars, 12px gaps, and 24px skeleton cards. "Copied" feedback lasts 2000ms, a clean sync closes the sheet after 1500ms, and tab switches use a 180ms crossfade with a 0.98 scale.

## Do's and Don'ts

### Do:
- **Do** use the five tonal tiers and a hairline border before reaching for a shadow.
- **Do** drive interaction feedback with the spring specs: `spatialDefault`, `spatialFast`, `spatialSlow`, and `effectsDefault`.
- **Do** clip dynamic text to one line with ellipsis; course codes, room names, and labels must never wrap into a card.
- **Do** collapse every spec to an instant, non-bouncy spring when the OS Reduce Motion setting or the in-app animation toggle is off.
- **Do** give clickable cards and chips an explicit button role, focus, and Enter/Space activation.
- **Do** verify every component in both Midnight and Classic Light; each appearance uses its own role tokens and neither is an afterthought.

### Don't:
- **Don't** use raw hex values in components; reference the named tokens.
- **Don't** port iOS/Cupertino controls or bring back `amazeui`, `shadcn`, or Tailwind web styles.
- **Don't** resurrect the legacy neon-glow, uniformly-pilled `AmazeTheme`/`AccentTheme` look.
- **Don't** tint the pure-black canvas or stack shadows on ordinary cards.
- **Don't** hand-pick per-screen font families or reintroduce the legacy Outfit/Geist pairing in M3E screens.
