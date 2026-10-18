package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Data only: action IDs belong to the caller; shortcuts are display labels, not key bindings. */
public sealed interface HarnessContextMenuEntry {
    public data object Divider : HarnessContextMenuEntry
    public data class Item(
        val id: String,
        val label: String,
        val icon: HarnessIconName? = null,
        val shortcut: String? = null,
        val enabled: Boolean = true,
        val destructive: Boolean = false,
        val children: List<HarnessContextMenuEntry> = emptyList(),
    ) : HarnessContextMenuEntry
}

/** Right-click wrapper. Left-click behavior stays with [content]; a leaf closes the entire menu. */
@Composable
public fun HarnessContextMenuArea(
    entries: List<HarnessContextMenuEntry>,
    modifier: Modifier = Modifier,
    onAction: (String) -> Unit = {},
    content: @Composable () -> Unit,
) {
    var position by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.pointerInput(entries) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed && entries.isNotEmpty()) {
                    position = event.changes.first().position
                    event.changes.forEach { it.consume() }
                }
            }
        }
    }) {
        content()
        position?.let { point ->
            val provider = remember(point) { object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val x = anchorBounds.left + point.x.toInt()
                    val y = anchorBounds.top + point.y.toInt()
                    return harnessPopoverPosition(IntRect(x, y, x, y), windowSize, popupContentSize,
                        sideOffset = 0, layoutDirection = layoutDirection)
                }
            } }
            Popup(provider, onDismissRequest = { position = null }, properties = PopupProperties(focusable = true)) {
                HarnessMenuCard(entries, onAction = { id -> position = null; onAction(id) },
                    onDismissRequest = { position = null })
            }
        }
    }
}

// Keep native focus in the root popup and route keys to the deepest open menu.
private class MenuKeyRouter { var handler: ((KeyEvent) -> Boolean)? = null }
private val LocalMenuKeyRouter = staticCompositionLocalOf<MenuKeyRouter?> { null }

/** Reusable menu card: grouped actions, hover/click submenus, and arrow-key navigation. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
public fun HarnessMenuCard(
    entries: List<HarnessContextMenuEntry>,
    onAction: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var active by remember(entries) { mutableStateOf(-1) }
    var submenu by remember(entries) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun activate(item: HarnessContextMenuEntry.Item) {
        if (!item.enabled) return
        if (item.children.isNotEmpty()) submenu = item.id else onAction(item.id)
    }
    val parentRouter = LocalMenuKeyRouter.current
    val childRouter = remember { MenuKeyRouter() }
    LaunchedEffect(Unit) { if (parentRouter == null) focus.requestFocus() }
    val cardSize = if (compact) Modifier.widthIn(min = 148.dp, max = 264.dp).width(IntrinsicSize.Max)
        else Modifier.width(264.dp)
    val handleKey: (KeyEvent) -> Boolean = { event ->
        if (submenu != null && childRouter.handler?.invoke(event) == true) true
        else if (event.type != KeyEventType.KeyDown) false else when (event.key) {
            Key.DirectionDown, Key.DirectionUp -> {
                val available = entries.indices.filter { (entries[it] as? HarnessContextMenuEntry.Item)?.enabled == true }
                if (available.isNotEmpty()) {
                    val current = available.indexOf(active)
                    active = available[if (event.key == Key.DirectionDown) (current + 1).mod(available.size)
                        else if (current < 0) available.lastIndex else (current - 1).mod(available.size)]
                    submenu = null
                }
                true
            }
            Key.Enter, Key.NumPadEnter, Key.DirectionRight -> {
                (entries.getOrNull(active) as? HarnessContextMenuEntry.Item)?.let { item ->
                    if (event.key != Key.DirectionRight || item.children.isNotEmpty()) activate(item)
                }
                true
            }
            Key.Escape, Key.DirectionLeft -> { onDismissRequest(); true }
            else -> false
        }
    }
    SideEffect { parentRouter?.handler = handleKey }
    DisposableEffect(parentRouter) { onDispose { parentRouter?.handler = null } }
    Surface(modifier.then(cardSize).semantics { testTag = "context-menu-card" }
        .focusRequester(focus).onPreviewKeyEvent(handleKey).focusable(), shape = RoundedCornerShape(8.dp),
        color = HarnessTokens.Colors.Surface,
        border = BorderStroke(0.5.dp, HarnessTokens.Colors.Text.copy(alpha = 0.12f)), shadowElevation = 12.dp,
    ) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            entries.forEachIndexed { index, entry ->
                when (entry) {
                    HarnessContextMenuEntry.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp),
                        thickness = 0.5.dp, color = HarnessTokens.Colors.BorderSubtle)
                    is HarnessContextMenuEntry.Item -> Box {
                        Surface(color = if (active == index) HarnessTokens.Colors.SurfaceSelected else HarnessTokens.Colors.Surface,
                            shape = RectangleShape) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 28.dp)
                                .onPointerEvent(PointerEventType.Enter) {
                                    active = index
                                    submenu = entry.id.takeIf { entry.enabled && entry.children.isNotEmpty() }
                                }
                                .clickable(enabled = entry.enabled, role = Role.Button) { active = index; activate(entry) }
                                .semantics {
                                    testTag = "context-menu-${entry.id}"
                                    if (entry.children.isNotEmpty()) stateDescription = if (submenu == entry.id) "已展开" else "有子菜单"
                                }.padding(horizontal = 20.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val color = when {
                                    !entry.enabled -> HarnessTokens.Colors.TextDisabled
                                    entry.destructive -> HarnessTokens.Colors.Danger
                                    else -> HarnessTokens.Colors.Text
                                }
                                Box(Modifier.size(16.dp)) { entry.icon?.let { HarnessIcon(it, tint = color, size = 16.dp, strokeRatio = 0.065f) } }
                                HarnessText(entry.label, Modifier.weight(1f), color = color,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
                                if (entry.children.isNotEmpty()) HarnessIcon(HarnessIconName.ChevronRight,
                                    tint = HarnessTokens.Colors.TextMuted, size = 14.dp)
                                else entry.shortcut?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)) }
                            }
                        }
                        if (submenu == entry.id && entry.children.isNotEmpty()) {
                            val provider = remember { object : PopupPositionProvider {
                                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                                    harnessPopoverPosition(anchorBounds, windowSize, popupContentSize,
                                        side = HarnessPopoverSide.End, sideOffset = 0, layoutDirection = layoutDirection)
                            } }
                            // Only the root popup owns outside-click dismissal. A focusable child
                            // treats the parent menu as outside and intercepts its mouse input.
                            Popup(provider, onDismissRequest = { submenu = null; focus.requestFocus() },
                                properties = PopupProperties(focusable = false)) {
                                CompositionLocalProvider(LocalMenuKeyRouter provides childRouter) {
                                    HarnessMenuCard(entry.children, onAction, { submenu = null; focus.requestFocus() }, compact = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
