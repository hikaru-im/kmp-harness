package im.hikaru.harness.client.panels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.ui.foundation.HarnessTokens
import im.hikaru.harness.ui.foundation.LocalHarnessCodeTextStyle
import kotlin.math.floor

public data class PtyGlyph(val column: Int, val endColumn: Int, val text: String, val style: TerminalTextStyle)
public data class PtyCell(val line: Int, val column: Int) : Comparable<PtyCell> {
    override fun compareTo(other: PtyCell): Int = compareValuesBy(this, other, PtyCell::line, PtyCell::column)
}
public data class PtySelection(val anchor: PtyCell, val end: PtyCell) {
    public val start: PtyCell get() = minOf(anchor, end)
    public val finish: PtyCell get() = maxOf(anchor, end)
}
public class IncompatibleTerminalMapping : IllegalArgumentException("终端快照缺少精确列映射；仅旧可打印 ASCII 可安全显示。")

/** Native offsets are the only width authority. UTF-16 pairs form a single drawing/copy unit. */
public object PtyGridModel {
    public fun offsets(run: TerminalTextRun): List<Int> = run.columnOffsets ?:
        if (run.text.length == run.span && run.text.all { it in ' '..'~' }) (0..run.span).toList()
        else throw IncompatibleTerminalMapping()

    public fun glyphs(line: TerminalLineSnapshot): List<PtyGlyph> = buildList {
        for (run in line.runs) {
            val offsets = offsets(run)
            var index = 0
            while (index < run.text.length) {
                val units = if (run.text[index].isHighSurrogate() && index + 1 < run.text.length &&
                    run.text[index + 1].isLowSurrogate()) 2 else 1
                val value = run.text.substring(index, index + units)
                add(PtyGlyph(run.column + offsets[index], run.column + offsets[index + units], value, run.style))
                index += units
            }
        }
    }

    public fun lines(snapshot: TerminalSnapshot): List<TerminalLineSnapshot> =
        if (snapshot.alternate) snapshot.screen else snapshot.history + snapshot.screen

    public fun validate(snapshot: TerminalSnapshot): String? = try {
        lines(snapshot).forEach { line -> line.runs.forEach { offsets(it) } }; null
    } catch (error: IncompatibleTerminalMapping) { error.message }

    public fun copy(snapshot: TerminalSnapshot, selection: PtySelection?): String {
        if (selection == null || selection.start == selection.finish) return ""
        val lines = lines(snapshot)
        val first = selection.start
        val last = selection.finish
        if (first.line !in lines.indices || last.line !in lines.indices) return ""
        return buildString {
            for (row in first.line..last.line) {
                val start = (if (row == first.line) first.column else 0).coerceIn(0, snapshot.size.columns)
                val end = (if (row == last.line) last.column else snapshot.size.columns).coerceIn(start, snapshot.size.columns)
                var column = start
                for (glyph in glyphs(lines[row])) {
                    if (glyph.endColumn <= start || glyph.column >= end) continue
                    repeat((glyph.column - column).coerceAtLeast(0)) { append(' ') }
                    if (TerminalTextAttribute.Hidden in glyph.style.attributes) repeat((minOf(end, glyph.endColumn) - maxOf(start, glyph.column)).coerceAtLeast(0)) { append(' ') }
                    else append(glyph.text)
                    column = maxOf(column, glyph.endColumn)
                }
                repeat((end - column).coerceAtLeast(0)) { append(' ') }
                if (row < last.line && !lines[row].wrapped) append('\n')
            }
        }
    }

    public fun viewport(width: Float, height: Float, cellWidth: Float, rowHeight: Float): TerminalSize {
        require(cellWidth > 0 && rowHeight > 0)
        return TerminalSize(floor(height.coerceAtLeast(0f) / rowHeight).toInt().coerceIn(2, 200),
            floor(width.coerceAtLeast(0f) / cellWidth).toInt().coerceIn(5, 500))
    }

    public fun cursor(snapshot: TerminalSnapshot, top: Int, visibleRows: Int, focused: Boolean, canInput: Boolean): PtyCell? {
        if (!focused || !canInput || !snapshot.cursor.visible || snapshot.cursor.column >= snapshot.size.columns) return null
        val lines = lines(snapshot)
        if (top + visibleRows < lines.size) return null
        val row = (if (snapshot.alternate) 0 else snapshot.history.size) + snapshot.cursor.row
        return PtyCell(row, snapshot.cursor.column).takeIf { row in top until top + visibleRows }
    }
}

/** One reading address for history + live screen; appending output follows only the bottom reader. */
public class PtyGridReading {
    public var top: Int by mutableStateOf(0)
        private set
    public var visibleRows: Int by mutableStateOf(2)
        private set
    public var selection: PtySelection? by mutableStateOf(null)
    public var follow: Boolean by mutableStateOf(true)
        private set
    public var total: Int by mutableStateOf(0)
        private set
    private var alternate: Boolean? = null
    private var previousLines: List<TerminalLineSnapshot> = emptyList()
    private var wheelRemainder = 0f
    public val maxTop: Int get() = (total - visibleRows).coerceAtLeast(0)
    public fun update(snapshot: TerminalSnapshot, rows: Int) {
        val next = PtyGridModel.lines(snapshot)
        val bufferChanged = alternate != null && alternate != snapshot.alternate
        val anchor = previousLines.getOrNull(top)
        if (bufferChanged) { selection = null; follow = true }
        else if (!follow && anchor != null && next.getOrNull(top) != anchor) {
            val address = next.indices.filter { next[it] == anchor }.minByOrNull { kotlin.math.abs(it - top) }
            if (address != null) {
                val shift = address - top
                selection = selection?.let { it.copy(anchor = it.anchor.copy(line = it.anchor.line + shift),
                    end = it.end.copy(line = it.end.line + shift)) }
                top = address
            } else selection = null
        }
        alternate = snapshot.alternate; visibleRows = rows.coerceAtLeast(1); total = next.size
        top = if (follow) maxTop else top.coerceIn(0, maxTop)
        if (selection?.let { it.start.line !in next.indices || it.finish.line !in next.indices ||
                it.start.column > snapshot.size.columns || it.finish.column > snapshot.size.columns } == true) selection = null
        previousLines = next
    }
    public fun scrollLines(delta: Int) {
        top = (top + delta).coerceIn(0, maxTop)
        follow = top == maxTop
    }
    public fun scrollPixels(delta: Float, rowHeight: Float): Float {
        wheelRemainder += -delta / rowHeight
        val rows = wheelRemainder.toInt()
        wheelRemainder -= rows
        scrollLines(rows)
        return delta
    }
    public fun bottom() { follow = true; top = maxTop }
    public fun scrollbar(fraction: Float) { top = (fraction.coerceIn(0f, 1f) * maxTop).toInt(); follow = top == maxTop }
}

/** 256-color xterm palette. Default colors follow the real app theme. */
public fun terminalColor(value: TerminalColorValue, default: Color): Color = when (value) {
    TerminalColorValue.Default -> default
    is TerminalColorValue.Rgb -> Color(value.red, value.green, value.blue)
    is TerminalColorValue.Indexed -> {
        val base = listOf(0x000000, 0xcd0000, 0x00cd00, 0xcdcd00, 0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5,
            0x7f7f7f, 0xff0000, 0x00ff00, 0xffff00, 0x5c5cff, 0xff00ff, 0x00ffff, 0xffffff)
        val index = value.index
        if (index < 16) Color(0xff000000L or base[index].toLong())
        else if (index >= 232) { val gray = 8 + (index - 232) * 10; Color(gray, gray, gray) }
        else {
            val cube = index - 16
            fun channel(component: Int): Int = if (component == 0) 0 else 55 + component * 40
            Color(channel(cube / 36), channel(cube / 6 % 6), channel(cube % 6))
        }
    }
}

/** Both consumers measure their drawable body after reserving the same 10dp scrollbar gutter. */
internal data class PtyTerminalMetrics(
    val measurer: TextMeasurer, val style: TextStyle, val cellWidth: Float, val rowHeight: Float,
) {
    fun viewport(size: IntSize): TerminalSize? = if (size.width > 0 && size.height > 0)
        PtyGridModel.viewport(size.width.toFloat(), size.height.toFloat(), cellWidth, rowHeight) else null
}

@Composable
internal fun rememberPtyTerminalMetrics(): PtyTerminalMetrics {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // HarnessTheme has already compensated this style for its UI font scale.
    val style = LocalHarnessCodeTextStyle.current
    val measured = measurer.measure(AnnotatedString("MMMM"), style = style, softWrap = false)
    val declaredHeight = if (style.lineHeight != TextUnit.Unspecified) with(density) { style.lineHeight.toPx() } else 0f
    return PtyTerminalMetrics(measurer, style, measured.size.width / 4f,
        measured.size.height.toFloat().coerceAtLeast(declaredHeight))
}

@Composable
public fun PtyTerminalGrid(
    snapshot: TerminalSnapshot, reading: PtyGridReading, focused: Boolean, canInput: Boolean,
    onViewport: (TerminalSize) -> Unit, onFocusRequest: () -> Unit, modifier: Modifier = Modifier,
) {
    val metrics = rememberPtyTerminalMetrics()
    val measurer = metrics.measurer
    val density = LocalDensity.current
    val style = metrics.style
    val cell = metrics.cellWidth
    val rowHeight = metrics.rowHeight
    var bodySize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = metrics.viewport(bodySize)
    val currentViewport by rememberUpdatedState(onViewport)
    val visibleRows = viewport?.rows ?: snapshot.size.rows
    LaunchedEffect(snapshot.metadata.key, viewport, style, density, cell, rowHeight) { viewport?.let(currentViewport) }
    SideEffect { reading.update(snapshot, visibleRows) }
    val lines = PtyGridModel.lines(snapshot)
    val foreground = HarnessTokens.Colors.Text
    val background = HarnessTokens.Colors.Surface
    val accent = HarnessTokens.Colors.TextMuted
    val scrollbarInteractions = remember(reading) { MutableInteractionSource() }
    val scrollbarHovered by scrollbarInteractions.collectIsHoveredAsState()
    var scrollbarActive by remember(reading) { mutableStateOf(false) }
    val scrollbarThumb = if (scrollbarHovered || scrollbarActive) HarnessTokens.Colors.BorderStrong else HarnessTokens.Colors.Border
    val scroll = rememberScrollableState { reading.scrollPixels(it, rowHeight) }
    fun address(position: Offset): PtyCell = PtyCell(
        (reading.top + floor(position.y / rowHeight).toInt()).coerceIn(0, lines.lastIndex),
        floor(position.x / cell).toInt().coerceIn(0, snapshot.size.columns))
    val currentAddress by rememberUpdatedState<(Offset) -> PtyCell>(::address)
    val currentFocusRequest by rememberUpdatedState(onFocusRequest)
    Row(modifier.clipToBounds()) {
        Canvas(Modifier.weight(1f).fillMaxHeight().onSizeChanged { bodySize = it }.scrollable(scroll, Orientation.Vertical)
            .pointerInput(snapshot.metadata.key, snapshot.alternate, cell, rowHeight) {
                detectTapGestures(onTap = { currentFocusRequest(); reading.selection = null })
            }.pointerInput(snapshot.metadata.key, snapshot.alternate, cell, rowHeight) {
                detectDragGestures(orientationLock = null, onDragStart = { down, slopTrigger, _ ->
                    currentFocusRequest(); reading.selection = PtySelection(currentAddress(down.position), currentAddress(slopTrigger.position))
                }, onDrag = { change, _ ->
                    reading.selection?.let { reading.selection = it.copy(end = currentAddress(change.position)) }; change.consume()
                })
            }.semantics {
                testTag = "pty-terminal-grid"
                stateDescription = "${snapshot.size.columns} columns ${snapshot.size.rows} rows; revision ${snapshot.revision}; ${snapshot.metadata.lifecycle}"
                text = AnnotatedString(lines.drop(reading.top).take(visibleRows).joinToString("\n") { line ->
                    PtyGridModel.glyphs(line).filter { TerminalTextAttribute.Hidden !in it.style.attributes }.joinToString("") { it.text }
                })
            }) {
            drawRect(background)
            for ((localRow, line) in lines.drop(reading.top).take(visibleRows).withIndex()) {
                val y = localRow * rowHeight
                for (run in line.runs) {
                    val inverse = TerminalTextAttribute.Inverse in run.style.attributes
                    val bg = terminalColor(if (inverse) run.style.foreground else run.style.background, if (inverse) foreground else background)
                    drawRect(bg, Offset(run.column * cell, y), Size(run.span * cell, rowHeight))
                }
                val selection = reading.selection
                val absoluteRow = reading.top + localRow
                if (selection != null && absoluteRow in selection.start.line..selection.finish.line) {
                    val from = if (absoluteRow == selection.start.line) selection.start.column else 0
                    val until = if (absoluteRow == selection.finish.line) selection.finish.column else snapshot.size.columns
                    drawRect(accent.copy(alpha = 0.3f), Offset(from * cell, y), Size((until - from) * cell, rowHeight))
                }
                for (glyph in PtyGridModel.glyphs(line)) {
                    val attributes = glyph.style.attributes
                    if (TerminalTextAttribute.Hidden in attributes) continue
                    val inverse = TerminalTextAttribute.Inverse in attributes
                    var fg = terminalColor(if (inverse) glyph.style.background else glyph.style.foreground, if (inverse) background else foreground)
                    if (TerminalTextAttribute.Dim in attributes) fg = fg.copy(alpha = 0.5f)
                    val glyphStyle = style.copy(color = fg,
                        fontWeight = if (TerminalTextAttribute.Bold in attributes) FontWeight.Bold else style.fontWeight,
                        fontStyle = if (TerminalTextAttribute.Italic in attributes) FontStyle.Italic else style.fontStyle,
                        textDecoration = if (TerminalTextAttribute.Underlined in attributes) TextDecoration.Underline else TextDecoration.None)
                    clipRect(glyph.column * cell, y, glyph.endColumn * cell, y + rowHeight) {
                        drawText(measurer, glyph.text, topLeft = Offset(glyph.column * cell, y), style = glyphStyle, softWrap = false)
                    }
                }
            }
            PtyGridModel.cursor(snapshot, reading.top, visibleRows, focused, canInput)?.let { cursor ->
                drawRect(foreground.copy(alpha = 0.55f), Offset(cursor.column * cell, (cursor.line - reading.top) * rowHeight), Size(cell, rowHeight))
            }
        }
        Canvas(Modifier.width(10.dp).fillMaxHeight().semantics { testTag = "pty-terminal-scrollbar" }
            .hoverable(scrollbarInteractions)
            .pointerInput(reading) {
                // Observe press/release without consuming input from the existing drag/tap handlers.
                try {
                    awaitPointerEventScope {
                        while (true) scrollbarActive = awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }
                    }
                } finally { scrollbarActive = false }
            }
            .pointerInput(reading) {
                detectDragGestures(onDragStart = { reading.scrollbar(it.y / size.height.coerceAtLeast(1)) },
                    onDrag = { change, _ -> reading.scrollbar(change.position.y / size.height.coerceAtLeast(1)); change.consume() })
            }.pointerInput(reading) { detectTapGestures { reading.scrollbar(it.y / size.height.coerceAtLeast(1)) } }) {
            drawRect(background)
            val fraction = (reading.visibleRows.toFloat() / reading.total.coerceAtLeast(1)).coerceIn(0.05f, 1f)
            val height = size.height * fraction
            val y = if (reading.maxTop == 0) 0f else (size.height - height) * reading.top / reading.maxTop
            drawRect(scrollbarThumb, Offset(0f, y), Size(size.width, height))
        }
    }
}
