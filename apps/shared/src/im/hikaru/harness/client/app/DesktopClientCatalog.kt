package im.hikaru.harness.client.app

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.PluginCatalog
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.plugin.simplePlugin
import im.hikaru.harness.ui.slot.UiContributionKey

public const val DESKTOP_CLIENT_ROOT_PLUGIN: String = "desktop-client-root"
public const val DESKTOP_CLIENT_HEADER_PLUGIN: String = "desktop-client-header"
public const val DESKTOP_CLIENT_WORKSPACE_PLUGIN: String = "desktop-client-workspace"

public val DesktopClientPluginCatalog: PluginCatalog by lazy {
    PluginCatalog(
        listOf(
            simplePluginDefinition(DESKTOP_CLIENT_ROOT_PLUGIN, desktopRootPlugin),
            simplePluginDefinition(DESKTOP_CLIENT_HEADER_PLUGIN, desktopHeaderPlugin),
            simplePluginDefinition(DESKTOP_CLIENT_WORKSPACE_PLUGIN, desktopWorkspacePlugin),
        )
    )
}

public val DesktopClientEntries: List<Entry> =
    listOf(
        Entry("root", DESKTOP_CLIENT_ROOT_PLUGIN),
        Entry("header", DESKTOP_CLIENT_HEADER_PLUGIN),
        Entry("workspace", DESKTOP_CLIENT_WORKSPACE_PLUGIN),
    )

private val desktopRootPlugin: SimplePlugin =
    simplePlugin {
        inject(UiContributionKey)
        apply { context, _ ->
            context.require(UiContributionKey).contribute(ClientUiSlots.Root) { props ->
                props.render()
            }
        }
    }

private val desktopHeaderPlugin: SimplePlugin =
    simplePlugin {
        inject(UiContributionKey)
        apply { context, _ ->
            context.require(UiContributionKey).contribute(ClientUiSlots.SessionHeader) { props ->
                Row {
                    props.render()
                    Text("  ·  Slots")
                }
            }
        }
    }


/** Demonstrates the workspace header region without exposing an action that has no local service behind it. */
private val desktopWorkspacePlugin: SimplePlugin =
    simplePlugin {
        inject(UiContributionKey)
        apply { context, _ ->
            val sink = context.require(UiContributionKey)
            sink.contribute(ClientUiSlots.WorkspaceHeader) { props ->
                props.render()
            }
        }
    }

private fun simplePluginDefinition(
    name: String,
    plugin: SimplePlugin,
) =
    im.hikaru.harness.loader.pluginDefinition(
        name = name,
        plugin = plugin,
    )
