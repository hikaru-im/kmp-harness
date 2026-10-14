package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

private class TooltipIntent(val eligible: Boolean) {
    var alive: Boolean = true
    var hoverPending: Boolean by mutableStateOf(false)
    var visible: Boolean by mutableStateOf(false)
    var blocked: Boolean = false

    fun dismiss(block: Boolean = false) {
        hoverPending = false
        visible = false
        if (block) blocked = true
    }
}

/**
 * Put the modifier supplied to [trigger] before the actual clickable/focusable modifier. The wrapper has no
 * focus target of its own. A fresh enter or focus opens; moves and recompositions never reopen.
 * [enabled] controls the tooltip only, independently of the trigger business action.
 */
@Composable
public fun HarnessTooltipTrigger(
    text: String?,
    enabled: Boolean = true,
    delayMillis: Long = 200,
    scope: Any? = Unit,
    popupSuppressed: Boolean = false,
    modifier: Modifier = Modifier,
    side: HarnessPopoverSide = HarnessPopoverSide.Top,
    align: HarnessPopoverAlign = HarnessPopoverAlign.Center,
    popupTestTag: String = "harness-tooltip",
    /** Rail tips are the inverted dark pill the Codex rail draws; other callers keep the surface tip. */
    railPill: Boolean = false,
    trigger: @Composable (Modifier) -> Unit,
) {
    require(delayMillis >= 0) { "Tooltip delay must be non-negative" }
    // A new lifetime also fences A -> B -> A changes and cancels a delayed intent. Physical
    // hover/focus state survives replacement, so held input cannot open the replacement text.
    val intent = remember(text, enabled, delayMillis, scope, popupSuppressed) {
        TooltipIntent(enabled && !popupSuppressed && !text.isNullOrBlank())
    }
    val latestIntent by rememberUpdatedState(intent)
    var hovered by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(intent) { onDispose { intent.alive = false; intent.dismiss() } }
    LaunchedEffect(intent, intent.hoverPending) {
        if (intent.hoverPending) {
            delay(delayMillis)
            if (intent.alive && intent === latestIntent && intent.eligible && hovered && !intent.blocked) {
                intent.visible = true
                intent.hoverPending = false
            }
        }
    }
    val triggerModifier = Modifier
        .onFocusChanged { state ->
            if (focused != state.isFocused) {
                focused = state.isFocused
                val current = latestIntent
                if (!focused) { current.dismiss(); current.blocked = false }
                else if (current.alive && current.eligible && !current.blocked) {
                    current.hoverPending = false
                    current.visible = true
                }
            }
        }
        .onPreviewKeyEvent { event ->
            val current = latestIntent
            val escape = event.key == Key.Escape
            val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
            val hadIntent = current.visible || current.hoverPending
            if (event.type == KeyEventType.KeyDown && (escape || activate)) current.dismiss(block = true)
            escape && hadIntent
        }
        // Unit is intentional: restarting on text/scope would synthesize an enter for held hover.
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val current = latestIntent
                    when (event.type) {
                        PointerEventType.Enter -> if (!hovered) {
                            hovered = true
                            current.blocked = false
                            if (current.alive && current.eligible) current.hoverPending = true
                        }
                        PointerEventType.Exit -> if (hovered) { hovered = false; current.dismiss(); current.blocked = false }
                        PointerEventType.Press -> current.dismiss(block = true)
                        else -> Unit
                    }
                }
            }
        }
    Box(modifier) {
        trigger(triggerModifier)
        if (intent.eligible && intent.visible) {
            HarnessTooltipPopup(text!!, side, align, popupTestTag, railPill) { intent.dismiss(block = true) }
        }
    }
}

/** Compatibility path for callers which own visibility; a popup never changes anchor layout. */
@Composable
internal fun HarnessControlledTooltip(
    text: String,
    visible: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        content()
        if (visible && text.isNotBlank()) {
            HarnessTooltipPopup(text, HarnessPopoverSide.Top, HarnessPopoverAlign.Center, "harness-tooltip") {}
        }
    }
}

@Composable
private fun HarnessTooltipPopup(
    text: String,
    side: HarnessPopoverSide,
    align: HarnessPopoverAlign,
    tag: String,
    railPill: Boolean = false,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val margin = with(density) { HarnessTokens.Spacing.Two.roundToPx() }
    // The Codex rail tip sits ~2px off the item; the surface tip keeps the wider 6dp gap.
    val offset = with(density) { (if (railPill) 2.dp else 6.dp).roundToPx() }
    val maxWidth = with(density) { (window.width - margin * 2).coerceAtLeast(0).toDp() }.coerceAtMost(320.dp)
    val maxHeight = with(density) { (window.height - margin * 2).coerceAtLeast(0).toDp() }
    val provider = remember(side, align, margin, offset) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize,
            ): IntOffset = harnessPopoverPosition(anchorBounds, windowSize, popupContentSize, side, align, offset, margin, layoutDirection)
        }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false)) {
        if (railPill) {
            // Measured rail tip: dark #1a1c1f pill, 13px white ink, 20px corner, 5x12 padding.
            Box(
                modifier =
                    Modifier
                        .testTag(tag)
                        // Measured off the live rail tip (two samples): the corner is a rounded RECTANGLE,
                        // ~1/3 of the height — 33px and 58px flat tops on 52x30 and 77x30 tips, reaching full
                        // width at dy=10. A 50% stadium would show no flat top at all, so this is 10dp, not a pill.
                        .clip(RoundedCornerShape(10.dp))
                        .background(HarnessTokens.Colors.InverseSurface)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(text, color = HarnessTokens.Colors.OnInverse, fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Clip, modifier = Modifier.testTag("$tag-text"))
            }
            return@Popup
        }
        HarnessSurface(Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight).testTag(tag)) {
            HarnessText(
                text,
                modifier = Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight)
                    .testTag("$tag-text")
                    .verticalScroll(rememberScrollState()).padding(HarnessTokens.Spacing.Two),
                overflow = TextOverflow.Clip,
            )
        }
    }
}
