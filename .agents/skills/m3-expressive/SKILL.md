---
name: m3-expressive
description: >-
  Use this skill whenever building or styling UI (Compose, Wear Compose,
  HTML/CSS, React) that should look like Material 3 Expressive (M3E),
  Google's 2025 evolution of Material Design. Trigger on any UI or visual
  design work where the goal is a modern, expressive, Google-native look:
  bold shapes, emphasized type, springy motion, and tonal color. Covers
  color, shape, typography, motion and easing, elevation, icons, and spacing.
---

# Material 3 Expressive (M3E)

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
Get these interactively (and the dark-theme set) via the **Material Theme Builder** Figma plugin, or https://material-foundation.github.io/material-theme-builder/. Custom brand palettes: feed your own hex into Theme Builder's primary/secondary/tertiary/neutral/neutral-variant inputs — it derives the full accessible scheme from your reference colors (best practice: primary & tertiary should be the most visually prominent, tertiary complementary to primary by hue shift; secondary/neutral/neutral-variant match primary's hue but decrease in chroma in that order).

### Advanced color customization (only if the default system doesn't cover your need)
- **Combine multiple schemes** on one screen (e.g. baseline app + content-based media player) — build hierarchy, link related content, immerse users in content color, but keep the source content visible and limit to 2 scheme-source types per screen.
- **Remap colors on components**: pick a role based on use case, in Figma hover the color row → Style icon → search your theme name → pick the closest-matching role. Always pick "on-" roles for guaranteed contrast.
- **Define static colors** (formerly "custom colors") — e.g. a green "Success" role your product needs but Material doesn't ship. Input a reference color into Theme Builder; you get back 4 roles (`success`, `on-success`, `success-container`, `on-success-container`) following the same conventions as the built-in accents. Can optionally **harmonize** static colors' hue slightly toward the scheme's primary for visual cohesion, or keep them fully static if tied to a literal source (brand color, real-world signage).
- **Define entirely custom color roles** beyond the 26+ standard ones — only if static colors and remapping still don't cover it. Requires specifying a palette + reference tone (e.g. `primary70`) for light/dark, the pairing relationships, and confirming contrast minimums, then generating with Material Color Utilities (MCU).
- **Adjust existing colors**: define your own **baseline scheme** (custom primary/secondary/tertiary/neutral/neutral-variant inputs) or your own **dynamic scheme** (custom hue/chroma rules per color group, e.g. "match the user's wallpaper but stay more vibrant than default"). **Color fidelity** is a toggle that adjusts tones to better match your literal input color when the accessible-tone-mapping would otherwise wash it out — enabled by default when defining custom/static colors in Theme Builder.

### What's new in color (2025)
- **May 2025**: three levels of contrast (standard/medium/high) now tokenized, so people can pick the level that suits their vision needs.
- Aug 2024: `on-primary-container`, `on-secondary-container`, `on-tertiary-container`, `on-error-container` became more colorful in light theme while staying accessible (affects badges, buttons, FAB, chips, lists, menus, nav bar/drawer/rail, switches, etc.).

---

## 2. Shape

M3E ships an **expanded shape library of 35 shapes** (see bundled `shapes-reference.png`). Categories: Basic (Circle, Square, Slanted, Arch, Semicircle, Oval, Pill, Triangle, Arrow, Fan), Geometric (Diamond, Clamshell, Pentagon, Gem, Very Sunny), Cookie/scalloped (Sunny, 4/6/7/9/12-sided cookie), Organic/clover (4-leaf clover, 8-leaf clover, Burst, Soft burst, Boom, Soft boom), Decorative (Flower, Puffy, Puffy diamond, Ghost-ish, Pixel circle, Pixel triangle, Bun, Heart).

### May 2025 changes
- Added the 35 new shapes + shape morphing to the Figma Design Kit and Jetpack Compose.
- New shape principles + refreshed art direction.
- Added corner-radius tokens: **large-increased (20dp)**, **extra-large-increased (32dp)**, **extra-extra-large (48dp)**.
- **Fully rounded corners now use a `full` token** — previously this was defined as 50% of the component size.

### Principles
- **Use shapes and text in harmony** — M3 shapes and Google Sans Flex share roundness visual attributes; using them together makes a product feel cohesive.
- **Morph shapes to connect function and feeling.** Use shape-morph to communicate interaction states (a button being selected), actions in progress (someone typing, a page loading), or environment changes (sound, temperature, time of day). Think about how a shape should react to tapping, swiping, scrolling, releasing, long-pressing.
- **Be bold, embrace tension.** Contrasting square + rounded shapes (or other unconventional pairings) create dynamic, more memorable, more expressive design — a break from Material's historically rounded-only default.
- **Shape is versatile, not semantic.** Don't assign one specific meaning to one specific shape — e.g. a wavy loading indicator isn't "the" symbol of progress; progress could equally use rotating shapes or shape-morph, and waveforms can show up elsewhere unrelated to progress.
- **Use abstract shapes sparingly and intentionally** — don't sacrifice clarity for visual flourish; ask why a shape variation is there and what it contributes.
- **Emphasize aesthetic moments with shape** — decorative/non-interactive elements (photo cropping, avatar masking, imagery) are where shape can get the most creative.
- **Shape can be 2.5D** — applying motion + shape differently per visual layer creates an illusion of depth.

### Corner radius scale (10 steps, size-based — not the old M2 size-tiered system)
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
- **Customize at the style level** (e.g. redefine what "medium" maps to — affects every component using that style) or **at the component level** (remap just one component, e.g. buttons default to `full`; remap to `small`/`medium` for a less-rounded look).
- The whole style family can be swapped from **rounded to cut** (straight-line corners instead of curved) — add extra padding in information-dense components so content/images aren't clipped; don't apply large/full corners to dense components like cards.
- **Optical roundness**: when nesting rounded objects, don't reuse the same corner radius for both — calculate `outer radius − padding = inner radius` (e.g. 48dp − 14dp = 34dp) so nested corners look proportional rather than unbalanced.
- The shape library is for **mostly visual elements** — avoid unconventional shapes on text-heavy containers; use sparingly, for "moments of delight."

### Shape morph
- Full shape-morph API access is platform-specific: **Android via the Shapes-in-Compose API**; **not currently available on Web.**
- Shape morphing uses the **expressive motion scheme by default** (can switch to standard).
- Two built-in components already use it: the **standard button group** (morphs on interaction) and the **loading indicator** (morphs to show progress).

---

## 3. Typography

### M3 type scale — 30 styles total
The type scale has **one scale, two style sets**: **15 baseline** + **15 emphasized**, both spanning Display L/M/S → Headline L/M/S → Title L/M/S → Body L/M/S → Label L/M/S. Emphasized styles were added in the May 2025 expressive update — higher weight + minor adjustments vs baseline, meant for bold/selection/emphasis moments. **Use baseline and emphasized together**, not one or the other exclusively.

### Default typeface
**Google Sans** (Display/Headline/Title Large use "Google Sans"; Title Medium/Small, Body, and Label all use "Google Sans Text") is now the default in the current M3 type-scale tokens — this supersedes the older "Roboto is the default" guidance for the Expressive scale. Roboto/Roboto Flex/Roboto Serif/Roboto Mono/Noto Sans remain available as variable-font families and fallbacks (see Fonts below), but **verify which one your target platform is actually shipping** since Android system defaults and the design-spec default aren't necessarily identical.

### Full baseline type-scale token table
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

Each style also exposes variable-font axis tokens (all 0/100 defaults unless noted): `wght`, `GRAD` (grade), `wdth` (width, 100), `ROND` (roundness), `opsz` (optical size — matches point size), `CRSV` (cursive), `slnt` (slant), `FILL`, `HEXP`.

### Emphasized type-scale deltas (vs baseline — same sizes, higher weight)
| Style | Weight | wght axis | Size / line-height |
|---|---|---|---|
| Emphasized Display Large | 500 | 500 | 57pt / 64pt (unchanged size) |
| Emphasized Display Medium | 500 | 500 | 45pt / 52pt |
| Emphasized Display Small | 500 | 500 | 36pt / 44pt |
| Emphasized Headline Large | 500 | 500 | 32pt / 40pt |
| Emphasized Headline Medium | 500 | 500 | 28pt / 36pt |
| Emphasized Headline Small | 500 | 500 | 24pt / 32pt |
| Emphasized Title Large | 500 | 500 | 22pt / 28pt |
| Emphasized Title Medium | 700 | 600 | 16pt / 24pt |
| Emphasized Title Small | 700 | 600 | 14pt / 20pt |

(Body/Label emphasized styles follow the same pattern — weight steps up, size/line-height stay put. Re-scrape `type-scale-tokens` for the exact Body/Label emphasized numbers if needed — the table above covers what was captured; Material explicitly recommends emphasized styles for **selection, actions, headlines, and editorial treatments**, not everyday body copy.)

### Applying type — the 5 roles
- **Display** (L/M/S) — largest text on screen; short, important text or numerals; works best on large screens; good place for an expressive/handwritten/script typeface.
- **Headline** (L/M/S) — short, high-emphasis text on smaller screens; marking primary passages/regions of content; can use expressive typefaces if line-height/letter-spacing are tuned for readability.
- **Title** (L/M/S) — medium-emphasis, relatively short; divides secondary passages/regions; use caution with expressive/display/handwritten/script fonts here.
- **Body** (L/M/S) — longer passages; use typefaces meant for body copy, legible at small sizes; avoid expressive/decorative fonts.
- **Label** (L/M/S) — small utilitarian text inside components (buttons use Label Large) or tiny content text like captions; needs to read fast at small size.

### Typesetting
- **Web/iOS**: use line-height + bounding box; text is vertically centered in the bounding box per CSS "half-leading" behavior; specify distances from fixed reference points (container edge), automate with Sass/CSS.
- **Android / platform-agnostic specs**: use the **baseline** (the invisible line text rests on) — measure line-height as baseline-to-baseline distance, specify center for vertical centering.

### Fonts and fallback
| Variable font | Available axes |
|---|---|
| Roboto Flex | Slant, Width, Weight, Grade, Optical Size + advanced axes (thick/thin stroke, counter width, uppercase/lowercase/ascender/descender height, figure height) |
| Roboto Mono | Weight, Italic |
| Noto Sans | Width, Weight, Italic |

- **Roboto** — default Android typeface, 3,300+ glyphs.
- **Roboto Flex** — variable font, extended weight/width range, 900+ glyphs (Latin/Greek/Cyrillic), standalone.
- **Roboto Serif** — variable, comfortable reading, broad weight/width range.
- **Roboto Mono** — monospaced, good for code / aligned numbers.
- **Noto Sans** — global fallback family, 150+ scripts, used when a language isn't supported by the primary font.
- **Fallback order** for products using Roboto Flex: Roboto Flex → Roboto → Noto Sans (per-language variant). Confirm actual fallback chain with your engineering partners.

### Editorial treatments
Standalone, showcase moments driven by type — dynamic, attention-grabbing custom sizing (larger display type, blockier feel), meant to depart from purely functional layouts. Can combine with motion/shape/color for product-wide hero moments. Three use cases:
1. **Celebrating content** — e.g. an exuberant cover image/type moment for a photo album; match the type's tone to the content's emotion (narrow/thin for serenity, bold/italic for liveliness).
2. **Voice of the user** — let users personalize type appearance or adjust text based on their input, to frame mood.
3. **Bespoke functionality** — express a unique in-product moment (example given: raising bedroom light brightness triggers an algorithmic response in a font's width/weight axes).

Best practices: tokenize editorial treatments for consistency across the product; match emotional tone to the task; don't mix clashing styles in one layout; don't let an editorial treatment be mistaken for user-personalization theming. **Editorial treatments should not be used in labels or purely informational text** — they're for showcase moments, not the everyday UI.

Adjustable variable-font axes for editorial work: **Weight** (thickness — be careful with very light weights on body text/low-res displays; save light weights for large display type), **Grade** (a finer, non-reflowing modifier of weight — useful to counteract text looking heavier on dark backgrounds via a negative grade), **Width** (narrower = more characters fit per line; wider = more personality but avoid in tight spaces like a top app bar), **Optical size** (small sizes optimize for readability; large sizes show off letterform detail — match optical size to actual point size).

---

## 4. Motion

### The physics-based system (May 2025, "the expressive update")
Material replaced its old easing/duration system with a **motion physics system** based on **springs** — more alive, fluid, natural, and easier to customize than fixed curves. Available today on **Jetpack Compose** (21 Material components use it by default) and **Android Views (MDC-Android)** (spring tokens available, not yet wired into components). **Flutter: unavailable. Web: compatible via converted curves** (see conversion table below), not native springs.

### Motion schemes
- **Expressive** — Material's default, opinionated scheme; **overshoots the final value to add bounce**. Use for most situations, especially hero moments and key interactions.
- **Standard** — more functional, minimal bounce, **eases into the final value** without overshoot. Use for utilitarian products.
- You can create fully custom motion schemes beyond these two presets, still via the same theming system, and switch schemes per-product or per-moment.

### How springs work
A spring = **stiffness + damping + initial velocity**. One spring definition can drive many situations (transitions, button effects, gestures) for consistent feel. Springs handle interruption/retargeting naturally (unlike fixed-duration curves).

Two spring **types**:
- **Spatial** — position, size, rotation, rounded-corner changes; **overshoots and bounces into place**.
- **Effects** — color, opacity; **no overshoot**.

Three **speeds** each — default, fast, slow:
| Speed | Spatial example | Effects example |
|---|---|---|
| Default | Animations covering part of the screen (bottom sheet, expanded nav rail) | Opacity of content inside a nav rail |
| Fast | Small components (switches, buttons) | Color change of a switch handle |
| Slow | Full-screen animations | Full-screen content refresh |

Token pattern: `md.sys.motion.spring.<speed>.<spatial|effects>`, scheme (expressive/standard) applied at the product level rather than baked into the token itself — so schemes can be swapped without re-touching every token call.

### Spring token values (damping / stiffness)
| Token | Damping | Stiffness |
|---|---|---|
| Fast spatial | 0.9 | 1400 |
| Fast effects | 1 | 3800 |
| Default spatial | 0.9 | 700 |
| Default effects | 1 | 1600 |
| Slow spatial | 0.9 | 300 |
| Slow effects | 1 | 800 |

*(These values are labeled "Standard, Android" in the live token table — confirm the Expressive-scheme equivalents live if you need exact per-scheme numbers; the spring **speeds relative to each other** hold regardless of device: fast is always faster than default/slow, but exact values differ per form factor — wearable vs phone vs tablet — to feel appropriately fast for that device.)*

### Web: spring → cubic-bezier curve conversion
| Spring | Curve (cubic-bezier) | Duration |
|---|---|---|
| Expressive fast spatial | 0.42, 1.67, 0.21, 0.90 | 350ms |
| Expressive default spatial | 0.38, 1.21, 0.22, 1.00 | 500ms |
| Expressive slow spatial | 0.39, 1.29, 0.35, 0.98 | 650ms |
| Expressive fast effects | 0.31, 0.94, 0.34, 1.00 | 150ms |
| Expressive default effects | 0.34, 0.80, 0.34, 1.00 | 200ms |
| Expressive slow effects | 0.34, 0.88, 0.34, 1.00 | 300ms |
| Standard fast spatial | 0.27, 1.06, 0.18, 1.00 | 350ms |
| Standard default spatial | 0.27, 1.06, 0.18, 1.00 | 500ms |
| Standard slow spatial | 0.27, 1.06, 0.18, 1.00 | 750ms |
| Standard fast effects | 0.31, 0.94, 0.34, 1.00 | 150ms |
| Standard default effects | 0.34, 0.80, 0.34, 1.00 | 200ms |
| Standard slow effects | 0.34, 0.88, 0.34, 1.00 | 300ms |

Use these curves on Web/iOS or anywhere true springs aren't available and the animation won't be interrupted/gestured.

### Legacy easing & duration system (still used for **transitions** specifically; not maintained further, but not dead)
> Note from the spec itself: "components and motion now use the motion physics system... Products should migrate to the new system. The easing and duration system is still used for transitions and can be used by teams that haven't yet updated to GM3 Expressive, but is no longer maintained."

**Easing curves + suggested duration pairing:**
| Easing | Duration | Transition type |
|---|---|---|
| Emphasized | 500ms | Begins and ends on screen |
| Emphasized decelerate | 400ms | Enters the screen |
| Emphasized accelerate | 200ms | Exits the screen |
| Standard | 300ms | Begins and ends on screen |
| Standard decelerate | 250ms | Enters the screen |
| Standard accelerate | 200ms | Exits the screen |

- **Emphasized** set: recommended for most transitions, captures the M3 style (snappy takeoff, soft landing).
- **Standard** set: for small, utility-focused transitions that need to be quick; also the fallback on platforms without Emphasized support (iOS, Web).
- Curve values: `Emphasized` = `M 0,0 C 0.05,0 0.133333,0.06 0.166666,0.4 C 0.208333,0.82 0.25,1 1,1` (Android path; N/A on CSS/iOS, fall back to Standard); `Emphasized decelerate` = cubic-bezier(0.05, 0.7, 0.1, 1.0); `Emphasized accelerate` = cubic-bezier(0.3, 0, 0.8, 0.15); `Standard` = cubic-bezier(0.2, 0, 0, 1); `Standard decelerate` = cubic-bezier(0, 0, 0, 1); `Standard accelerate` = cubic-bezier(0.3, 0, 1, 1).

**Duration tokens:**
| Bucket | Tokens (ms) |
|---|---|
| Short | 50 / 100 / 150 / 200 |
| Medium | 250 / 300 / 350 / 400 |
| Long | 450 / 500 / 550 / 600 |
| Extra long | 700 / 800 / 900 / 1000 |

Rules of thumb: **short** = small utility transitions (e.g. selection controls, 200ms Standard); **medium** = medium-area transitions (e.g. FAB expanding into a sheet, 400ms Emphasized); **long** = large expressive transitions (e.g. a card expanding full-screen, 500ms Emphasized); **extra long** = rare, ambient/non-input-driven transitions (e.g. carousel auto-advance, 1000ms Emphasized). Transition size should scale duration (small area → short duration, large area → long duration); **exit/dismiss/collapse transitions use shorter durations** than enter/persist transitions, since exits need less user attention than what comes next.

### Transition patterns (6 named patterns)
1. **Container transform** — one element seamlessly morphs into an expanded/detailed view (card → detail page). Strongest relationship, most expressive/dramatic pattern — reserve for hero moments, shallow hierarchies (expand → collapse), or where a clean connection between elements matters. Don't use in deep-hierarchy, utility-focused navigation — becomes excessive. Used with: cards, lists, image galleries, search boxes, sheets, FABs, chips.
2. **Forward and backward** — moving between consecutive hierarchy levels (inbox → thread). Use **platform defaults** (Android fades while sliding; iOS uses parallax — background slides slower than foreground). Used with: lists, cards, buttons, links.
3. **Lateral** — moving between peer content at the same hierarchy level (swiping tabs). Sliding motion without fade/parallax; elements move in unison, hinting at gestural swipe. Used with: tabs, carousels, image galleries.
4. **Top level** — moving between top-level destinations (tapping a nav-bar item). Exiting screen fades out, then entering screen fades in — intentionally **no** persistent/grouped elements since top-level content isn't necessarily related. Used with: nav bar, nav rail, nav drawer.
5. **Enter and exit** — introducing/removing a component. Within-screen-bounds components (FABs, dialogs, menus, snackbars, time pickers, tooltips): Android expands/collapses along x or y (never scale/z-axis, since that implies elevation change which doesn't match M3's reduced elevation model); iOS scales + fades. The direction of entry is informed by on-screen location (expands away from the device edge it originates from).
6. **Skeleton loaders** — stabilize layout during loading with a subtly pulsing placeholder rather than letting content pop in and shift position.

### What makes a good transition (checklist)
- **Respects reduced-motion settings** — swap to subtle fades, disable parallax/shape-morph decorative effects.
- **Consistent** — same transition type used the same way across the product (e.g. the same forward/back transition across a family of apps).
- **Stable layouts** — no content popping in / shifting mid-transition.
- **No jarring jump cuts** for common transitions (an instant cut can be fine for pure-efficiency utility cases, like opening a menu in a productivity app).
- **Coherent spatial model** — don't switch a carousel between horizontal and vertical layouts mid-flow, etc.
- **Unified direction** — elements move together along one primary axis; only truly persistent elements (e.g. a hero image) should move independently.
- **Clean fades** — fully fade old content out before fading new content in; avoid overlapping semi-transparent frames. If a cross-fade must happen, keep it short and hidden in the fastest part of the transition.
- **Simple style** — transitions are frequent and often cover large screen area, so they shouldn't be highly stylized; save bouncy/springy flourish for hero moments, not routine navigation.

---

## 5. Elevation

Elevation = distance between two surfaces on the z-axis, measured in **dp**. M3 uses **6 levels: 0, +1, +2, +3, +4, +5**. Resting state normally sits at levels 0–3; **levels +4 and +5 are reserved for interacted states** (hover, drag).

### Differences from M2
- M2 applied shadows at all levels by default. **M3 uses tonal surface color instead of shadows by default**, reserving visible shadows for when they're actually needed (protection against a busy background, or encouraging interaction).
- New color mappings, dynamic-color compatible.
- Elevation is now described in discrete **levels** rather than raw dp.

### Depicting elevation
To successfully show elevation, a surface needs: **edges** (contrast vs surroundings), **overlap** with other surfaces, and **distance** from other surfaces. Techniques: tonal difference (default — surface/surface-container roles, not tied to elevation, chosen for flexibility incl. user-controlled contrast), drop shadow, or a scrim.
- **Interactive components must meet accessible contrast** at their edges to visually register as separate from what's behind them.
- **Scrims** use the `scrim` role at **32% opacity**, placed beneath elements like modals/expanded nav menus to bring focus forward.
- **Shadows**: size + softness communicate distance (small/sharp = close; large/soft = far). Use visible shadows sparingly — to **protect elements** against a visually busy/patterned background (cards, chips, buttons), or to **encourage interaction** (an element temporarily lifting on focus/selection/hover/swipe; a raised element lowering when something higher appears).

### Component elevation → dp table
| Level | Components | DP |
|---|---|---|
| 5 | (not assigned as a resting level) | 12dp |
| 4 | (not assigned as a resting level) | 8dp |
| 3 | Date pickers, Dialogs (modal), Extended FAB, FAB, FAB menu (close button), Search, Time pickers | 6dp |
| 2 | App bar (scrolled), Menu, Navigation bar, Rich tooltip, Toolbar | 3dp |
| 1 | Banner, Bottom sheet (modal), Button (elevated), Card (elevated), Chips (elevated), Navigation drawer (modal), Side sheet (modal) | 1dp |
| 0 | App bar (not scrolled), Buttons (filled/tonal/outlined), Button groups, Cards (filled/outlined), Carousel, Chips, Dialog (full-screen), Extended FAB (in nav rail), FAB (in nav rail), FAB menu (list items), Icon buttons, List, Navigation rail, Segmented button, Side sheet (docked), Slider, Split button, Tabs | 0dp |

Rules: **avoid changing a component's default resting elevation.** When elevation *does* change (hover, focus, selection, drag), it should raise by **1 level** consistently across similar components (e.g. hovering a FAB: level 3 → 4, and this applies to all Material buttons on hover).

Note: **Surface tint color is deprecated** — use elevation-level tokens (0–5) instead.

---

## 6. Icons

### Material Symbols — the current default icon system
Three styles, each available as a variable font (the older static "Material Icons" set still exists but lacks variable-font capability):
- **Outlined** — stroke + fill, light/clean, works well in dense UIs; stroke weight can be tuned to complement your typography weight. Some icons (full-body human figures, proprietary marks) should stay filled for legibility even in the outlined set.
- **Rounded** — rounded corner radius, pairs well with heavier typography, curved logos, circular brand elements.
- **Sharp** — straight-edge corners (0dp radius), crisp and legible even small; fits brands with rectangular design language.

### Four adjustable variable-font axes ("Material Symbols" only, not legacy Material Icons)
- **Weight** — stroke thickness, range **100 (thin) → 700 (bold)**; also affects overall symbol size. Apply weights *consistently* — don't mix within one UI. **Don't use the thinnest weight at standard 24dp size** — minimum recommended weight there is **200**.
- **Fill** — 0 (unfilled/outlined) → 1 (fully filled); commonly used to show selected vs unselected state (e.g. bottom nav).
- **Grade** — a finer modifier of thickness than weight, smaller size impact; can be matched to a text font's own grade value for visual harmony. Default grade is **0** for a dark icon on light background, **−25** for a light icon on dark background (compensates for optical "bleed" where light-on-dark reads bigger). Positive grade = extra emphasis for an active state.
- **Optical size** — 20dp / 24dp / 40dp / 48dp; automatically adjusts stroke weight so the icon *looks* consistent as size scales (rather than naively resizing a 24dp source vector, which gets disproportionately heavy when scaled up). Use 20dp for dense desktop layouts; 40–48dp when a primary action needs highlighting.

### Sizing & grid
- **Standard size: 24dp × 24dp.** Additional supported sizes: **20dp** (dense/desktop), **40dp**, **48dp** (paired with display/headline type, larger screens).
- **Live area**: 20dp × 20dp centered, with **2dp padding** around the perimeter (total 24dp trim area). Content may extend into the padding zone if extra visual weight is needed, but must never cross the outer trim boundary.
- **Keyline shapes** (the grid foundation): square 18dp × 18dp, circle 20dp diameter, vertical rectangle 20dp × 16dp, horizontal rectangle 16dp × 20dp. A 24dp keyline template is downloadable (Apache 2.0) for custom-icon design in Illustrator if the 2,000+ built-in Material Symbols don't cover your need.
- **Corners**: 2dp radius by default on outlined-style exterior corners; interior corners stay square (not rounded), and stroke corners shouldn't round at all below 2dp width. Rounded style rounds both exterior and interior corners; sharp style reduces both to 0dp.
- **Stroke**: recommended weight is **2dp** (= regular/400 weight), covering curves, angles, interior and exterior strokes.

### Pairing icons with type
- Match **size** and **optical weight** between a Material Symbol and adjacent text for a cohesive look — don't mix icon sizes/weights against the text style they sit next to.

### Localizing / accessibility
- Icons should face forward — don't tilt, rotate, or fake dimensionality on them.
- Keep icon meaning simple/graphic/bold rather than overly literal or delicately organic — legibility at small size is the constraint that governs every other decision here.

---

## 7. Spacing

Spacing = the space around and between component/layout elements. Applied to **margins, padding, gaps** of a component/UI element/layout, and should **adapt to context** (mobile vs desktop, density settings). Currently **Jetpack-Compose-only** for the system-token implementation (Android Views/MDC and Web: unavailable as of this scrape).

### Spacing unit scale (base unit = 8dp = `space100`)
| Token | Value |
|---|---|
| Space 0 | 0dp |
| Space 25 | 2dp |
| Space 50 | 4dp |
| Space 75 | 6dp |
| **Space 100** | **8dp** *(base unit)* |
| Space 125 | 10dp |
| Space 150 | 12dp |
| Space 175 | 14dp |
| Space 200 | 16dp |
| Space 250 | 20dp |
| Space 300 | 24dp |
| Space 400 | 32dp |
| Space 450 | 36dp |
| Space 500 | 40dp |
| Space 600 | 48dp |
| Space 700 | 56dp |
| Space 800 | 64dp |
| Space 900 | 72dp |

Non-multiples-of-8 (2, 4, 6, 10dp) exist as "nested units" for cases that actually need them — Material only defines the ones in active use, not every conceivable value. New custom tokens should follow the multiplier pattern (`space225` = 8dp × 2.25 = 18dp).

### The three categories of spacing
- **Padding** — space *inside* an element, buffering the container edge from its content (text, icons). Horizontal/vertical or per-edge (leading/trailing/top/bottom).
- **Gap** — space *between* elements in a grid/container. Horizontal gap = side-by-side elements; vertical gap = stacked elements. Simple components: one uniform gap value. Complex components with varied spacing: name gaps by the elements on each side (e.g. "icon–label gap").
- **Margin** — space *outside* an element, between it and its parent container/screen edge. Material rarely uses margins on components (padding/gaps apply more uniformly); reserve margins for spacing beyond the parent's own padding, or for layout-level structure. **Prefer defining padding/gaps on the parent container** over defining margins on child elements (child margins tend to be non-uniform and need more tokens).
- Leading/trailing swap sides in RTL languages.
- Worked example — the Search container: **8dp vertical padding, 8dp horizontal gaps, 24dp horizontal margins (12dp when focused)** — note only the margin changes on focus; padding and gaps stay fixed.

### Text scaling
When text scales up to 200%, **spacing should stay the same by default** — don't let padding/gaps balloon proportionally with the text.

### Customizing the system
1. **Customize an existing component's spacing mapping** (e.g. remap "button top padding" from `space125` to `space200` for a taller default button) — changes it product-wide.
2. **Add custom system tokens/patterns** when you need a value or a recurring adaptive pattern Material doesn't ship (e.g. a shared "surface content horizontal padding" token if cards and sheets should always adapt the same way).
3. **Add adaptive layout/density behavior** — map a component's spacing to different tokens per device type (mobile vs desktop), or per density setting (adjust vertical padding per density tier), while keeping the same underlying component.

Component-attribute naming convention going forward: `padding` / `margin` / `gap` + a positional word (`horizontal`, `vertical`, `leading`, `trailing`, `top`, `bottom`) — e.g. "Medium button: leading padding." (Older tokens instead used generic `space` language like "leading-space" — you may still see both in the wild.)

---

## 8. Putting it together — quick checklist for a new UI

1. **Color**: static (brand-locked, or targeting iOS, or migrating from M2) vs dynamic (user-generated wallpaper and/or content-based) — generate via Material Theme Builder → map roles to components (primary = the one true CTA, secondary = accents/less-urgent, tertiary = contrast/attention, `-container` variants for lower-emphasis fills, surface-container tiers instead of shadows for layering).
2. **Shape**: assign the 10-step corner-radius scale to most components; reserve 1–2 signature shapes from the 35-shape library for something you actually want to pop (primary FAB, hero avatar, a slider track) — and use shape-morph on interaction/progress states where it clarifies what's happening.
3. **Typography**: Google Sans / Google Sans Text across the 15 baseline roles; promote only the 1–2 most important text elements per screen to their Emphasized counterpart; reach for editorial treatments only for genuine showcase moments, never for labels.
4. **Elevation**: default to tonal surface-container color; reserve real shadows for FAB/dialog/menu/snackbar/search or busy-background protection; never touch a component's default resting elevation.
5. **Motion**: pick Expressive or Standard scheme once, product-wide; use spatial springs for movement/resizing/morphing, effects springs for opacity/color; on Web, convert to the matching cubic-bezier curve; for full-screen/navigation transitions specifically, the legacy easing+duration system + the 6 named transition patterns still apply.
6. **Icons**: Material Symbols, pick outlined/rounded/sharp to match your shape/type direction, keep weight/fill/grade/optical-size consistent with adjacent type, mind the 24dp/20dp-live-area/2dp-padding grid.
7. **Spacing**: base everything on the 8dp scale (`space100` = 8dp); keep spacing fixed when text scales; prefer parent-level padding/gap over child-level margin.

---

## Live re-verification: `scrape_m3.py`

This skill's bundled `scrape_m3.py` (Playwright + Chromium) is what actually produced the content above — it renders each `m3.material.io/styles/...` URL (networkidle + a settle delay + best-effort accordion-expansion clicks), then extracts `document.body.innerText` and any `<table>` data into JSON. If Anthropic's sandbox is doing TLS interception, launch the browser context with `ignore_https_errors=True` (already set in the bundled script) or the site will fail with `ERR_CERT_AUTHORITY_INVALID`.

To refresh a section: edit the `URLS` list to just the pages you need, re-run, then read the JSON out of `m3_pages/`. Full URL list covered by this skill's research:

- https://m3.material.io/styles
- https://m3.material.io/styles/color/system/overview, /roles, /choosing-a-scheme
- https://m3.material.io/styles/color/static/baseline, /custom-brand
- https://m3.material.io/styles/color/dynamic/choosing-a-source, /user-generated-source, /content-based-source
- https://m3.material.io/styles/color/advanced/overview, /apply-colors, /define-new-colors, /adjust-existing-colors
- https://m3.material.io/styles/color/resources
- https://m3.material.io/styles/elevation/overview, /applying-elevation, /tokens
- https://m3.material.io/styles/icons/overview, /designing-icons, /applying-icons
- https://m3.material.io/styles/motion/overview/how-it-works, /specs
- https://m3.material.io/styles/motion/easing-and-duration/applying-easing-and-duration, /tokens-specs
- https://m3.material.io/styles/motion/transitions/transition-patterns, /applying-transitions
- https://m3.material.io/styles/shape/overview-principles, /corner-radius-scale, /shape-morph
- https://m3.material.io/styles/spacing/overview, /applying-spacing, /tokens
- https://m3.material.io/styles/typography/overview, /fonts, /type-scale-tokens, /applying-type, /editorial-treatments
- Theme Builder tool: https://material-foundation.github.io/material-theme-builder/


## 9. Components (M3 Expressive catalog)

This section is what makes or breaks an M3E build — the `/styles/` tokens above are the vocabulary, but **components are where the expressive look actually lives.** Sourced from the live `/components/` pages. When you need exact per-size token values, re-run `scrape_components.py`.

### 9.0 Component families (the full catalog)
Organized by purpose: **Action** (buttons, button groups, FABs, extended FABs, FAB menu, icon buttons, split buttons, segmented buttons [deprecated → connected button group]), **Date & time pickers**, **Loading & progress** (loading indicator, progress indicators), **Navigation** (navigation bar, navigation rail, navigation drawer), **Sheets** (bottom sheets, side sheets), **Containment/communication/selection/text-input** (app bars, badges, cards, carousel, checkbox, chips, dialogs, divider, lists, menus, switch, etc.).

---

### 9.1 Progress indicators — ⭐ THE WAVY ONE

**Two variants: linear and circular.** Each has **flat (default)** or **wavy** shape, and **default (4dp)** or **variable/configurable** track thickness. The wavy shape is the single most recognizable M3 Expressive visual.

**What the wavy indicator actually looks like** (from the anatomy + the reference screenshots):
- The **active indicator** (the filled/progress portion) is a **squiggle — a sine wave** in the accent color.
- The **track** (the not-yet-filled remaining portion) is a **flat straight line** in a lower-emphasis color.
- There is a **small gap** between the active indicator and the track.
- A **stop indicator** — a **4dp dot** — marks the very end of the track (linear determinate only). Required if the track's contrast with its container is below 3:1; may be removed only if contrast ≥ 3:1.
- So a determinate linear indicator reads left-to-right as: **[squiggly filled wave] — gap — [flat remaining track] · [end dot]**. As progress increases, more of the line becomes squiggle and less stays flat.
- On a **circular** wavy indicator, the active arc is a wavy/scalloped ring instead of a smooth arc.

**Wavy geometry:** defined by **amplitude** (center of resting position to center of the peak) and **wavelength** (distance between two adjacent peaks). The overall **container height grows** when wavy (the wave needs vertical room). At very small sizes the wave may not be visible — don't use wavy for tiny indicators.

**Exact linear measurements (from the M3 spec's dimensioned drawing — these are the real numbers):**
| Variant | Active-track thickness | Track thickness | Wavelength | Gap (active↔track) | Gap (track↔stop dot) | Stop dot | Overall container height |
|---|---|---|---|---|---|---|---|
| Flat, default | 4dp | 4dp | — | 4dp | 4dp | 4dp | **4dp** |
| Flat, thick | 8dp | 8dp | — | 4dp | 4dp | 4dp (2dp inset) | **8dp** |
| Wavy, default | 4dp (stroke) | 4dp | **40dp** | 4dp | 4dp | 4dp | **10dp** (amplitude 3dp) |
| Wavy, thick | 8dp (stroke) | 8dp | **40dp** | 4dp | 4dp | 4dp (2dp inset) | **14dp** (amplitude 3dp) |

So the wavy default reads: a **3dp-amplitude sine wave**, **40dp wavelength**, **4dp stroke**, sitting in a **10dp-tall** container, with a **4dp gap**, then the flat remaining track, another **4dp gap**, and the **4dp stop dot**. Linear indicator is inset **4dp** from the screen edge.

**Determinate progression (what fills look like at increasing values):** the *filled* (active) portion is the squiggle; as `value` goes 0→1 the squiggle **extends rightward** and the flat track shrinks. At `value=1` the whole thing is squiggle (no flat track, no stop dot needed). The circular determinate equivalent: the active arc is a **wavy/scalloped ring segment** that sweeps from the top clockwise, growing to a full wavy ring at 100% — thicker strokes make the scallops more pronounced.

**Indeterminate:** a **fixed-length squiggle segment travels** along the track, growing then shrinking (the flat track is always visible behind it). Circular indeterminate = a wavy arc segment rotating around. The "building blocks" for indeterminate are: a short squiggle segment, a dot, and the flat track — animated together.

**Anatomy:** Active indicator, Track, Stop indicator.

**Behavior:**
- **Determinate** — known progress/wait; fills 0→100%; the active indicator *must* accurately represent real progress.
- **Indeterminate** — unknown progress/wait; grows and shrinks along a fixed track repeatedly.
- When progress *first begins*, the active indicator appears as a **dot**, then grows (so low percentages still read as "something is happening").
- As info becomes available, transition **indeterminate → determinate**.
- Linear animates leading→trailing; circular animates from the top, clockwise by default.

**When to use which indicator (strict wait-time mapping — this governs progress AND loading):**
| Expected wait | Use |
|---|---|
| Instant (< 200ms) | **No indicator** — just show the content |
| Short (200ms – 5s) | **Loading indicator** (§9.2) |
| Long (> 5s) | **Progress indicator** (this section) |

**Usage rules:** one indicator for a *group* of loading items (don't put one on every item); use the *same variant* for the same process everywhere (if refresh is circular in one place, circular everywhere); linear best on a container's edge, circular best centered in an element.

**Color roles:** active indicator = `primary`; track = `secondary-container`; stop indicator = `primary`. (Baseline hex: active `#6750A4`, track `#E8DEF8`, stop `#6750A4`.) Linear is inset **4dp** from the screen edge. Default active/track thickness **4dp**; stop indicator **4dp**.

**Web/CSS implementation — MWC gives you baseline only.** `<md-linear-progress value="0.6">` and `<md-circular-progress value="0.6">` are **flat** (no wavy config on web). So build the wavy indicator yourself from the exact measurements above:

```html
<!-- Wavy linear determinate, ~60% -->
<svg width="100%" height="10" viewBox="0 0 300 10" preserveAspectRatio="none"
     style="overflow:visible">
  <!-- active: a 3dp-amplitude, 40dp-wavelength sine, 4dp stroke, primary -->
  <path d="M0,5 Q10,2 20,5 T40,5 T60,5 T80,5 T100,5 T120,5 T140,5 T160,5 T180,5"
        fill="none" stroke="var(--md-sys-color-primary,#6750A4)"
        stroke-width="4" stroke-linecap="round"/>
  <!-- 4dp gap, then flat remaining track (secondary-container) -->
  <line x1="188" y1="5" x2="292" y2="5"
        stroke="var(--md-sys-color-secondary-container,#E8DEF8)"
        stroke-width="4" stroke-linecap="round"/>
  <!-- 4dp stop dot (primary) -->
  <circle cx="298" cy="5" r="2" fill="var(--md-sys-color-primary,#6750A4)"/>
</svg>
```
Animate `value` by moving the boundary x-position (advance the sine path's end, retract the track's start). For **indeterminate**, keep a fixed-length sine segment and animate its `translateX` across the track (grow→shrink via the expressive spring / the §4 cubic-beziers). The wave itself can also scroll (animate the path's phase) for extra life. Circular: draw the active arc as a wavy `<path>` around a circle (or a `<circle>` with a wavy stroke via `stroke-dasharray` tricks) and rotate it. Alternatively animate a real sine with a JS `requestAnimationFrame` loop generating the path `d` each frame — more accurate for the traveling-wave look.

---

### 9.2 Loading indicator — ⭐ THE 7-SHAPE MORPHING BLOB (new in M3E, May 2025)

**This is NOT a spinner.** The loading indicator's **active indicator is "a looping shape-morph sequence composed of seven unique Material 3 shapes"** — a single blob that continuously morphs from one shape in the 35-shape library to the next on a loop, in `primary` color. That morph *is* the animation. Rendering it as a rotating circular spinner is the #1 mistake.

**Exact shape sequence (observed, in loop order):**
`soft burst → 9-sided cookie → pentagon → pill → sunny → 4-sided cookie → oval → (back to soft burst)`
— see `shapes-reference.png` for what each looks like.

**Exact motion character (this is what sells it):**
- The blob is **continuously rotating** the entire time, not just morphing in place.
- During each shape-to-shape morph it **speeds up its rotation** — rotation is faster mid-morph, slower as it settles into each shape.
- The morphs are **springy/bouncy** — each transition slightly overshoots and settles (expressive spatial spring), so it reads as a soft "boing" between shapes rather than a linear tween.
- Net feel: a lively, rotating shape that keeps "popping" from one silhouette to the next.

- **Purpose:** replaces most uses of the *indeterminate circular progress indicator*, for **short (200ms–5s)** waits. Also the component behind **pull-to-refresh**.
- **Never** transition a loading indicator into a determinate progress indicator (unlike progress indicators, which *do* transition indeterminate→determinate). If a process will become measurable, use a progress indicator from the start.
- Always reflects a real ongoing process — never decorative.
- **Two configurations:** **default** (bare morphing blob, for placement directly on a surface) and **contained** (blob sits inside a circular container for extra contrast when placed over other content / for pull-to-refresh). When contained, the active indicator color changes from `primary` to `on-primary-container`, and the container uses `primary-container`.
- **Size:** default **48dp**, flexible **24dp–240dp**. Contained baseline: container 38dp wide × 48dp tall, active indicator 48dp (the 48dp vs 38dp gives margin).
- Can be embedded in buttons to show an in-progress action (form validation, checking for updates).
- Placement: centered in the page/container it's loading; when loading *more* items, place it in the empty space where new content will appear, not overlapping existing content.

**Web/CSS note:** no MWC support. Implement as an SVG blob whose `d` path animates (morphs) through ~7 shape keyframes on a loop — e.g. CSS `@keyframes` swapping `clip-path` / animating an SVG path via SMIL or JS (GSAP MorphSVG, Flubber, or manual path interpolation).

---

### 9.3 Buttons — 5 sizes, 2 shapes, shape-morph, toggle (major M3E overhaul, May 2025)

**Two variants:** **default** and **toggle** (selection). **Five color styles** (now called *configurations*): **elevated, filled (default), tonal, outlined, text.** Toggle buttons don't use the text style.

**Five sizes** (new — M3 previously had only one): **Extra small, Small (default), Medium, Large, Extra large.**

**Two shapes:** **round (default)** and **square**. And the expressive core:
- **Shape morphs when pressed** — press a round button and it momentarily squishes toward square (or vice versa) via a spatial spring, then springs back.
- **Shape morphs when selected** — a toggle button changes shape between round and square to signal selected state (not just a color change).

**Other specs:**
- Default button height is now **40dp** (M2 was 36dp); fully-rounded corners by default.
- Leading/trailing icon standard size is **20dp**. Icons and labels share the same color (no more separate icon tint).
- Small-button padding is now **16dp** (24dp is *no longer recommended*).
- Labels: concise, sentence case.
- The neutral text button is no longer recommended.
- Color mapping example (elevated): container `surface-container-low`, icon+label `primary`; toggle-selected flips to container `primary`, content `on-primary`. Note from the spec: these role choices are for coherence, but *any* roles work as long as container/text hit **3:1 contrast** (e.g. tertiary + on-tertiary).

**Emphasis / choosing a button (all 10 action components):**
| Emphasis | Component(s) | Example actions |
|---|---|---|
| **High** | Extended FAB, FAB, FAB menu | Create, Compose, New thread |
| High | Filled button | Save, Confirm, Done |
| High | Split button (primary color + menu icon, for a key action with options) | Send, Add, Create |
| High | Standard button group (color+motion+shape to show multiple key actions) | Back, Pause, Next |
| **Medium** | Tonal button (secondary palette) | Save, Confirm, Done |
| Medium | Elevated button (secondary palette + shadow — *only* when separation from a patterned background is needed) | Reply, Add to cart |
| Medium | Outlined button (attention but not primary; also the "change your mind / escape a flow" button) | See all, Add to cart |
| **Low** | Connected button group (multiple related options, e.g. changing visible content) | Walk, Bike, Drive |
| Low | Text button (no fill/outline; non-essential actions) | Learn more, Turn on |
| Low | Icon button (most compact; optional supplementary actions) | Bookmark, Star |

**Hierarchy rule:** exactly **one high-emphasis button per screen** for the primary action; other buttons use lower-emphasis styles. Place buttons side-by-side rather than stacked when there's room; pair a higher-emphasis button with a lower-emphasis one (filled + text, outlined + filled, etc.).

**The 10 button types total:** Button, Toggle button, Icon button, Toggle icon button, Split button, Standard button group, Connected button group, FAB, Extended FAB, FAB menu.

**FAB / FAB menu / icon-button visual detail (from the reference screenshots):**
- **FAB** — a rounded-square container (not a circle) with an icon; sizes scale. **Extended FAB** = pill/rounded-rect with icon + label ("Get started"). Both use `primary-container` family fills by default; the FAB is the single highest-emphasis action on a screen.
- **FAB menu** — a FAB that, when tapped, **morphs open** into a vertical stack of pill-shaped labeled items (each with a leading icon: "First / Second / Third"), with the trigger becoming an **X close button** at the bottom. Comes in **three container-color themes**: **Primary Container** (purple), **Secondary Container** (muted lavender), **Tertiary Container** (pink) — the item pills use the `-container` role and the close button uses the stronger accent. On open, items spring/stagger in; on close they collapse back into the FAB.
- **Icon buttons** — the container is a **rounded-square / wide-rounded shape** (a "squircle"/wide pill), NOT a perfect circle — this is a giveaway of correct M3E. They come in the same 5 color styles and can be toggle (filled when selected). Icon inside is 24dp (or scaled with the button).
- **Split button** — a filled primary action + a small attached trailing segment with a dropdown chevron (e.g. "Label ▾", or "✓ 8 oz" toggled). The two segments have a hairline gap and the trailing segment opens a menu.
- **Standard button group** in use (e.g. a media control "◀◀ ▶ Play ▶▶") — buttons sit with gaps; the active/selected one is larger/differently-shaped (the "Play" is a wide pill among round neighbors), and pressing nudges neighbor widths.
- **Connected button group** in use (e.g. "My files | Shared | Computers", or "✓ Relevance | Distance", or a multi-select "✓ $ | ✓ $$ | $$$ | $$$$") — buttons are **flush together** (tiny gaps), the selected one takes a strong `secondary`/dark fill with a leading checkmark, unselected ones use a light `-container` tint. Multi-select shows checkmarks on each selected item.

---

### 9.4 Button groups (new in M3E — replaces segmented buttons)

**Two variants:** **standard** and **connected**. **Connected button group replaces the segmented button** (segmented button is no longer recommended). Work with all button sizes XS–XL, round or square default shape, and support **single-select / multi-select / selection-required**.

- Button groups are **invisible containers** — they hold no buttons by default, add padding between buttons, and modify button shape. No color of their own (buttons inside carry the color). Avoid standard icon buttons / text buttons inside them (no container treatment to modify).
- **Standard button group** — adds interaction *between adjacent buttons*: selecting/pressing a button changes **its width, shape, and padding**, and briefly nudges the width of the buttons *next to it* (the springy "accordion" effect). Toggle selection morphs the button between square and round.
- **Connected button group** — buttons sit flush together; selecting a button changes **only that button's shape**, with **no effect on neighbors**.
- Press interaction uses spring motion — e.g. XSmall: height 32dp, between-space 18dp, pressed spring damping 0.9 / stiffness 1400, pressed width multiplier +15%.
- Padding between buttons scales with size to keep a **48dp minimum accessible target**.

---

### 9.5 App bars (flexible, M3E, May 2025)

Renamed from "top app bar" → **app bar**. **Four variants: Search app bar, Small, Medium flexible, Large flexible.** Medium/Large *flexible* replace the old medium/large app bars (no longer recommended).
- **On scroll: no drop shadow** — instead a **color fill** overlays to separate the bar from content beneath.
- Flexible bars add: reduced height, **larger title text, a subtitle, left- or center-aligned text, text wrapping**, and room for imagery + filled buttons.
- **Search app bar** supports icons inside/outside the search field and centered text; opens the search view when tapped.
- Focus on describing the current page + 1–2 essential actions (use a *toolbar* for more page actions). Can animate on/off screen alongside another control row (e.g. a row of chips).

---

### 9.6 Navigation bar (flexible, M3E, May 2025)

For **compact/medium** window sizes; **3–5 destinations** of equal, fixed importance.
- New **flexible navigation bar** replaces the baseline one (no longer recommended): **shorter height**, and in *medium* windows supports **horizontal** nav items (icon + label side by side).
- **No shadow** (M2 had one). Active destination indicated by a **pill-shaped active indicator** in a contrasting color + a **filled** icon.
- Active label color changed to **`secondary`** (was `on-surface-variant`).
- Navigation rail = mid-sized devices; navigation drawer = larger devices (same destination concept, different form factor).

---

### 9.7 Menus (vertical menus, M3E, Nov 2025)

New **vertical menus** (recommended for new designs; baseline menu still available).
- **Two color styles: standard and vibrant** — vibrant uses saturated color to make selected items pop.
- Support new shapes, selection states, refined submenu motion, and (on Android) **gaps and dividers** for more flexible grouping.
- Open from many triggers: icon buttons, split buttons, text fields. Context menus (secondary-click) provide actions for a specific element.

---

### 9.8 Carousel (M3-native, expressive motion)

Visual-item collection (images/video + optional label). **Six layouts:** multi-browse, uncontained, uncontained multi-aspect-ratio (Nov 2025), hero, center-aligned hero, full-screen. Start- or center-aligned.
- **Items change size** as they move through the carousel, and their **visuals have a parallax effect** (content moves at a different speed than the container).
- Items **change shape** when scrolled and **snap into place**. Hero carousels swipe one large item at a time; multi-browse scrolls many at once (research: people expect ~10 items in a multi-browse).

---

### 9.9 Switch, Checkbox, Badges, Pickers (quick reference)

- **Switch:** taller + wider track than M2; handle can contain an **optional icon**; state must be obvious at a glance. Use switches (not radio buttons) for independently-controllable list items.
- **Checkbox:** now has **indeterminate** and **error** states (for unselected/selected/indeterminate). Use checkboxes when multiple options can be selected.
- **Badges:** two variants **small** and **large**; anchored at the **upper-trailing edge** inside the icon bounding box; limit to **4 characters incl. a `+`**; keep default color mapping.
- **Date pickers:** three variants — **docked** (was "desktop"), **modal** (was "mobile"), **modal input**. Larger typography, no shadow, 48dp targets.
- **Time pickers:** modal, cover main content; two variants — **dial** and **input**.

---

### 9.10 Developer platforms (how to actually ship this)

| Platform | M3 | M3 Expressive | How you build it |
|---|---|---|---|
| **Jetpack Compose** | ✅ | ✅ (21 components use motion physics by default) | Primary/recommended path. `androidx.compose.material3`. Compose is now the Compose-first, all-in Material Android toolkit. |
| **Android Views (MDC-Android)** | ✅ | ✅ (spring tokens available; not yet on all components) | `com.google.android.material` |
| **Flutter** | ✅ | ⚠️ partial (has M3, lacks most Expressive additions/motion physics) | `useMaterial3: true` |
| **Web (Material Web / MWC)** | ✅ baseline | ❌ **not implemented — maintenance mode** | See below |

**Material Web (MWC) — baseline M3 only, for the web. Full token conventions below (from the material-web.dev docs).**

**Custom elements** (tag = `md-` + component; each variant is a *separate* element, not an attribute):
`<md-elevated-button>` `<md-filled-button>` `<md-filled-tonal-button>` `<md-outlined-button>` `<md-text-button>` · `<md-icon-button>` `<md-filled-icon-button>` `<md-filled-tonal-icon-button>` `<md-outlined-icon-button>` · `<md-fab>` `<md-branded-fab>` · `<md-checkbox>` `<md-radio>` `<md-switch>` `<md-slider>` · `<md-chip-set>` + `<md-assist-chip>`/`<md-filter-chip>`/`<md-input-chip>`/`<md-suggestion-chip>` · `<md-dialog>` · `<md-divider>` · `<md-elevation>` · `<md-focus-ring>` · `<md-ripple>` · `<md-icon>` · `<md-list>`+`<md-list-item>` · `<md-menu>`+`<md-menu-item>`/`<md-sub-menu>` · `<md-linear-progress>` `<md-circular-progress>` · `<md-outlined-select>`/`<md-filled-select>`+`<md-select-option>` · `<md-tabs>`+`<md-primary-tab>`/`<md-secondary-tab>` · `<md-outlined-text-field>`/`<md-filled-text-field>`.
> Note: there is **no `<md-tonal-button>`** — the correct tag is `<md-filled-tonal-button>`. And there is **no button-group / split-button / FAB-menu / segmented-button / app-bar / navigation-bar / carousel / date-picker / loading-indicator** element in MWC — those are Expressive-only, so on web you build them by hand.

**Quick start (CDN, prototyping):**
```html
<head>
  <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
  <script type="importmap">
    { "imports": { "@material/web/": "https://esm.run/@material/web/" } }
  </script>
  <script type="module">
    import '@material/web/all.js';
    import {styles as typescaleStyles} from '@material/web/typography/md-typescale-styles.js';
    document.adoptedStyleSheets.push(typescaleStyles.styleSheet);
  </script>
</head>
<body>
  <h1 class="md-typescale-display-medium">Hello Material!</h1>
  <md-outlined-button>Back</md-outlined-button>
  <md-filled-button>Next</md-filled-button>
</body>
```
Production: `npm install @material/web`, import per-component (`import '@material/web/button/filled-button.js'`), bundle with Rollup + `@rollup/plugin-node-resolve`. The typography classes (`md-typescale-display-medium`, `md-typescale-body-large`, etc.) come from the adopted typescale stylesheet.

**The token system has 3 layers:**
1. **Reference tokens** `--md-ref-*` — raw values. `--md-ref-typeface-brand` / `--md-ref-typeface-plain` swap fonts across everything. (MWC does **not** support `--md-ref-palette-*`.)
2. **System tokens** `--md-sys-*` — the roles that give the design its character; set these once on `:root` and every component follows.
3. **Component tokens** `--md-<component>-*` — override a single component; can be a concrete value or point at a system token. Scope with any CSS selector.

**System color — the full role set** (each has an `on-` pair; set on `:root`, generate values from Material Theme Builder or `material-color-utilities`):
```css
:root {
  /* accents */
  --md-sys-color-primary: #6750A4;   --md-sys-color-on-primary: #FFFFFF;
  --md-sys-color-primary-container: #EADDFF; --md-sys-color-on-primary-container: #21005D;
  --md-sys-color-secondary: #625B71; --md-sys-color-on-secondary: #FFFFFF;
  --md-sys-color-secondary-container: #E8DEF8; --md-sys-color-on-secondary-container: #1D192B;
  --md-sys-color-tertiary: #7D5260;  --md-sys-color-on-tertiary: #FFFFFF;
  --md-sys-color-tertiary-container: #FFD8E4; --md-sys-color-on-tertiary-container: #31111D;
  --md-sys-color-error: #B3261E;     --md-sys-color-on-error: #FFFFFF;
  --md-sys-color-error-container: #F9DEDC; --md-sys-color-on-error-container: #410E0B;
  /* surfaces */
  --md-sys-color-surface: #FEF7FF;   --md-sys-color-on-surface: #1D1B20;
  --md-sys-color-on-surface-variant: #49454F;
  --md-sys-color-surface-container-lowest: #FFFFFF;
  --md-sys-color-surface-container-low: #F7F2FA;
  --md-sys-color-surface-container: #F3EDF7;
  --md-sys-color-surface-container-high: #ECE6F0;
  --md-sys-color-surface-container-highest: #E6E0E9;
  --md-sys-color-surface-dim: #DED8E1; --md-sys-color-surface-bright: #FEF7FF;
  --md-sys-color-inverse-surface: #322F35; --md-sys-color-inverse-on-surface: #F5EFF7;
  --md-sys-color-inverse-primary: #D0BCFF;
  /* outline + fixed + misc */
  --md-sys-color-outline: #79747E;   --md-sys-color-outline-variant: #CAC4D0;
  --md-sys-color-primary-fixed: #EADDFF; --md-sys-color-primary-fixed-dim: #D0BCFF;
  --md-sys-color-secondary-fixed: #E8DEF8; --md-sys-color-secondary-fixed-dim: #CCC2DC;
  --md-sys-color-tertiary-fixed: #FFD8E4; --md-sys-color-tertiary-fixed-dim: #EFB8C8;
  --md-sys-color-scrim: #000000; --md-sys-color-shadow: #000000;
  --md-sys-color-surface-tint: #6750A4;
}
```
(Full sorted role list in MWC: background/error/error-container/inverse-*/on-*/outline/outline-variant/primary(+container/fixed/fixed-dim)/scrim/secondary(+…)/shadow/surface(+bright/container tiers/dim/tint/variant)/tertiary(+…).)

**System shape:** `--md-sys-shape-corner-{none|extra-small|small|medium|large|large-increased|extra-large|extra-large-increased|extra-extra-large|full}`. Example: `--md-sys-shape-corner-medium: 12px;`. Value `--md-sys-shape-corner-full` = pill/circle. **Making buttons square = `--md-filled-button-container-shape: 0px;`** (this is how you'd approximate the round↔square Expressive shapes on web).

**System typescale:** `--md-sys-typescale-<role>-{font|size|line-height|weight|tracking}`, e.g. `--md-sys-typescale-body-medium-size: 1rem; --md-sys-typescale-body-medium-line-height: 1.5rem;`. Roles = display/headline/title/body/label × large/medium/small.

**MWC does NOT support `--md-sys-motion-*`** — all motion (springs, shape-morph) is your own CSS/JS.

**Per-component token pattern** (from the docs — the whole library follows this):
```css
/* one component type, globally */
:root { --md-filled-button-container-shape: 0px; }
/* scoped by class */
.square-buttons { --md-filled-button-container-shape: 0px; }
/* re-map a component to a different role (e.g. make a filled button an error button) */
md-filled-button.error {
  --md-filled-button-container-color: var(--md-sys-color-error);
  --md-filled-button-label-text-color: var(--md-sys-color-on-error);
}
```
Real component token examples verified from the docs:
- **Filled button:** `--md-filled-button-container-color` → `--md-sys-color-primary`; `--md-filled-button-container-shape` → `--md-sys-shape-corner-full`; `--md-filled-button-label-text-color` → `--md-sys-color-on-primary`; `--md-filled-button-label-text-font` → `--md-sys-typescale-label-large-font`.
- **Filled tonal button:** container → `secondary-container`, label → `on-secondary-container`.
- **Elevated button:** container → `surface` (docs) / `surface-container-low`, label → `on-surface`/`primary`.
- **Outlined button:** `--md-outlined-button-outline-color` → `--md-sys-color-outline`.
- **Text button:** `--md-text-button-label-text-color` → `--md-sys-color-primary`.
- **Filled icon button:** `--md-filled-icon-button-container-width`/`-height` (default 40px), `--md-filled-icon-button-icon-size` (24px), `--md-filled-icon-button-container-shape` → `--md-sys-shape-corner-full`, `--md-filled-icon-button-selected-container-color` → `--md-sys-color-primary`.
- **Progress:** `--md-circular-progress-active-indicator-color` → `--md-sys-color-primary`, `--md-circular-progress-size` (48px), `--md-circular-progress-active-indicator-width` (unit-less % of size); linear has active-indicator + track color tokens.

**Progress on web is FLAT/baseline:** `<md-linear-progress value="0.6">`, `<md-linear-progress indeterminate>`, `<md-circular-progress value="0.6">`, plus `four-color` indeterminate (cycles primary → primary-container → tertiary → tertiary-container) and linear `buffer="0.8"` (buffer dots). **None of these are wavy** — for the Expressive squiggle, hand-build the SVG (see §9.1). `value` is 0–1.

**Dark theme** isn't automatic (deliberately — keeps CSS small): supply a dark `--md-sys-color-*` set under your own selector/media query, e.g. Sass `@use '@material/web/color/color'; :root { @media (prefers-color-scheme: dark) { @include color.dark-theme; } }`, or hand-write the dark values.

**Sub-parts:** reach a component's internals via `::part(<name>)`, where the part name is the inner element's tag minus the `md-` prefix.

**State colors** (hover/focus/pressed) have their own tokens, but the shortcut is: just set the `--md-sys-color-*` role the component maps to and all its states follow.

- **Bottom line for web M3E:** MWC = **baseline M3 components + `--md-sys-*`/`--md-<component>-*` theming**. Everything Expressive is **your own code**: wavy progress (SVG sine path, §9.1), the 7-shape morphing loader (§9.2, animate an SVG path / `clip-path` through the 7 shapes), shape-morph-on-press (spring `transform` + `border-radius`/`clip-path` transitions using the §4 cubic-beziers), button groups / split buttons / FAB menu / flexible app+nav bars (compose from `<md-*>` primitives + custom CSS), the 35 decorative shapes (SVG or `clip-path`), and emphasized type (heavier `font-variation-settings: 'wght' 500`). **Angular Material** is an alternative that implements M3 with `--mat-sys-*` tokens and `light-dark()` (may differ from MWC).

---

## 10. Component-specific "don't get it wrong" checklist

When an AI (or you) builds M3E, verify these — they're the tells of a *correct* M3E implementation vs a generic-Material one:

1. **Progress bar is wavy** — linear determinate = a **3dp-amplitude, 40dp-wavelength, 4dp-stroke sine squiggle** for the filled part, a **flat** line for the remaining track, a **4dp gap** between them, and a **4dp end dot** (default container height 10dp wavy / 4dp flat). Not a flat filled bar. On web this is hand-built SVG — MWC's `<md-linear-progress>` is flat only.
2. **Loading spinner is a morphing blob** — a continuously rotating shape that morphs through the 7-shape loop (soft burst → 9-sided cookie → pentagon → pill → sunny → 4-sided cookie → oval), speeding up its rotation and bouncing/overshooting during each morph. Not a rotating ring, not a plain CSS spinner. (A wavy circular progress indicator is a different, also-valid thing — but don't confuse the two.)
3. **Buttons can be square OR round**, come in 5 sizes, and **morph shape on press/select** — not just a color/opacity change.
4. **Segmented buttons don't exist anymore** — use a connected button group.
5. **FABs/loaders/selected states use shape-morph**, driven by the **expressive spring** (overshoot/bounce), not a linear fade.
6. **App bars & nav bars have no drop shadow** — separation on scroll is a color fill; the nav bar's active item is a **pill indicator + filled icon**.
7. **Decorative shapes come from the 35-shape library** (cookie, clover, sunny, gem, pixel, heart…), used sparingly on avatars/imagery/FAB backgrounds — not random rounded blobs.
8. **Emphasized type** is used for the 1–2 hero/CTA elements; **Google Sans / Google Sans Text**, not Roboto, for the current scale.
9. **Elevation is tonal** (surface-container tiers), shadows reserved for FAB/dialog/menu/snackbar/search.
10. **On the web, none of the above comes free** — MWC gives baseline M3 only; the expressive parts are custom code.

## Component pages scraped (for re-verification via `scrape_components.py`)
`/components` · `/components/all-buttons` · buttons overview+specs · button-groups overview+specs+guidelines+accessibility · app-bars overview+specs+guidelines+accessibility · badges overview+specs+guidelines+accessibility · progress-indicators overview+specs+guidelines+accessibility · loading-indicator overview+specs+guidelines+accessibility · navigation-bar overview · carousel overview · switch overview · checkbox overview · date-pickers overview · time-pickers overview · menus overview · develop/web · develop/android/jetpack-compose · develop/android/mdc-android · plus GitHub: material-web quick-start & theming (`--md-sys-*` tokens), material-components-android component docs.

---

## Bundled files
- `shapes-reference.png` — the 35-shape M3E library grid with labels.
- `scrape_m3.py` — the Playwright scraper for the `/styles/` pages.
- `scrape_components.py` — the Playwright scraper for the `/components/` pages.
- `progress-linear-determinate.png` — flat + wavy linear determinate progression at various fills/thicknesses.
- `progress-linear-indeterminate.png` — flat + wavy linear indeterminate.
- `progress-circular-determinate.png` — flat → wavy/scalloped circular rings at various fills/thicknesses.
- `progress-circular-indeterminate.png` — circular indeterminate + the wave "building blocks".
- `progress-linear-measurements.png` — ⭐ the dimensioned spec drawing (amplitude 3, wavelength 40, gaps 4, stroke 4/8, heights 4/8/10/14). This is the source of the exact numbers in §9.1.
- `all-buttons-reference.png` — the 10 button types with the numbered legend.
- `buttons-usage-examples.png` — real button usage (icon-button squircle shapes, media control group, connected group, split button).
- `connected-button-group.png` — connected group single- and multi-select with checkmarks.
- `fab-menu-containers.png` — FAB menu open, in Primary / Secondary / Tertiary container colors.
- `icon-buttons-shapes.png` — the rounded-square (not circular) icon-button shape.

## Reference images worth attaching when using this skill
Already bundled: shapes grid, wavy progress indicators, all-buttons. Still genuinely useful to attach when relevant:
- A **short screen recording/GIF** of any motion you're matching — spring overshoot amount, shape-morph timing, the loading-indicator 7-shape morph, and button-group "accordion" width nudges are all far easier to match from a clip than from damping/stiffness numbers.
- A screenshot of a specific **component's live page** (e.g. FAB menu open state, connected vs standard button group interaction, flexible app bar, vibrant menu) if you're building that exact component — the token tables are captured but the *look* isn't.
- Any **brand reference** for "M3E but branded to X."

