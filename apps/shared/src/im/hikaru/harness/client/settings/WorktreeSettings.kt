package im.hikaru.harness.client.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Persisted Worktrees preferences. Only preferences with a real stored value belong in the saved
 * document.
 *
 * ASAR evidence (`worktrees-settings-page`): the Worktrees settings page groups its rows into a
 * "General" section with a "Worktree root" field and an "Environment" section with three rows:
 * "automatically delete old worktrees" (`worktree-auto-cleanup-enabled`, boolean, default `true`),
 * "auto-delete limit" (`worktree-keep-count`, integer >= 1, default `15`), and "always fetch upstream
 * before creating worktrees" (`worktree-upstream-refresh-mode`, enum `never` | `best-effort`, default
 * `never`). Disabling auto-cleanup first asks for confirmation. This document persists those stored
 * values; the destructive pruning and the pre-create fetch themselves are separate backend behavior
 * and are not wired here.
 */
@Serializable
public data class WorktreeSettings(
    public val root: String = "",
    public val autoCleanupEnabled: Boolean = WorktreeAutoCleanupDefault,
    public val keepCount: Int = WorktreeKeepCountDefault,
    public val upstreamRefreshMode: WorktreeUpstreamRefreshMode = WorktreeUpstreamRefreshMode.Never,
) {
    /** The trimmed root, or null when it is blank (the "default location" case). */
    public val configuredRoot: String? get() = worktreeRootValue(root).ifEmpty { null }
}

/** `worktree-upstream-refresh-mode`; `never` is the observed default. */
@Serializable
public enum class WorktreeUpstreamRefreshMode(public val wire: String) {
    @SerialName("never") Never("never"),
    @SerialName("best-effort") BestEffort("best-effort"),
}

/** `worktree-auto-cleanup-enabled` default. */
public const val WorktreeAutoCleanupDefault: Boolean = true

/** `worktree-keep-count` default; the original schema is `int().min(1)`. */
public const val WorktreeKeepCountDefault: Int = 15

/** Trimmed root text with backslashes normalised to `/`; blank stays blank. */
public fun worktreeRootValue(root: String): String = root.trim().replace('\\', '/')

/** Parsed keep-count, or null unless the trimmed text is an integer >= 1 (the original schema). */
public fun keepCountValue(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it >= 1 }

/** Null when [text] is a keep-count the original would accept; otherwise an inline error message. */
public fun keepCountError(text: String): String? =
    if (keepCountValue(text) != null) null else "请输入不小于 1 的整数。"

/**
 * Null when [root] is blank (default location) or a usable absolute path; otherwise a message for the
 * field's inline error. The original silently ignores a non-absolute value on blur; this page names
 * the reason instead.
 */
public fun worktreeRootError(root: String): String? {
    val value = worktreeRootValue(root)
    if (value.isEmpty()) return null
    if (value.contains('\u0000')) return "路径不能包含空字符。"
    val absolute = value.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(value)
    return if (absolute) null else "请输入绝对路径，或留空使用默认位置。"
}

/**
 * Default parent for a new worktree: `<root>/<branch slug>` once a root is configured and the branch
 * is a real name; null otherwise, in which case the page keeps asking for an explicit path.
 */
public fun worktreeDefaultPath(root: String, branch: String): String? {
    val base = worktreeRootValue(root).removeSuffix("/")
    if (base.isEmpty() || worktreeRootError(root) != null) return null
    val slug = branchSlug(branch) ?: return null
    return "$base/$slug"
}

/** Filesystem-safe slug for a branch name; null when nothing usable remains ("codex/" placeholder). */
public fun branchSlug(branch: String): String? {
    val trimmed = branch.trim()
    if (trimmed.isEmpty() || trimmed == "codex/") return null
    val slug = trimmed.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.')
    return slug.ifEmpty { null }
}
