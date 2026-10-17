package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.ui.foundation.LocalHarnessCodeTextStyle
import im.hikaru.harness.ui.foundation.LocalHarnessContentTextStyle
import io.ktor.http.Url
import org.intellij.markdown.MarkdownElementTypes as Elements
import org.intellij.markdown.MarkdownTokenTypes as Tokens
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes as GfmElements
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes as GfmTokens
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.entities.Entities
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

/**
 * Native Compose rendering of JetBrains Markdown's GFM AST (org.jetbrains:markdown:0.7.18).
 * The parser owns block/inline syntax, nesting and reference resolution; no HTML/WebView is used.
 * Supported presentation: headings, paragraphs, emphasis/strong/strike, code spans and blocks,
 * links/references/autolinks, nested lists/quotes, rules and aligned pipe tables. Task items show
 * read-only checkbox glyphs. Images display alt text without fetching remote content. Raw HTML,
 * math, footnotes and other unsupported constructs remain literal rather than executing/loading.
 * No syntax highlighting or in-document anchor navigation. Render recursion is bounded at 48.
 */
@Composable
internal fun ConversationMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ConversationAppearance.ink,
) {
    val document = remember(text) {
        val source = text.replace("\r\n", "\n").replace('\r', '\n')
        val root = MarkdownParser(GFMFlavourDescriptor(), assertionsEnabled = false)
            .buildMarkdownTreeFromString(source as CharSequence)
        ConversationMarkdownDocument(source, root, LinkMap.buildLinkMap(root, source))
    }
    SelectionContainer(modifier) {
        ConversationMarkdownBlocks(document.root.children, document, color)
    }
}

private data class ConversationMarkdownDocument(val source: String, val root: ASTNode, val links: LinkMap) {
    fun text(node: ASTNode): String = source.substring(node.startOffset, node.endOffset)
}
private const val conversationMarkdownMaxDepth = 48
private val conversationHeadingTypes = listOf(Elements.ATX_1, Elements.ATX_2, Elements.ATX_3, Elements.ATX_4, Elements.ATX_5, Elements.ATX_6)

@Composable
private fun ConversationMarkdownBlocks(
    nodes: List<ASTNode>, document: ConversationMarkdownDocument, color: Color, depth: Int = 0,
    inQuote: Boolean = false, inListItem: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        val visible = nodes.filter { it.type !in listOf(Elements.LINK_DEFINITION, Tokens.WHITE_SPACE, Tokens.EOL, Tokens.BLOCK_QUOTE, Tokens.LIST_BULLET, Tokens.LIST_NUMBER, GfmTokens.CHECK_BOX) }
        visible.forEachIndexed { index, node ->
            if (depth >= conversationMarkdownMaxDepth) {
                BasicText(document.text(node), style = conversationMarkdownBodyStyle().copy(color = color))
            } else when (node.type) {
                Elements.PARAGRAPH -> ConversationMarkdownText(
                    node.children, document, color,
                    Modifier.padding(
                        top = when { inQuote || index == 0 -> 0.dp; inListItem -> 14.dp
                            visible[index - 1].type == Elements.PARAGRAPH -> 14.dp
                            visible[index - 1].type in conversationHeadingTypes -> 0.dp
                            else -> 7.dp },
                        bottom = if (inQuote || inListItem || index == visible.lastIndex) 0.dp else 3.5.dp,
                    ),
                    conversationMarkdownBodyStyle().copy(lineHeight = if (inQuote) 21.sp else 22.75.sp),
                )
                in conversationHeadingTypes, Elements.SETEXT_1, Elements.SETEXT_2 -> {
                    val level = when (node.type) {
                        Elements.SETEXT_1 -> 1
                        Elements.SETEXT_2 -> 2
                        else -> conversationHeadingTypes.indexOf(node.type) + 1
                    }
                    val size = listOf(21f, 17.5f, 15.75f, 14f, 14f, 14f)[level - 1]
                    val line = listOf(28f, 24.5f, 24.5f, 21f, 22.75f, 22.75f)[level - 1]
                    ConversationMarkdownText(
                        node.children.filter { it.type == Tokens.ATX_CONTENT || it.type == Tokens.SETEXT_CONTENT },
                        document, color, Modifier.padding(
                            top = if (level <= 4 && index != 0) 14.dp else 0.dp,
                            bottom = when (level) { 1 -> 7.dp; 2, 3 -> 3.5.dp; else -> 0.dp },
                        ).semantics { heading() },
                        conversationMarkdownBodyStyle().copy(fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Elements.CODE_FENCE, Elements.CODE_BLOCK -> ConversationMarkdownCode(node, document)
                Elements.BLOCK_QUOTE -> Row(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp).padding(vertical = 7.dp).height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(17.5.dp),
                ) {
                    Box(Modifier.width(3.5.dp).fillMaxHeight().background(
                        ConversationAppearance.muted.copy(alpha = 0.35f), RoundedCornerShape(1.75.dp),
                    ))
                    Box(Modifier.weight(1f)) {
                        ConversationMarkdownBlocks(node.children, document, color, depth + 1, inQuote = true)
                    }
                }
                Elements.ORDERED_LIST, Elements.UNORDERED_LIST -> {
                    val ordered = node.type == Elements.ORDERED_LIST
                    val items = node.children.filter { it.type == Elements.LIST_ITEM }
                    val start = items.firstOrNull()?.children?.firstOrNull { it.type == Tokens.LIST_NUMBER }
                        ?.let { document.text(it).trim().dropLast(1).toLongOrNull() } ?: 1L
                    Column(Modifier.fillMaxWidth()) {
                        items.forEachIndexed { index, item ->
                            val checkbox = item.children.firstOrNull { it.type == GfmTokens.CHECK_BOX }
                            val marker = when {
                                checkbox != null -> if (document.text(checkbox).contains('x', true)) "☑" else "☐"
                                ordered -> "${start + index}."
                                else -> "•"
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.25.dp)) {
                                BasicText(
                                    marker, Modifier.width(22.75.dp),
                                    style = conversationMarkdownBodyStyle().copy(
                                        color = color, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                Box(Modifier.weight(1f)) {
                                    ConversationMarkdownBlocks(item.children, document, color, depth + 1, inQuote, inListItem = true)
                                }
                            }
                        }
                    }
                }
                GfmElements.TABLE -> ConversationMarkdownTable(node, document, color)
                Tokens.HORIZONTAL_RULE -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.5.dp).height(1.dp)
                        .background(ConversationAppearance.muted.copy(alpha = 0.25f)),
                )
                Elements.LINK_DEFINITION, Tokens.WHITE_SPACE, Tokens.EOL, Tokens.BLOCK_QUOTE,
                Tokens.LIST_BULLET, Tokens.LIST_NUMBER, GfmTokens.CHECK_BOX -> Unit
                else -> if (document.text(node).isNotBlank()) {
                    // Unsupported block extensions and HTML remain visible as source text.
                    BasicText(document.text(node), style = conversationMarkdownBodyStyle().copy(color = color))
                }
            }
        }
    }
}

@Composable
private fun conversationMarkdownBodyStyle(): TextStyle =
    LocalHarnessContentTextStyle.current.copy(fontSize = 14.sp, lineHeight = 22.75.sp,
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None))

@Composable
private fun ConversationMarkdownText(
    nodes: List<ASTNode>, document: ConversationMarkdownDocument, color: Color,
    modifier: Modifier = Modifier, style: TextStyle = conversationMarkdownBodyStyle(),
) {
    val uriHandler = LocalUriHandler.current
    val code = LocalHarnessCodeTextStyle.current
    val codeBackground = ConversationAppearance.codeBackground
    val linkColor = if (ConversationAppearance.dark) Color(0xFF8AB4F8) else Color(0xFF145BC7)
    val annotated = remember(nodes, document, code, codeBackground, linkColor, uriHandler) {
        val rendering = ConversationInlineRendering(
            SpanStyle(
                fontFamily = code.fontFamily ?: FontFamily.Monospace, fontSize = code.fontSize,
                fontWeight = code.fontWeight, fontStyle = code.fontStyle,
                letterSpacing = code.letterSpacing, background = codeBackground,
            ),
            linkColor,
            openLink = { destination ->
                safeConversationMarkdownLink(destination)?.let { safe -> runCatching { uriHandler.openUri(safe) } }
            },
        )
        buildAnnotatedString { appendConversationMarkdownInline(nodes, document, rendering) }
    }
    BasicText(annotated, modifier, style = style.copy(color = color))
}

@Suppress("DEPRECATION") // Portable synchronous clipboard API shared by all configured targets.
@Composable
private fun ConversationMarkdownCode(node: ASTNode, document: ConversationMarkdownDocument) {
    val clipboard = LocalClipboardManager.current
    val (language, code) = remember(node, document) { conversationCodeContent(node, document) }
    val dark = ConversationAppearance.dark
    val shape = RoundedCornerShape(20.dp)
    // #181818 with a white 8% overlay rounds to #2b2b2b.
    val background = if (dark) Color(0xFF2B2B2B) else ConversationAppearance.codeBackground
    val border = if (dark) Color.White.copy(alpha = 0.043f) else Color.Black.copy(alpha = 0.07f)
    Column(Modifier.fillMaxWidth().padding(vertical = 17.5.dp).clip(shape).background(background).border(1.dp, border, shape)) {
        DisableSelection {
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(start = 20.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ConversationIcon(ConversationGlyph.Code, ConversationAppearance.ink)
                BasicText(
                    language.ifBlank { "纯文本" }, Modifier.weight(1f).padding(start = 8.dp, end = 12.dp),
                    style = LocalHarnessContentTextStyle.current.copy(
                        color = ConversationAppearance.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    ), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                ConversationActionButton(ConversationGlyph.Expand, "展开代码（暂不可用）", size = 26.dp,
                    tint = ConversationAppearance.muted)
                ConversationActionButton(
                    ConversationGlyph.Copy, "复制代码", onClick = { clipboard.setText(AnnotatedString(code)) },
                    size = 26.dp, tint = ConversationAppearance.muted,
                )
            }
        }
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            BasicText(
                code,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp)
                    .merge(LocalHarnessCodeTextStyle.current).copy(color = ConversationAppearance.ink),
                softWrap = false,
            )
        }
    }
}

private fun conversationCodeContent(node: ASTNode, document: ConversationMarkdownDocument): Pair<String, String> {
    val fenced = node.type == Elements.CODE_FENCE
    val language = node.children.firstOrNull { it.type == Tokens.FENCE_LANG }
        ?.let { document.text(it).trim().takeWhile { c -> !c.isWhitespace() }.take(64) }.orEmpty()
    val indent = if (fenced) document.text(node).takeWhile { it == ' ' }.length else 4
    var started = !fenced
    val code = buildString {
        node.children.forEach { child ->
            if (started) when (child.type) {
                Tokens.CODE_FENCE_CONTENT, Tokens.CODE_LINE -> append(HtmlGenerator.trimIndents(document.text(child), indent))
                Tokens.EOL -> append('\n')
            }
            if (child.type == Tokens.EOL) started = true
        }
    }
    // Only remove the EOL separating content from an actual closing fence. An open fence
    // (common during streaming) retains every content character, including its final line.
    val closed = node.children.any { it.type == Tokens.CODE_FENCE_END }
    return language to if (fenced && closed) code.removeSuffix("\n") else code
}

@Composable
private fun ConversationMarkdownTable(node: ASTNode, document: ConversationMarkdownDocument, color: Color) {
    val rows = remember(node) {
        node.children.filter { it.type == GfmElements.HEADER || it.type == GfmElements.ROW }
            .map { row -> row.children.filter { it.type == GfmTokens.CELL } }
    }
    val columns = rows.firstOrNull()?.size ?: return
    if (columns == 0) return
    val alignment = remember(node, document) {
        node.children.firstOrNull { it.type == GfmTokens.TABLE_SEPARATOR }
            ?.let { document.text(it).trim().removePrefix("|").removeSuffix("|").split('|') }
            .orEmpty().map { cell ->
                when {
                    cell.trim().startsWith(':') && cell.trim().endsWith(':') -> TextAlign.Center
                    cell.trim().endsWith(':') -> TextAlign.End
                    else -> TextAlign.Start
                }
            }
    }
    val widths = remember(rows, document) {
        (0 until columns).map { column ->
            rows.maxOf { row -> row.getOrNull(column)?.let { document.text(it).length } ?: 0 }
                .let { (it.coerceAtMost(40) * 7 + 24).coerceIn(112, 320).dp }
        }
    }
    val border = ConversationAppearance.muted.copy(alpha = 0.22f)
    val configuredCodeSize = LocalHarnessCodeTextStyle.current.fontSize
    val tableSize = if (configuredCodeSize.isSp) maxOf(12.25f, configuredCodeSize.value).sp else 12.25.sp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        val naturalWidth = widths.reduce { a, b -> a + b }
        val extraWidth = ((maxWidth - naturalWidth).coerceAtLeast(0.dp)) / columns
        val layoutWidths = widths.map { it + extraWidth }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) Box(Modifier.width(layoutWidths.reduce { a, b -> a + b }).height(1.dp).background(border))
                Row {
                    (0 until columns).forEach { column ->
                        ConversationMarkdownText(
                            row.getOrNull(column)?.children.orEmpty(), document, color,
                            Modifier.width(layoutWidths[column]).padding(
                                end = when { column != columns - 1 -> 21.dp; rowIndex == 0 -> 35.dp; else -> 0.dp },
                                top = if (rowIndex == 0) 7.dp else 8.75.dp,
                                bottom = when { rowIndex == 0 -> 7.dp; rowIndex == rows.lastIndex -> 21.dp; else -> 8.75.dp },
                            ),
                            conversationMarkdownBodyStyle().copy(
                                fontSize = tableSize, lineHeight = if (rowIndex == 0) 14.sp else 22.75.sp,
                                fontWeight = if (rowIndex == 0) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = alignment.getOrElse(column) { TextAlign.Start },
                            ),
                        )
                    }
                }
            }
        }
    }
}

private data class ConversationInlineRendering(val codeStyle: SpanStyle, val linkColor: Color, val openLink: (String) -> Unit)

private fun AnnotatedString.Builder.appendConversationMarkdownInline(
    nodes: List<ASTNode>, document: ConversationMarkdownDocument, rendering: ConversationInlineRendering,
    depth: Int = 0, allowLinks: Boolean = true,
) {
    if (depth >= conversationMarkdownMaxDepth) { nodes.forEach { append(document.text(it)) }; return }
    nodes.forEachIndexed { index, node ->
        when (node.type) {
            Elements.EMPH, Elements.STRONG, GfmElements.STRIKETHROUGH -> {
                val style = when (node.type) {
                    Elements.EMPH -> SpanStyle(fontStyle = FontStyle.Italic)
                    Elements.STRONG -> SpanStyle(fontWeight = FontWeight.Bold)
                    else -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                }
                val delimiter = if (node.type == GfmElements.STRIKETHROUGH) GfmTokens.TILDE else Tokens.EMPH
                val content = node.children.dropWhile { it.type == delimiter }.dropLastWhile { it.type == delimiter }
                withStyle(style) { appendConversationMarkdownInline(content, document, rendering, depth + 1, allowLinks) }
            }
            Elements.CODE_SPAN -> {
                var code = node.children.drop(1).dropLast(1).joinToString("") { document.text(it) }.replace('\n', ' ')
                if (code.startsWith(' ') && code.endsWith(' ') && code.any { it != ' ' }) code = code.drop(1).dropLast(1)
                var parent = node.parent
                while (parent != null && parent.type != GfmTokens.CELL) parent = parent.parent
                if (parent != null) code = code.replace("\\|", "|")
                withStyle(rendering.codeStyle) { append(code) }
            }
            Elements.INLINE_LINK, Elements.FULL_REFERENCE_LINK, Elements.SHORT_REFERENCE_LINK -> {
                val label = node.children.firstOrNull { it.type == Elements.LINK_TEXT }
                    ?: node.children.firstOrNull { it.type == Elements.LINK_LABEL }
                val destination = if (node.type == Elements.INLINE_LINK) {
                    node.children.firstOrNull { it.type == Elements.LINK_DESTINATION }?.let { document.text(it) }
                } else {
                    node.children.firstOrNull { it.type == Elements.LINK_LABEL }
                        ?.let { document.links.getLinkInfo(document.text(it))?.destination?.toString() }
                }
                val children = label?.children?.drop(1)?.dropLast(1).orEmpty()
                if (label == null || destination == null) append(document.text(node))
                else appendConversationMarkdownLink(destination, rendering, allowLinks) {
                    appendConversationMarkdownInline(children, document, rendering, depth + 1, allowLinks = false)
                }
            }
            Elements.IMAGE -> appendConversationMarkdownInline(
                node.children.filter { it.type != Tokens.EXCLAMATION_MARK }, document, rendering, depth + 1, allowLinks = false,
            )
            Elements.AUTOLINK, GfmTokens.GFM_AUTOLINK, Tokens.AUTOLINK, Tokens.EMAIL_AUTOLINK -> {
                val label = document.text(node).removeSurrounding("<", ">")
                val destination = when {
                    label.startsWith("www.") -> "https://$label"
                    '@' in label && ":" !in label -> "mailto:$label"
                    else -> label
                }
                appendConversationMarkdownLink(destination, rendering, allowLinks) { append(conversationMarkdownUnescape(label)) }
            }
            Tokens.HARD_LINE_BREAK -> append('\n')
            Tokens.EOL -> if (nodes.getOrNull(index - 1)?.type != Tokens.HARD_LINE_BREAK) append(' ')
            Tokens.BLOCK_QUOTE -> {
                var parent = node.parent
                while (parent != null && parent.type != GfmTokens.CELL) parent = parent.parent
                if (parent != null) append(document.text(node).trimStart())
            }
            Tokens.ATX_CONTENT, Tokens.SETEXT_CONTENT, GfmTokens.CELL ->
                appendConversationMarkdownInline(node.children, document, rendering, depth + 1, allowLinks)
            Tokens.WHITE_SPACE -> {
                if (index != 0 && index != nodes.lastIndex && nodes.getOrNull(index + 1)?.type != Tokens.HARD_LINE_BREAK) {
                    append(document.text(node))
                }
            }
            else -> if (node.children.isNotEmpty()) {
                // Unsupported inline math/highlight keeps its source notation.
                append(document.text(node))
            } else append(conversationMarkdownUnescape(document.text(node)))
        }
    }
}

private fun AnnotatedString.Builder.appendConversationMarkdownLink(
    destination: String, rendering: ConversationInlineRendering, enabled: Boolean,
    label: AnnotatedString.Builder.() -> Unit,
) {
    val decoded = conversationMarkdownUnescape(destination.removeSurrounding("<", ">"))
    val safe = if (enabled) safeConversationMarkdownLink(decoded) else null
    if (safe == null) label()
    else withLink(LinkAnnotation.Url(
        safe,
        styles = TextLinkStyles(style = SpanStyle(color = rendering.linkColor, textDecoration = TextDecoration.Underline)),
        linkInteractionListener = { rendering.openLink(safe) },
    ), label)
}

private val conversationEntity = Regex("\\\\([!\"#$%&'()*+,\\-./:;<=>?@\\[\\]\\\\^_`{|}~])|&(?:#[xX][0-9a-fA-F]{1,8}|#[0-9]{1,8}|[a-zA-Z][a-zA-Z0-9]+);")
/** Decode to text (the parser's HTML EntityConverter intentionally HTML-escapes &, < and >). */
private fun conversationMarkdownUnescape(text: String): String = conversationEntity.replace(text) { match ->
    if (match.groupValues[1].isNotEmpty()) match.groupValues[1]
    else {
        val entity = match.value
        val code = when {
            entity.startsWith("&#x", true) -> entity.substring(3, entity.length - 1).toIntOrNull(16)
            entity.startsWith("&#") -> entity.substring(2, entity.length - 1).toIntOrNull()
            else -> Entities.map[entity]
        }
        when {
            code == null -> entity
            code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF -> "�"
            code <= 0xFFFF -> code.toChar().toString()
            else -> {
                val adjusted = code - 0x10000
                "${(0xD800 + (adjusted shr 10)).toChar()}${(0xDC00 + (adjusted and 0x3FF)).toChar()}"
            }
        }
    }
}

/** Explicit user activation only, with no local files, custom schemes or relative destinations. */
private fun safeConversationMarkdownLink(destination: String): String? {
    if (destination.isEmpty() || destination.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7f } || '\\' in destination) return null
    return when {
        destination.startsWith("https://", true) || destination.startsWith("http://", true) ->
            runCatching { Url(destination) }.getOrNull()?.takeIf {
                it.host.isNotBlank() && it.protocol.name.lowercase() in setOf("http", "https")
            }?.let { destination }
        destination.startsWith("mailto:", true) -> destination.takeIf {
            val address = it.substringAfter(':').substringBefore('?')
            address.isNotBlank() && '@' in address && !it.contains("%0a", true) && !it.contains("%0d", true)
        }
        else -> null
    }
}
