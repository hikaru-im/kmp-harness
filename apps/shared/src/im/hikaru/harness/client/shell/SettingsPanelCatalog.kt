package im.hikaru.harness.client.shell

/**
 * How a Codex Settings panel is represented on the Harness side.
 *
 * The M0 baseline only needs this distinction: a panel is either rendered inside the Settings
 * surface, reachable from a Settings row as its own page, or not implemented at all.
 */
public enum class SettingsPanelAvailability {
    /** Rendered by the `PageId.Settings` content switch. */
    Implemented,

    /** Reached from a Harness Settings row, but rendered as its own page (plugins). */
    ExternalPage,

    /** A Codex runtime panel with no Harness surface yet; only the mapping is registered. */
    Missing,
}

/**
 * One Settings panel the Codex 26.1027.61615 runtime exposes.
 *
 * The order, slugs and labels mirror `13-runtime/settings-rail-inventory.json` (19 buttons) from
 * `references/codex-26.1027.61615-24141/full-restore/`. [matrixId] pins the row in
 * `docs/ui-migration-gap-matrix.md` §3.1.2 so the M0 tests stay traceable to the migration matrix.
 *
 * [harnessSlug] is the Harness navigator row that stands for the panel, or `null` when Harness has
 * no surface for it yet. Missing panels are registered here only; M0 does not implement them.
 */
public data class CodexSettingsPanel(
    val slug: String,
    val label: String,
    val matrixId: String,
    val harnessSlug: String? = null,
    val availability: SettingsPanelAvailability = SettingsPanelAvailability.Missing,
)

/**
 * One row of Harness's Settings navigator and the Codex panel identity it maps to.
 *
 * [codexSlug] is `null` for Harness-owned rows that have no Codex runtime panel. The test tag
 * reuses the Codex slug verbatim so a Compose semantics node and a Codex
 * `data-settings-panel-slug` name the same surface (SET-01); Harness extensions keep their own row
 * slug.
 */
public data class HarnessSettingsPanel(
    val harnessSlug: String,
    val codexSlug: String?,
    val matrixId: String,
    val availability: SettingsPanelAvailability,
) {
    /** Stable semantic identity used by the M0 structure + visual baseline. */
    public val testTag: String = "settings-panel-" + (codexSlug ?: harnessSlug)
}

/**
 * M0 mapping between Harness's current 8 Settings rows and Codex's 19 runtime panels (SET-02).
 *
 * This is a static, testable model on purpose: the mapping is data, so coverage, slug uniqueness
 * and the missing-panel set are assertable without Compose UI tooling.
 */
public object SettingsPanelCatalog {
    /** The 19 panels Codex 26.1027.61615 exposes, in the R13 rail order (SET-02). */
    public val codexPanels: List<CodexSettingsPanel> = listOf(
        CodexSettingsPanel("general-settings", "常规", "SET-10", "general", SettingsPanelAvailability.Implemented),
        CodexSettingsPanel("import", "导入", "SET-11"),
        CodexSettingsPanel("appearance", "外观", "SET-12", "appearance", SettingsPanelAvailability.Implemented),
        CodexSettingsPanel("archived-chats", "已归档的聊天", "SET-13"),
        CodexSettingsPanel("voice", "语音", "SET-14"),
        CodexSettingsPanel("storage", "存储", "SET-15"),
        CodexSettingsPanel("agent", "配置", "SET-16"),
        CodexSettingsPanel("personalization", "个性化", "SET-17"),
        CodexSettingsPanel("pets", "Mini 与虚拟宠物", "SET-18"),
        CodexSettingsPanel("keyboard-shortcuts", "键盘快捷键", "SET-19", "keyboard-shortcuts", SettingsPanelAvailability.Implemented),
        CodexSettingsPanel("plugins-settings", "插件", "SET-20", "plugins", SettingsPanelAvailability.ExternalPage),
        CodexSettingsPanel("computer-use", "电脑操控", "SET-21"),
        CodexSettingsPanel("appshots", "应用快照", "SET-22"),
        CodexSettingsPanel("browser-use", "浏览器", "SET-23"),
        CodexSettingsPanel("hooks-settings", "钩子", "SET-24"),
        CodexSettingsPanel("connections", "连接", "SET-25", "connections", SettingsPanelAvailability.Implemented),
        CodexSettingsPanel("git-settings", "Git", "SET-26", "git", SettingsPanelAvailability.Implemented),
        CodexSettingsPanel("local-environments", "环境", "SET-27"),
        CodexSettingsPanel("worktrees", "工作树", "SET-28", "worktrees", SettingsPanelAvailability.Implemented),
    )

    /** Harness's 8 current Settings rows, in render order (SET-02). */
    public val harnessPanels: List<HarnessSettingsPanel> = listOf(
        HarnessSettingsPanel("general", "general-settings", "SET-10", SettingsPanelAvailability.Implemented),
        HarnessSettingsPanel("appearance", "appearance", "SET-12", SettingsPanelAvailability.Implemented),
        // D-05: model management is a Harness extension, not a Codex runtime panel. The nearest
        // Codex surface is the static-only `gateways` (SET-29), which this build does not expose.
        HarnessSettingsPanel("models", null, "SET-30", SettingsPanelAvailability.Implemented),
        HarnessSettingsPanel("keyboard-shortcuts", "keyboard-shortcuts", "SET-19", SettingsPanelAvailability.Implemented),
        HarnessSettingsPanel("plugins", "plugins-settings", "SET-20", SettingsPanelAvailability.ExternalPage),
        HarnessSettingsPanel("connections", "connections", "SET-25", SettingsPanelAvailability.Implemented),
        HarnessSettingsPanel("git", "git-settings", "SET-26", SettingsPanelAvailability.Implemented),
        HarnessSettingsPanel("worktrees", "worktrees", "SET-28", SettingsPanelAvailability.Implemented),
    )

    /** Codex panels with no Harness surface yet; M0 registers the mapping only. */
    public val missingCodexPanels: List<CodexSettingsPanel> =
        codexPanels.filter { it.harnessSlug == null }

    /** Harness-owned rows that must not be counted as Codex-aligned panels (D-05). */
    public val harnessExtensions: List<HarnessSettingsPanel> =
        harnessPanels.filter { it.codexSlug == null }

    /** The Harness mapping for [slug], or null when the navigator has no such row. */
    public fun forHarnessSlug(slug: String): HarnessSettingsPanel? =
        harnessPanels.firstOrNull { it.harnessSlug == slug }

    /** Stable panel tag for a Settings section (SET-01); Codex slugs are reused verbatim. */
    public fun panelTestTag(section: SettingsSection): String = panelTestTag(section.navigatorSlug())

    /** Stable panel tag for a Harness navigator slug; falls back to the row slug for unknown rows. */
    public fun panelTestTag(harnessSlug: String): String =
        forHarnessSlug(harnessSlug)?.testTag ?: "settings-panel-$harnessSlug"
}
