package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Raster preview decoding shared by every platform. Returns null when the bytes are not an image the
 * platform decoder understands, so the diff card can fall back to its text.
 */
public fun decodeGitRasterPreview(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()

/**
 * SVG decoding. Android has no Skia SVG module in this project, so its actual returns null and the
 * card keeps the diff text; the JVM and iOS actuals use the Skia-backed painter.
 */
public expect fun decodeGitSvgPreview(bytes: ByteArray, density: Density): Painter?

/**
 * First-page PDF rendering. Only the JVM actual has a renderer (PDFBox); other platforms return null
 * and the card keeps the diff text instead of pretending to show the document.
 */
public expect fun renderGitPdfPreview(bytes: ByteArray): ImageBitmap?
