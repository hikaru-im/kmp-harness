package im.hikaru.harness.client.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * M0 guardrails for SET-01/SET-02: the Codex runtime panel inventory, the Harness row mapping,
 * the missing-panel set and the stable panel test tags are data, so they stay assertable without
 * Compose UI tooling.
 */
class SettingsPanelCatalogTest {
    @Test
    fun codexPanelsMatchTheRuntimeInventory() {
        assertEquals(
            listOf(
                "general-settings", "import", "appearance", "archived-chats", "voice", "storage",
                "agent", "personalization", "pets", "keyboard-shortcuts", "plugins-settings",
                "computer-use", "appshots", "browser-use", "hooks-settings", "connections",
                "git-settings", "local-environments", "worktrees",
            ),
            SettingsPanelCatalog.codexPanels.map { it.slug },
        )
        assertEquals(19, SettingsPanelCatalog.codexPanels.size)
        assertEquals(
            listOf(
                "常规", "导入", "外观", "已归档的聊天", "语音", "存储", "配置", "个性化",
                "Mini 与虚拟宠物", "键盘快捷键", "插件", "电脑操控", "应用快照", "浏览器", "钩子",
                "连接", "Git", "环境", "Worktrees",
            ),
            SettingsPanelCatalog.codexPanels.map { it.label },
        )
    }

    @Test
    fun everyHarnessRowMapsToACodexPanelOrIsMarkedAsAnExtension() {
        assertEquals(
            listOf("general", "appearance", "models", "keyboard-shortcuts", "plugins", "connections", "git", "worktrees"),
            SettingsPanelCatalog.harnessPanels.map { it.harnessSlug },
        )
        assertEquals(SettingsNavigatorModel.allItems.map { it.slug }, SettingsPanelCatalog.harnessPanels.map { it.harnessSlug })
        assertEquals(
            mapOf(
                "general" to "general-settings",
                "appearance" to "appearance",
                "models" to null,
                "keyboard-shortcuts" to "keyboard-shortcuts",
                "plugins" to "plugins-settings",
                "connections" to "connections",
                "git" to "git-settings",
                "worktrees" to "worktrees",
            ),
            SettingsPanelCatalog.harnessPanels.associate { it.harnessSlug to it.codexSlug },
        )
        assertEquals(listOf("models"), SettingsPanelCatalog.harnessExtensions.map { it.harnessSlug })

        // The two directions of the mapping must agree: a mapped row's Codex panel points back to
        // the same Harness row and reports the same availability.
        SettingsPanelCatalog.harnessPanels.filter { it.codexSlug != null }.forEach { mapping ->
            val codex = SettingsPanelCatalog.codexPanels.single { it.slug == mapping.codexSlug }
            assertEquals(mapping.harnessSlug, codex.harnessSlug)
            assertEquals(mapping.availability, codex.availability)
        }
        SettingsPanelCatalog.codexPanels.filter { it.harnessSlug != null }.forEach { codex ->
            assertEquals(codex.slug, SettingsPanelCatalog.forHarnessSlug(codex.harnessSlug!!)?.codexSlug)
        }
    }

    @Test
    fun missingCodexPanelsAreRegisteredButNotImplemented() {
        assertEquals(
            listOf(
                "import", "archived-chats", "voice", "storage", "agent", "personalization", "pets",
                "computer-use", "appshots", "browser-use", "hooks-settings", "local-environments",
            ),
            SettingsPanelCatalog.missingCodexPanels.map { it.slug },
        )
        assertEquals(12, SettingsPanelCatalog.missingCodexPanels.size)
        assertTrue(SettingsPanelCatalog.missingCodexPanels.all { it.availability == SettingsPanelAvailability.Missing })
        assertTrue(SettingsPanelCatalog.missingCodexPanels.all { it.harnessSlug == null })
        // The four Phase 2 ChatGPT/cloud routes are not part of the 19 runtime panels.
        val phaseTwo = setOf("billing", "teams", "parental-controls", "cloud-computer")
        assertTrue(SettingsPanelCatalog.codexPanels.none { it.slug in phaseTwo })
    }

    @Test
    fun panelTagsReuseTheCodexSlugVerbatim() {
        assertEquals("settings-panel-general-settings", SettingsPanelCatalog.panelTestTag(SettingsSection.General))
        assertEquals("settings-panel-appearance", SettingsPanelCatalog.panelTestTag(SettingsSection.Appearance))
        assertEquals("settings-panel-models", SettingsPanelCatalog.panelTestTag(SettingsSection.Models))
        assertEquals("settings-panel-keyboard-shortcuts", SettingsPanelCatalog.panelTestTag(SettingsSection.Shortcuts))
        assertEquals("settings-panel-plugins-settings", SettingsPanelCatalog.panelTestTag(SettingsSection.Plugins))
        assertEquals("settings-panel-connections", SettingsPanelCatalog.panelTestTag(SettingsSection.Connections))
        assertEquals("settings-panel-git-settings", SettingsPanelCatalog.panelTestTag(SettingsSection.Git))
        assertEquals("settings-panel-worktrees", SettingsPanelCatalog.panelTestTag(SettingsSection.Worktrees))
        assertEquals(
            SettingsPanelCatalog.harnessPanels.size,
            SettingsPanelCatalog.harnessPanels.map { it.testTag }.toSet().size,
        )
        assertEquals("settings-panel-unknown", SettingsPanelCatalog.panelTestTag("unknown"))
        assertNull(SettingsPanelCatalog.forHarnessSlug("unknown"))
    }

    @Test
    fun everyMappingKeepsItsMatrixRow() {
        assertEquals(
            mapOf(
                "general" to "SET-10",
                "appearance" to "SET-12",
                "models" to "SET-30",
                "keyboard-shortcuts" to "SET-19",
                "plugins" to "SET-20",
                "connections" to "SET-25",
                "git" to "SET-26",
                "worktrees" to "SET-28",
            ),
            SettingsPanelCatalog.harnessPanels.associate { it.harnessSlug to it.matrixId },
        )
        SettingsPanelCatalog.codexPanels.forEach { panel ->
            assertTrue(panel.matrixId.matches(Regex("SET-[0-9]+")), panel.slug)
        }
    }
}
