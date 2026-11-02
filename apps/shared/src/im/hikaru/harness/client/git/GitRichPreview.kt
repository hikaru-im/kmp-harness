package im.hikaru.harness.client.git

/**
 * Extensions the original renders as rich previews instead of diff text
 * (`code-diff-38f339f0a601.js` / `app-shared-8bd9d0399038.js`: `_Or`, `gOr`, `mOr`).
 */
public val GitMarkdownExtensions: Set<String> = setOf("markdown", "md", "mdown", "mdx", "mkd")

/** Raster formats the platform image decoder can turn into an `ImageBitmap`. */
public val GitImageExtensions: Set<String> =
    setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "avif", "tif", "tiff")

/** Vector images render through the SVG painter, not the raster decoder. */
public val GitSvgExtensions: Set<String> = setOf("svg")

public val GitPdfExtensions: Set<String> = setOf("pdf")

/**
 * What a diff file card shows when rich preview is on. Codex's `RichPreviewKind` is
 * `image | markdown | pdf | pdb`; SVG is split out because it needs a vector painter rather than the
 * raster decoder, and `pdb` stays on the diff fallback (no molecular renderer here).
 */
public enum class GitDiffPreviewKind { Diff, Markdown, Image, Svg, Pdf }

/**
 * The lowercased extension of the base name, or null when there is none. Mirrors the original
 * `D1(filePath)`: the base name is what follows the last `/` or `\`, and a leading dot is not an
 * extension (`i > 0`). A trailing dot is also treated as "no extension" here, which reaches the
 * same rich-preview decision as the original's empty string.
 */
public fun gitFileExtension(path: String): String? {
    val lower = path.lowercase()
    val separator = maxOf(lower.lastIndexOf('/'), lower.lastIndexOf('\\'))
    val base = if (separator >= 0) lower.substring(separator + 1) else lower
    val dot = base.lastIndexOf('.')
    return if (dot > 0 && dot < base.length - 1) base.substring(dot + 1) else null
}

/**
 * Original `fOr({filePath,imagePreviewMode,isDeletion,richPreviewEnabled})`: a deleted file never
 * previews, and markdown, raster images, SVG and PDF each get their own renderer while everything
 * else keeps its diff text.
 */
public fun gitDiffPreviewKind(path: String, isDeletion: Boolean, richPreviewEnabled: Boolean): GitDiffPreviewKind {
    if (!richPreviewEnabled || isDeletion) return GitDiffPreviewKind.Diff
    val extension = gitFileExtension(path)
    return when {
        extension in GitMarkdownExtensions -> GitDiffPreviewKind.Markdown
        extension in GitSvgExtensions -> GitDiffPreviewKind.Svg
        extension in GitPdfExtensions -> GitDiffPreviewKind.Pdf
        extension in GitImageExtensions -> GitDiffPreviewKind.Image
        else -> GitDiffPreviewKind.Diff
    }
}

/**
 * MIME type when the preview must be read as bytes rather than UTF-8 text. SVG is text on disk but
 * still travels through the binary path so the card can render it as a vector.
 */
public fun gitPreviewBinaryMimeType(path: String): String? = when (gitFileExtension(path)) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "ico" -> "image/x-icon"
    "avif" -> "image/avif"
    "tif", "tiff" -> "image/tiff"
    "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"
    else -> null
}

/**
 * A diff-relative path is safe to resolve inside the repository root when it is non-blank, not
 * absolute, has no `..` or `.` segment, no drive prefix, no NUL and no backslash separator.
 */
public fun gitPreviewPathIsSafe(path: String): Boolean {
    if (path.isBlank() || path.contains('\u0000') || path.contains('\\')) return false
    if (path.startsWith("/")) return false
    if (path.length >= 2 && path[1] == ':') return false
    val segments = path.split('/')
    return segments.none { it.isEmpty() || it == "." || it == ".." }
}

/** Largest text preview the renderer will accept; larger files fall back to the diff. */
public const val GitPreviewTextLimit: Int = 256 * 1024

/** Largest binary preview (image, SVG or PDF); larger files fall back to the diff. */
public const val GitPreviewBinaryLimit: Int = 8 * 1024 * 1024
