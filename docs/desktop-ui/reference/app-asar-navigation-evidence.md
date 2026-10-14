# app.asar navigation evidence

Read-only extraction from the locally installed Codex Desktop bundle. This file records the
measured bundle names, route literals, message ids, and component/state names that the shared
`PageId` / `NavigationState` model is derived from.

## Baseline

| Field | Value |
| --- | --- |
| Archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| App version | `26.930.31730` |
| SHA-256 | `64023959c7c3a4c19022c44f3e8d4b3e4010cefb6e2ea1edce0b535fd417776e` |
| Size | `522 MB` |
| Extraction method | `npx --yes @electron/asar@latest extract-file`, run inside a `mktemp -d /tmp/...` directory |

Extraction is read-only and never writes inside the repository. Working-tree `*.js` files must stay
absent.

## Analyzed bundles

| Bundle | Bytes | Role |
| --- | ---: | --- |
| `sidebar-d1bfe8c77b62.js` | 51319 | Scheduled tasks, task filters, run history |
| `sidebar-b3619131cde6.js` | 22590 | Sidebar sections: recents, pinned, coding, integrations, personal, archived |
| `projects-index-page-52d8e1d790a3.js` | 58616 | Projects index: local / cloud / remote projects, thread keys, search |
| `local-conversation-page-c5fb95945be8.js` | 55706 | Thread page shell and panel composition |
| `local-conversation-thread-4cbba7705144.js` | 353006 | Thread timeline, sources, plan, usage, created tasks, chrome tabs |
| `task-summary-739d44dbb9bd.js` | 3992 | Task summary entry, run status, unread |
| `thread-app-shell-chrome-db6a0c50aea0.js` | 102953 | Titlebar, right panel outlet, bottom panel outlet, header actions |
| `thread-right-panel-state-891c0842f478.js` | 100 | Exports `toggleThreadSidePanel` |
| `settings-page-bbd5e3fa3b8c.js` | 50261 | Settings section routing and navigation |
| `settings-tabs-4bf8cf96da38.js` | 1368 | Worktrees tab: `general`, `environments` |

## Route literals observed

```text
/new
/scheduled
/skills
/settings/usage
/settings/teams
/settings/teams/:teamId
/settings/worktrees
/settings/worktrees?tab=environments
/settings/cloud-environments?tab=preferences
```

Settings is a routed section with nested tabs, not a single placeholder page. `/skills` and
`/settings/teams` confirm separate extension and team routes that are out of scope for the first
pass.

## Sidebar sections and controls

Sections read from `sidebar-b3619131cde6.js`: `recents`, `pinned`, `coding`, `integrations`,
`personal`, `archived`, plus task and code-review groups.

Controls and states: `sidebar.search`, `sidebar.searchPlaceholder`, `sidebar.searchTasks`,
`sidebar.filterBy`, `sidebar.filterTasks`, `sidebar.pinned`, `sidebar.recents`, `sidebar.manageTeams`,
`sidebar.loading`, `sidebar.loadError`, `sidebar.noMatchingTasks`, `sidebar.noScheduledTasks`,
`sidebar.noSearchResults`, `sidebar.unavailable`, `sidebar.upcoming`, `sidebar.emptySection`,
`sidebar.display.options` (compact / detailed / sections), `sidebar.runs.empty`,
`sidebar.runs.error`, `sidebar.runs.showOlder`.

The sidebar is a projection over several data sources with its own display preferences, so it cannot
be modeled as one flat session list.

## App rail

Independent entry icons exist as separate bundles: `sidebar-projects-icon`, `sidebar-tasks-icon`,
`sidebar-library-icon`, `sidebar-plugins-icon`, `sidebar-new-chat-icon`, `sidebar-search-icon`,
`sidebar-more-icon`. This confirms a narrow app-level rail distinct from the workspace navigator,
with at least Projects, Tasks, Library, and Plugins as separate destinations.

## Projects index

Component and field names from `projects-index-page-52d8e1d790a3.js`: `ProjectsIndexPage`,
`localProjects`, `pinnedProjects`, `showChatGptProjects`, `showLocalProjects`, `showRemoteProjects`,
`localThreadKeys`, `recentThreadKeys`, `resolvedProjectsWithThreads`.

Messages: `Chats in {projectName}`, `Local chats in {projectName}`,
`Matching local chats in {projectName}`, `Create a project to organize chats and give ChatGPT access
to folders on your computer.`, `Local project`, `Archive chats`, `Archived`, `Could not load chats`,
`Loading projects`.

The project page is a project index with expandable children (`Project > chat/thread`), and it keeps
local projects separate from cloud and remote projects.

## Thread shell and panels

Names from `thread-app-shell-chrome-db6a0c50aea0.js`: `Titlebar`, `titlebarSlots`, `HeaderAction`,
`RightPanelOutlet`, `RightPanelTabs`, `RightPanelTabsEmptyState`, `RightPanelTabListAfterSticky`,
`BottomPanelOutlet`, `BottomPanelTabs`, `BottomPanelTabListAfter`, and message id
`appShell.header.bottomPanel`. `thread-right-panel-state-891c0842f478.js` exports
`toggleThreadSidePanel`.

Messages: `Chat toolbar`, `Chat actions`, `Bottom panel`, `Hide bottom panel`,
`Close {title} tab`, `Loading tabs…`, `Dismiss {title}`, `Fork chat`, `Archive chats`.

The thread surface owns a titlebar with header action slots, plus independent right and bottom panel
outlets. The project context panel is one right-panel tab, not part of the left navigation.

## Thread content tabs

From `local-conversation-thread-4cbba7705144.js`: `codex.localConversation.sources.title`,
`codex.localConversation.plan.title`, `codex.localConversation.usage.title`,
`codex.localConversation.createdTasks.title` with `status.starting` / `status.working` /
`status.done` / `status.failed`, `codex.localConversation.backgroundTasks.title.subagents`,
`codex.localConversation.backgroundTasks.title.backgroundProcesses`,
`codex.localConversation.gitSummary.*`, `codex.localConversation.pullRequestsSummary.title`,
`codex.localConversation.summaryPanel.toggle`, `togglePinned`,
`codex.localConversation.sideChats.title`, `codex.localConversation.chromeTabs.title`.

## Task summary

From `task-summary-739d44dbb9bd.js`: task detail routes back to a navigation source of
`conversation`, uses `automationId` / `source` (`local` or `cloud`), and renders `Created task` /
`Created tasks`, `Current task usage`, and `scheduled.summary.unavailable`. Task is a distinct domain
object with status and run history, projected into recent, project, and thread surfaces.

## Settings

`settings-page-bbd5e3fa3b8c.js` exposes routed settings with grouped navigation:
`settings.nav.heading.personal`, `settings.nav.heading.coding`, `settings.nav.heading.integrations`,
`settings.nav.heading.archived`, `settings.nav.title`, `settings.nav.back`,
`settings.nav.collapseSidebar`, `settings.nav.expandSidebar`, `settings.search.*`,
`settings.hostDropdown.allSettings`, `settings.teams.nav.*`. `settings-tabs-4bf8cf96da38.js`
defines the Worktrees tabs `general` and `environments` and navigates to
`/settings/worktrees?tab=environments`.

## Conclusions for the shared model

1. App Rail and Workspace Navigator are separate owners. The rail holds app-level destinations; the
   navigator holds workspace, projects, tasks, threads, and recents.
2. A project is a container with expandable task/thread children, not a thread list.
3. Task and Thread are distinct: a task carries status and run history and can appear in recents,
   inside a project, and in the thread surface.
4. The Project Context Panel and Bottom Panel are thread-surface overlays with their own outlets.
   They are not navigation destinations.
5. Settings is a routed, sectioned page with nested tabs.

These conclusions drive `PageId`, `OverlayId`, and `NavigationState` in
`apps/shared/src/im/hikaru/harness/client/shell/`.
