package com.amazecc.app.shared.ui.screens.home

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amazecc.app.shared.theme.AmazeColors
import com.amazecc.app.shared.theme.AmazeTheme
import com.amazecc.app.shared.theme.getGeistFontFamily
import com.amazecc.app.shared.theme.getOutfitFontFamily

/**
 * The simplified home screen's design tokens.
 *
 * ## Why this file exists
 *
 * The simplified home is a port of the web app's `SimplifiedMobileHome.tsx`, whose
 * tokens live in `src/lib/uiTokens.ts` and `src/lib/weekStrip.ts`. That module is
 * a single source of truth shared by every web surface, and it is deliberately
 * written against *semantic* variables (`--surface`, `--border-muted`,
 * `--text-heading`) rather than zinc steps, so the accent picker reaches them.
 *
 * This file is the same idea on the Compose side. It resolves the web's roles
 * against [AmazeColors] so the new home:
 *
 * * matches the web layout exactly, and
 * * still follows all four app themes, all six accent themes, and the 18-role
 *   custom palette editor.
 *
 * Every colour below is a *role*, never a literal. `docs/COLORS.md` forbids
 * hardcoded colours, and a home screen that ignored the palette editor would
 * drift from every other screen the moment the palette changed.
 *
 * ## The role map
 *
 * | Web token | Compose role |
 * |---|---|
 * | `--surface` | [AmazeColors.surface] |
 * | `--surface-secondary` / `--surface-tertiary` | [AmazeColors.elevatedSurface] |
 * | `--border-muted` | [AmazeColors.border] |
 * | `--border-strong` | [HomePalette.borderStrong] (border lerped toward muted text) |
 * | `--text-heading` | [AmazeColors.textPrimary] |
 * | `--text-secondary` | [AmazeColors.textSecondary] |
 * | `--text-muted` (zinc-400) | [AmazeColors.textMuted] |
 * | `--theme-accent` (indigo/sky/blue family) | [AmazeColors.accent] |
 * | `emerald` | [AmazeColors.success] / [AmazeColors.successText] |
 * | `amber` | [AmazeColors.warning] / [AmazeColors.warningText] |
 * | `red` | [AmazeColors.danger] / [AmazeColors.dangerText] |
 *
 * The one place the port is deliberately not byte-identical is *ink* on a tinted
 * wash. The web uses `text-emerald-600` on `bg-emerald-500/10`; this app uses
 * [AmazeColors.successText], which is a step darker in light mode and a step
 * lighter in dark mode. That is the same colour the rest of the app already
 * uses for success text, and it clears WCAG AA on the wash where the web's does
 * not. Using the shared token is the point; matching a hex exactly is not.
 */

// ── Opacity scale ──

/**
 * The web's `/NN` alpha suffixes, as named values.
 *
 * The tinted washes are the reason the week strip reads as one encoding rather
 * than five badges (see [HomeWeekFlavour]): a fill makes every day kind the same
 * shape, so the eye compares colours instead of words.
 */
internal object HomeAlpha {
    /** `bg-<c>-500/10` — a wash, not a paint. */
    const val WASH = 0.10f

    /** `border-<c>-500/20` */
    const val HAIRLINE = 0.20f

    /** `border-<c>-500/30` |
     *  `ring-<c>-500/20` */
    const val RING = 0.30f

    /** `bg-surface/80`, `dark:bg-surface/70` */
    const val TILE_LIGHT = 0.80f

    /** See [TILE_LIGHT]. */
    const val TILE_DARK = 0.70f

    /** `divide-border-muted/60` */
    const val DIVIDER = 0.60f

    /** `border-border-muted/60` on chips */
    const val CHIP_BORDER = 0.60f

    /** `border-border-muted/80` on icon buttons and rows */
    const val HAIRLINE_STRONG = 0.80f

    /** `bg-indigo-500/15` */
    const val FILL_STRONG = 0.15f
}

// ── Radii ──

/**
 * Corner steps, resolved from the shared [com.amazecc.app.shared.theme.AmazeRadius].
 *
 * The web scale is `8 / 12 / 16 / 24 / 28 / 32`; this app's is `8 / 12 / 16 / 24 / 32`.
 * They agree except at 28, which the web reserves for bottom sheets and dashed
 * empty states. The app's `extraLarge` (32) is the same step, so
 * [emptyState] uses it rather than introducing a seventh value — the radius table
 * in `docs/DESIGN_LANGUAGE.md` is explicit that arbitrary radii are not allowed.
 */
@Immutable
data class HomeShape(
    /** 8dp — segmented-control segments, inner chips. */
    val chip: Dp,
    /** 12dp — icon buttons, tone icon tiles, ghost buttons. */
    val control: Dp,
    /** 16dp — list shells, content cards, row icons. */
    val card: Dp,
    /** 24dp — tile surfaces, class cards. */
    val tile: Dp,
    /** 32dp — bottom sheets, dashed empty states. */
    val emptyState: Dp
) {
    /** Full circle, for the week-strip discs. */
    val circle: Dp = 0.dp
}

@Composable
internal fun rememberHomeShape(): HomeShape {
    val r = AmazeTheme.radius
    return remember(r) {
        HomeShape(chip = r.xs, control = r.small, card = r.medium, tile = r.large, emptyState = r.extraLarge)
    }
}

// ── Spacing ──

/**
 * The steps the simplified home uses that [com.amazecc.app.shared.theme.AmazeSpacing]
 * does not name.
 *
 * These are not a new scale: they are the same 4pt grid the app already uses in
 * every screen (`padding(6.dp)`, `padding(10.dp)`, `size(42.dp)` and so on appear
 * throughout the existing code). They are named here so the home does not repeat
 * raw literals — `docs/DESIGN_LANGUAGE.md` requires the spacing values be chosen
 * deliberately, and a named constant is how a reader can see the choice.
 */
internal object HomeSpace {
    /** 4dp — `gap-1`, `px-1`, `py-1`, `mt-1`. */
    val xs = 4.dp

    /** 8dp — `gap-2`, `p-2`, `px-2`, `py-2`. */
    val sm = 8.dp

    /** 16dp — `gap-4`, `p-4`, and the house icon size. */
    val md = 16.dp

    /** 24dp — `gap-6`, `p-6`, `space-y-6`. */
    val lg = 24.dp

    /** 32dp — `p-8`, the empty state's padding. */
    val xl = 32.dp

    /** 2dp — `gap-0.5`, week-strip track gap. */
    val hairline = 2.dp

    /** 6dp — `gap-1.5`, `py-1.5`, `mt-1.5`. */
    val xxs = 6.dp

    /** 10dp — `gap-2.5`, `px-2.5`, `py-2.5`, `mt-2.5`. */
    val between = 10.dp

    /** 12dp — `gap-3`, `py-3`, `p-3`, `px-3`. */
    val row = 12.dp

    /** 14dp — the 3.5 unit used for secondary icons. */
    val iconLg = 14.dp

    /** 16dp — icons, `p-4`. */
    val icon = 16.dp

    /** 20dp — `p-5`, the class card's padding. */
    val card = 20.dp

    /** 24dp — `p-6`, `space-y-6`. */
    val section = 24.dp

    /** 28dp — the slide distance the week strip animates across. */
    val slide = 28.dp

    /** 32dp — the height of the carousel's y-shift. */
    val shift = 6.dp

    /** 40dp — the top-bar avatar. */
    val avatar = 40.dp

    /** 44dp — the top-bar avatar at larger text scales. */
    val avatarLg = 44.dp

    /** 46dp — the week-strip disc cap: 7 x 46 already fills a 390dp phone. */
    val dayDisc = 46.dp

    /** 128dp — the pinned tile height (`h-32`). */
    val tileShort = 128.dp

    /** 144dp — the pinned tile height at larger text scales (`sm:h-36`). */
    val tileTall = 144.dp

    /** 80% of the viewport — the exam-day scroll budget. */
    val viewport = 320.dp
}

// ── Motion ──

/**
 * Durations from the design language's motion table (`docs/DESIGN_LANGUAGE.md`):
 * 150ms fast for hover and press, 250ms standard for panels and navigation,
 * 350ms slow for dialogs and large transitions.
 *
 * The two 200ms entries are the web home's own slide timings, carried over
 * verbatim — the week strip and the insight carousel both animate in 200ms and
 * reading them any slower makes the strip feel like it is being dragged.
 */
internal object HomeMotion {
    /** Fast. */
    const val FAST = 150

    /** The web home's slide duration. */
    const val SLIDE = 200

    /** Standard. */
    const val STANDARD = 250

    /** The web's dot-indicator and page-enter duration. */
    const val INDICATOR = 300

    /** Slow. */
    const val SLOW = 350

    /** `duration-700 ease-linear` on the live class progress bar. */
    const val PROGRESS = 700

    /** 5s between insight slides. */
    const val CAROUSEL_INTERVAL = 5000L

    @Composable
    internal fun <T> fast(): AnimationSpec<T> = remember { tween(FAST, easing = FastOutSlowInEasing) }

    @Composable
    internal fun <T> slide(): AnimationSpec<T> = remember { tween(SLIDE, easing = FastOutSlowInEasing) }

    @Composable
    internal fun <T> standard(): AnimationSpec<T> = remember { tween(STANDARD, easing = FastOutSlowInEasing) }

    @Composable
    internal fun <T> indicator(): AnimationSpec<T> = remember { tween(INDICATOR) }

    @Composable
    internal fun <T> progress(): AnimationSpec<T> = remember { tween(PROGRESS, easing = androidx.compose.animation.core.LinearEasing) }
}

// ── Press affordance ──

/**
 * The web's `hover:scale-[1.01] active:scale-[0.98]` on tiles and
 * `active:scale-95` on icon buttons.
 *
 * [pressScale] is the resting/pressed pair for a tile; [iconPressScale] is the
 * shallower one for the small square controls, which would look like a
 * different card entirely at 0.98.
 */
internal object HomePress {
    const val TILE_REST = 1f
    const val TILE_HOVER = 1.01f
    const val TILE_PRESSED = 0.98f

    const val ICON_REST = 1f
    const val ICON_PRESSED = 0.95f

    const val DISC_REST = 1f
    const val DISC_SELECTED = 1.05f
    const val DISC_PRESSED = 0.95f
}

// ── Colour roles ──

/**
 * True when the resolved palette is a dark one.
 *
 * The web's tokens branch on `dark:` at every call site; Compose branches on
 * the resolved [AmazeColors] instead, so a light theme with a custom dark
 * background still gets the dark-mode alphas. Luminance is the honest test
 * here: LIGHT is #F1F5F9, DARK and AMOLED are both #000000.
 */
internal val AmazeColors.isDark: Boolean
    get() = background.luminance() < 0.5f

/**
 * The four neutral roles the home's chrome needs beyond [AmazeColors].
 *
 * The web has four surface steps and two border weights; this app has two
 * surface steps and one border. Rather than invent tokens, the extras are
 * *derived* from the ones that exist, so a palette override still propagates.
 */
@Immutable
data class HomePalette(
    /** The page background. */
    val background: Color,
    /** A card resting on the page — the `bg-surface/80` wash. */
    val surfaceWash: Color,
    /** `surface-secondary` / `surface-tertiary` — chips, icon buttons, list hover. */
    val surfaceRaised: Color,
    /** `--border-muted` */
    val borderMuted: Color,
    /** `--border-strong`, one step heavier than [borderMuted]. */
    val borderStrong: Color,
    /** The dashed border of an empty state. */
    val dashed: Color
)

@Composable
internal fun rememberHomePalette(colors: AmazeColors = AmazeTheme.colors): HomePalette {
    val isDark = colors.isDark
    val tileAlpha = if (isDark) HomeAlpha.TILE_DARK else HomeAlpha.TILE_LIGHT
    return remember(colors.background, colors.surface, colors.elevatedSurface, colors.border, colors.textMuted, isDark) {
        HomePalette(
            background = colors.background,
            surfaceWash = colors.surface.copy(alpha = tileAlpha),
            surfaceRaised = colors.elevatedSurface,
            borderMuted = colors.border,
            // border-strong sits roughly a third of the way from border to muted
            // text, which is where the web's `oklch(0.84)` lands between
            // `oklch(0.93)` and the ink.
            borderStrong = lerp(colors.border, colors.textMuted, 0.4f),
            dashed = lerp(colors.border, colors.textMuted, 0.3f)
        )
    }
}

/**
 * One semantic hue, resolved the way the web's `TONE_*` maps resolve it.
 *
 * The web keeps three separate maps (`TONE_BADGE`, `TONE_TEXT`,
 * `TONE_ICON_TILE`) because Tailwind cannot compose them. Compose can, so one
 * [HomeToneColors] carries all three slots and every call site reads the one it
 * needs — which also makes it impossible to pair, say, a red wash with a green
 * ink, a mistake the three maps permit.
 *
 * The hue names are the *meaning*, not the paint: emerald/amber/red map onto
 * this app's success/warning/danger so a "3 absences" badge reads as critical
 * whatever accent the user has picked.
 */
@Immutable
data class HomeToneColors(
    /** `--text-<c>-600` / `dark:text-<c>-400` — the value's own colour. */
    val ink: Color,
    /** `bg-<c>-500/10` */
    val wash: Color,
    /** `border-<c>-500/20` */
    val border: Color,
    /** The solid `<c>-500` dot, for the 6px status pip. */
    val dot: Color
)

/**
 * The five tones the home is allowed, per the colour discipline in
 * `docs/social-tt/11-ui-redesign.md`: at most three semantic hues plus the
 * accent, and colour in only four places — a badge, a number, an icon tile, or
 * a status dot. Everything else is neutral.
 */
enum class HomeTone {
    /** `emerald` — safe attendance, a bunkable margin, a positive status. */
    SUCCESS,

    /** `amber` — a shortened day, a warning band, a zero margin. */
    WARNING,

    /** `red` — a genuine loss of a teaching day, a critical shortage. */
    DANGER,

    /** The accent family (`indigo`/`sky`/`blue` in the web), remapped here. */
    ACCENT,

    /** `zinc` — neutral, reading the surface tokens. */
    NEUTRAL
}

@Composable
internal fun homeToneColors(tone: HomeTone, colors: AmazeColors = AmazeTheme.colors): HomeToneColors {
    val base = when (tone) {
        HomeTone.SUCCESS -> colors.success
        HomeTone.WARNING -> colors.warning
        HomeTone.DANGER -> colors.danger
        HomeTone.ACCENT -> colors.accent
        HomeTone.NEUTRAL -> return remember(colors.textSecondary, colors.border) {
            HomeToneColors(
                ink = colors.textSecondary,
                wash = colors.elevatedSurface,
                border = colors.border,
                dot = colors.textMuted
            )
        }
    }
    // The ink is the app's own `<role>Text` token rather than the base hue, so
    // the number on the wash clears AA in both modes.
    val ink = when (tone) {
        HomeTone.SUCCESS -> colors.successText
        HomeTone.WARNING -> colors.warningText
        HomeTone.DANGER -> colors.dangerText
        else -> colors.accent
    }
    return remember(base, ink) {
        HomeToneColors(
            ink = ink,
            wash = base.copy(alpha = HomeAlpha.WASH),
            border = base.copy(alpha = HomeAlpha.HAIRLINE),
            dot = base
        )
    }
}

/** The same slot list, resolved for every tone at once. */
@Composable
internal fun rememberHomeTones(colors: AmazeColors = AmazeTheme.colors): Map<HomeTone, HomeToneColors> {
    val success = colors.success
    val warning = colors.warning
    val danger = colors.danger
    val accent = colors.accent
    val neutralInk = colors.textSecondary
    val neutralWash = colors.elevatedSurface
    val neutralBorder = colors.border
    val neutralDot = colors.textMuted
    val successInk = colors.successText
    val warningInk = colors.warningText
    val dangerInk = colors.dangerText
    return remember(
        success, warning, danger, accent,
        neutralInk, neutralWash, neutralBorder, neutralDot,
        successInk, warningInk, dangerInk
    ) {
        mapOf(
            HomeTone.SUCCESS to HomeToneColors(successInk, success.copy(alpha = HomeAlpha.WASH), success.copy(alpha = HomeAlpha.HAIRLINE), success),
            HomeTone.WARNING to HomeToneColors(warningInk, warning.copy(alpha = HomeAlpha.WASH), warning.copy(alpha = HomeAlpha.HAIRLINE), warning),
            HomeTone.DANGER to HomeToneColors(dangerInk, danger.copy(alpha = HomeAlpha.WASH), danger.copy(alpha = HomeAlpha.HAIRLINE), danger),
            HomeTone.ACCENT to HomeToneColors(accent, accent.copy(alpha = HomeAlpha.WASH), accent.copy(alpha = HomeAlpha.HAIRLINE), accent),
            HomeTone.NEUTRAL to HomeToneColors(neutralInk, neutralWash, neutralBorder, neutralDot)
        )
    }
}

// ── Type ──

/**
 * The type roles the simplified home uses, resolved once.
 *
 * ## The map
 *
 * | Web | Here | Size |
 * |---|---|---|
 * | `text-xl sm:text-2xl font-black tracking-tight font-outfit` (page title) | [pageTitle] | 20sp |
 * | `text-xs font-semibold` (greeting eyebrow) | [eyebrow] | 12sp |
 * | `text-[10px] sm:text-xs font-bold uppercase tracking-wider font-outfit` (tile label) | [tileLabel] | 10sp |
 * | `text-[9px] sm:text-[10px] font-extrabold uppercase` (tile badge) | [badge] | 10sp |
 * | `text-3xl sm:text-4xl font-black font-outfit leading-none` (hero + carousel) | [hero] | 32sp |
 * | `text-2xl sm:text-3xl` (smaller carousel headline) | [heroSmall] | 24sp |
 * | `text-[10.5px] sm:text-xs` (tile subline) | [subline] | 11sp |
 * | `text-sm font-black font-outfit tracking-tight` (section header) | [sectionTitle] | 14sp |
 * | `text-sm font-extrabold font-outfit` (selected-day header) | [dayTitle] | 14sp |
 * | `text-sm font-bold font-outfit` (compact pill title) | [rowTitle] | 14sp |
 * | `text-xs font-bold` (list row title) | [rowTitleSmall] | 12sp |
 * | `text-[11px]` / `text-xs` (meta lines) | [meta] / [body] | 11 / 12sp |
 * | `text-[9px]` / `text-[10px] font-extrabold uppercase` (chips) | [micro] | 10sp |
 * | `text-[8.5px] font-bold uppercase tracking-wider` (week day code) | [dayCode] | 10sp |
 * | `text-[15px] font-black font-outfit` (week date) | [weekDate] | 16sp |
 * | `text-lg sm:text-xl font-black font-outfit` (exam title) | [examTitle] | 20sp |
 * | `text-base font-black font-outfit` (compact pill percentage) | [pillValue] | 16sp |
 * | `text-base font-extrabold font-outfit` (detailed pill title) | [pillTitle] | 16sp |
 *
 * ## Why there is one exception to the sizes
 *
 * The web sets the week-strip day code at `text-[8.5px]`. This app's smallest
 * named step is 10sp ([com.amazecc.app.shared.theme.AmazeFontSize.micro]) and
 * the design language forbids introducing new sizes for a single screen. 10sp in
 * a 46dp disc reads better than 8.5sp ever did, so the size is lifted to the
 * scale and the layout — 46dp cap, 2dp track gap, the two stacked rows — is
 * unchanged.
 *
 * Outfit carries display, headings, large numbers and category labels; Geist
 * carries everything else. That is the split `docs/TYPOGRAPHY.md` describes and
 * the one the rest of the app already follows.
 */
@Immutable
data class HomeType(
    val pageTitle: TextStyle,
    val eyebrow: TextStyle,
    val tileLabel: TextStyle,
    val badge: TextStyle,
    val hero: TextStyle,
    val heroSmall: TextStyle,
    val subline: TextStyle,
    val sectionTitle: TextStyle,
    val dayTitle: TextStyle,
    val rowTitle: TextStyle,
    val rowTitleSmall: TextStyle,
    val meta: TextStyle,
    val body: TextStyle,
    val micro: TextStyle,
    val dayCode: TextStyle,
    val weekDate: TextStyle,
    val examTitle: TextStyle,
    val pillValue: TextStyle,
    val pillTitle: TextStyle
)

@Composable
internal fun rememberHomeType(): HomeType {
    val display = getOutfitFontFamily()
    val body = getGeistFontFamily()
    return remember(display, body) {
        HomeType(
            pageTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
            eyebrow = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.sp),
            tileLabel = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.0.sp),
            badge = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp),
            hero = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
            heroSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
            subline = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
            sectionTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp),
            dayTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp),
            rowTitle = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
            rowTitleSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
            meta = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.1.sp),
            body = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp),
            micro = TextStyle(fontFamily = body, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.6.sp),
            dayCode = TextStyle(fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 10.sp, letterSpacing = 1.0.sp),
            weekDate = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp),
            examTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
            pillValue = TextStyle(fontFamily = display, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp),
            pillTitle = TextStyle(fontFamily = display, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp)
        )
    }
}
