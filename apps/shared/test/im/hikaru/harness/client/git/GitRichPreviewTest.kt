package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.*

class GitRichPreviewTest {
    @Test
    fun extensionMirrorsTheOriginalDotRule() {
        assertEquals("md", gitFileExtension("docs/readme.md"))
        assertEquals("md", gitFileExtension("README.MD"))
        assertEquals("gz", gitFileExtension("a/b/archive.tar.gz"))
        assertEquals("md", gitFileExtension("C:\\docs\\readme.md"))
        assertNull(gitFileExtension("Makefile"))
        assertNull(gitFileExtension(".gitignore"))
        assertNull(gitFileExtension("dir.d/file"))
        assertNull(gitFileExtension("trailing."))
        assertNull(gitFileExtension(""))
    }

    @Test
    fun previewKindOnlySelectsMarkdownWhenTheToggleIsOnAndTheFileSurvives() {
        for (extension in GitMarkdownExtensions)
            assertEquals(GitDiffPreviewKind.Markdown, gitDiffPreviewKind("a.$extension", false, true), extension)
        assertEquals(GitDiffPreviewKind.Diff, gitDiffPreviewKind("a.md", false, false))
        assertEquals(GitDiffPreviewKind.Diff, gitDiffPreviewKind("a.md", true, true))
        // The original also previews raster images, SVG and PDF; this Harness keeps their diff text.
        for (path in listOf("shot.png", "icon.svg", "paper.pdf", "main.kt", "Makefile"))
            assertEquals(GitDiffPreviewKind.Diff, gitDiffPreviewKind(path, false, true), path)
    }

    @Test
    fun previewPathSafetyRejectsEscapesAndAcceptsRepositoryRelativePaths() {
        for (path in listOf("docs/readme.md", "a/b/c.md", "README.md", "a b/c-d.md"))
            assertTrue(gitPreviewPathIsSafe(path), path)
        for (path in listOf(
            "", "   ", "/etc/passwd", "../x.md", "a/../b.md", "./a.md", "a/./b.md",
            "a//b.md", "C:/x.md", "a\\b.md", "a\u0000b",
        )) assertFalse(gitPreviewPathIsSafe(path), path)
    }

    @Test
    fun blockParserSplitsHeadingsParagraphsCodeQuotesListsAndRules() {
        val markdown = listOf(
            "# 标题",
            "",
            "段落 **粗体**",
            "",
            "```kotlin",
            "val x = 1",
            "```",
            "",
            "> 引用",
            "> 第二行",
            "",
            "- 项目一",
            "  - 嵌套",
            "",
            "1. 第一",
            "2. 第二",
            "",
            "---",
        ).joinToString("\n")
        assertEquals(
            listOf(
                GitMarkdownBlock.Heading(1, "标题"),
                GitMarkdownBlock.Paragraph("段落 **粗体**"),
                GitMarkdownBlock.Code("kotlin", "val x = 1"),
                GitMarkdownBlock.Quote("引用\n第二行"),
                GitMarkdownBlock.Bullet(0, "项目一"),
                GitMarkdownBlock.Bullet(1, "嵌套"),
                GitMarkdownBlock.Ordered(0, 1, "第一"),
                GitMarkdownBlock.Ordered(0, 2, "第二"),
                GitMarkdownBlock.Rule,
            ),
            parseMarkdownBlocks(markdown),
        )
    }

    @Test
    fun blockParserMergesWrappedParagraphLinesAndToleratesWindowsNewlines() {
        assertEquals(
            listOf(GitMarkdownBlock.Paragraph("one two three")),
            parseMarkdownBlocks("one\r\ntwo\r\nthree"),
        )
        assertEquals(emptyList(), parseMarkdownBlocks("\n\n   \n"))
        // An unterminated fence still yields a code block rather than dropping the body.
        assertEquals(
            listOf(GitMarkdownBlock.Code(null, "body")),
            parseMarkdownBlocks("```\nbody"),
        )
    }

    @Test
    fun inlineRunsCoverBoldItalicCodeAndLinksAndKeepUnmatchedMarkersLiteral() {
        val source = "普通 **粗体** 与 *斜体* 和 `code` 与 [链接](https://example.com/x) 以及 __下划线__"
        val tokens = parseMarkdownInline(source)
        // A matched run drops its markers; unmatched markers stay literal (asserted below).
        assertEquals("普通 粗体 与 斜体 和 code 与 链接 以及 下划线", tokens.joinToString("") { it.text })
        assertEquals(GitMarkdownToken("粗体", GitMarkdownInline.Bold), tokens.single { it.text == "粗体" })
        assertEquals(GitMarkdownToken("斜体", GitMarkdownInline.Italic), tokens.single { it.text == "斜体" })
        assertEquals(GitMarkdownToken("code", GitMarkdownInline.Code), tokens.single { it.text == "code" })
        assertEquals(
            GitMarkdownToken("链接", GitMarkdownInline.Link, "https://example.com/x"),
            tokens.single { it.style == GitMarkdownInline.Link },
        )
        assertEquals(GitMarkdownToken("下划线", GitMarkdownInline.Bold), tokens.single { it.text == "下划线" })

        val literal = "只有 * 星号"
        assertEquals(listOf(GitMarkdownToken(literal, GitMarkdownInline.Plain)), parseMarkdownInline(literal))
        // A link with an empty label falls back to its href so no text is lost.
        assertEquals(
            listOf(GitMarkdownToken("http://x", GitMarkdownInline.Link, "http://x")),
            parseMarkdownInline("[](http://x)"),
        )
    }

    @Test
    fun annotatedStringKeepsEveryCharacterAndStylesRuns() {
        val rendered = markdownAnnotatedString("前 **粗** 后", Color.Red)
        assertEquals("前 粗 后", rendered.text)
        val bold = rendered.spanStyles.single()
        assertEquals(FontWeight.SemiBold, bold.item.fontWeight)
        assertEquals("粗", rendered.text.substring(bold.start, bold.end))
    }
}
