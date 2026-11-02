package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.compose.resources.decodeToSvgPainter

public actual fun decodeGitSvgPreview(bytes: ByteArray, density: Density): Painter? =
    runCatching { bytes.decodeToSvgPainter(density) }.getOrNull()

public actual fun renderGitPdfPreview(bytes: ByteArray): ImageBitmap? = runCatching {
    Loader.loadPDF(bytes).use { document ->
        if (document.numberOfPages <= 0) return@use null
        val page: BufferedImage = PDFRenderer(document).renderImageWithDPI(0, PdfPreviewDpi)
        val png = ByteArrayOutputStream()
        ImageIO.write(page, "png", png)
        decodeGitRasterPreview(png.toByteArray())
    }
}.getOrNull()

/** 96 DPI keeps a letter page near 794x1123 px, readable without a huge bitmap. */
private const val PdfPreviewDpi: Float = 96f
