package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import org.jetbrains.compose.resources.decodeToSvgPainter

public actual fun decodeGitSvgPreview(bytes: ByteArray, density: Density): Painter? =
    runCatching { bytes.decodeToSvgPainter(density) }.getOrNull()

/** No PDF renderer is linked on iOS yet; the diff card keeps its text instead. */
public actual fun renderGitPdfPreview(bytes: ByteArray): ImageBitmap? = null
