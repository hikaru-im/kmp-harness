package im.hikaru.harness.client.git

/**
 * Extensions the original renders as rich previews instead of diff text
 * (`code-diff-38f339f0a601.js` / `app-shared-8bd9d0399038.js`: `_Or`, `gOr`, `mOr`).
 */
public val GitMarkdownExtensions: Set<String> = setOf("markdown", "md", "mdown", "mdx", "mkd")

/** What a diff file card shows when rich preview is on. Image and PDF previews are not implemented. */
public enum class GitDiffPreviewKind { Diff, Markdown }

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
 * Original `fOr({filePath,imagePreviewMode,isDeletion,richPreviewEnabled})`, reduced to the branch
 * this Harness can serve: a deleted file never previews, and only markdown files become a rendered
 * preview. Raster images, SVG and PDF keep their diff text here (no preview source is wired).
 */
public fun gitDiffPreviewKind(path: String, isDeletion: Boolean, richPreviewEnabled: Boolean): GitDiffPreviewKind =
    if (richPreviewEnabled && !isDeletion && gitFileExtension(path) in GitMarkdownExtensions) {
        GitDiffPreviewKind.Markdown
    } else {
        GitDiffPreviewKind.Diff
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

/** Largest preview the renderer will accept; larger files fall back to the diff. */
public const val GitPreviewTextLimit: Int = 256 * 1024
