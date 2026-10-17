package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Codex cloud mark: a cloud-shaped ring holding a shell prompt (`>` and `_`). It is the artwork the
 * reference's 新聊天 page centres above its hero heading (`div[data-testid="home-icon"]`), and it is kept
 * apart from [HarnessIconName] because it is neither a filled 20-unit grid glyph nor a rail icon — it is
 * one outline stroked into a ring plus two prompt strokes.
 *
 * Transcribed byte-for-byte from the `<svg viewBox="149 149 418 418">` the live page ships, probed over
 * the remote debugging port. The source draws the ring as a *filled* path (the outline stroked and
 * converted to a fill) masked by the outline's own interior; [drawCodexMark] reaches the same ring by
 * clipping to the outline and stroking it with [RING_STROKE], which is the offset the source's two paths
 * differ by (the filled path spans 159.962..556.039 against the outline's 180.543..535.458, i.e. 20.58 a
 * side). Stroking on the outline and clipping to its interior leaves the band that sits *inside* the
 * outline — the ring the reference shows — so the mark's outer edge lands on the outline path.
 */
public object CodexMarkArtwork {
    /** The `viewBox`'s origin: the artwork's own coordinates start at 149, not 0. */
    public const val ORIGIN: Float = 149f

    /** The `viewBox`'s span on both axes. A coordinate maps onto a box by `box / SPAN`. */
    public const val SPAN: Float = 418f

    /**
     * The cloud's centre line — the closed outline the ring is stroked from and clipped to. Its `Z` is
     * what makes the clip an interior rather than an open curve.
     */
    public const val OUTLINE: String =
        "M247.429 247.43C257.73 208.911 292.871 180.543 334.638 180.543C359.555 180.543 " +
            "382.115 190.64 398.449 206.964C405.906 204.97 413.743 203.905 421.829 203.905C471.681 " +
            "203.906 512.096 244.32 512.096 294.173C512.096 302.259 511.031 310.096 509.037 " +
            "317.553C525.361 333.887 535.458 356.446 535.458 381.364C535.458 423.131 507.09 " +
            "458.271 468.571 468.572C458.271 507.091 423.131 535.459 381.364 535.459C356.446 " +
            "535.459 333.886 525.362 317.552 509.037C310.095 511.031 302.258 512.097 294.172 " +
            "512.097C244.319 512.097 203.906 471.682 203.906 421.829C203.906 413.743 204.969 " +
            "405.905 206.963 398.448C190.639 382.115 180.543 359.555 180.543 334.638C180.543 " +
            "292.871 208.91 257.73 247.429 247.43Z"

    /**
     * The ring's thickness: the offset between the source's filled path and its mask outline. Stroked on
     * [OUTLINE] and clipped to that outline's interior it leaves a band of half this width inside the
     * outline, which is the 20.58-unit ring the reference draws.
     */
    public const val RING_STROKE: Float = 41.16f

    /** The prompt's `>` chevron (`M276.533 309.154…`), an open stroke. */
    public const val PROMPT_CARET: String =
        "M276.533 309.154L303.468 357.831C304.433 359.575 304.412 361.698 303.414 363.423L276.533 409.854"

    /** The prompt's `_` cursor (`M436.706 408.738H370.021`), an open stroke. */
    public const val PROMPT_CURSOR: String = "M436.706 408.738H370.021"

    /** Stroke width the source gives both prompt strokes (`stroke-width="24"`). */
    public const val PROMPT_STROKE: Float = 24f
}

/**
 * The Codex mark drawn into a [size] box. [tint] is the mark's ink; the reference paints it with
 * `--color-text-default` and then drops the whole mark to 30% (40% while pointed at), so callers that
 * want the reference's own tone pass the ink at that alpha rather than a weaker colour.
 */
@Composable
public fun CodexMark(
    modifier: Modifier = Modifier,
    tint: Color = HarnessTokens.Colors.ChromeText,
    size: Dp = 56.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / CodexMarkArtwork.SPAN
        translate(left = -CodexMarkArtwork.ORIGIN * unit, top = -CodexMarkArtwork.ORIGIN * unit) {
            scale(scaleX = unit, scaleY = unit, pivot = Offset.Zero) {
                drawCodexMark(tint)
            }
        }
    }
}

private fun DrawScope.drawCodexMark(tint: Color) {
    val outline = PathParser().parsePathString(CodexMarkArtwork.OUTLINE).toPath()
    val stroke = Stroke(
        width = CodexMarkArtwork.RING_STROKE,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    // The ring: the outline's own stroke, cut back to the outline's interior — the mask the source
    // applies to its filled path, reached by clipping instead.
    clipPath(outline) { drawPath(outline, tint, style = stroke) }
    // The prompt is not masked in the source; it sits inside the ring on the same ink.
    val prompt = Stroke(
        width = CodexMarkArtwork.PROMPT_STROKE,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    drawPath(PathParser().parsePathString(CodexMarkArtwork.PROMPT_CARET).toPath(), tint, style = prompt)
    drawPath(PathParser().parsePathString(CodexMarkArtwork.PROMPT_CURSOR).toPath(), tint, style = prompt)
}
