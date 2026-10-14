# Codex page composition recovered from app.asar

This document records the first behavior and composition pass over the local Codex Desktop bundle. The evidence comes from selected bundle names, component props, message IDs, route strings, and action snippets in this directory.

## Shared application shell

The thread surface is composed from these independently controlled regions:

| Region | Evidence | Reproduction seam |
| --- | --- | --- |
| Titlebar | thread-app-shell-chrome, Titlebar, titlebarSlots | AppShell.titlebar |
| Header actions | HeaderAction, Chat toolbar, Chat actions | typed header action model |
| Conversation body | local-conversation-page, local-conversation-thread | ThreadPage and thread state |
| Right panel | RightPanelOutlet, RightPanelTabs, toggleThreadSidePanel | contextPanel plus panel state |
| Bottom panel | BottomPanelOutlet, BottomPanelTabs, appShell.header.bottomPanel | composer and bottom panel state |
| Composer | composer-controls, showComposer, renderComposer | composer state and submit action |

The right and bottom panels are outlets owned by the thread shell. They should remain independent overlays in the Kotlin model rather than becoming additional left navigation destinations.

## Sidebar and app navigation

The sidebar bundle shows these data projections and states:

- Recents, pinned, coding, integrations, personal, and archived sections.
- Search, task filtering, display options, loading, error, empty, and no-results states.
- Scheduled task groups with run history, ownership filters, team filters, and pagination.
- Team actions that open /settings/teams/:teamId.
- Scheduled task runs that navigate back to a local conversation with automationId, run, and navigationSource.

The scheduled task sidebar is already enough to define a real local model:

    ScheduledTask
      id
      title
      owner: all | team | personal
      teamId?
      runs: ScheduledRun[]
      hasMoreRuns
      loading | error | empty

    ScheduledRun
      id
      status
      chatRef?
      summary?
      unconfirmed

The main actions recovered from the bundle are New task, Show older, retry loading, show only this team, open team, and open run. These can map directly to Kotlin intents without waiting for visual work.

## Projects page

projects-index-page exposes a project index rather than a flat thread list. It distinguishes local, cloud, and remote projects and keeps project children expandable.

The page contains:

- Search and forced expansion when a child matches.
- Sort controls for Name and Updated.
- Expand or collapse project rows.
- Start a new ChatGPT chat in a project.
- Start a new local task in a project.
- Open an existing chat.
- Pin or unpin a project.
- Project settings and delete confirmation.
- Load more project chats.
- Loading, empty, and request error states with retry.

The project row action model should therefore look like:

    ProjectAction
      open
      toggleExpanded
      startCloudChat
      startLocalTask
      togglePinned
      openSettings
      requestDelete
      loadMoreChildren

The current ProjectsPage can keep its visual fixture while these actions are introduced as callbacks. That lets screenshot fixtures stay deterministic while behavior becomes testable.

## Thread page

The thread bundles show several real subdomains that are currently represented by fixture content in the Kotlin app:

- Conversation turns and streaming response state.
- Created tasks with starting, working, done, and failed states.
- Background agents with active and completed groups.
- Background terminals with open-terminal and stop-all actions.
- Chrome tabs with switch, open, close, and new-tab actions.
- Usage popover with a route to usage settings.
- Artifacts and file rows with open actions.
- Sources, plan, usage, created tasks, subagents, and Chrome tabs as summary panel sections.

The most concrete recovered actions are:

| Source | Action | Result |
| --- | --- | --- |
| Background terminal row | click | open terminal panel |
| Stop terminal control | click | stop one terminal or stop all terminals |
| Background agent row | click | open agent or subagent panel |
| Usage menu | select | open usage settings or usage details |
| Thread tab suggestions | select | switch chat, panel tab, or launch action |
| Artifact row | click | open artifact, possibly replacing a tab |
| Toolbar action | click | open bottom panel, artifact, terminal, or another thread tool |

The thread state should be modeled independently from the page shell:

    ThreadSurfaceState
      conversationId
      turns
      backgroundAgents
      backgroundTerminals
      chromeTabs
      artifacts
      rightPanel
      bottomPanel
      usage
      connection

## Settings page

settings-page shows a grouped settings navigator with:

- Personal, coding, integrations, and archived headings.
- Search and search-result selection.
- Host filtering and clearing the host filter.
- Team selection.
- Back navigation.
- Collapse and expand navigator controls.
- Section dispatch by slug to page components.
- Disabled or unavailable sections.

settings-tabs confirms that Worktrees is a nested tab page:

    /settings/worktrees
    /settings/worktrees?tab=environments

The current Settings General page should therefore be treated as one section component inside a routed Settings container. The container owns search, selected slug, host filter, and back behavior; the section owns its rows and controls.

## Data source evidence

The first scan found direct endpoint strings in the shared runtime and feature bundles, including:

- /automations, /automations/remove, /automations/save, /automations/set_status
- /wham/tasks/list, /wham/usage, /wham/usage/stream
- /settings/user, /settings/account_user_setting, /settings/notifications
- /wham/settings/code_review, /wham/settings/code_review_search
- /api/timedtext
- https://chatgpt.com/backend-api

These strings are evidence of data boundaries. They are not yet a complete API contract because some request paths are assembled dynamically or hidden behind query helpers.

## Next implementation order

1. Replace ReferenceFixture values for Projects and Scheduled Tasks with local repositories backed by deterministic in-memory data.
2. Add intent callbacks for the recovered Project and Scheduled Task actions.
3. Add thread panel state and terminal or agent actions behind the existing ThreadPage fixture.
4. Connect repositories to RemoteConnection or HostRelayConnection only after the local action model is stable.
5. Keep screenshot cases on the same repositories with fixed seed data.

This gives the current prototype real page behavior without coupling visual iteration to network availability.
