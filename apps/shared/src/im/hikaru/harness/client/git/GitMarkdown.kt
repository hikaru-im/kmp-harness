package im.hikaru.harness.client.git

/**
 * A deliberately small markdown subset for the diff rich preview. It is not a CommonMark
 * implementation: it covers headings, paragraphs, fenced code, block quotes, bullet and ordered
 * lists, thematic rules and inline bold / italic / code / links, and leaves everything else as
 * literal text.
 */
public sealed interface GitMarkdownBlock {
    public data class Heading(val level: Int, val text: String) : GitMarkdownBlock
    public data class Paragraph(val text: String) : GitMarkdownBlock
    public data class Code(val language: String?, val text: String) : GitMarkdownBlock
    public data class Quote(val text: String) : GitMarkdownBlock
    public data class Bullet(val depth: Int, val text: String) : GitMarkdownBlock
    public data class Ordered(val depth: Int, val number: Int, val text: String) : GitMarkdownBlock
    public data object Rule : GitMarkdownBlock
}

public enum class GitMarkdownInline { Plain, Bold, Italic, Code, Link }

/** One inline run. [href] is set only for [GitMarkdownInline.Link]. */
public data class GitMarkdownToken(
    val text: String,
    val style: GitMarkdownInline,
    val href: String? = null,
)

private val heading = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val ordered = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val quote = Regex("^\\s{0,3}>\\s?(.*)$")
private val rule = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val fence = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^`]*)$")

/** Parses the subset above. Blank lines only separate blocks; they emit nothing. */
public fun parseMarkdownBlocks(text: String): List<GitMarkdownBlock> {
    val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<GitMarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val quoted = mutableListOf<String>()
    var index = 0

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += GitMarkdownBlock.Paragraph(paragraph.joinToString(" ") { it.trim() })
            paragraph.clear()
        }
    }

    fun flushQuote() {
        if (quoted.isNotEmpty()) {
            blocks += GitMarkdownBlock.Quote(quoted.joinToString("\n") { it.trim() })
            quoted.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val fenceMatch = fence.matchEntire(line)
        if (fenceMatch != null && paragraph.isEmpty() && quoted.isEmpty()) {
            val marker = fenceMatch.groupValues[1]
            val language = fenceMatch.groupValues[2].trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotEmpty() }
            val body = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trimStart().startsWith(marker.first().toString().repeat(3))) {
                body += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += GitMarkdownBlock.Code(language, body.joinToString("\n"))
            continue
        }
        when {
            line.isBlank() -> {
                flushQuote()
                flushParagraph()
            }
            heading.matches(line) -> {
                flushQuote()
                flushParagraph()
                val match = heading.matchEntire(line)!!
                blocks += GitMarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }
            rule.matches(line) -> {
                flushQuote()
                flushParagraph()
                blocks += GitMarkdownBlock.Rule
            }
            quote.matches(line) -> {
                flushParagraph()
                quoted += quote.matchEntire(line)!!.groupValues[1]
            }
            bullet.matches(line) -> {
                flushQuote()
                flushParagraph()
                val match = bullet.matchEntire(line)!!
                blocks += GitMarkdownBlock.Bullet(depth(match.groupValues[1]), match.groupValues[2])
            }
            ordered.matches(line) -> {
                flushQuote()
                flushParagraph()
                val match = ordered.matchEntire(line)!!
                blocks += GitMarkdownBlock.Ordered(
                    depth(match.groupValues[1]),
                    match.groupValues[2].toIntOrNull()?.coerceAtMost(999_999) ?: 1,
                    match.groupValues[3],
                )
            }
            else -> {
                flushQuote()
                paragraph += line
            }
        }
        index++
    }
    flushQuote()
    flushParagraph()
    return blocks
}

private fun depth(leading: String): Int = (leading.count { it == ' ' } + leading.count { it == '\t' } * 2) / 2

/**
 * Splits a block's text into styled runs. Unmatched or nested markers stay literal, so a preview
 * never silently drops the author's characters.
 */
public fun parseMarkdownInline(text: String): List<GitMarkdownToken> {
    val tokens = mutableListOf<GitMarkdownToken>()
    val plain = StringBuilder()

    fun flush() {
        if (plain.isNotEmpty()) {
            tokens += GitMarkdownToken(plain.toString(), GitMarkdownInline.Plain)
            plain.clear()
        }
    }

    var index = 0
    while (index < text.length) {
        val match = inlineAt(index, text)
        if (match == null) {
            plain.append(text[index])
            index++
            continue
        }
        val (consumed, token) = match
        flush()
        tokens += token
        index += consumed
    }
    flush()
    return tokens
}

private val codeSpan = Regex("^`([^`\\n]+)`")
private val boldSpan = Regex("^\\*\\*([^*\\n]+)\\*\\*")
private val boldUnderscore = Regex("^__([^_\\n]+)__")
private val italicStar = Regex("^\\*([^*\\n]+)\\*")
private val italicUnderscore = Regex("^_([^_\\n]+)_")
private val linkSpan = Regex("^\\[([^\\]\\n]*)\\]\\(([^)\\s]+)\\)")

private fun inlineAt(index: Int, text: String): Pair<Int, GitMarkdownToken>? {
    val rest = text.substring(index)
    codeSpan.find(rest)?.let {
        return it.value.length to GitMarkdownToken(it.groupValues[1], GitMarkdownInline.Code)
    }
    linkSpan.find(rest)?.let {
        val label = it.groupValues[1].ifBlank { it.groupValues[2] }
        return it.value.length to GitMarkdownToken(label, GitMarkdownInline.Link, it.groupValues[2])
    }
    boldSpan.find(rest)?.let {
        return it.value.length to GitMarkdownToken(it.groupValues[1], GitMarkdownInline.Bold)
    }
    boldUnderscore.find(rest)?.let {
        return it.value.length to GitMarkdownToken(it.groupValues[1], GitMarkdownInline.Bold)
    }
    italicStar.find(rest)?.let {
        return it.value.length to GitMarkdownToken(it.groupValues[1], GitMarkdownInline.Italic)
    }
    italicUnderscore.find(rest)?.let {
        return it.value.length to GitMarkdownToken(it.groupValues[1], GitMarkdownInline.Italic)
    }
    return null
}
