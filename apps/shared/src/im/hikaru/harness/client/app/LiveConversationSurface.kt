package im.hikaru.harness.client.app

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.ui.foundation.*

/**
 * Owners may override the shared editor height cap for constrained viewports.
 */
public val LocalConversationEditorMaxHeight = staticCompositionLocalOf { HarnessTokens.Composer.EditorMaxHeight }
/** Supply the main viewport height when Thread and its composer occupy separate layout slots. */
public val LocalConversationViewportHeight = staticCompositionLocalOf<Dp?> { null }
/** How much of the timeline's viewport the floating composer covers; the list reserves it at its end. */
internal val LocalConversationBottomInset = staticCompositionLocalOf { 0.dp }

/**
 * The 新聊天 page: the surface a new chat opens on, measured against the live Codex page.
 *
 * The hero, project picker and composer form one centered group. Short viewports scroll.
 * The heading names the bound Host; project selection belongs to the controls above the composer.
 */
@Composable
public fun LiveHomeSurface(hostLabel: String?, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().semantics { testTag = "live-home" }) {
        val viewportHeight = if (constraints.hasBoundedHeight) maxHeight else null
        CompositionLocalProvider(LocalConversationViewportHeight provides viewportHeight) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .heightIn(min = viewportHeight ?: HarnessTokens.Composer.EditorMaxHeight)
                    .padding(HarnessTokens.Composer.PageInset),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = HarnessTokens.Composer.MaxWidth).fillMaxWidth()
                    .semantics { testTag = "live-home-group" },
                    verticalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.WelcomeGap),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeHero(hostLabel)
                    Box(Modifier.fillMaxWidth().semantics { testTag = "live-home-content" }) { content() }
                }
            }
        }
    }
}

/** The heading the reference sets when no project is selected (`home.hero.noProjectWhatShouldWeBuild`). */
private const val HomeHeroNoProject = "我们要构建什么？"

/** The heading around the project name (`home.hero.whatShouldWeBuildInSelectedProject`). */
private const val HomeHeroPrefix = "你想让我们在 "

private const val HomeHeroSuffix = " 中构建什么？"

/**
 * The hero block: the Codex mark over the heading, 24dp apart (the reference's `gap-6`). The mark keeps
 * its ink and changes its own opacity with the pointer, exactly as the live element does — it is
 * `aria-hidden` there, so it is drawn without an action here too.
 */
@Composable
private fun HomeHero(hostLabel: String?) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Six),
    ) {
        CodexMark(
            modifier = Modifier.hoverable(source),
            tint = HarnessTokens.Colors.ChromeText.copy(
                alpha =
                    if (hovered) HarnessTokens.Control.HomeHeroMarkHoverAlpha
                    else HarnessTokens.Control.HomeHeroMarkAlpha,
            ),
            size = HarnessTokens.Control.HomeHeroMark,
        )
        HeroHeading(hostLabel?.takeIf { it.isNotBlank() })
    }
}

/**
 * The hero's question, centred on the reference's `heading-xl` (28sp on a 33.6sp line at weight 400).
 * With a name to place, the question wraps around it and the name carries the reference's dotted rule
 * (`decoration-dotted decoration-[1px] underline-offset-4`); without one it is the reference's own
 * shorter question. Both variants wrap, so a narrow pane wraps the question instead of clipping it.
 */
@Composable
private fun HeroHeading(project: String?) {
    val style =
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = HarnessTokens.Text.HeadingXl,
            lineHeight = 33.6.sp,
            fontWeight = FontWeight.Normal,
        )
    val modifier = Modifier.semantics { testTag = "live-home-hero"; heading() }
    if (project == null) {
        Text(
            text = HomeHeroNoProject,
            modifier = modifier,
            color = HarnessTokens.Colors.ChromeText,
            style = style,
            textAlign = TextAlign.Center,
        )
        return
    }
    val text = buildAnnotatedString {
        append(HomeHeroPrefix)
        append(project)
        append(HomeHeroSuffix)
    }
    val range = HomeHeroPrefix.length until HomeHeroPrefix.length + project.length
    // Read the ink in the composition: the draw lambda below is not a composable scope.
    val underlineInk = HarnessTokens.Colors.ChromeIcon
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        modifier = modifier.drawBehind { drawHeroUnderline(layout, range, underlineInk) },
        color = HarnessTokens.Colors.ChromeText,
        style = style,
        textAlign = TextAlign.Center,
        onTextLayout = { layout = it },
    )
}

/**
 * The dotted rule under the project name, drawn from the laid-out text rather than as a text
 * decoration: Compose has no dotted underline, and the reference's is a measured pattern — 1px dots on
 * a 2px pitch, sitting 4px under the baseline, running the name's full width (scanned row by row on the
 * live page: dots on y=664 against a baseline at 660). A name that wraps draws a run per line, each
 * stopping at the line's end.
 */
private fun DrawScope.drawHeroUnderline(layout: TextLayoutResult?, range: IntRange, ink: Color) {
    if (layout == null || range.isEmpty()) return
    val pitch = HarnessTokens.Control.HomeHeroUnderlinePitch.toPx()
    val dot = pitch / 2f
    val baseline = HarnessTokens.Control.HomeHeroUnderlineOffset.toPx()
    for (line in layout.getLineForOffset(range.first)..layout.getLineForOffset(range.last)) {
        val from = maxOf(range.first, layout.getLineStart(line))
        val to = minOf(range.last + 1, layout.getLineEnd(line))
        if (to <= from) continue
        val left = layout.getHorizontalPosition(from, usePrimaryDirection = true)
        val right = layout.getHorizontalPosition(to, usePrimaryDirection = false)
        val top = layout.getLineBaseline(line) + baseline
        var x = left
        while (x < right) {
            drawRect(ink, topLeft = Offset(x, top), size = Size(minOf(dot, right - x), dot))
            x += pitch
        }
    }
}

/** Timeline only. The app owns the selected route, controller and separate shared composer. */
@Composable
public fun LiveThreadSurface(
    state: ConversationState,
    onRetry: () -> Unit,
    onEditMessage: ((Message) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(HarnessTokens.Spacing.Four).semantics { testTag = "live-thread" },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            !state.connected || state.historyPhase == ConversationDataPhase.Disconnected ->
                ConversationNotice("连接已断开。重新连接后可继续此会话。", "live-thread-disconnected")
            state.historyPhase == ConversationDataPhase.Loading ->
                ConversationNotice("正在读取会话…", "live-thread-loading")
            state.historyPhase == ConversationDataPhase.Failed -> {
                // The failure itself is a toast; the surface keeps only the retry that unblocks it.
                ConversationNotice("会话历史不可用。", "live-thread-unavailable")
                HarnessButton(
                    "重试", onRetry, enabled = state.connected && state.historyPhase != ConversationDataPhase.Loading,
                    testTag = "live-thread-retry", size = HarnessControlSize.Compact,
                )
            }
        }
        if (state.history.isNotEmpty()) {
            key(state.host?.hostId, state.host?.identity, state.selectedSessionId) {
                ConversationViewport(state.history, Modifier.weight(1f).semantics { testTag = "live-thread-timeline" },
                    onEditMessage = onEditMessage?.takeIf { state.canEdit })
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.connected && state.historyPhase !in listOf(ConversationDataPhase.Loading, ConversationDataPhase.Failed,
                        ConversationDataPhase.Disconnected)) {
                    HarnessText("此会话暂无消息。", Modifier.semantics { testTag = "live-thread-empty" },
                        color = HarnessTokens.Colors.TextMuted)
                }
            }
        }
    }
}

@Composable
internal fun ConversationNotice(
    text: String, tag: String, isError: Boolean = false, modifier: Modifier = Modifier,
) {
    HarnessText(text, modifier.semantics { testTag = tag; if (isError) error(text) },
        color = if (isError) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
        style = MaterialTheme.typography.bodySmall)
}
