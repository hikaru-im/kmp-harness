package im.hikaru.harness.client.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.BrowserIcon
import im.hikaru.harness.ui.foundation.BrowserIconGlyph
import im.hikaru.harness.ui.foundation.HarnessIcon
import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * One 工具 card of the new-tab page: its Codex label, the keyboard chord the reference prints in the
 * card, the artwork of its leading glyph, and whether this client can open it.
 *
 * [opens] is true for 变更 only: it opens the Git page, which is the one destination the reference's
 * other three cards have an equivalent for. The rest keep the reference's geometry and are drawn
 * disabled instead of pretending to open a terminal, a file picker or a side chat this client does not
 * have. [options] marks the card that carries the reference's own `⋯` control (终端).
 */
public enum class NewTabTool(
    public val label: String,
    public val chord: String,
    public val icon: BrowserIcon,
    public val opens: Boolean = false,
    public val options: Boolean = false,
) {
    Changes("变更", "⌃⇧G", BrowserIcon.Changes, opens = true),
    Terminal("终端", "⌃`", BrowserIcon.Terminal, options = true),
    Files("文件", "⌘P", BrowserIcon.Files),
    SideChat("侧边聊天", "⌥⌘S", BrowserIcon.SideChat),
}

/** The reference's two-column grid fills row by row, so the cards are laid out in this order. */
private val NewTabToolRows: List<List<NewTabTool>> = listOf(
    listOf(NewTabTool.Changes, NewTabTool.Terminal),
    listOf(NewTabTool.Files, NewTabTool.SideChat),
)

/**
 * The reference's disabled tone. Codex marks a disabled control with `opacity: .4` and keeps its ink
 * (`disabled:opacity-40` on the options control, `opacity: 0.4` on the toolbar's 返回/前进) rather than
 * greying the colour out.
 */
private const val DisabledAlpha: Float = 0.4f

/**
 * The 新标签页 page: the surface a new tab opens on.
 *
 * Laid out from the live Codex pane this client mirrors, not invented — a 48dp browser toolbar across
 * the top of the pane (white, closed by the measured 1px `border-default` rule) holding the
 * 返回/前进/重新加载 cluster, the 搜索或输入网址 capsule and the 扩展程序/浏览器选项 pills; below it, on the
 * page's own 250 canvas, a 768 column centred in 32/20 padding with the 工具 section: a heading and a
 * 2x2 grid of 40dp cards carrying a glyph, a label and the card's keyboard chord.
 *
 * The page draws its own column, so the shell must not cap it at the shared 672 — it asks for that with
 * `PageShellSpec.ownContentColumn`.
 *
 * Live-only controls are dead here and say so: the toolbar's 返回/前进 pair is disabled exactly as the
 * reference's is, 重新加载/扩展程序/浏览器选项 draw their measured hover fill but never act, and the cards
 * without a destination in this client are drawn disabled ([NewTabTool.opens]).
 *
 * The reference's 推荐 row is deliberately not drawn.
 */
@Composable
public fun NewTabPage(
    onOpenTool: (NewTabTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)) {
        BrowserToolbar()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(HarnessTokens.Colors.SurfaceCanvas)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = HarnessTokens.Control.BrowserPagePaddingX,
                        vertical = HarnessTokens.Control.BrowserPagePaddingY,
                    ),
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = HarnessTokens.Control.BrowserPageColumn)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Control.BrowserSectionGap),
            ) {
                ToolSection(onOpenTool)
            }
        }
    }
}

/**
 * The page's browser toolbar: a 48dp white band with the reference's own 8px padding, 6px between its
 * clusters and a 1px rule along its bottom edge. Every control in it is a pill — the 28dp cluster
 * controls and the 32dp 浏览器选项 — and the address capsule is capped at 770 inside the space between
 * them, centred.
 */
@Composable
private fun BrowserToolbar() {
    val ruleInk = HarnessTokens.Colors.ChromeRule
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HarnessTokens.Control.BrowserToolbar)
                .background(HarnessTokens.Colors.Canvas)
                .drawBehind {
                    val thickness = 1.dp.toPx()
                    drawRect(
                        color = ruleInk,
                        topLeft = Offset(0f, size.height - thickness),
                        size = Size(size.width, thickness),
                    )
                }
                .padding(HarnessTokens.Spacing.Two)
                .semantics { testTag = "new-tab-toolbar" },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.BrowserToolbarGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.BrowserToolbarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarPill("返回", "new-tab-back", enabled = false) {
                HarnessIcon(
                    HarnessIconName.ArrowLeft,
                    tint = HarnessTokens.Colors.ChromeIcon,
                    size = HarnessTokens.Spacing.Four,
                )
            }
            ToolbarPill("前进", "new-tab-forward", enabled = false) {
                HarnessIcon(
                    HarnessIconName.ArrowRight,
                    tint = HarnessTokens.Colors.ChromeIcon,
                    size = HarnessTokens.Spacing.Four,
                )
            }
            ToolbarPill("重新加载页面", "new-tab-reload") {
                BrowserIconGlyph(BrowserIcon.Reload, tint = HarnessTokens.Colors.ChromeText)
            }
        }
        Box(
            modifier = Modifier.weight(1f).padding(horizontal = HarnessTokens.Spacing.One),
            contentAlignment = Alignment.Center,
        ) {
            AddressCapsule()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Control.BrowserToolbarGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarPill("扩展程序", "new-tab-extensions") {
                BrowserIconGlyph(BrowserIcon.Extensions, tint = HarnessTokens.Colors.ChromeText)
            }
            Box(
                modifier =
                    Modifier
                        .size(HarnessTokens.Control.BrowserOptionsControl)
                        .clip(CircleShape)
                        .semantics {
                            contentDescription = "浏览器选项"
                            testTag = "new-tab-options"
                        },
                contentAlignment = Alignment.Center,
            ) {
                BrowserIconGlyph(BrowserIcon.Options, tint = HarnessTokens.Colors.ChromeText)
            }
        }
    }
}

/**
 * The 搜索或输入网址 capsule: 770 wide — the page column's 768 plus the pixel a side its ring takes back —
 * sitting on the page card's soft shadow (`0 4px 16px` black at 5%) and ringed with the 4%
 * `HarnessTokens.Colors.SurfaceRing` hairline. The reference's own ring is `0 0 0 1px` ink at 4.7%;
 * that is under half a level over white, so the capsule keeps the token the page card and the composer
 * already use instead of a token for 0.7%. It is filled with white.
 *
 * The field inside is the reference's placeholder only. This client has no browser page to address, so
 * the capsule draws no editable field rather than a text box that cannot do anything; the placeholder
 * sits at the reference's own inset — 2px of capsule padding around a field with 8px of its own.
 */
@Composable
private fun AddressCapsule() {
    val shape = CircleShape
    Box(
        modifier =
            Modifier
                .widthIn(max = HarnessTokens.Control.BrowserAddressMaxWidth)
                .fillMaxWidth()
                .height(HarnessTokens.Control.BrowserOptionsControl)
                .dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = 16.dp,
                            spread = 0.dp,
                            offset = DpOffset(x = 0.dp, y = 4.dp),
                            color = Color.Black.copy(alpha = 0.05f),
                        ),
                )
                .clip(shape)
                .background(HarnessTokens.Colors.SurfaceElevated)
                .border(1.dp, HarnessTokens.Colors.SurfaceRing, shape)
                // The reference's placeholder inset: 2px of capsule padding + 8px of field padding.
                .padding(horizontal = 10.dp)
                .semantics {
                    contentDescription = "搜索或输入网址"
                    testTag = "new-tab-address"
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        HarnessText(
            "搜索或输入网址",
            color = HarnessTokens.Colors.ChromeIcon,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

/** A toolbar control: a pill that fills with the measured hover ink but owns no action. */
@Composable
private fun ToolbarPill(
    label: String,
    testTag: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier =
            Modifier
                .size(HarnessTokens.Control.BrowserControl)
                .clip(CircleShape)
                .background(
                    if (hovered && enabled) HarnessTokens.Colors.ChromeFill else Color.Transparent,
                )
                .hoverable(source, enabled = enabled)
                .alpha(if (enabled) 1f else DisabledAlpha)
                .semantics {
                    contentDescription = label
                    this.testTag = testTag
                    if (!enabled) disabled()
                },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** The 工具 section: its heading, then the reference's two-column grid. */
@Composable
private fun ToolSection(onOpenTool: (NewTabTool) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Four)) {
        HarnessText(
            "工具",
            color = HarnessTokens.Colors.ChromeText,
            style = MaterialTheme.typography.labelLarge,
        )
        Column(verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
            NewTabToolRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Four)) {
                    row.forEach { tool -> ToolCell(tool, onOpenTool) }
                }
            }
        }
    }
}

/**
 * One 40dp card, a `rounded-md` white-96% surface the reference fills with its 10% ink on hover. Its
 * surface spans the whole grid cell — including the space the 终端 card's options control sits in,
 * exactly as the reference's cell does — while the card's own click target stops at the options control,
 * and a card this client cannot open keeps the measured ink at [DisabledAlpha].
 *
 * The corner is [HarnessTokens.Corners.Control]: the reference's `rounded-md` computes to 10px here,
 * and that is the token our codebase already maps that CSS corner onto (9dp), so the card shares the
 * settings rows' corner instead of inventing a tenth value.
 */
@Composable
private fun RowScope.ToolCell(tool: NewTabTool, onOpenTool: (NewTabTool) -> Unit) {
    val colors = HarnessTokens.Colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        modifier =
            Modifier
                .weight(1f)
                .height(HarnessTokens.Control.BrowserToolRow)
                .clip(RoundedCornerShape(HarnessTokens.Corners.Control))
                .background(colors.SurfaceElevated)
                .background(if (hovered && tool.opens) colors.ChromeFill else Color.Transparent)
                .hoverable(source, enabled = tool.opens)
                .alpha(if (tool.opens) 1f else DisabledAlpha)
                .padding(
                    end = if (tool.options) HarnessTokens.Control.BrowserToolCardPaddingX else 0.dp,
                )
                .semantics {
                    testTag = "new-tab-tool-" + tool.name.lowercase()
                    // The card this client cannot open says so, like the toolbar's dead 返回/前进 pair.
                    if (!tool.opens) disabled()
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        enabled = tool.opens,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpenTool(tool) }
                    .padding(
                        horizontal = HarnessTokens.Control.BrowserToolCardPaddingX,
                        vertical = HarnessTokens.Spacing.Two,
                    ),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserIconGlyph(
                tool.icon,
                tint = colors.TextSecondary,
                size = HarnessTokens.Spacing.Four,
            )
            HarnessText(
                tool.label,
                modifier = Modifier.weight(1f),
                color = colors.ChromeText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            ChordPill(tool.chord)
        }
        if (tool.options) ToolOptions()
    }
}

/**
 * The card's keyboard chord: the reference's 16dp keycap — 6.5% `HarnessTokens.Colors.SurfaceKeycap`
 * ink, `padding: 2px 6px`, `HarnessTokens.Colors.TextSecondary` lettering whose 12sp/16 fills the
 * height, and a 10px corner CSS clamps to 8 on a 16dp box, i.e. the pill drawn here.
 */
@Composable
private fun ChordPill(chord: String) {
    Box(
        modifier =
            Modifier
                .height(HarnessTokens.Control.BrowserKeycap)
                .clip(CircleShape)
                .background(HarnessTokens.Colors.SurfaceKeycap)
                .padding(horizontal = HarnessTokens.Control.BrowserKeycapPaddingX),
        contentAlignment = Alignment.Center,
    ) {
        HarnessText(
            chord,
            color = HarnessTokens.Colors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
    }
}

/** The 终端 card's own options control: the reference's 28dp `⋯` at the card's end. */
@Composable
private fun ToolOptions() {
    Box(
        modifier =
            Modifier
                .size(HarnessTokens.Control.BrowserControl)
                .clip(RoundedCornerShape(HarnessTokens.Control.TabRowControlRadius))
                .semantics {
                    contentDescription = "终端选项"
                    testTag = "new-tab-tool-options"
                },
        contentAlignment = Alignment.Center,
    ) {
        BrowserIconGlyph(BrowserIcon.Options, tint = HarnessTokens.Colors.ChromeIcon)
    }
}
