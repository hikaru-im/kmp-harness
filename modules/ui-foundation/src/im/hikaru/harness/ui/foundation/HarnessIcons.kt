package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Semantic icon names used by the shared UI. Icons are vector drawn, never text glyphs or emoji. */
public enum class HarnessIconName {
    Menu,
    ChevronLeft,
    ChevronRight,
    ChevronDown,
    Plus,
    Search,
    Settings,
    User,
    Host,
    Send,
    Stop,
    Attach,
    More,
    Refresh,
    Share,
    Copy,
    Warning,
    Check,
    Close,
    Home,
    /** Solid house drawn when Home is the selected rail destination. */
    HomeFilled,
    Clock,
    /** Solid clock drawn when Recent is the selected rail destination. */
    ClockFilled,
    Folder,
    /** Solid folder drawn when Projects is the selected rail destination. */
    FolderFilled,
    /** Plug and cable, the artwork Codex uses for its Plugins rail destination. */
    Plug,
    /** Solid plug drawn when Plugins is the selected rail destination. */
    PlugFilled,
    /**
     * Three vertical bars, the artwork Codex uses for its Library rail destination
     * ([sidebar-library-icon]). Codex ships no separate solid variant, so selection keeps this outline.
     */
    Library,
    /**
     * Two overlapping photo frames, the artwork Codex uses for its Images rail destination
     * ([sidebar-images-icon]). Codex ships no separate solid variant, so selection keeps this outline.
     */
    Images,
    Pin,
    Archive,
    /** Notification bell header action of the workspace navigator. */
    Bell,
    /**
     * Pencil in a rounded square, the artwork Codex uses for its New Chat row
     * (`CGPT_Icon_NewChatPencil`, `sidebar-new-chat-icon`).
     */
    NewChat,
    /** Sign-out glyph for the profile-dropdown log-out item (bracket plus an arrow leaving it). */
    SignOut,
    /** Arrow leaving its box at the top right; the Settings capture's external-link row marker. */
    ExternalLink,
    PanelRight,
    /**
     * Long-shafted return arrow, the artwork the live Codex tab bar draws for 返回
     * (`M6.96231 2.96214…` on its 16-unit grid). [ArrowRight] is the same artwork mirrored, which is
     * what Codex does for 前进 — it ships one path and flips the transform.
     */
    ArrowLeft,
    ArrowRight,
    /**
     * Rounded rectangle with a flush left panel divider, the live Codex 隐藏侧边栏 control
     * (16-unit grid, `fill-rule: evenodd`).
     */
    SidebarToggle,
    /**
     * Rounded rectangle cut by a centred vertical divider, the live Codex 隐藏标签页 control — the same
     * artwork the tab-strip toggle draws (16-unit grid, `fill-rule: evenodd`).
     */
    SplitView,
    /**
     * Two opposing corner brackets, the live Codex 进入完整视图 control: the bottom-left and top-right
     * arrowheads of a 16-unit grid, drawn as two closed subpaths.
     */
    FullView,
    /**
     * Rounded square with a plus inside, the live Codex 新建标签页 control — the tab strip's plus while
     * the side panel is closed, and the strip's own trailing plus while it is open. Not [Plus]: the live
     * glyph is the plus *inside a panel outline*, and the outline is what tells it apart from a plain
     * 添加 at a glance.
     */
    PanelAdd,
    /** Three stacked, shortening bars (`filter_list`), the task-filter control's glyph. */
    Filter,
    /**
     * Three flat dots in a row, the glyph the rail 探索 slot uses. Measured from the live Codex rail
     * (`svg[viewBox="0 0 20 20"]` on the 探索 button): three filled circles of radius ~1.5 at y=10,
     * centred on x = 4.17 / 10 / 15.83.
     */
    Explore,
    /**
     * The Codex 项目 glyph: a rounded box with a tab slot at the top-left and a horizontal shelf.
     * Transcribed from the live 探索 menu row's 16x16 leading icon path.
     */
    Projects,
    /**
     * Pushpin (thumbtack), the 探索 menu row's trailing pin control. Transcribed from the live 16x16
     * pin button path; the outline is the un-pinned state.
     */
    Pushpin,
    /** Solid pushpin, the pinned state of [Pushpin]. */
    PushpinFilled,
    /** Solid three-bar library glyph, the selected state of [Library]. */
    LibraryFilled,
    /** Solid project box, the selected state of [Projects]. */
    ProjectsFilled,
}

@Composable
public fun HarnessIcon(
    name: HarnessIconName,
    modifier: Modifier = Modifier,
    tint: Color = HarnessTokens.Colors.Text,
    size: Dp = HarnessTokens.Control.Icon,
    glyphScale: Float = 1f,
    strokeRatio: Float = DEFAULT_ICON_STROKE_RATIO,
) {
    Canvas(modifier = modifier.size(size)) {
        val box = this.size.minDimension
        val artwork = box * glyphScale
        val stroke =
            Stroke(
                width = artwork * strokeRatio,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
        // The shared artwork fills 0.64 of its reference square; translating + scaling lets a caller
        // render the same glyph at a wider extent (the Codex rail glyphs fill 16 of 20px).
        translate((box - artwork) / 2f, (box - artwork) / 2f) {
            drawHarnessIcon(name, tint, artwork, stroke)
        }
    }
}

/**
 * Pairs an outline destination glyph with the solid variant Codex draws when that destination is
 * selected (`railIcons.selected` in app.asar). Names without a solid variant return themselves.
 */
public fun HarnessIconName.filledVariant(): HarnessIconName =
    when (this) {
        HarnessIconName.Home -> HarnessIconName.HomeFilled
        HarnessIconName.Clock -> HarnessIconName.ClockFilled
        HarnessIconName.Folder -> HarnessIconName.FolderFilled
        HarnessIconName.Plug -> HarnessIconName.PlugFilled
        HarnessIconName.Library -> HarnessIconName.LibraryFilled
        HarnessIconName.Projects -> HarnessIconName.ProjectsFilled
        HarnessIconName.Pushpin -> HarnessIconName.PushpinFilled
        else -> this
    }

private const val DEFAULT_ICON_STROKE_RATIO: Float = 0.095f
/**
 * Home artwork bounds on the Codex 20-unit icon grid, shared by [HarnessIconName.Home] and
 * [HarnessIconName.HomeFilled].
 *
 * Home has no `app.asar` icon chunk (see docs/desktop-ui/reference/codex-rail-icons.md), so these
 * bounds are matched to the user's Codex rail screenshot and the rail component sheet: every rail
 * glyph fills the 2.5..17.5 grid extent, and a stroked glyph then reaches the 16-of-20px rail extent.
 * The outline and the solid variant share one silhouette so selection does not change the shape.
 */
public object HomeGlyphGrid {
    public const val LEFT: Float = 2.5f
    public const val RIGHT: Float = 17.5f
    public const val TOP: Float = 2.5f
    public const val BOTTOM: Float = 17.5f
    /** Grid span of the artwork on both axes. */
    public const val SPAN: Float = RIGHT - LEFT
    /** Roof eave line: where the roof meets the walls. */
    public const val EAVE: Float = 8.1f
    public const val DOOR_LEFT: Float = 8.6f
    public const val DOOR_RIGHT: Float = 11.4f
    public const val DOOR_TOP: Float = 10.6f
    /** Bottom-corner radius of the body. */
    public const val CORNER: Float = 1.4f
}

/**
 * Rail glyphs are authored on the Codex 20x20 icon grid so their proportions survive a redraw.
 * The coordinates come from the icon bundles in `app.asar` (`sidebar-projects-icon`,
 * `sidebar-tasks-icon`, `sidebar-plugins-icon`); the extraction and the per-glyph comparison are in
 * docs/desktop-ui/reference/codex-rail-icons.md.
 *
 * [HarnessIcon] draws into an artwork box of `box * glyphScale`, not into the 20-unit grid, so every
 * grid value is mapped through this helper. The rail renders at 23 units (the 20-unit grid plus a
 * 1.5-unit bleed on each side), and at that scale [at] reproduces the grid coordinate exactly.
 */
private class CodexGrid(min: Float) : CodexUnit {
    private val unit = min / (CODEX_ICON_GRID + CODEX_ICON_BLEED * 2f)

    /** Grid coordinate, including the bleed that keeps a full-bleed glyph inside the artwork box. */
    override fun at(coordinate: Float): Float = (coordinate + CODEX_ICON_BLEED) * unit

    /** Grid length; unlike [at] it is not shifted by the bleed. */
    fun len(length: Float): Float = length * unit
}

/** One Codex icon grid, mapping a path coordinate onto the artwork box. */
private interface CodexUnit {
    fun at(coordinate: Float): Float
}

/**
 * The Codex controls whose `<svg>` carries `viewBox="0 0 16 16"` (the title-bar arrows, the panel and
 * split-view boxes, the plus) have no bleed: the artwork is drawn straight onto the 16-unit box, so a
 * path coordinate maps by a plain `min / 16` scale. Transcribing those paths through [CodexGrid]
 * would inset them by the 1.5-unit bleed and render them ~15% small.
 */
private class CodexGrid16(min: Float) : CodexUnit {
    private val unit = min / 16f

    override fun at(coordinate: Float): Float = coordinate * unit
}

/**
 * Writes a transcribed `<path d="…">` onto an icon box. The coordinate overloads take the numbers
 * exactly as they appear in the live SVG, so the glyph can be diffed against its source by eye
 * instead of against a wall of `grid.at(…)` calls.
 */
private class CodexArtwork(private val grid: CodexUnit) {
    val path: Path = Path()

    fun moveTo(x: Float, y: Float): CodexArtwork = apply { path.moveTo(grid.at(x), grid.at(y)) }
    fun lineTo(x: Float, y: Float): CodexArtwork = apply { path.lineTo(grid.at(x), grid.at(y)) }
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float): CodexArtwork =
        apply { path.cubicTo(grid.at(x1), grid.at(y1), grid.at(x2), grid.at(y2), grid.at(x3), grid.at(y3)) }
    fun close(): CodexArtwork = apply { path.close() }

    /** `fill-rule: evenodd`, which the Codex panel and split-view boxes use to cut their dividers out. */
    fun evenOdd(): CodexArtwork = apply { path.fillType = PathFillType.EvenOdd }
}

private fun artwork16(min: Float): CodexArtwork = CodexArtwork(CodexGrid16(min))
private fun artwork20(min: Float): CodexArtwork = CodexArtwork(CodexGrid(min))

/**
 * The live tab bar's 返回 glyph, transcribed from the 16-unit path it ships. 前进 is the same artwork
 * mirrored onto the vertical centre line rather than a second path, which is what Codex does too.
 */
private fun returnArrow(min: Float): Path = artwork16(min).apply {
    moveTo(6.96231f, 2.96214f)
    cubicTo(7.16727f, 2.7574f, 7.49953f, 2.75742f, 7.7045f, 2.96214f)
    cubicTo(7.90913f, 3.16712f, 7.9092f, 3.49939f, 7.7045f, 3.70433f)
    lineTo(3.93301f, 7.47483f)
    lineTo(13.3334f, 7.47483f)
    cubicTo(13.6232f, 7.47501f, 13.8578f, 7.71038f, 13.8578f, 8.00022f)
    cubicTo(13.8577f, 8.28999f, 13.6232f, 8.52544f, 13.3334f, 8.52562f)
    lineTo(3.93301f, 8.52562f)
    lineTo(7.7045f, 12.2951f)
    cubicTo(7.90924f, 12.5001f, 7.90909f, 12.8323f, 7.7045f, 13.0373f)
    cubicTo(7.49953f, 13.2423f, 7.16735f, 13.2422f, 6.96231f, 13.0373f)
    lineTo(2.53067f, 8.60667f)
    lineTo(2.47208f, 8.54124f)
    cubicTo(2.21571f, 8.22666f, 2.21566f, 7.77279f, 2.47208f, 7.45823f)
    lineTo(2.53067f, 7.3928f)
    close()
}.path

private const val CODEX_ICON_GRID: Float = 20f
private const val CODEX_ICON_BLEED: Float = 1.5f

private fun DrawScope.drawHarnessIcon(
    name: HarnessIconName,
    color: Color,
    min: Float,
    stroke: Stroke,
) {
    val left = min * 0.18f
    val right = min * 0.82f
    val center = min * 0.5f

    when (name) {
        HarnessIconName.Menu -> {
            listOf(0.28f, 0.5f, 0.72f).forEach { y ->
                drawLine(color, Offset(left, min * y), Offset(right, min * y), stroke.width, StrokeCap.Round)
            }
        }
        HarnessIconName.Filter -> {
            drawLine(color, Offset(left, min * 0.33f), Offset(right, min * 0.33f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(min * 0.32f, min * 0.5f), Offset(min * 0.68f, min * 0.5f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(min * 0.44f, min * 0.67f), Offset(min * 0.56f, min * 0.67f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.ChevronLeft -> drawPath(pathOf(min, 0.60f, 0.27f, 0.40f, 0.50f, 0.50f, 0.60f, 0.73f), color, style = stroke)
        HarnessIconName.ChevronRight -> drawPath(pathOf(min, 0.40f, 0.27f, 0.60f, 0.50f, 0.50f, 0.40f, 0.73f), color, style = stroke)
        HarnessIconName.ChevronDown -> drawPath(pathOf(min, 0.27f, 0.40f, 0.50f, 0.60f, 0.73f, 0.40f), color, style = stroke)
        HarnessIconName.Plus -> {
            // Codex's own plus: a filled outline of four 1.05-unit arms with round joins, spanning
            // 2.14..13.86 of the 16-unit grid. A fill, not two strokes, so the measured arm width
            // (1.05 of 16 — about 1px at the 16px display size) is not lost to the stroke ratio.
            drawPath(
                artwork16(min).apply {
                    moveTo(8.00049f, 2.14111f)
                    cubicTo(8.29021f, 2.14138f, 8.52588f, 2.37672f, 8.52588f, 2.6665f)
                    lineTo(8.52588f, 7.4751f)
                    lineTo(13.3335f, 7.4751f)
                    cubicTo(13.6233f, 7.47527f, 13.8579f, 7.71065f, 13.8579f, 8.00049f)
                    cubicTo(13.8576f, 8.29011f, 13.6231f, 8.5257f, 13.3335f, 8.52588f)
                    lineTo(8.52588f, 8.52588f)
                    lineTo(8.52588f, 13.3335f)
                    cubicTo(8.5257f, 13.6231f, 8.29011f, 13.8576f, 8.00049f, 13.8579f)
                    cubicTo(7.71065f, 13.8579f, 7.47527f, 13.6233f, 7.4751f, 13.3335f)
                    lineTo(7.4751f, 8.52588f)
                    lineTo(2.6665f, 8.52588f)
                    cubicTo(2.37672f, 8.52588f, 2.14138f, 8.29021f, 2.14111f, 8.00049f)
                    cubicTo(2.14111f, 7.71054f, 2.37655f, 7.4751f, 2.6665f, 7.4751f)
                    lineTo(7.4751f, 7.4751f)
                    lineTo(7.4751f, 2.6665f)
                    cubicTo(7.4751f, 2.37655f, 7.71054f, 2.14111f, 8.00049f, 2.14111f)
                    close()
                }.path,
                color,
            )
        }
        HarnessIconName.Search -> {
            drawCircle(color, radius = min * 0.28f, center = Offset(min * 0.43f, min * 0.43f), style = stroke)
            drawLine(color, Offset(min * 0.63f, min * 0.63f), Offset(min * 0.80f, min * 0.80f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.Settings -> {
            drawCircle(color, radius = min * 0.22f, center = Offset(center, center), style = stroke)
            repeat(8) { index ->
                val angle = (index * 45f) * kotlin.math.PI.toFloat() / 180f
                val inner = min * 0.36f
                val outer = min * 0.46f
                drawLine(
                    color,
                    Offset(center + kotlin.math.cos(angle) * inner, center + kotlin.math.sin(angle) * inner),
                    Offset(center + kotlin.math.cos(angle) * outer, center + kotlin.math.sin(angle) * outer),
                    stroke.width,
                    StrokeCap.Round,
                )
            }
        }
        HarnessIconName.User -> {
            drawCircle(color, radius = min * 0.16f, center = Offset(center, min * 0.31f), style = stroke)
            drawArc(
                color,
                205f,
                130f,
                false,
                topLeft = Offset(min * 0.23f, min * 0.38f),
                size = Size(min * 0.54f, min * 0.42f),
                style = stroke,
            )
        }
        HarnessIconName.Host -> {
            drawRoundRect(color, Offset(min * 0.16f, min * 0.18f), Size(min * 0.68f, min * 0.48f), cornerRadius = CornerRadius(min * 0.05f), style = stroke)
            drawLine(color, Offset(min * 0.36f, min * 0.79f), Offset(min * 0.64f, min * 0.79f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(center, min * 0.66f), Offset(center, min * 0.79f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.Send -> {
            val path = Path().apply {
                moveTo(min * 0.18f, min * 0.22f)
                lineTo(min * 0.82f, center)
                lineTo(min * 0.18f, min * 0.78f)
                lineTo(min * 0.36f, center)
                close()
            }
            drawPath(path, color, style = stroke)
            drawLine(color, Offset(min * 0.36f, center), Offset(min * 0.82f, center), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.Stop -> drawRoundRect(color, Offset(min * 0.26f, min * 0.26f), Size(min * 0.48f, min * 0.48f), cornerRadius = CornerRadius(min * 0.05f), style = Fill)
        HarnessIconName.Attach -> {
            val path = Path().apply {
                moveTo(min * 0.62f, min * 0.30f)
                lineTo(min * 0.38f, min * 0.54f)
                cubicTo(min * 0.22f, min * 0.70f, min * 0.47f, min * 0.92f, min * 0.64f, min * 0.75f)
                lineTo(min * 0.78f, min * 0.61f)
            }
            drawPath(path, color, style = stroke)
            drawPath(pathOf(min, 0.54f, 0.36f, 0.38f, 0.52f, 0.68f, 0.48f, 0.74f), color, style = stroke)
        }
        HarnessIconName.More -> listOf(0.28f, 0.5f, 0.72f).forEach { x -> drawCircle(color, min * 0.065f, Offset(min * x, center)) }
        HarnessIconName.Refresh -> {
            drawArc(color, 35f, 270f, false, topLeft = Offset(min * 0.2f, min * 0.2f), size = Size(min * 0.6f, min * 0.6f), style = stroke)
            drawPath(pathOf(min, 0.68f, 0.22f, 0.78f, 0.22f, 0.78f, 0.34f), color, style = stroke)
        }
        HarnessIconName.Share -> {
            drawLine(color, Offset(min * 0.34f, min * 0.48f), Offset(min * 0.66f, min * 0.30f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(min * 0.34f, min * 0.52f), Offset(min * 0.66f, min * 0.70f), stroke.width, StrokeCap.Round)
            listOf(Offset(min * 0.27f, center), Offset(min * 0.73f, min * 0.24f), Offset(min * 0.73f, min * 0.76f)).forEach { drawCircle(color, min * 0.10f, it, style = stroke) }
        }
        HarnessIconName.Copy -> {
            drawRoundRect(color, Offset(min * 0.29f, min * 0.18f), Size(min * 0.47f, min * 0.53f), cornerRadius = CornerRadius(min * 0.05f), style = stroke)
            drawRoundRect(color, Offset(min * 0.18f, min * 0.30f), Size(min * 0.47f, min * 0.53f), cornerRadius = CornerRadius(min * 0.05f), style = stroke)
        }
        HarnessIconName.Warning -> {
            val path = Path().apply {
                moveTo(center, min * 0.16f)
                lineTo(min * 0.84f, min * 0.80f)
                lineTo(min * 0.16f, min * 0.80f)
                close()
            }
            drawPath(path, color, style = stroke)
            drawLine(color, Offset(center, min * 0.38f), Offset(center, min * 0.59f), stroke.width, StrokeCap.Round)
            drawCircle(color, min * 0.035f, Offset(center, min * 0.70f))
        }
        HarnessIconName.Check -> drawPath(pathOf(min, 0.20f, 0.52f, 0.42f, 0.72f, 0.80f, 0.28f, 0.80f, 0.52f), color, style = stroke)
        HarnessIconName.Close -> {
            // The tab-close cross from the live tab bar: a filled outline on the 20-unit grid,
            // spanning 4.88..15.92 with ~1.2-unit arms (0.84px at the 14px display size).
            drawPath(
                artwork20(min).apply {
                    moveTo(14.6549f, 5.57307f)
                    cubicTo(14.9283f, 5.2997f, 15.3718f, 5.2997f, 15.6451f, 5.57307f)
                    cubicTo(15.9185f, 5.84643f, 15.9185f, 6.28993f, 15.6451f, 6.5633f)
                    lineTo(11.3903f, 10.8182f)
                    lineTo(15.6451f, 15.0731f)
                    lineTo(15.735f, 15.1834f)
                    cubicTo(15.9141f, 15.4551f, 15.8842f, 15.8242f, 15.6451f, 16.0633f)
                    cubicTo(15.4061f, 16.3024f, 15.0369f, 16.3322f, 14.7653f, 16.1531f)
                    lineTo(14.6549f, 16.0633f)
                    lineTo(10.4f, 11.8084f)
                    lineTo(6.14515f, 16.0633f)
                    cubicTo(5.87178f, 16.3367f, 5.42828f, 16.3367f, 5.15492f, 16.0633f)
                    cubicTo(4.88155f, 15.7899f, 4.88155f, 15.3464f, 5.15492f, 15.0731f)
                    lineTo(9.4098f, 10.8182f)
                    lineTo(5.15492f, 6.5633f)
                    lineTo(5.06507f, 6.45295f)
                    cubicTo(4.88597f, 6.18128f, 4.91584f, 5.81214f, 5.15492f, 5.57307f)
                    cubicTo(5.39399f, 5.33399f, 5.76313f, 5.30413f, 6.0348f, 5.48322f)
                    lineTo(6.14515f, 5.57307f)
                    lineTo(10.4f, 9.82795f)
                    lineTo(14.6549f, 5.57307f)
                    close()
                }.path,
                color,
            )
        }
        HarnessIconName.Home -> {
            // Home has no app.asar icon chunk; the silhouette follows the Codex rail screenshot and
            // the rail component sheet, on the same 2.5..17.5 grid extent as the other rail glyphs.
            val g = CodexGrid(min)
            val left = HomeGlyphGrid.LEFT
            val right = HomeGlyphGrid.RIGHT
            val top = HomeGlyphGrid.TOP
            val bottom = HomeGlyphGrid.BOTTOM
            val eave = HomeGlyphGrid.EAVE
            val corner = HomeGlyphGrid.CORNER
            drawPath(
                Path().apply {
                    moveTo(g.at(left), g.at(eave))
                    lineTo(g.at(10f), g.at(top))
                    lineTo(g.at(right), g.at(eave))
                },
                color,
                style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(g.at(left), g.at(eave))
                    lineTo(g.at(left), g.at(bottom - corner))
                    cubicTo(
                        g.at(left),
                        g.at(bottom - corner * 0.45f),
                        g.at(left + corner * 0.45f),
                        g.at(bottom),
                        g.at(left + corner),
                        g.at(bottom),
                    )
                    lineTo(g.at(HomeGlyphGrid.DOOR_LEFT), g.at(bottom))
                    lineTo(g.at(HomeGlyphGrid.DOOR_LEFT), g.at(HomeGlyphGrid.DOOR_TOP))
                    lineTo(g.at(HomeGlyphGrid.DOOR_RIGHT), g.at(HomeGlyphGrid.DOOR_TOP))
                    lineTo(g.at(HomeGlyphGrid.DOOR_RIGHT), g.at(bottom))
                    lineTo(g.at(right - corner), g.at(bottom))
                    cubicTo(
                        g.at(right - corner * 0.45f),
                        g.at(bottom),
                        g.at(right),
                        g.at(bottom - corner * 0.45f),
                        g.at(right),
                        g.at(bottom - corner),
                    )
                    lineTo(g.at(right), g.at(eave))
                },
                color,
                style = stroke,
            )
        }
        HarnessIconName.HomeFilled -> {
            val g = CodexGrid(min)
            val left = HomeGlyphGrid.LEFT
            val right = HomeGlyphGrid.RIGHT
            val top = HomeGlyphGrid.TOP
            val bottom = HomeGlyphGrid.BOTTOM
            val eave = HomeGlyphGrid.EAVE
            val corner = HomeGlyphGrid.CORNER
            val doorLeft = HomeGlyphGrid.DOOR_LEFT
            val doorRight = HomeGlyphGrid.DOOR_RIGHT
            val doorTop = HomeGlyphGrid.DOOR_TOP
            val path =
                Path().apply {
                    moveTo(g.at(10f), g.at(top))
                    lineTo(g.at(right), g.at(eave))
                    lineTo(g.at(right), g.at(bottom - corner))
                    cubicTo(
                        g.at(right),
                        g.at(bottom - corner * 0.45f),
                        g.at(right - corner * 0.45f),
                        g.at(bottom),
                        g.at(right - corner),
                        g.at(bottom),
                    )
                    lineTo(g.at(doorRight), g.at(bottom))
                    lineTo(g.at(doorRight), g.at(doorTop))
                    lineTo(g.at(doorLeft), g.at(doorTop))
                    lineTo(g.at(doorLeft), g.at(bottom))
                    lineTo(g.at(left + corner), g.at(bottom))
                    cubicTo(
                        g.at(left + corner * 0.45f),
                        g.at(bottom),
                        g.at(left),
                        g.at(bottom - corner * 0.45f),
                        g.at(left),
                        g.at(bottom - corner),
                    )
                    lineTo(g.at(left), g.at(eave))
                    close()
                }
            drawPath(path, color, style = Fill)
        }
        HarnessIconName.Clock -> {
            // Codex's Tasks glyph: a 7.5-radius face with hands at 12 and about 7:30.
            val g = CodexGrid(min)
            val pivot = Offset(g.at(11.05f), g.at(11.05f))
            drawCircle(color, radius = g.len(7.5f), center = Offset(g.at(10f), g.at(10f)), style = stroke)
            drawLine(color, pivot, Offset(g.at(11.05f), g.at(6.9f)), stroke.width, StrokeCap.Round)
            drawLine(color, pivot, Offset(g.at(8.95f), g.at(13.15f)), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.ClockFilled -> {
            // Solid disc with the two hands punched out through an even-odd fill.
            val g = CodexGrid(min)
            val pivot = Offset(g.at(11.05f), g.at(11.05f))
            val hand = g.len(0.72f)
            val face =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(
                        Rect(
                            Offset(g.at(10f) - g.len(7.5f), g.at(10f) - g.len(7.5f)),
                            Size(g.len(15f), g.len(15f)),
                        ),
                    )
                    addRect(Rect(Offset(pivot.x - hand / 2f, g.at(6.9f)), Size(hand, pivot.y - g.at(6.9f))))
                    slot(pivot, Offset(g.at(8.95f), g.at(13.15f)), hand)
                }
            drawPath(face, color, style = Fill)
        }
        HarnessIconName.Folder -> {
            // Codex draws the Projects folder as two paths: the body and the tabbed top edge.
            val g = CodexGrid(min)
            drawPath(folderBodyPath(g), color, style = stroke)
            drawPath(folderTopPath(g), color, style = stroke)
        }
        HarnessIconName.FolderFilled -> {
            val g = CodexGrid(min)
            drawPath(folderBodyPath(g), color, style = Fill)
            drawPath(folderTopPath(g), color, style = stroke)
        }
        HarnessIconName.Plug -> {
            // Codex's Plugins glyph is a plug body with two pins inside a cable loop.
            val g = CodexGrid(min)
            drawPath(plugCablePath(g), color, style = stroke)
            drawPath(plugBodyPath(g), color, style = stroke)
            drawPath(plugPinPath(g), color, style = stroke)
        }
        HarnessIconName.PlugFilled -> {
            val g = CodexGrid(min)
            drawPath(plugCablePath(g), color, style = stroke)
            drawPath(plugBodyPath(g), color, style = Fill)
            drawPath(plugPinPath(g), color, style = stroke)
        }
        HarnessIconName.Library -> {
            // Codex sidebar-library-icon: three vertical bars. The first two share the x=6.8 edge,
            // so their inner corners meet at a pinched waist; the third is the same bar rotated -10
            // degrees about its centre (14.93, 10) on the 20-unit grid.
            val g = CodexGrid(min)
            drawCodexBar(g, stroke, color, left = 2.22f, top = 3.33f, width = 4.58f, height = 13.34f)
            drawCodexBar(g, stroke, color, left = 6.8f, top = 3.33f, width = 4.59f, height = 13.34f)
            rotate(degrees = -10f, pivot = Offset(g.at(14.93f), g.at(10f))) {
                drawCodexBar(g, stroke, color, left = 12.64f, top = 3.33f, width = 4.58f, height = 13.34f)
            }
        }
        HarnessIconName.LibraryFilled -> {
            // Selected state keeps the same three-bar silhouette and fills the bars.
            val g = CodexGrid(min)
            drawCodexBar(g, stroke, color, left = 2.22f, top = 3.33f, width = 4.58f, height = 13.34f, style = Fill)
            drawCodexBar(g, stroke, color, left = 6.8f, top = 3.33f, width = 4.59f, height = 13.34f, style = Fill)
            rotate(degrees = -10f, pivot = Offset(g.at(14.93f), g.at(10f))) {
                drawCodexBar(g, stroke, color, left = 12.64f, top = 3.33f, width = 4.58f, height = 13.34f, style = Fill)
            }
        }
        HarnessIconName.Images -> {
            // Codex sidebar-images-icon: a back frame, a front frame tilted -8 degrees about its
            // centre (7.368, 12.159), and the mountain line inside the front frame.
            val g = CodexGrid(min)
            drawCodexBar(g, stroke, color, left = 7.14f, top = 2.37f, width = 10.83f, height = 10.84f, radius = 2.36f)
            rotate(degrees = -8f, pivot = Offset(g.at(7.368f), g.at(12.159f))) {
                drawCodexBar(g, stroke, color, left = 1.988f, top = 6.779f, width = 10.76f, height = 10.76f, radius = 2.3f)
            }
            drawPath(mountainPath(g), color, style = stroke)
        }
        HarnessIconName.Pin -> {
            drawCircle(color, radius = min * 0.15f, center = Offset(min * 0.36f, min * 0.34f), style = stroke)
            drawLine(color, Offset(min * 0.46f, min * 0.44f), Offset(min * 0.78f, min * 0.76f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.Explore ->
            // Three flat dots, the measured 探索 glyph: radius ~1.5 of the 20-unit grid, on the
            // horizontal centre line, at x = 4.17 / 10 / 15.83.
            listOf(0.2085f, 0.5f, 0.7915f).forEach { x ->
                drawCircle(color, radius = min * 0.075f, center = Offset(min * x, center))
            }
        HarnessIconName.Projects ->
            drawPath(
                projectsBoxPath(min).apply { fillType = PathFillType.EvenOdd },
                color,
            )
        HarnessIconName.ProjectsFilled ->
            drawPath(projectsBoxPath(min), color)

        HarnessIconName.Pushpin ->
            drawPath(pushpinPath(min), color, style = stroke)
        HarnessIconName.PushpinFilled ->
            drawPath(pushpinPath(min), color)

        HarnessIconName.Archive -> {
            drawRoundRect(
                color,
                Offset(min * 0.16f, min * 0.24f),
                Size(min * 0.68f, min * 0.14f),
                cornerRadius = CornerRadius(min * 0.03f),
                style = stroke,
            )
            drawPath(pathOf(min, 0.22f, 0.42f, 0.22f, 0.80f, 0.78f, 0.80f, 0.78f, 0.42f), color, style = stroke)
            drawLine(color, Offset(min * 0.42f, min * 0.56f), Offset(min * 0.58f, min * 0.56f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.Bell -> {
            // Notification bell: rounded dome, flared skirt, flat base and a clapper arc.
            val g = CodexGrid(min)
            drawPath(
                Path().apply {
                    moveTo(g.at(4.9f), g.at(14.0f))
                    cubicTo(g.at(5.7f), g.at(13.3f), g.at(6.2f), g.at(12.3f), g.at(6.2f), g.at(11.2f))
                    lineTo(g.at(6.2f), g.at(8.3f))
                    cubicTo(g.at(6.2f), g.at(6.0f), g.at(7.9f), g.at(4.4f), g.at(10f), g.at(4.4f))
                    cubicTo(g.at(12.1f), g.at(4.4f), g.at(13.8f), g.at(6.0f), g.at(13.8f), g.at(8.3f))
                    lineTo(g.at(13.8f), g.at(11.2f))
                    cubicTo(g.at(13.8f), g.at(12.3f), g.at(14.3f), g.at(13.3f), g.at(15.1f), g.at(14.0f))
                    lineTo(g.at(4.9f), g.at(14.0f))
                },
                color,
                style = stroke,
            )
            drawPath(
                Path().apply {
                    moveTo(g.at(8.7f), g.at(15.6f))
                    cubicTo(g.at(9.1f), g.at(16.6f), g.at(10.9f), g.at(16.6f), g.at(11.3f), g.at(15.6f))
                },
                color,
                style = stroke,
            )
        }
        HarnessIconName.NewChat -> {
            // CGPT_Icon_NewChatPencil, transcribed from the 16-unit `<svg>` the live sidebar rows and
            // the navigator header ship. Two filled paths, not strokes: the square is a ring whose
            // top-right corner is cut away *parallel to the pencil*, so the pencil's shaft leaves
            // through the gap, and the pencil itself is an outline with its own inner contour.
            drawPath(
                artwork16(min).apply {
                    moveTo(6.33325f, 1.80763f)
                    cubicTo(6.62314f, 1.80763f, 6.85855f, 2.04315f, 6.85864f, 2.33302f)
                    cubicTo(6.85864f, 2.62297f, 6.6232f, 2.85841f, 6.33325f, 2.85841f)
                    lineTo(4.66626f, 2.85841f)
                    cubicTo(3.66786f, 2.85859f, 2.85891f, 3.66765f, 2.85864f, 4.66603f)
                    lineTo(2.85864f, 11.333f)
                    cubicTo(2.85864f, 12.3316f, 3.66769f, 13.1414f, 4.66626f, 13.1416f)
                    lineTo(11.3333f, 13.1416f)
                    cubicTo(12.332f, 13.1416f, 13.1418f, 12.3317f, 13.1418f, 11.333f)
                    lineTo(13.1418f, 9.66603f)
                    cubicTo(13.1421f, 9.37642f, 13.3766f, 9.14179f, 13.6663f, 9.14161f)
                    cubicTo(13.956f, 9.14161f, 14.1914f, 9.37631f, 14.1917f, 9.66603f)
                    lineTo(14.1917f, 11.333f)
                    cubicTo(14.1917f, 12.9116f, 12.9119f, 14.1914f, 11.3333f, 14.1914f)
                    lineTo(4.66626f, 14.1914f)
                    cubicTo(3.0878f, 14.1912f, 1.80786f, 12.9115f, 1.80786f, 11.333f)
                    lineTo(1.80786f, 4.66603f)
                    cubicTo(1.80813f, 3.08775f, 3.08796f, 1.80781f, 4.66626f, 1.80763f)
                    lineTo(6.33325f, 1.80763f)
                    close()
                }.path,
                color,
            )
            drawPath(
                artwork16(min).apply {
                    evenOdd()
                    moveTo(10.842f, 2.32228f)
                    cubicTo(11.6259f, 1.55049f, 12.8863f, 1.55435f, 13.6643f, 2.33204f)
                    cubicTo(14.4442f, 3.11205f, 14.4469f, 4.37706f, 13.6702f, 5.16017f)
                    lineTo(9.41626f, 9.4463f)
                    cubicTo(9.05652f, 9.80879f, 8.59956f, 10.0601f, 8.10083f, 10.1699f)
                    lineTo(6.19263f, 10.5899f)
                    cubicTo(5.7111f, 10.6958f, 5.28165f, 10.2666f, 5.38794f, 9.78517f)
                    lineTo(5.80884f, 7.88185f)
                    cubicTo(5.9196f, 7.38036f, 6.17342f, 6.92096f, 6.53931f, 6.56056f)
                    lineTo(10.842f, 2.32228f)
                    close()
                    moveTo(12.9221f, 3.07521f)
                    cubicTo(12.552f, 2.7051f, 11.9524f, 2.70322f, 11.5793f, 3.07033f)
                    lineTo(7.27563f, 7.30861f)
                    cubicTo(7.05429f, 7.52663f, 6.90125f, 7.80504f, 6.83423f, 8.10841f)
                    lineTo(6.54028f, 9.43849f)
                    lineTo(7.87524f, 9.14454f)
                    cubicTo(8.17687f, 9.07807f, 8.45355f, 8.92625f, 8.67114f, 8.70704f)
                    lineTo(12.9241f, 4.41993f)
                    cubicTo(13.2934f, 4.04741f, 13.2928f, 3.44631f, 12.9221f, 3.07521f)
                    close()
                }.path,
                color,
            )
        }
        HarnessIconName.ArrowLeft -> drawPath(returnArrow(min), color)
        HarnessIconName.ArrowRight ->
            // Codex ships one return-arrow path and mirrors the transform for 前进.
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(center, center)) { drawPath(returnArrow(min), color) }
        HarnessIconName.SidebarToggle -> drawPath(
            artwork16(min).apply {
                evenOdd()
                moveTo(11.5f, 2.30762f)
                cubicTo(13.1707f, 2.30762f, 14.5254f, 3.66235f, 14.5254f, 5.33301f)
                lineTo(14.5254f, 10.666f)
                cubicTo(14.5254f, 12.3367f, 13.1707f, 13.6914f, 11.5f, 13.6914f)
                lineTo(4.5f, 13.6914f)
                cubicTo(2.82934f, 13.6914f, 1.47461f, 12.3367f, 1.47461f, 10.666f)
                lineTo(1.47461f, 5.33301f)
                cubicTo(1.47461f, 3.66235f, 2.82934f, 2.30762f, 4.5f, 2.30762f)
                close()
                moveTo(6.52539f, 12.6416f)
                lineTo(11.5f, 12.6416f)
                cubicTo(12.5908f, 12.6416f, 13.4746f, 11.7568f, 13.4746f, 10.666f)
                lineTo(13.4746f, 5.33301f)
                cubicTo(13.4746f, 4.24225f, 12.5908f, 3.3584f, 11.5f, 3.3584f)
                lineTo(6.52539f, 3.3584f)
                close()
                moveTo(4.5f, 3.3584f)
                cubicTo(3.40924f, 3.3584f, 2.52539f, 4.24225f, 2.52539f, 5.33301f)
                lineTo(2.52539f, 10.666f)
                cubicTo(2.52539f, 11.7568f, 3.40924f, 12.6416f, 4.5f, 12.6416f)
                lineTo(5.47461f, 12.6416f)
                lineTo(5.47461f, 3.3584f)
                close()
            }.path,
            color,
        )
        HarnessIconName.SplitView -> drawPath(
            artwork16(min).apply {
                evenOdd()
                moveTo(12.1338f, 2.14111f)
                cubicTo(13.4543f, 2.14136f, 14.5251f, 3.21219f, 14.5254f, 4.53271f)
                lineTo(14.5254f, 10.8003f)
                cubicTo(14.5251f, 12.1208f, 13.4543f, 13.1916f, 12.1338f, 13.1919f)
                lineTo(3.86621f, 13.1919f)
                cubicTo(2.54569f, 13.1916f, 1.47486f, 12.1208f, 1.47461f, 10.8003f)
                lineTo(1.47461f, 4.53271f)
                cubicTo(1.47486f, 3.2122f, 2.54569f, 2.14136f, 3.86621f, 2.14111f)
                close()
                moveTo(8.52539f, 12.1411f)
                lineTo(12.1338f, 12.1411f)
                cubicTo(12.8744f, 12.1409f, 13.4744f, 11.5409f, 13.4746f, 10.8003f)
                lineTo(13.4746f, 4.53271f)
                cubicTo(13.4744f, 3.79209f, 12.8744f, 3.19214f, 12.1338f, 3.19189f)
                lineTo(8.52539f, 3.19189f)
                close()
                moveTo(3.86621f, 3.19189f)
                cubicTo(3.12559f, 3.19214f, 2.52564f, 3.79209f, 2.52539f, 4.53271f)
                lineTo(2.52539f, 10.8003f)
                cubicTo(2.52564f, 11.5409f, 3.12559f, 12.1409f, 3.86621f, 12.1411f)
                lineTo(7.47461f, 12.1411f)
                lineTo(7.47461f, 3.19189f)
                close()
            }.path,
            color,
        )
        HarnessIconName.FullView -> drawPath(
            artwork16(min).apply {
                moveTo(4.6582f, 7.83984f)
                cubicTo(4.94815f, 7.83984f, 5.18359f, 8.07528f, 5.18359f, 8.36523f)
                lineTo(5.18359f, 10.7998f)
                lineTo(7.67676f, 10.7998f)
                cubicTo(7.96671f, 10.7998f, 8.20215f, 11.0352f, 8.20215f, 11.3252f)
                cubicTo(8.20215f, 11.6151f, 7.96671f, 11.8506f, 7.67676f, 11.8506f)
                lineTo(4.9082f, 11.8506f)
                cubicTo(4.48018f, 11.8506f, 4.13281f, 11.5032f, 4.13281f, 11.0752f)
                lineTo(4.13281f, 8.36523f)
                cubicTo(4.13281f, 8.07528f, 4.36825f, 7.83984f, 4.6582f, 7.83984f)
                close()
                moveTo(11.0918f, 4.14941f)
                cubicTo(11.5197f, 4.14955f, 11.8672f, 4.49687f, 11.8672f, 4.9248f)
                lineTo(11.8672f, 7.63477f)
                cubicTo(11.8672f, 7.92463f, 11.6316f, 8.16002f, 11.3418f, 8.16016f)
                cubicTo(11.0518f, 8.16016f, 10.8164f, 7.92472f, 10.8164f, 7.63477f)
                lineTo(10.8164f, 5.2002f)
                lineTo(8.32324f, 5.2002f)
                cubicTo(8.03329f, 5.2002f, 7.79785f, 4.96475f, 7.79785f, 4.6748f)
                cubicTo(7.79785f, 4.38486f, 8.03329f, 4.14941f, 8.32324f, 4.14941f)
                close()
            }.path,
            color,
        )
        HarnessIconName.PanelAdd -> drawPath(
            artwork16(min).apply {
                evenOdd()
                // The plus, spanning 4.64..11.36 of the 16-unit grid — a 6.72-unit cross, so it sits
                // well inside the panel outline rather than touching it.
                moveTo(8.00037f, 4.64087f)
                cubicTo(8.29007f, 4.64107f, 8.52562f, 4.87654f, 8.52576f, 5.16626f)
                lineTo(8.52576f, 7.47485f)
                lineTo(10.8334f, 7.47485f)
                cubicTo(11.1232f, 7.47503f, 11.3578f, 7.7104f, 11.3578f, 8.00024f)
                cubicTo(11.3577f, 8.28997f, 11.1231f, 8.52546f, 10.8334f, 8.52563f)
                lineTo(8.52576f, 8.52563f)
                lineTo(8.52576f, 10.8333f)
                cubicTo(8.52558f, 11.1229f, 8.29004f, 11.3575f, 8.00037f, 11.3577f)
                cubicTo(7.71053f, 11.3577f, 7.47515f, 11.1231f, 7.47498f, 10.8333f)
                lineTo(7.47498f, 8.52563f)
                lineTo(5.16638f, 8.52563f)
                cubicTo(4.87668f, 8.52544f, 4.64112f, 8.28996f, 4.64099f, 8.00024f)
                cubicTo(4.64099f, 7.71042f, 4.8766f, 7.47505f, 5.16638f, 7.47485f)
                lineTo(7.47498f, 7.47485f)
                lineTo(7.47498f, 5.16626f)
                cubicTo(7.47511f, 4.87642f, 7.7105f, 4.64087f, 8.00037f, 4.64087f)
                close()
                // The panel outline: a 2.51-radius rounded square spanning 1.81..14.19, 1.05 units
                // thick, as a ring (outer contour then inner, which is what the odd-even rule needs).
                moveTo(11.683f, 1.80786f)
                cubicTo(13.0683f, 1.80786f, 14.1918f, 2.93134f, 14.1918f, 4.31665f)
                lineTo(14.1918f, 11.6829f)
                cubicTo(14.1918f, 13.0682f, 13.0683f, 14.1917f, 11.683f, 14.1917f)
                lineTo(4.31677f, 14.1917f)
                cubicTo(2.93146f, 14.1917f, 1.80798f, 13.0682f, 1.80798f, 11.6829f)
                lineTo(1.80798f, 4.31665f)
                cubicTo(1.80798f, 2.93134f, 2.93146f, 1.80786f, 4.31677f, 1.80786f)
                close()
                moveTo(4.31677f, 2.85864f)
                cubicTo(3.51136f, 2.85864f, 2.85876f, 3.51124f, 2.85876f, 4.31665f)
                lineTo(2.85876f, 11.6829f)
                cubicTo(2.85876f, 12.4883f, 3.51136f, 13.1418f, 4.31677f, 13.1418f)
                lineTo(11.683f, 13.1418f)
                cubicTo(12.4884f, 13.1418f, 13.142f, 12.4883f, 13.142f, 11.6829f)
                lineTo(13.142f, 4.31665f)
                cubicTo(13.142f, 3.51124f, 12.4884f, 2.85864f, 11.683f, 2.85864f)
                close()
            }.path,
            color,
        )
        HarnessIconName.PanelRight -> {
            drawRoundRect(
                color,
                Offset(min * 0.16f, min * 0.20f),
                Size(min * 0.68f, min * 0.60f),
                cornerRadius = CornerRadius(min * 0.05f),
                style = stroke,
            )
            drawLine(color, Offset(min * 0.60f, min * 0.20f), Offset(min * 0.60f, min * 0.80f), stroke.width, StrokeCap.Round)
        }
        HarnessIconName.SignOut -> {
            val g = CodexGrid(min)
            drawPath(
                Path().apply {
                    moveTo(g.at(10.6f), g.at(4.2f))
                    lineTo(g.at(6.2f), g.at(4.2f))
                    cubicTo(g.at(4.9f), g.at(4.2f), g.at(4f), g.at(5.2f), g.at(4f), g.at(6.4f))
                    lineTo(g.at(4f), g.at(13.6f))
                    cubicTo(g.at(4f), g.at(14.8f), g.at(4.9f), g.at(15.8f), g.at(6.2f), g.at(15.8f))
                    lineTo(g.at(10.6f), g.at(15.8f))
                },
                color,
                style = stroke,
            )
            drawLine(color, Offset(g.at(9.2f), g.at(10f)), Offset(g.at(16f), g.at(10f)), stroke.width, StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(g.at(13.4f), g.at(7.4f))
                    lineTo(g.at(16f), g.at(10f))
                    lineTo(g.at(13.4f), g.at(12.6f))
                },
                color,
                style = stroke,
            )
        }
        HarnessIconName.ExternalLink -> {
            drawLine(color, Offset(min * 0.30f, min * 0.70f), Offset(min * 0.72f, min * 0.28f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(min * 0.46f, min * 0.28f), Offset(min * 0.72f, min * 0.28f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(min * 0.72f, min * 0.28f), Offset(min * 0.72f, min * 0.54f), stroke.width, StrokeCap.Round)
        }
    }
}

private fun pathOf(size: Float, vararg points: Float): Path = Path().apply {
    if (points.size < 2) return@apply
    moveTo(size * points[0], size * points[1])
    var index = 2
    while (index + 1 < points.size) {
        lineTo(size * points[index], size * points[index + 1])
        index += 2
    }
}

/** Adds a rectangular slot between two points, so an even-odd fill can punch out a diagonal hand. */
private fun Path.slot(from: Offset, to: Offset, width: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length <= 0f) return
    val nx = -dy / length * width / 2f
    val ny = dx / length * width / 2f
    moveTo(from.x + nx, from.y + ny)
    lineTo(to.x + nx, to.y + ny)
    lineTo(to.x - nx, to.y - ny)
    lineTo(from.x - nx, from.y - ny)
    close()
}

// Codex ProjectsFolder (`sidebar-projects-icon`): body 2.5..17.5 x 9.2..16.7, tabbed top above it.
private fun folderBodyPath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(2.5f), g.at(9.2f))
        lineTo(g.at(17.5f), g.at(9.2f))
        lineTo(g.at(17.5f), g.at(14.3f))
        cubicTo(g.at(17.5f), g.at(15.7f), g.at(16.2f), g.at(16.7f), g.at(14.9f), g.at(16.7f))
        lineTo(g.at(5.1f), g.at(16.7f))
        cubicTo(g.at(3.8f), g.at(16.7f), g.at(2.5f), g.at(15.7f), g.at(2.5f), g.at(14.3f))
        close()
    }

private fun folderTopPath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(17.5f), g.at(9.3f))
        lineTo(g.at(17.5f), g.at(7.5f))
        cubicTo(g.at(17.5f), g.at(6.2f), g.at(16.5f), g.at(5.2f), g.at(15.2f), g.at(5.2f))
        lineTo(g.at(10.75f), g.at(5.2f))
        lineTo(g.at(9.35f), g.at(4.7f))
        lineTo(g.at(8.3f), g.at(3.9f))
        lineTo(g.at(6.9f), g.at(3.4f))
        lineTo(g.at(4.9f), g.at(3.4f))
        cubicTo(g.at(3.6f), g.at(3.4f), g.at(2.5f), g.at(4.4f), g.at(2.5f), g.at(5.7f))
        lineTo(g.at(2.5f), g.at(14.3f))
    }

// Codex Plugins (`sidebar-plugins-icon`): a cable loop around a two-pin plug body.
private fun plugCablePath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(14.4f), g.at(16.2f))
        cubicTo(g.at(13.2f), g.at(17.05f), g.at(11.6f), g.at(17.4f), g.at(10f), g.at(17.4f))
        cubicTo(g.at(5.35f), g.at(17.4f), g.at(2.6f), g.at(13.8f), g.at(2.6f), g.at(9.5f))
        cubicTo(g.at(2.6f), g.at(5.6f), g.at(6.3f), g.at(2.5f), g.at(10.3f), g.at(2.5f))
        cubicTo(g.at(14.9f), g.at(2.5f), g.at(17.7f), g.at(6.05f), g.at(17.4f), g.at(10.1f))
        cubicTo(g.at(17.2f), g.at(13f), g.at(14.3f), g.at(14.1f), g.at(12.5f), g.at(12.2f))
    }

private fun plugBodyPath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(6.9f), g.at(10.9f))
        lineTo(g.at(11.05f), g.at(6.9f))
        lineTo(g.at(12.6f), g.at(8.3f))
        lineTo(g.at(12.25f), g.at(12.3f))
        lineTo(g.at(8.2f), g.at(12.55f))
        lineTo(g.at(6.88f), g.at(11.17f))
        close()
    }

private fun plugPinPath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(10.2f), g.at(7.45f))
        lineTo(g.at(9.1f), g.at(6.3f))
        moveTo(g.at(7.5f), g.at(10.1f))
        lineTo(g.at(6.4f), g.at(9f))
    }

/**
 * One rounded bar of the Codex rail artwork, in 20-unit grid coordinates. The Library bars and the
 * Images frames are the same rounded-rectangle geometry; the default 1.54 radius is the bar corner
 * measured from sidebar-library-icon, and the Images frames pass their own 2.3 / 2.36 radius.
 */
/** The Codex 项目 box. [HarnessIconName.Projects] fills it even-odd; the solid variant fills it. */
private fun projectsBoxPath(min: Float): Path =
    Path().apply {
                    moveTo(min*0.347458f, min*0.133835f)
                    cubicTo(min*0.379049f, min*0.133835f, min*0.409861f, min*0.143879f, min*0.435409f, min*0.162461f)
                    lineTo(min*0.488633f, min*0.201157f)
                    cubicTo(min*0.502963f, min*0.211577f, min*0.52023f, min*0.217209f, min*0.537949f, min*0.217209f)
                    lineTo(min*0.747912f, min*0.217209f)
                    cubicTo(min*0.830462f, min*0.217209f, min*0.897388f, min*0.284129f, min*0.897388f, min*0.366684f)
                    lineTo(min*0.897388f, min*0.716662f)
                    cubicTo(min*0.897388f, min*0.799212f, min*0.830462f, min*0.866138f, min*0.747912f, min*0.866138f)
                    lineTo(min*0.25206f, min*0.866138f)
                    cubicTo(min*0.169518f, min*0.866119f, min*0.102585f, min*0.799206f, min*0.102585f, min*0.716662f)
                    lineTo(min*0.102585f, min*0.28331f)
                    cubicTo(min*0.1026f, min*0.200777f, min*0.169527f, min*0.13385f, min*0.25206f, min*0.133835f)
                    lineTo(min*0.347458f, min*0.133835f)
                    close()
                    moveTo(min*0.168259f, min*0.491196f)
                    lineTo(min*0.168259f, min*0.716662f)
                    cubicTo(min*0.168259f, min*0.762962f, min*0.205762f, min*0.800506f, min*0.25206f, min*0.800519f)
                    lineTo(min*0.747912f, min*0.800519f)
                    cubicTo(min*0.794219f, min*0.800519f, min*0.831769f, min*0.762969f, min*0.831769f, min*0.716662f)
                    lineTo(min*0.831769f, min*0.491196f)
                    lineTo(min*0.168259f, min*0.491196f)
                    close()
                    moveTo(min*0.25206f, min*0.199509f)
                    cubicTo(min*0.205771f, min*0.199524f, min*0.168274f, min*0.237021f, min*0.168259f, min*0.28331f)
                    lineTo(min*0.168259f, min*0.425522f)
                    lineTo(min*0.831769f, min*0.425522f)
                    lineTo(min*0.831769f, min*0.366684f)
                    cubicTo(min*0.831769f, min*0.320373f, min*0.794219f, min*0.282822f, min*0.747912f, min*0.282822f)
                    lineTo(min*0.537949f, min*0.282822f)
                    cubicTo(min*0.506358f, min*0.282822f, min*0.475546f, min*0.272838f, min*0.449997f, min*0.254258f)
                    lineTo(min*0.396774f, min*0.2155f)
                    cubicTo(min*0.382448f, min*0.205091f, min*0.365167f, min*0.199509f, min*0.347458f, min*0.199509f)
                    lineTo(min*0.25206f, min*0.199509f)
                    close()
                    }

/** The 探索 pin glyph; the outline state strokes it and the pinned state fills it. */
private fun pushpinPath(min: Float): Path =
    Path().apply {
                    moveTo(min*0.54364f, min*0.142094f)
                    cubicTo(min*0.581941f, min*0.085041f, min*0.659438f, min*0.076738f, min*0.710756f, min*0.116703f)
                    lineTo(min*0.720644f, min*0.12537f)
                    lineTo(min*0.720825f, min*0.125493f)
                    lineTo(min*0.873537f, min*0.276433f)
                    lineTo(min*0.873656f, min*0.276554f)
                    cubicTo(min*0.900331f, min*0.303228f, min*0.908531f, min*0.338866f, min*0.904844f, min*0.370671f)
                    cubicTo(min*0.901206f, min*0.402032f, min*0.885656f, min*0.433236f, min*0.859925f, min*0.453068f)
                    lineTo(min*0.8595f, min*0.453434f)
                    lineTo(min*0.859006f, min*0.453801f)
                    lineTo(min*0.708069f, min*0.561589f)
                    cubicTo(min*0.6977f, min*0.569005f, min*0.690481f, min*0.580105f, min*0.687925f, min*0.592594f)
                    lineTo(min*0.654844f, min*0.754519f)
                    lineTo(min*0.654663f, min*0.755556f)
                    lineTo(min*0.654419f, min*0.756537f)
                    cubicTo(min*0.642344f, min*0.801212f, min*0.610366f, min*0.837675f, min*0.569092f, min*0.851869f)
                    cubicTo(min*0.526398f, min*0.866544f, min*0.478631f, min*0.855675f, min*0.44104f, min*0.815312f)
                    lineTo(min*0.335205f, min*0.711125f)
                    lineTo(min*0.148193f, min*0.8982f)
                    lineTo(min*0.101807f, min*0.851812f)
                    lineTo(min*0.288452f, min*0.665106f)
                    lineTo(min*0.188721f, min*0.566837f)
                    cubicTo(min*0.151966f, min*0.531135f, min*0.139501f, min*0.482551f, min*0.151733f, min*0.439213f)
                    cubicTo(min*0.16401f, min*0.395761f, min*0.200091f, min*0.361075f, min*0.252136f, min*0.350895f)
                    lineTo(min*0.407166f, min*0.315129f)
                    cubicTo(min*0.419363f, min*0.31231f, min*0.430098f, min*0.305075f, min*0.437256f, min*0.294804f)
                    lineTo(min*0.54364f, min*0.142094f)
                    close()
                    }

private fun DrawScope.drawCodexBar(
    g: CodexGrid,
    stroke: Stroke,
    color: Color,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float = 1.54f,
    style: DrawStyle = stroke,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(g.at(left), g.at(top)),
        size = Size(g.len(width), g.len(height)),
        cornerRadius = CornerRadius(g.len(radius)),
        style = style,
    )
}

/**
 * Codex sidebar-images-icon mountain. The extracted path is straight to the peak, one curved segment
 * over the summit, then straight down to the frame corner; the control points are the measured
 * tangents (out (0.67,-0.88) at the peak, in (-0.88,-0.67) at the summit end).
 */
private fun mountainPath(g: CodexGrid): Path =
    Path().apply {
        moveTo(g.at(2.33f), g.at(15.63f))
        lineTo(g.at(4.06f), g.at(13.34f))
        cubicTo(g.at(4.73f), g.at(12.46f), g.at(5.98f), g.at(12.28f), g.at(6.86f), g.at(12.95f))
        lineTo(g.at(12.08f), g.at(16.89f))
    }
