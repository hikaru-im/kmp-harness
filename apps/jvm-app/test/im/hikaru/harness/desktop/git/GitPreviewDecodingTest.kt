package im.hikaru.harness.desktop.git

import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.git.decodeGitRasterPreview
import im.hikaru.harness.client.git.decodeGitSvgPreview
import im.hikaru.harness.client.git.renderGitPdfPreview
import kotlin.test.*

/**
 * The decoding actuals are Skia-backed, so they are checked where the desktop test runtime has the
 * native library: the shared module's plain JVM test runner does not load it.
 */
class GitPreviewDecodingTest {
    @Test
    fun rasterPreviewDecodesTheBytesTheHostReads() {
        val bitmap = assertNotNull(decodeGitRasterPreview(GitPreviewFixtures.png()))
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }

    @Test
    fun rasterPreviewReturnsNullInsteadOfThrowingOnBytesItCannotDecode() {
        assertNull(decodeGitRasterPreview(ByteArray(0)))
        assertNull(decodeGitRasterPreview("not an image".toByteArray()))
    }

    @Test
    fun svgPreviewDecodesIntoAPainterWithTheDocumentSize() {
        val painter = assertNotNull(decodeGitSvgPreview(GitPreviewFixtures.svg(), Density(1f)))
        assertEquals(24f, painter.intrinsicSize.width)
        assertEquals(16f, painter.intrinsicSize.height)
    }

    @Test
    fun svgPreviewReturnsNullOnMalformedMarkup() {
        assertNull(decodeGitSvgPreview("<svg><not-closed>".toByteArray(), Density(1f)))
    }

    @Test
    fun pdfPreviewRendersTheFirstPageAtThePreviewDpi() {
        val bitmap = assertNotNull(renderGitPdfPreview(GitPreviewFixtures.pdf()))
        // A 200x100pt page at 96 DPI; PDFBox rounds each axis down, and the exact pixel is its call.
        assertTrue(bitmap.width in 264..268, "width=${bitmap.width}")
        assertTrue(bitmap.height in 131..135, "height=${bitmap.height}")
    }

    @Test
    fun pdfPreviewReturnsNullOnBytesThatAreNotAPdf() {
        assertNull(renderGitPdfPreview(ByteArray(0)))
        assertNull(renderGitPdfPreview("%PDF-1.4 truncated".toByteArray()))
    }
}
