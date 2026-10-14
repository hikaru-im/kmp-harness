package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

public enum class HarnessPopoverSide { Top, Bottom, Start, End }
public enum class HarnessPopoverAlign { Start, Center, End }

/** Logical start/end obey RTL; flip to the opposite side when it fits, then clamp to the window. */
public fun harnessPopoverPosition(
    anchor: IntRect,
    window: IntSize,
    popup: IntSize,
    side: HarnessPopoverSide = HarnessPopoverSide.Bottom,
    align: HarnessPopoverAlign = HarnessPopoverAlign.Start,
    sideOffset: Int = 6,
    margin: Int = 8,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): IntOffset {
    require(sideOffset >= 0 && margin >= 0) { "Popover offsets must be non-negative" }
    val rtl = layoutDirection == LayoutDirection.Rtl
    val vertical = side == HarnessPopoverSide.Top || side == HarnessPopoverSide.Bottom
    val after = side == HarnessPopoverSide.Bottom || (side == HarnessPopoverSide.End && !rtl) || (side == HarnessPopoverSide.Start && rtl)
    val start = if (vertical) anchor.top else anchor.left
    val end = if (vertical) anchor.bottom else anchor.right
    val length = if (vertical) popup.height else popup.width
    val limit = if (vertical) window.height else window.width
    val preferred = if (after) end + sideOffset else start - sideOffset - length
    val opposite = if (after) start - sideOffset - length else end + sideOffset
    fun fits(position: Int): Boolean = position >= margin && position + length <= limit - margin
    val main = if (!fits(preferred) && fits(opposite)) opposite else preferred
    val logicalAlign = if (vertical && rtl) when (align) {
        HarnessPopoverAlign.Start -> HarnessPopoverAlign.End
        HarnessPopoverAlign.End -> HarnessPopoverAlign.Start
        HarnessPopoverAlign.Center -> align
    } else align
    val crossStart = if (vertical) anchor.left else anchor.top
    val crossEnd = if (vertical) anchor.right else anchor.bottom
    val crossLength = if (vertical) popup.width else popup.height
    val cross = when (logicalAlign) {
        HarnessPopoverAlign.Start -> crossStart
        HarnessPopoverAlign.Center -> crossStart + (crossEnd - crossStart - crossLength) / 2
        HarnessPopoverAlign.End -> crossEnd - crossLength
    }
    fun clamp(position: Int, extent: Int, available: Int): Int {
        val lower = margin.coerceAtMost(((available - extent) / 2).coerceAtLeast(0))
        return position.coerceIn(lower, (available - extent - lower).coerceAtLeast(lower))
    }
    return if (vertical) IntOffset(clamp(cross, popup.width, window.width), clamp(main, popup.height, window.height))
    else IntOffset(clamp(main, popup.width, window.width), clamp(cross, popup.height, window.height))
}

/**
 * [anchor] owns the trigger and receives a toggle callback. Keep this composable mounted when
 * closed; only popup content is mounted conditionally. Escape/outside clicks request owner dismissal.
 */
@Composable
public fun HarnessPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    anchor: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    side: HarnessPopoverSide = HarnessPopoverSide.Bottom,
    align: HarnessPopoverAlign = HarnessPopoverAlign.Start,
    sideOffset: Dp = 6.dp,
    focusable: Boolean = true,
    popupModifier: Modifier = Modifier,
    popupWidth: Dp? = null,
    popupMaxHeight: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    require(sideOffset >= 0.dp) { "Popover sideOffset must be non-negative" }
    val density = LocalDensity.current
    val offset = with(density) { sideOffset.roundToPx() }
    val margin = with(density) { HarnessTokens.Spacing.Two.roundToPx() }
    val window = LocalWindowInfo.current.containerSize
    val maxWidth = with(density) { (window.width - margin * 2).coerceAtLeast(0).toDp() }
    val windowHeight = with(density) { (window.height - margin * 2).coerceAtLeast(0).toDp() }
    require(popupWidth == null || popupWidth >= 0.dp) { "Popover width must be non-negative" }
    require(popupMaxHeight == Dp.Unspecified || popupMaxHeight >= 0.dp) { "Popover height must be non-negative" }
    val maxHeight = if (popupMaxHeight == Dp.Unspecified) windowHeight else popupMaxHeight.coerceAtMost(windowHeight)
    val provider = remember(side, align, offset, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                harnessPopoverPosition(anchorBounds, windowSize, popupContentSize, side, align, offset, margin, layoutDirection)
        }
    }
    Box(modifier) {
        anchor { onExpandedChange(!expanded) }
        if (expanded) {
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = onDismissRequest,
                properties = PopupProperties(focusable = focusable),
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = maxWidth).heightIn(max = maxHeight),
                    color = HarnessTokens.Colors.SurfaceElevated,
                    contentColor = HarnessTokens.Colors.Text,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(HarnessTokens.Corners.Medium),
                    border = BorderStroke(1.dp, HarnessTokens.Colors.BorderSubtle),
                    shadowElevation = HarnessTokens.Spacing.Two,
                ) {
                    val width = if (popupWidth == null) Modifier else Modifier.width(popupWidth.coerceAtMost(maxWidth))
                    Column(Modifier.then(width).then(popupModifier).widthIn(max = maxWidth)
                        .heightIn(max = maxHeight).verticalScroll(rememberScrollState()), content = content)
                }
            }
        }
    }
}

/** Typed menu with keyboard navigation and dispatch after the owner closes the popup. */
@Composable
public fun HarnessMenu(
    expanded: Boolean,
    entries: List<HarnessMenuEntry>,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    anchor: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    side: HarnessPopoverSide = HarnessPopoverSide.Bottom,
    align: HarnessPopoverAlign = HarnessPopoverAlign.Start,
    sideOffset: Dp = 6.dp,
    popupModifier: Modifier = Modifier,
    popupWidth: Dp? = null,
    popupMaxHeight: Dp = Dp.Unspecified,
    wrapLabels: Boolean = false,
    enabled: Boolean = true,
    selectionScope: Any? = Unit,
    onContentMountChange: ((Boolean) -> Unit)? = null,
    onContentApplied: (() -> Unit)? = null,
) {
    val snapshot = entries.toList()
    val model = remember(snapshot) { HarnessMenuModel(snapshot) }
    var activeKey by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(HarnessMenuSelection()) }
    // Scope changes invalidate both queued selections and retained platform callbacks, including A -> B -> A.
    val lifetime = remember(snapshot, selectionScope, enabled) { booleanArrayOf(true) }
    val latestLifetime by rememberUpdatedState(lifetime)
    val latestModel by rememberUpdatedState(model)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestExpanded by rememberUpdatedState(expanded)
    var pendingLifetime by remember { mutableStateOf<BooleanArray?>(null) }
    var pendingPopup by remember { mutableStateOf<MutableState<Boolean>?>(null) }
    val latestSelect by rememberUpdatedState(onSelect)
    val latestMountChange by rememberUpdatedState(onContentMountChange)
    val density = LocalDensity.current
    val menuMaxWidth = with(density) {
        (LocalWindowInfo.current.containerSize.width - HarnessTokens.Spacing.Two.roundToPx() * 2).coerceAtLeast(0).toDp()
    }.coerceAtMost(popupWidth ?: 320.dp)
    DisposableEffect(lifetime) { onDispose { lifetime[0] = false } }
    // Executed after the closed composition has removed the popup, before opening a new dialog/route.
    LaunchedEffect(expanded, enabled, selection, model, lifetime, pendingPopup?.value) {
        if ((!expanded || !enabled) && selection.pendingKey != null && pendingPopup?.value == false) {
            val key = selection.readyKey(false, latestModel)
            val current = latestEnabled && pendingLifetime === latestLifetime && pendingLifetime?.get(0) == true
            selection = HarnessMenuSelection()
            pendingLifetime = null
            pendingPopup = null
            if (current && key != null) latestSelect(key)
        }
    }
    fun select(key: String, popupAlive: MutableState<Boolean>) {
        if (!popupAlive.value || !lifetime[0] || latestLifetime !== lifetime || !latestExpanded || !latestEnabled ||
            latestModel.entries != model.entries || latestModel.selectable(key) == null) return
        val next = selection.request(model, key)
        if (next != selection) {
            activeKey = key
            pendingLifetime = lifetime
            pendingPopup = popupAlive
            selection = next
            onExpandedChange(false)
        }
    }
    HarnessPopover(
        expanded = expanded && enabled, onDismissRequest = { onExpandedChange(false) }, onExpandedChange = onExpandedChange,
        anchor = anchor, modifier = modifier, side = side, align = align, sideOffset = sideOffset,
        popupModifier = popupModifier, popupWidth = popupWidth, popupMaxHeight = popupMaxHeight,
    ) {
        val popupAlive = remember { mutableStateOf(true) }
        DisposableEffect(popupAlive) {
            latestMountChange?.invoke(true)
            onDispose { popupAlive.value = false; latestMountChange?.invoke(false) }
        }
        if (onContentApplied != null) SideEffect { onContentApplied() }
        val focus = remember { FocusRequester() }
        LaunchedEffect(model) {
            activeKey = model.selectable(activeKey)?.key ?: model.navigate(null, HarnessNavigation.First)
            if (activeKey == null) focus.requestFocus()
        }
        Column(
            Modifier.widthIn(min = 180.dp.coerceAtMost(menuMaxWidth), max = menuMaxWidth)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionDown -> HarnessNavigation.Next
                        Key.DirectionUp -> HarnessNavigation.Previous
                        Key.MoveHome -> HarnessNavigation.First
                        Key.MoveEnd -> HarnessNavigation.Last
                        else -> null
                    }
                    val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar
                    val dismiss = event.key == Key.Escape || event.key == Key.Tab
                    if (direction == null && !activate && !dismiss) false
                    else {
                        if (event.type == KeyEventType.KeyDown) {
                            if (direction != null) activeKey = model.navigate(activeKey, direction)
                            if (activate) activeKey?.let { select(it, popupAlive) }
                            if (dismiss) onExpandedChange(false)
                        }
                        true
                    }
                }
                .focusable().padding(HarnessTokens.Spacing.One),
        ) {
            snapshot.forEach { entry ->
                when (entry) {
                    HarnessMenuEntry.Separator -> HarnessMenuSeparator()
                    is HarnessMenuEntry.Item -> key(entry.key) {
                        val key = entry.key
                        val itemFocus = remember(key) { FocusRequester() }
                        LaunchedEffect(activeKey, entry.interactive) {
                            if (activeKey == key && entry.interactive) itemFocus.requestFocus()
                        }
                        HarnessActionSurface(
                            label = entry.text, onClick = { select(key, popupAlive) },
                            modifier = Modifier.fillMaxWidth()
                                .then(if (wrapLabels) Modifier.heightIn(min = HarnessTokens.Control.Row) else Modifier.height(HarnessTokens.Control.Row))
                                .focusRequester(itemFocus),
                            state = HarnessButtonState(entry.enabled, entry.selected, entry.state),
                            variant = HarnessButtonVariant.Ghost, tag = entry.testTag, highlighted = activeKey == key,
                        ) { color ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Two,
                                    vertical = if (wrapLabels) HarnessTokens.Spacing.Two else 0.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
                            ) {
                                if (entry.state == HarnessControlState.Loading) HarnessProgress(color)
                                if (entry.selected) HarnessText("✓", color = color)
                                HarnessText(entry.text, Modifier.weight(1f), color = if (entry.destructive && entry.enabled) HarnessTokens.Colors.Danger else color,
                                    maxLines = if (wrapLabels) Int.MAX_VALUE else 1)
                                entry.shortcut?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
public fun HarnessMenuSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(vertical = HarnessTokens.Spacing.One).semantics { testTag = "menu-separator" }, color = HarnessTokens.Colors.BorderSubtle)
}
