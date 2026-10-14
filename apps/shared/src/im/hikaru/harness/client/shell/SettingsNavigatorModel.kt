package im.hikaru.harness.client.shell

import im.hikaru.harness.ui.foundation.HarnessIconName
import im.hikaru.harness.ui.foundation.SettingsNavGlyph
import im.hikaru.harness.ui.foundation.SettingsNavIcon
import im.hikaru.harness.ui.foundation.SettingsPlaceholderGlyph

/**
 * One Settings-navigator row, in render order.
 *
 * [slug] is the stable identity the selected row is carried by (`SettingsNavigatorState.selectedSlug`);
 * [label] is the Codex zh-CN string from the 2026-10-06 Settings capture; [icon] is the leading glyph
 * — a catalogued Codex artwork ([SettingsNavIcon]) where one has been extracted, otherwise the
 * previous batches' shared placeholder ([SettingsPlaceholderGlyph]); [external] draws the trailing
 * external-link arrow Codex puts on rows that leave the Settings window.
 */
public data class SettingsNavItem(
    val slug: String,
    val label: String,
    val icon: SettingsNavGlyph,
    val external: Boolean = false,
)

/** A titled group of Settings-navigator rows. The heading is drawn as its own section label. */
public data class SettingsNavGroup(
    val heading: String,
    val items: List<SettingsNavItem>,
)

/**
 * The Settings page's left navigator, transcribed from
 * `docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png`: four groups
 * (`个人` / `集成` / `编码` / `已归档`) in this order, each row keeping the screenshot's slug, label and
 * external-link flag.
 *
 * This is a static, testable model on purpose: the Settings navigator rows are data, not rows
 * hardcoded inside the composable, so group order, slug uniqueness and the search filter are all
 * assertable without Compose UI tooling.
 */
public object SettingsNavigatorModel {
    /** Selected row when the Settings page opens; the capture shows `常规` selected. */
    public const val DefaultSlug: String = "general"

    public val groups: List<SettingsNavGroup> = listOf(
        SettingsNavGroup("个人", listOf(
            SettingsNavItem("general", "常规", SettingsNavIcon.General),
            SettingsNavItem("appearance", "外观", SettingsNavIcon.Appearance),
            SettingsNavItem("models", "模型与 Provider", SettingsPlaceholderGlyph(HarnessIconName.Settings)),
            SettingsNavItem("keyboard-shortcuts", "键盘快捷键", SettingsNavIcon.KeyboardShortcuts),
        )),
        SettingsNavGroup("集成", listOf(
            SettingsNavItem("plugins", "插件", SettingsNavIcon.Plugins),
        )),
        SettingsNavGroup("编码", listOf(
            SettingsNavItem("connections", "连接", SettingsNavIcon.Connections),
            SettingsNavItem("git", "Git", SettingsNavIcon.Git),
            SettingsNavItem("worktrees", "Worktrees", SettingsNavIcon.Worktrees),
        )),
    )

    /** Every row across every group, in render order. */
    public val allItems: List<SettingsNavItem> = groups.flatMap { it.items }

    /** The row carried by [slug], or null when no row has it. */
    public fun item(slug: String): SettingsNavItem? = allItems.firstOrNull { it.slug == slug }

    /** True when [slug] names a row; used to keep `selectedSlug` from pointing at nothing. */
    public fun hasSlug(slug: String): Boolean = item(slug) != null
}

/**
 * Groups the Settings navigator lists for [state]. A blank query keeps every group; otherwise a group
 * is kept when its heading matches, and only its matching rows are kept. A group whose rows all
 * disappear is dropped, so the heading never shows over an empty run. The filter reads only
 * [SettingsNavigatorState.query], never the Workspace search.
 */
public fun settingsNavigatorGroups(state: SettingsNavigatorState): List<SettingsNavGroup> {
    val query = state.query.trim()
    if (query.isEmpty()) return SettingsNavigatorModel.groups
    return SettingsNavigatorModel.groups.mapNotNull { group ->
        val headingMatches = group.heading.contains(query, ignoreCase = true)
        val items = group.items.filter { headingMatches || it.label.contains(query, ignoreCase = true) }
        if (items.isEmpty()) null else group.copy(items = items)
    }
}

/** Compile-time routing for every visible Settings row. */
public fun SettingsNavItem.toPageId(): PageId = when (slug) {
    "plugins" -> PageId.Plugins
    else -> PageId.Settings(settingsSectionForSlug(slug))
}

public fun settingsSectionForSlug(slug: String): SettingsSection = when (slug) {
    "general" -> SettingsSection.General
    "appearance" -> SettingsSection.Appearance
    "models" -> SettingsSection.Models
    "connections" -> SettingsSection.Connections
    "keyboard-shortcuts" -> SettingsSection.Shortcuts
    "git" -> SettingsSection.Git
    "worktrees" -> SettingsSection.Worktrees
    else -> error("Unsupported Settings section: $slug")
}

public fun SettingsSection.navigatorSlug(): String = when (this) {
    SettingsSection.General -> "general"
    SettingsSection.Appearance -> "appearance"
    SettingsSection.Models -> "models"
    SettingsSection.Plugins -> "plugins"
    SettingsSection.Connections -> "connections"
    SettingsSection.Shortcuts -> "keyboard-shortcuts"
    SettingsSection.Git -> "git"
    SettingsSection.Worktrees -> "worktrees"
}
