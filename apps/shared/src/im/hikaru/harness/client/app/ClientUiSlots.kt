package im.hikaru.harness.client.app

import androidx.compose.runtime.Composable
import im.hikaru.harness.ui.slot.ListUiSlotKey
import im.hikaru.harness.ui.slot.SingleUiSlotKey
import im.hikaru.harness.ui.slot.UiSchema
import im.hikaru.harness.ui.slot.UiSlotKey

public data class RootProps(
    val render: @Composable () -> Unit,
)

public data class SessionHeaderProps(
    val title: String,
    val status: HarnessConnectionState,
    val render: @Composable () -> Unit,
)

public data class SidebarActionProps(
    val onSelect: (HarnessDestination) -> Unit,
)

/** Generic contribution payload for shell regions whose contribution is purely composable. */
public data class UiRenderProps(
    val render: @Composable () -> Unit,
)

public object ClientUiSlots {
    // Migration aliases: existing plugins keep working while ownership moves to the new shell regions.
    public val Root: SingleUiSlotKey<RootProps> = SingleUiSlotKey("root")
    public val SessionHeader: SingleUiSlotKey<SessionHeaderProps> = SingleUiSlotKey("conversation.header")
    public val SidebarActions: ListUiSlotKey<SidebarActionProps> = ListUiSlotKey("sidebar.actions")

    public val AppRailPrimary: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("app.rail.primary")
    public val WorkspaceHeader: SingleUiSlotKey<UiRenderProps> = SingleUiSlotKey("workspace.header")
    public val WorkspaceProjectTree: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("workspace.project.tree")
    public val WorkspaceProjectActions: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("workspace.project.actions")
    public val WorkspaceRecentTasks: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("workspace.recent.tasks")
    public val ThreadTitlebarActions: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("thread.titlebar.actions")
    public val ThreadSummary: SingleUiSlotKey<UiRenderProps> = SingleUiSlotKey("thread.summary")
    public val ProjectContextPanel: SingleUiSlotKey<UiRenderProps> = SingleUiSlotKey("project.context.panel")
    public val SettingsTabs: ListUiSlotKey<UiRenderProps> = ListUiSlotKey("settings.tabs")
}

public val ClientUiSchema: UiSchema =
    UiSchema(
        listOf<UiSlotKey<*>>(
            ClientUiSlots.Root,
            ClientUiSlots.SessionHeader,
            ClientUiSlots.SidebarActions,
            ClientUiSlots.AppRailPrimary,
            ClientUiSlots.WorkspaceHeader,
            ClientUiSlots.WorkspaceProjectTree,
            ClientUiSlots.WorkspaceProjectActions,
            ClientUiSlots.WorkspaceRecentTasks,
            ClientUiSlots.ThreadTitlebarActions,
            ClientUiSlots.ThreadSummary,
            ClientUiSlots.ProjectContextPanel,
            ClientUiSlots.SettingsTabs,
        )
    )
