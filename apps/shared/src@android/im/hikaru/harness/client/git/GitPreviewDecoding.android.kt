package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density

/** Android has no Skia SVG module wired here; the diff card keeps its text instead. */
public actual fun decodeGitSvgPreview(bytes: ByteArray, density: Density): Painter? = null

/** No PDF renderer is linked on Android yet; the diff card keeps its text instead. */
public actual fun renderGitPdfPreview(bytes: ByteArray): ImageBitmap? = null
