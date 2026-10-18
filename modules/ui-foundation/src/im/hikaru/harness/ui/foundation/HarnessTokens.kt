package im.hikaru.harness.ui.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Semantic colors for one theme. Values are measured from the Codex Desktop light and dark token
 * sets in app.asar (codex-light / app-shared CSS custom properties).
 */
public data class HarnessColors(
    val Canvas: Color,
    /**
     * Window background behind the page surface card. Measured #F6F6F6 (246) on the live Codex
     * window; the card, the navigator and the main content sit on top of it.
     */
    val AppBackground: Color,
    val Surface: Color,
    val SurfaceSecondary: Color,
    val SurfaceElevated: Color,
    val SurfaceSelected: Color,
    /**
     * The page canvas the 新标签页 page's content sits on, below its own toolbar. Measured on the live
     * pane as `bg-surface-canvas` = `color(srgb 0.98 0.98 0.98)`, i.e. 250 (#FAFAFA) — one step below
     * the white toolbar band above it.
     */
    val SurfaceCanvas: Color,
    /**
     * Keycap fill: the ink of [Text] at 6.5%, measured as `oklab(.2256 … / .065)` on the 新标签页 tool
     * cards' keyboard chords, which reads 241 over the card's white. Lighter than [ChromeFill]: a 10%
     * fill would read 232, nine levels away.
     */
    val SurfaceKeycap: Color,
    val Border: Color,
    val BorderSubtle: Color,
    val BorderStrong: Color,
    val BorderFocused: Color,
    val Text: Color,
    val TextMuted: Color,
    val TextDisabled: Color,
    /**
     * Secondary ink: the 新标签页 tool cards' leading glyphs, their keyboard chords and the browser
     * toolbar's 搜索或输入网址 placeholder, all measured as `color(srgb 0.101961 0.109804 0.121569 /
     * 0.65)` — [Text] at 65%. Distinguish it from the opaque grey [TextMuted] and from [ChromeIcon],
     * which is the same ink at 49.4%.
     */
    val TextSecondary: Color,
    val Accent: Color,
    val AccentStrong: Color,
    val Danger: Color,
    val Success: Color,
    val Warning: Color,
    val OnAccent: Color,
    /**
     * Inverse (tooltip) surface: the Codex tooltip is a solid pill that inverts the theme — dark on
     * light, light on dark (`--color-background-primary-solid` + `--color-text-primary-solid`).
     */
    /**
     * Navigator surface. The reference renders the embedded navigator as 65% white over the page
     * surface card (measured 252 over the 246 background), so it is a translucent fill rather than
     * the opaque [Surface].
     */
    val NavigatorSurface: Color,
    /**
     * The navigator hover preview's own surface. The reference paints the peeking card with
     * `--app-shell-sidebar-popover-background`, which resolves to an *opaque* elevated surface
     * (`rgb(255,255,255)` in light) — not the 65% tint [NavigatorSurface] gives the embedded column.
     */
    val NavigatorPeekSurface: Color,
    /**
     * Stronger hairline ring: the navigator hover preview rings itself with a 1px 8% ink line
     * (`rgba(26,28,31,.08)`), unlike the 4% ring the card and composer use.
     */
    val SurfaceRingStrong: Color,
    /**
     * Hairline ring the reference draws around the page surface card and the composer
     * (`0 0 0 0.5px rgba(26,28,31,.04)`), which separates them from the window background.
     */
    val SurfaceRing: Color,
    /**
     * Title-bar chrome ink. The Codex tab bar draws every title-bar control (back/forward, hide
     * sidebar, new tab, split view, tab close) with `rgba(26,28,31,.494)` — alpha 0x7E — and lifts it
     * to full-strength text when the control is toggled on. A disabled control keeps this colour and
     * drops to `opacity: .4` instead of greying out. Dark mode inverts the channel, keeping the alpha.
     */
    val ChromeIcon: Color,
    /**
     * Title-bar *text* ink: the pane's own title while the tab strips are hidden, measured on the live
     * bar as `color: rgb(26, 28, 31)` (`--color-text-default`, 16px / weight 430 / 24px line). It is
     * the full-strength ink the chrome icons are drawn from, not the app's [Text] — Codex keeps a
     * separate, slightly lighter value for the window chrome (0x1A1C1F against [Text] `0x0D0D0D`).
     */
    val ChromeText: Color,
    /**
     * Title-bar control hover fill and the toggled-on pill: the same ink at 10%
     * (measured `oklab(.2256 … / .1)` on the engaged "退出完整视图" control).
     */
    val ChromeFill: Color,
    /**
     * Split-seam ink: the 1px line the panes draw when the seam is hovered or dragged, measured as
     * text at 25% (`oklab(.2256 … / .25)`). The line is a full-height *vertical* gradient — transparent
     * at both ends, this ink in the middle — and is invisible (`opacity: 0`) while the seam is idle.
     * The seam is not empty at rest; the pane edge an idle seam shows is [ChromeSeamEdge].
     */
    val ChromeSeam: Color,
    /**
     * The split's own boundary hairline, the divider an idle split shows: ink at 7%, measured as 239
     * in the boundary column of the live 1178 boundary — and 239 on *every* row sampled from 60 to 1300
     * (the white pane reads 255 one pixel to its left). The live pane draws it with a `left-0 w-px`
     * element carrying `shadow-[-8px_0_16px_-8px_rgb(0_0_0/0.18)]`: the shadow's peak lands under the
     * 1px element itself, so what is visible at rest is one pixel, not a band (x 1170…1174 is the
     * left pane's own white except in the few rows where its content does not reach the edge).
     */
    val ChromeSeamEdge: Color,
    /**
     * Title-bar hairline ink: the 1px rules that cut the title bar into its segments, measured on the
     * live shell as `--color-border` = `rgba(26,28,31,.078)` (ink at 8%). The rules are 20dp tall
     * between the leading chrome and the pane segments, 16dp tall in the trailing cluster, and 12dp
     * tall between two adjacent tabs.
     */
    val ChromeRule: Color,
    val InverseSurface: Color,
    val OnInverse: Color,
    val CodeBackground: Color,
    val DiffAdded: Color,
    val DiffRemoved: Color,
    val DiffAddedBackground: Color,
    val DiffRemovedBackground: Color,
)

/**
 * Codex light palette. Sourced from app-shared CSS:
 * gray-fixed-0/25/50/75/100/400/500/1000, alpha-05/alpha-10, blue-500, green-500, red-500, yellow-500.
 */
public val HarnessLightColors: HarnessColors =
    HarnessColors(
        Canvas = Color(0xFFFFFFFF),
        AppBackground = Color(0xFFF6F6F6),
        Surface = Color(0xFFF9F9F9),
        SurfaceSecondary = Color(0xFFF3F3F3),
        SurfaceElevated = Color(0xFFFFFFFF),
        SurfaceSelected = Color(0xFFEDEDED),
        // Measured `bg-surface-canvas` on the live 新标签页 pane.
        SurfaceCanvas = Color(0xFFFAFAFA),
        // Measured `oklab(.2256 … / .065)` on that page's keyboard-chord pills.
        SurfaceKeycap = Color(0x111A1C1F),
        Border = Color(0x297F7F7F),
        // Measured #eeedee (238) over white on the settings card border, row dividers and control
        // outline in the reference capture. The app's border-subtle is a ~7 percent tint of
        // --alpha-base (#0d0d0d), not the 8 percent gray-127 alpha-05 that rendered #f5f5f5.
        BorderSubtle = Color(0x227F7F7F),
        BorderStrong = Color(0x337F7F7F),
        BorderFocused = Color(0xFF0169CC),
        Text = Color(0xFF0D0D0D),
        TextMuted = Color(0xFF5D5D5D),
        TextDisabled = Color(0xFF8F8F8F),
        // Measured 65% of the chrome ink on that page's tool cards.
        TextSecondary = Color(0xA61A1C1F),
        Accent = Color(0xFF0169CC),
        AccentStrong = Color(0xFF004F99),
        Danger = Color(0xFFE02E2A),
        Success = Color(0xFF00A240),
        Warning = Color(0xFFE0AC00),
        OnAccent = Color(0xFFFFFFFF),
        // Measured rail tip fill rgb(26,28,31) with white ink.
        NavigatorSurface = Color(0xA6FFFFFF),
        // Measured on the live peeking card: background rgb(255,255,255).
        NavigatorPeekSurface = Color(0xFFFFFFFF),
        SurfaceRingStrong = Color(0x141A1C1F),
        SurfaceRing = Color(0x0A1A1C1F),
        InverseSurface = Color(0xFF1A1C1F),
        OnInverse = Color(0xFFFFFFFF),
        ChromeIcon = Color(0x7E1A1C1F),
        ChromeText = Color(0xFF1A1C1F),
        ChromeFill = Color(0x1A1A1C1F),
        ChromeSeam = Color(0x401A1C1F),
        ChromeSeamEdge = Color(0x121A1C1F),
        ChromeRule = Color(0x141A1C1F),
        CodeBackground = Color(0xFFF9F9F9),
        DiffAdded = Color(0xFF00A240),
        DiffRemoved = Color(0xFFE02E2A),
        DiffAddedBackground = Color(0x1A00A240),
        DiffRemovedBackground = Color(0x1AE02E2A),
    )

/** Codex dark palette for the same semantic slots. */
public val HarnessDarkColors: HarnessColors =
    HarnessColors(
        Canvas = Color(0xFF171717),
        // Dark counterpart is a project choice; not measured on a dark Codex window yet.
        AppBackground = Color(0xFF0F0F0F),
        Surface = Color(0xFF202020),
        SurfaceSecondary = Color(0xFF1C1C1C),
        SurfaceElevated = Color(0xFF282828),
        SurfaceSelected = Color(0xFF303030),
        // Dark counterpart is a project choice; the page canvas is measured light-only.
        SurfaceCanvas = Color(0xFF1A1A1A),
        SurfaceKeycap = Color(0x11FFFFFF),
        Border = Color(0xFF3A3A3A),
        BorderSubtle = Color(0xFF2E2E2E),
        BorderStrong = Color(0xFF4A4A4A),
        BorderFocused = Color(0xFF8AB4F8),
        Text = Color(0xFFF2F2F2),
        TextMuted = Color(0xFFA8A8A8),
        TextDisabled = Color(0xFF6F6F6F),
        TextSecondary = Color(0xA6FFFFFF),
        Accent = Color(0xFF8AB4F8),
        AccentStrong = Color(0xFFB8D1FF),
        Danger = Color(0xFFFF8F8F),
        Success = Color(0xFF8FD3A8),
        Warning = Color(0xFFFFD166),
        OnAccent = Color(0xFF10213A),
        // Dark theme inverts the pill: light fill, dark ink.
        NavigatorSurface = Color(0xFF202020),
        // The dark counterpart of the opaque popover surface is the dark surface itself.
        NavigatorPeekSurface = Color(0xFF202020),
        SurfaceRingStrong = Color(0x14FFFFFF),
        SurfaceRing = Color(0x0AFFFFFF),
        InverseSurface = Color(0xFFFCFCFC),
        OnInverse = Color(0xFF0D0D0D),
        ChromeIcon = Color(0x7EFFFFFF),
        // The light value is the measured one; the dark counterpart follows the dark ink.
        ChromeText = Color(0xFFF2F2F2),
        ChromeFill = Color(0x1AFFFFFF),
        ChromeSeam = Color(0x40FFFFFF),
        ChromeSeamEdge = Color(0x12FFFFFF),
        ChromeRule = Color(0x14FFFFFF),
        CodeBackground = Color(0xFF1A1A1A),
        DiffAdded = Color(0xFF8FD3A8),
        DiffRemoved = Color(0xFFFF8F8F),
        DiffAddedBackground = Color(0x2E8FD3A8),
        DiffRemovedBackground = Color(0x2EFF8F8F),
    )

public val LocalHarnessColors = staticCompositionLocalOf { HarnessLightColors }

/** Shared visual values. Components should consume these values instead of Material defaults. */
public object HarnessTokens {
    /**
     * Shared geometry for welcome and docked conversation composers, measured on the live Codex
     * composer (`_ComposerLayoutRoot`, 736×98): 14 top + 44 editor + 4 gap + 28 footer + 8 bottom.
     */
    public object Composer {
        public val Radius: Dp = 22.dp
        /** Input inset; the fixed `0 12px` padding of the reference editor wrapper. */
        public val PaddingX: Dp = 12.dp
        /** Footer inset; `_ComposerLayoutFooter` keeps `0 8px`. */
        public val FooterPaddingX: Dp = 8.dp
        public val PaddingTop: Dp = 14.dp
        public val PaddingBottom: Dp = 8.dp
        public val SectionGap: Dp = 4.dp
        public val ControlGap: Dp = 5.dp
        /** Chip inset: the reference permission chip keeps `0 6px`. */
        public val ButtonInsetX: Dp = 6.dp
        public val DockInset: Dp = 16.dp
        public val PageInset: Dp = 24.dp
        public val WelcomeGap: Dp = 24.dp
        public val MaxWidth: Dp = 760.dp
        public val WelcomeEditorMinHeight: Dp = 72.dp
        public val ThreadEditorMinHeight: Dp = 44.dp
        public val EditorMaxHeight: Dp = 160.dp
        public const val ViewportFraction: Float = 0.25f
        /** Every footer control is a 28px slot; the reference draws a 16px icon inside. */
        public val ActionSize: Dp = 28.dp
        /** Composer body text: 14 / 20 on the live `ProseMirror`. */
        public val TextSize: TextUnit = 14.sp
        public val TextLineHeight: TextUnit = 20.sp
        /** Footer labels: 13 / 18 on the live permission chip. */
        public val LabelSize: TextUnit = 13.sp
        public val LabelLineHeight: TextUnit = 18.sp
        public val PopupWidth: Dp = 280.dp
        /** Live model picker: 254 wide, radius 20, 4 inset, opening 9px above the trigger. */
        public val ModelPopupWidth: Dp = 254.dp
        public val PermissionPopupWidth: Dp = 320.dp
        /**
         * Live model menu ceiling. `_ModelList` caps its scroll area at
         * `min(316px, content-available-height - 4px)` and the panel adds its 4px top inset, so the
         * menu never grows past 320; the explicit-model simple view is 96 high.
         */
        public val PopupMaxHeight: Dp = 320.dp
        public val ModelListMaxHeight: Dp = 316.dp
        /** Live permission popup: 320 wide, radius 20, 4 inset; rows are 312×42.6 at radius 15. */
        public val PopupRadius: Dp = 20.dp
        public val PopupContainerInset: Dp = 4.dp
        public val PopupItemInsetX: Dp = 8.dp
        public val PopupItemInsetY: Dp = 5.dp
        public val PopupItemRadius: Dp = 15.dp
        /** Live permission rows are 312×42.5625, model-list rows 235×28.5625. A fixed height also
         *  keeps Material3's 48dp minimum interactive size from inflating them. */
        public val PermissionItemHeight: Dp = 42.5.dp
        public val ModelItemHeight: Dp = 28.5.dp
        /** Live row content gap between the 18px icon and the label column. */
        public val PopupItemGap: Dp = 6.dp
        public val PopupShadow: Dp = 16.dp
        public val PopupInset: Dp = 12.dp
        /** Live permission header: 26 high, `5px 8px`, 13/16 description ink. */
        public val PopupHeaderHeight: Dp = 26.dp
        /** Live `_ViewToggle`: a 32 chip (`min-height:32px`, `4px` inset) inside the picker. */
        public val ModelChipHeight: Dp = 32.dp
        /** Live `_ViewControls` row: `min-height:36px`, centring the 32 chip between its edge slots. */
        public val ModelChipRowHeight: Dp = 36.dp
        /** Distance between the model menu's bottom edge and the trigger's top edge. */
        public val ModelPopupOffset: Dp = 9.dp
        public val ModelChipRadius: Dp = 8.dp
        public val ModelChipInset: Dp = 32.dp
        public val ModelResetSize: Dp = 32.dp
        /** Effort slider (live `_Container_xwb5v_1`): 32 slot, 24 track, 28 knob, 8 inset → 230 track. */
        public val EffortRowHeight: Dp = 32.dp
        public val EffortTrackHeight: Dp = 24.dp
        public val EffortKnobSize: Dp = 28.dp
        public val EffortTrackInset: Dp = 8.dp
        /** Live `_Tick_xwb5v_40`: 4px dots, 30% white when reached and 25% otherwise. */
        public val EffortTickSize: Dp = 4.dp
        /**
         * Home composer rail (`data-composer-rail=above`): a 710×38 strip attached to the 736 card,
         * inset 13 on both sides, with a ~16 radius top. The live rail box is 42 high and the card
         * covers its last 4px, which is why [RailOverlap] exists.
         */
        public val RailInset: Dp = 13.dp
        public val RailHeight: Dp = 38.dp
        public val RailOverlap: Dp = 4.dp
        public val RailRadius: Dp = 16.dp
        /** Rail chips: 28 high pills, `0 8px`, a 16px icon, 6px gap, and 8px between chips. */
        public val RailChipHeight: Dp = 28.dp
        public val RailChipInsetX: Dp = 8.dp
        public val RailChipGap: Dp = 6.dp
        public val RailChipSpacing: Dp = 8.dp
        public val RailChipInset: Dp = 4.dp
        /** Environment gear: 30×28, radius 10, 16px icon inset 6. */
        public val RailGearWidth: Dp = 30.dp
        public val RailGearRadius: Dp = 10.dp
        /** Project dropdown: 260 wide; work-location menu: 280 wide; both radius 20 with a 4 inset. */
        public val ProjectPopupWidth: Dp = 260.dp
        public val WorkPopupWidth: Dp = 280.dp
        public val ProjectPopupMaxHeight: Dp = 320.dp
        /** Live menu rows are 28.5625 high at radius 15; the search row keeps `5px 8px`. */
        public val RailMenuRowHeight: Dp = 28.5.dp
        public val RailMenuRowRadius: Dp = 15.dp
        public val RailSearchHeight: Dp = 28.5.dp
        /** `工作位置` section label: 26.5625 high with `4px 8px`. */
        public val RailSectionHeight: Dp = 26.5.dp
        /** No outer border; the reference renders a 1px inset glow instead. */
        public val BorderWidth: Dp = 0.5.dp
        public const val FocusAlpha: Float = 0.45f
        /** Permission selection emphasis, not an execution-status indicator. */
        public val FullAccessColor: Color = Color(0xFFFF8549)
    }
    public object Slider {
        public val TrackHeight: Dp = 24.dp
        public val ThumbSize: Dp = 26.dp
        public val ThumbElevation: Dp = 2.dp
        public val TickRadius: Dp = 2.dp
    }
    /** Active palette. Reads the theme provided by [HarnessTheme]; never hardcodes dark. */
    public val Colors: HarnessColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHarnessColors.current

    /** Codex uses "OpenAI Sans" with an -apple-system fallback; Compose maps to the platform UI font. */
    public object Fonts {
        public val Ui: FontFamily = FontFamily.SansSerif
        public val Code: FontFamily = FontFamily.Monospace
    }

    /**
     * Identity colours that do not follow the light/dark palette. Codex derives the account chip
     * colour from the signed-in account; the value below is measured from the reference account chip
     * of the target screenshot and is the first-version default until accounts carry a colour.
     */
    public object Identity {
        /** Account avatar fill, measured at #E69E3E in the reference window capture. */
        public val AvatarSurface: Color = Color(0xFFE69E3E)
        /** Initials drawn on [AvatarSurface]. */
        public val OnAvatar: Color = Color(0xFFFFFFFF)
    }

    /** Type scale measured from app-shared CSS (text-xs/sm/md/lg, heading-xs/sm). */
    public object Text {
        public val Xs: TextUnit = 12.sp
        public val Sm: TextUnit = 14.sp
        public val Md: TextUnit = 16.sp
        public val Lg: TextUnit = 18.sp
        public val HeadingSm: TextUnit = 18.sp
        public val HeadingMd: TextUnit = 20.sp
        public val HeadingLg: TextUnit = 24.sp
        /**
         * The Codex `heading-xl` the 新聊天 page's hero heading is set in: 28px on a 33.6px line
         * (line-height 1.2) at weight 400, measured on the live page.
         */
        public val HeadingXl: TextUnit = 28.sp
    }

    public object Spacing {
        public val One: Dp = 4.dp
        public val Two: Dp = 8.dp
        public val Three: Dp = 12.dp
        public val Four: Dp = 16.dp
        public val Five: Dp = 20.dp
        public val Six: Dp = 24.dp
        public val Seven: Dp = 32.dp
    }

    public object Corners {
        public val Small: Dp = 4.dp
        /**
         * Compact inline control corner. Fitting the left-edge profile of the 2026-10-06 settings capture
         * (row button and row select, both 28px tall) gives a radius of 9.5-10.0px, the Codex --radius-lg
         * token. The method is validated by the same capture's switch, which fits 10.0 for a full-round
         * 32x20 track. The shared 8px Medium was slightly too square for these controls.
         */
        public val Control: Dp = 9.dp
        public val Medium: Dp = 8.dp
        public val Large: Dp = 12.dp
        public val Panel: Dp = 16.dp
        /**
         * Template/landing card corner. The 2026-10-06 Scheduled capture draws its template cards with a
         * dashed outline whose top-left arc, fit with a corner tangent constrained to both edges (robust to
         * dash phase), reads a radius of 22.6; our 22dp path reads 21.9. 22dp also equals the documented
         * --composer-radius token. The naive top-row-dark-extent proxy reads the same pair as 18 vs 17.
         */
        public val Card: Dp = 22.dp
    }

    /**
     * Geometry measured from app.asar: --height-token-row 36px, --height-titlebar 44px,
     * --height-toolbar 46px, --icon-leading-size 20px, --thread-content-max-width 42rem (672px),
     * --composer-radius calc(--spacing * 5.5) = 22px,
     * --spacing-token-sidebar clamp(240px, 275px, min(520px, 100vw - 320px)).
     */
    public object Control {
        public val CompactHeight: Dp = 28.dp
        /**
         * Horizontal content padding for the Compact control size. Measured on the 2026-10-06 settings
         * capture: the compact change button is 44px wide around a 24px two-glyph ink, i.e. about 10px a
         * side, where the compact size previously reused Spacing.Four (16px) and drew 58px.
         */
        public val CompactPaddingX: Dp = 10.dp
        /**
         * Chevron drawn by a select trigger. The 2026-10-06 capture measures 10x5 including the stroke;
         * the HarnessIcon ChevronDown path spans 46 percent of its box, so a 14dp box draws 12x6 at 1.5
         * and the measured 10x5 at 1.25.
         */
        public val SelectChevron: Dp = 14.dp
        public val SelectChevronScale: Float = 1.25f
        /**
         * Horizontal content padding of a select trigger. The 2026-10-06 capture measures the leading
         * icon and label ink inset at 15-16px and the chevron ink right inset at 15px; HarnessText and
         * HarnessIcon each carry about 1-2px of side bearing inside their layout box, so a 14px padding
         * reproduces those ink insets (Spacing.Three draws them at 12-13, Spacing.Four at 16-18).
         */
        public val SelectPaddingX: Dp = 14.dp
        public val Row: Dp = 36.dp
        public val NavRow: Dp = 30.dp
        public val Height: Dp = 36.dp
        public val LargeHeight: Dp = 44.dp
        public val Titlebar: Dp = 44.dp
        public val Toolbar: Dp = 46.dp
        public val Icon: Dp = 20.dp
        public val IconButton: Dp = 28.dp
        /**
         * Title-bar control corner. The Codex tab bar uses two measured radii on the same 28x28
         * control: the sidebar-header trio (back, forward, hide sidebar) is `9.375px`, while the
         * controls living in the tab row itself (new tab, summary, full view, split view) are `12.5px`.
         */
        public val TitlebarControlRadius: Dp = 9.375.dp
        public val TabRowControlRadius: Dp = 12.5.dp
        /** Tab-strip control box: the Codex new-tab/split buttons and the tab close target. */
        public val TabStripControl: Dp = 28.dp
        public val TabClose: Dp = 20.dp
        /**
         * The tab slot's ceiling, which is also its chip: measured over a viewport sweep, the live slot
         * is `clamp(stripWidth / count, 90px, 240px)` and its chip is the slot less the 2px the slot's
         * own wrapper reserves (`pe-0.5`), so chip 238 is what a slot at the 240px ceiling holds.
         */
        public val TabChip: Dp = 238.dp
        /** The tab slot's floor. Below it the live strip stops dividing and scrolls instead. */
        public val TabSlotFloor: Dp = 90.dp
        public val TabChipHeight: Dp = 32.dp
        /** The width a slot reserves at its trailing edge for the 1px separator and the chip's own gap. */
        public val TabChipGap: Dp = 2.dp
        /**
         * The extra width a **leading** slot gives back to its chip. Measured in 完整视图, the pinned
         * 聊天 tab's wrapper is `ps-0 pe-0.5` (`-ms-1 pe-0.5`) — but the chip inside it is `231` of a
         * `240` slot, i.e. `slot - 9`, while the panel's own tabs read `238` of `240`. The 7px is the
         * leading tab's own `me-0.75` plus the button's `pe-2`/`pe-1.75` step, so the 聊天 chip never
         * reaches the separator its slot reserves; a panel tab does.
         */
        public val LeadingTabChipGap: Dp = 9.dp
        /** The gap the hidden trailing separator leaves before the plus (measured 6px). */
        public val TabStripPlusGap: Dp = 6.dp
        /**
         * How much of a tab title fades out when it does not fit. Measured from the live strip's
         * `text-fade-truncate` mask, `linear-gradient(to right, #000 calc(100% - 1rem), transparent)`:
         * the last `1rem` — 16px — of the title's box loses its ink linearly to nothing.
         */
        public val TextFadeTruncate: Dp = 16.dp
        /** Tab chip corner (10px) and its 1px 8% ink ring. */
        public val TabChipRadius: Dp = 10.dp
        /**
         * Split-seam hit area. Measured: the pane separator is a 16px `cursor-col-resize` band
         * (a 1px line at its centre, `after:` expanding the hit area), so the seam takes 16px of the
         * split region and the panes divide what is left. The panes themselves paint no shadow; the
         * boundary they divide at is the 1px centre line, drawn in `ChromeSeamEdge`.
         */
        public val SplitSeamWidth: Dp = 16.dp
        /**
         * The divider's own width, and the width of the mark the pointer reveals. Measured: the live
         * pane's edge element is `w-px`, and the idle hairline it paints is one pixel wide on every row
         * of the seam — the boundary is a line, not a band.
         */
        public val SplitSeamLine: Dp = 1.dp
        /**
         * The 新标签页 page's browser toolbar, measured on the live pane as
         * `flex h-12 min-w-0 items-center gap-1.5 px-2 py-2` inside
         * `relative h-12 w-full border-b border-default bg-surface`: a 48px white band across the top
         * of the pane, its own padding 8px (see `Spacing.Two`) and 6px between controls, closed by a 1px
         * [HarnessColors.ChromeRule] rule (`border-default`) instead of a page-surface edge.
         */
        public val BrowserToolbar: Dp = 48.dp
        public val BrowserToolbarGap: Dp = 6.dp
        /**
         * A toolbar control. The 返回 / 前进 / 重新加载 controls and the 扩展程序 control are all 28x28
         * pills (`rounded-full`, no fill of their own); 浏览器选项 is the one 32x32 pill.
         */
        public val BrowserControl: Dp = 28.dp
        public val BrowserOptionsControl: Dp = 32.dp
        /**
         * The 搜索或输入网址 capsule's own cap. Measured 770px on the live toolbar — the page column's
         * 768 (`max-w-3xl`) plus the pixel a side the reference's ring takes back — laid out centred in
         * the space between the leading and trailing clusters, `padding: 2px` around a 28px field.
         */
        public val BrowserAddressMaxWidth: Dp = 770.dp
        /**
         * The 工具 section: heading, then a 2-column grid whose measured geometry is a function of the
         * page column — cells 376 = (768 - 16) / 2 wide, 40 tall, 16 apart across and 4 apart down.
         */
        public val BrowserToolRow: Dp = 40.dp
        public val BrowserToolCardPaddingX: Dp = 10.dp
        /**
         * The keyboard-chord pill: 16 tall with `padding: 2px 6px` around a 12sp/16 label, which fills
         * the height exactly — so the pill needs no vertical padding of its own.
         */
        public val BrowserKeycap: Dp = 16.dp
        public val BrowserKeycapPaddingX: Dp = 6.dp
        /**
         * The page's own content column, measured `mx-auto flex w-full max-w-3xl flex-col gap-10` at
         * 768 inside a `px-panel py-8` (`padding: 32px 20px`) scrolling container. Wider than the
         * shell's [ContentMaxWidth] (672), which is why the page asks the shell not to cap it
         * (`PageShellSpec.ownContentColumn`).
         */
        public val BrowserPageColumn: Dp = 768.dp
        public val BrowserPagePaddingY: Dp = 32.dp
        public val BrowserPagePaddingX: Dp = 20.dp
        /** The gap between the column's sections (the live `gap-10`); 16 between a heading and its grid. */
        public val BrowserSectionGap: Dp = 40.dp
        /**
         * Title bar segmentation, measured on the live 44px strip. Codex cuts the bar into the leading
         * chrome, one segment per layout column and the trailing cluster with 1px `--color-border`
         * rules: 20px tall (12px inset top and bottom) between the leading chrome and a pane, 16px tall
         * in the trailing cluster, and 12px tall between two adjacent tabs. Controls inside a cluster
         * sit 6px apart, and a pane's own content is inset 8px from its start and 6px from its end.
         */
        public val TitlebarRule: Dp = 20.dp
        public val TitlebarTrailingRule: Dp = 16.dp
        public val TabSeparator: Dp = 12.dp
        public val TitlebarGap: Dp = 6.dp
        public val TabStripInsetStart: Dp = 8.dp
        public val TabStripInsetEnd: Dp = 6.dp
        /**
         * Where the leading chrome's controls start, measured at window x=88: the macOS traffic lights
         * own the first ~82px and the 返回/前进/侧边栏/新聊天 cluster must clear them. The value is in
         * *window* coordinates — it is the same number Jewel's native inset reserves, and the strip
         * shifts its own columns by that inset (`HarnessTabStrip(contentInset = …)`).
         */
        public val TitlebarLeadInset: Dp = 88.dp
        public val SidebarExpanded: Dp = 275.dp
        /**
         * Workspace navigator header strip. Measured 50px in the Codex navigator capture
         * (`docs/desktop-ui/reference/rail-measurement-2026-10-05.md`, navigator section).
         */
        public val NavigatorHeader: Dp = 50.dp
        /** Navigator row-box horizontal inset, the Codex `--padding-row-x` token (8px). */
        public val NavigatorPaddingX: Dp = 8.dp
        /** Navigator row corner: Codex `.sidebar-navigation` sets `--radius-token-row: 10px`. */
        public val NavigatorRowRadius: Dp = 10.dp
        /** Navigator row leading glyph. The Codex capture draws 12-14px inside its leading cell. */
        public val NavigatorIcon: Dp = 16.dp
        /** Workspace-selector dropdown chevron in the navigator header (Codex draws it smaller than the name). */
        public val NavigatorChevron: Dp = 14.dp
        /** Space above a navigator section label. */
        public val NavigatorSectionGap: Dp = 12.dp
        /** Space between a navigator section label and its first row. */
        public val NavigatorSectionRun: Dp = 8.dp
        /**
         * Vertical gap between Workspace Navigator rows. The Codex `.sidebar-navigation` scope sets
         * `--height-token-row: var(--height-token-nav-row)` = 30px and lays the list out with `gap-px`
         * (1px), so two 30px rows sit 31px apart. Measured 31.0px between Codex recent rows against
         * 30.0px here; see the Workspace Navigator measurement note.
         */
        public val NavigatorRowGap: Dp = 1.dp
        /**
         * Width of a page-owned navigator. Only the Workspace navigator is user-resizable and opens
         * at [SidebarExpanded]; every page-owned navigator (Settings, Plugins, Hosts, Scheduled) is
         * fixed at this width. All three 2026-10-06 Codex captures put the page navigator at
         * x=52..340 in a 2560px window: `settings-general.png` (recorded as 289px below),
         * `plugins-marketplace.png` and `scheduled-tasks.png`. The Hosts navigator has no capture of
         * its own; it is the same page-owned component and follows the same fixed width.
         */
        public val PageNavigatorWidth: Dp = 289.dp
        /**
         * Settings page-owned navigator, measured from the 2026-10-06 Codex Settings capture
         * (docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png). The page
         * navigator sits at x=52..340 (289px wide), the title strip is 52px, the search field is 36px
         * with a 12px corner, the group-heading box is 36px and rows are 30px on a 1px gap.
         */
        public val SettingsNavigatorWidth: Dp = PageNavigatorWidth
        public val SettingsHeader: Dp = 52.dp
        public val SettingsSearchHeight: Dp = 36.dp
        public val SettingsSearchRadius: Dp = 12.dp
        public val SettingsGroupHeading: Dp = 36.dp
        public val SettingsRowRadius: Dp = 8.dp
        /**
         * Extra scale for a Settings row's shared placeholder glyph. The 2026-10-06 capture draws
         * ~14px of ink inside the 16px leading box, and the catalogued Settings artwork is authored to
         * fill that box. The previous 1.17 assumed the 0.64-extent shared artwork, but the placeholder
         * actually in use is drawn on the full-bleed grid: at 1.17 it measured 20px of ink and poked
         * out of its cell (ink x rel 14..33 where the catalogued rows read 17..30), so the scale is
         * calibrated back to the measured 14px ink.
         */
        public val SettingsRowIconScale: Float = 0.82f
        /**
         * App-level rail width. Measured from the live Codex window at 1px = 1dp; the method and the
         * scale proof are in docs/desktop-ui/reference/rail-measurement-2026-10-05.md. The workspace
         * navigator owns [SidebarExpanded] separately, so the rail never collapses with it.
         */
        public val RailWidth: Dp = 52.dp
        /** Rail icon button box: the Codex selected highlight is --height-token-row, 36px. */
        public val RailItem: Dp = 36.dp
        /** Rail item corner: Codex sets `--radius-token-row: 10px` on the rail container. */
        public val RailItemRadius: Dp = 10.dp
        /** Rail account avatar diameter: the Codex bottom identity chip measures 24px. */
        public val RailAvatar: Dp = 24.dp
        /** Rail group divider width: the Codex rail rule is `w-6`, i.e. 24px, centred in the rail. */
        public val RailDividerWidth: Dp = 24.dp
        /**
         * Rail glyph extent. The captured rail glyph including its stroke occupies 16px of the 20px
         * leading-icon box; the shared artwork spans 0.64 of its box plus a 0.055 stroke, so the box
         * is scaled by 16 / ((0.64 + 0.055) * 20) = 1.15.
         */
        public val RailIconScale: Float = 1.15f
        /** Rail glyph stroke: ~1.3px in the capture, i.e. 0.055 of the scaled 23-unit artwork box. */
        public val RailIconStrokeRatio: Float = 0.055f
        public val ProjectContext: Dp = 320.dp
        /**
         * Navigator hover preview: the floating navigator a rail item shows while the page has no
         * navigator column on screen. Measured on the live shell with the sidebar collapsed and a rail
         * item hovered (`data-slate-sidebar-peeking=true` on the left panel, and the panel's
         * `[data-slate-sidebar-content]` becomes the card): x 56..340, y 48..1312 of a 2560x1320 window,
         * i.e. the 288px column less the 4px start margin, 4px off the rail, the title strip and the
         * bottom. Its CSS is `margin-block: var(--spacing); margin-inline-start: var(--spacing);
         * border-radius: calc(var(--radius-xl-base) - var(--spacing))` = 8px (that corner renders as
         * ~5px of ink, like the rail tip's 20px radius that renders 10), an opaque surface, and
         * `inset 0 0 0 var(--hairline) var(--app-shell-sidebar-popover-border), var(--shadow-card)`.
         */
        public val NavigatorPeekInset: Dp = 4.dp
        public val NavigatorPeekRadius: Dp = 8.dp
        /**
         * The one card shadow the reference shares between the page surface
         * (`--app-shell-page-surface-shadow: var(--elevation-stroke-subtle), var(--shadow-card)`) and the
         * peek (`var(--shadow-card)` = `0 4px 16px 0 #0000000d`).
         */
        public val CardShadow: Shadow =
            Shadow(
                radius = 16.dp,
                spread = 0.dp,
                offset = DpOffset(x = 0.dp, y = 4.dp),
                color = Color.Black.copy(alpha = 0.05f),
            )
        /** Page surface card: 12px corner, inset 4px from the window's right and bottom edges. */
        public val PageSurfaceRadius: Dp = 12.dp
        public val PageSurfaceInset: Dp = 4.dp
        /** Height of the strip band that repaints the card's shadow above the page surface card. */
        public val PageSurfaceShadowBand: Dp = 12.dp
        public val ContentMaxWidth: Dp = 672.dp
        /** Settings General content column measured at 728px in the supplied 2560px capture. */
        public val SettingsContentMaxWidth: Dp = 728.dp
        /** Settings page title inset measured at 73px (30px menu bar plus the 43px content top). */
        public val SettingsPageTopPadding: Dp = 73.dp
        /** Settings card corner radius measured at 12px. */
        public val SettingsCardRadius: Dp = 12.dp
        /**
         * Settings card row content and separator inset measured at 17px from the card edge
         * (card x 1079..1806, row ink and separator x 1096..1789 in the 2560px capture).
         */
        public val SettingsRowInsetX: Dp = 17.dp

        /**
         * The Codex mark above the 新聊天 page's hero heading: the reference's `size-14`, measured 56x56
         * on the live page (the mark's own ink spans 47.5 of that box, the source `<svg>` carrying a
         * 418-unit view box whose cloud reaches 354.9 units).
         */
        public val HomeHeroMark: Dp = 56.dp

        /**
         * The mark's own tone. The reference paints it with `--color-text-default` and then sets
         * `opacity: .3` on the whole element, lifting it to `.4` while pointed at
         * (`[@media(hover:hover)_and_(pointer:fine)]:hover:opacity-40`) rather than changing the ink.
         */
        public val HomeHeroMarkAlpha: Float = 0.3f

        public val HomeHeroMarkHoverAlpha: Float = 0.4f

        /**
         * The dotted rule under the hero heading's project name. The reference underlines it with
         * `decoration-dotted decoration-[1px] underline-offset-4`: scanning the live row of dots at
         * y=664 against a baseline at 660 gives 1px dots on a 2px pitch, running the project's full
         * 163px width.
         */
        public val HomeHeroUnderlineOffset: Dp = 4.dp

        public val HomeHeroUnderlinePitch: Dp = 2.dp

        /**
         * Profile-dropdown menu, from the Codex menu primitive in app.asar
         * (`extracted-app-shared-9d148924be0b.js`: the content class `rounded-2xl
         * p-[var(--app-menu-gutter,var(--spacing))]` and the item class
         * `min-h-[var(--app-menu-item-height)] rounded-xl p-[var(--padding-row-y)_var(--padding-row-x)]`).
         * The measurement note is docs/desktop-ui/reference/account-menu-measurement-2026-10-06.md.
         */
        public val MenuRadius: Dp = 20.dp
        /**
         * 探索 pin menu, measured off the live Codex rail: 228 wide, 20px corner, 4px padding, and
         * its left edge 5px right of the 探索 item (top edge 1px above the item).
         */
        public val ExploreMenuWidth: Dp = 228.dp
        public val ExploreMenuRadius: Dp = 20.dp
        public val ExploreMenuOffsetX: Dp = 5.dp
        public val ExploreMenuOffsetY: Dp = -1.dp
        /** Codex menu content `p-[var(--app-menu-gutter, var(--spacing))]`, 4px on every edge. */
        public val MenuPadding: Dp = 4.dp
        /** Codex menu content `min-w-[180px]`. */
        public val MenuMinWidth: Dp = 180.dp
        /** Harness menu width; kept inside the Codex 180px min / 320px (`--container-xs`) max bound. */
        /** Profile/account dropdown width; the reference measures 228px. */
        public val MenuWidth: Dp = 228.dp
        /** Item `min-h-[--app-menu-item-height]`; the app sets `calc(var(--spacing) * 9)` = 36px. */
        public val MenuItemHeight: Dp = 36.dp
        /** Item `rounded-xl`. */
        public val MenuItemRadius: Dp = 12.dp
        /** Item padding block, Codex `--padding-row-y` on the electron body = `spacing * 1.25` = 5px. */
        public val MenuItemPaddingY: Dp = 5.dp
        /** Item padding inline, Codex `--padding-row-x` = `spacing * 2` = 8px. */
        public val MenuItemPaddingX: Dp = 8.dp
        /** Item leading-icon box `icon-xs` = 16px. */
        public val MenuIcon: Dp = 16.dp
        /** Item icon-to-label gap, Codex `--spacing-menu-item-content` = `spacing * 1.5` = 6px. */
        public val MenuIconGap: Dp = 6.dp
        /** Separator block margin, Codex `--app-menu-separator-gutter` = `spacing` = 4px. */
        public val MenuSeparatorGutter: Dp = 4.dp
        /** Identity header height: avatar row above the first item. */
        public val MenuHeaderHeight: Dp = 48.dp
        /** Identity avatar diameter, matching the rail account chip. */
        public val MenuAvatar: Dp = 24.dp
        /**
         * Dropdown-overlay shadow, Codex `--shadow-dropdown-overlay` = `--shadow-xl-spread` =
         * `0 0 0 .5px <border>, 0px 8px 16px -4px #0000001f`. The .5px ring is the existing menu
         * hairline border, so only the xl blur is applied here.
         */
        public val MenuShadowOffsetY: Dp = 8.dp
        public val MenuShadowBlur: Dp = 16.dp
        public val MenuShadowSpread: Dp = (-4).dp
        /** `#0000001f` = 12.2% black. */
        public val MenuShadowColor: Color = Color(0x1F000000)
    }
}
