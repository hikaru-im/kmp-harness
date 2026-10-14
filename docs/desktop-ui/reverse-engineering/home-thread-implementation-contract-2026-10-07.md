# Home / Thread implementation contract — 2026-10-07

Status: source analysis complete for the first real Session slice; implementation and visual acceptance pending.

## Scope and source authority

Reproduce the local Codex Home and conversation composition in static Kotlin/Compose. Use the current installed `/Applications/ChatGPT.app/Contents/Resources/app.asar`. Evidence metadata and bounded excerpts are in `home-thread-evidence-2026-10-07.json`; offsets are explicitly character indices or archive content byte offsets. Cloud, voice, pets, browser/computer control, image library and automatic review remain excluded.

The actual Home chain is `home-composer-route → app-primary BMt → VMt/HMt → F9 → pMt → hMt → _Z → _ct → mct`. `VMt` is the Codex branch without the ChatGPT mode switch. The local conversation page imports `Oc`, exported as `_Z`, so Home and Thread share the composer adapter. `xZ` adds the real queued-message list to `mct`. Do not copy similarly named BrowserSkills, Aeon/Orbit, Sketch, medical or enterprise composers.

## Composition and layout

| Region | Source | Implementation rule |
| --- | --- | --- |
| Home main | `hMt`, `YAt` | Flexible main area; hero and composer are separate regions, without prototype cards or a create-session form. |
| Home hero | `nMt` | Centered regular-weight heading, min-height 28 spacing units; real project name only when supplied. Source text is “What should we build?”. |
| Home composer | `eH`, `e3e`, `mct` | Same editor/footer as Thread; action context may be above or below. Preserve selected project/cwd where available. |
| Editor | `app-initial pFt` | Multiline minimum 2.5rem, line-height 1.35, editor maximum min(25dvh, configured maximum); scroll editor instead of growing past viewport. |
| Footer | `tH`, `V4e` | Leading model/mode controls and trailing send/stop. Switch to compact layout below 40rem content width. No dead enabled buttons. |
| Thread | `local-conversation-page`, `_ct` | Real typed message timeline above composer; titlebar references the selected route session. |

Source rem/spacing values are browser values; conversion to Compose dp must be documented. The conditional 42rem thread-width override in `hMt` is not a universal width token. First acceptance covers 420, 600 and 1440 logical widths, light/dark, real empty/loading/error/populated states. Exact pixel parity still requires reference screenshots.

## Real data and action mapping

| UI action / data | Existing consumer | Limits |
| --- | --- | --- |
| List sessions / recent rows | `Connection.session.list()` | Enumerates live agents, not all persisted chats. Summary has id/createdAt/cwd/agentPreset, no title/status/model. |
| Select / open Thread | `PageId.Thread.threadId`, `session.history(id)` | Honor the route id. A missing live session renders a recoverable unavailable state. |
| Home submit | `session.create(options, agentOptions)` then `session.prompt(created.id, userMessage(text))` | Create once, retain the created id if prompt fails; subsequent retry must not create another session. |
| Thread submit | `session.prompt(selected, userMessage(text))` | Snapshot the draft and session before suspending. Prevent duplicate in-flight submit. |
| Completion | `SessionPrompt.awaitIdle()` | Local implementation waits for agent idle; current Relay implementation is Unit. Do not infer remote generation completion from the receipt. |
| Stop | `session.cancel(id)` | Catch errors and restore action state. Stop is distinct from navigating away. |
| History updates | `SessionStreamDriver`, Relay events and HistoryRecovery | History is authoritative; filter selected session and scope/generation. Release subscriptions on selection/connection disposal. |
| Timeline content | typed `Message` Text/Reasoning/ToolCall/ToolResult | Reuse/evolve `ConversationComponents`; never convert runtime messages to ReferenceFixture. |
| Model menu | local `ModelSettingsRepository` / snapshot | Repository describes this desktop, not a remote Host. Only expose its catalog when connection is proven local; remote defaults may be used with explicit catalog-unavailable state. |
| Attach / permissions / mode / queue / archive / rename | No verified corresponding Session consumer | Disabled with a short product-facing reason, or omitted if excluded. No empty click handlers. |

Never synthesize sample projects/messages/status. A session id is a truthful fallback label. An in-memory title derived from an actual user message may be used only if labeled as such and not represented as server metadata. Reference fixtures remain for preview/tests only.

## State/controller contract

First worker owns a Compose-free `client/conversation` state/controller plus matching shared tests. It accepts the real `Connection`, explicit host scope, and selected `SessionId?`. APIs are typed and static. No new gateway/backend behavior in this slice.

1. Bind/change connection using referential connection identity plus explicit host scope/generation. Clear data/selection when Host changes; same-Host reconnect may retain route selection while invalidating old requests. Logout cannot retain remote history.
2. Async list/history/create/send/cancel results apply only to their captured connection, selection, request token and generation. A slow old response cannot overwrite a newer selection or refresh. Cancellation propagates.
3. New draft submits acquire the busy state before the first suspension. Create failure preserves draft; prompt failure after creation preserves selected id and draft. No silent automatic resend after an ambiguous transport error.
4. Clear the sent draft at prompt acceptance only if its text/revision still matches the snapshot. Editing while create/prompt/awaitIdle is suspended must preserve the new text. Completion/history errors after acceptance cannot make the already accepted prompt look unsent.
5. Local awaitIdle can drive sending→idle. Relay receipt is only delivery acknowledgement. Model this distinction in the connection/controller contract; do not show a false remote idle/completed badge. A stop action may be available for the selected real session when running state is unknown.
6. Controller has immutable StateFlow state, phase-specific sanitized errors/retry capabilities, loading/empty/disconnected states, and bounded close/disposal. Do not expose raw exception messages or credentials.
7. Keep service operations injectable for deterministic delayed/failure tests. Tests may use fakes; runtime never does.

## Integration slice after shortcuts release

Use the controller once at the app level; Home and Thread share draft/action state and real list. Remove `HarnessReferenceFixture` runtime reads from Home, Thread, workspace navigator, recent tasks and context. An absent Project API renders empty state; creating project or opening context cannot insert example records. Route callbacks use actual SessionId, not fixture title/id. Home submit navigates to the newly created actual session. Thread selection comes from the route, not an internal unrelated picker.

Replace the existing private `SessionScreen` provider/model text form and its `take(8)` list with real route-driven presentation. The workspace recent list shows every available live session in a scrollable region. Keep Settings/Connections/shortcut integration intact. Renderer evolves actual typed messages and stable ids. Any intentional screenshot fixtures must be injected by tests.

## Required verification

- State tests: route selection; create→prompt; duplicate submit; failed create; failed prompt retains created id; accepted prompt followed by history error; edited draft survives delayed send; stop failure; stale Host/selection/list/history response; logout; same-Host reconnect; Relay receipt distinction.
- Integration tests: Home submission opens actual returned id; navigation opens actual history; no runtime fixture text; disconnected send disabled; model catalog not leaked to remote Host; unsupported actions disabled; subscriptions released.
- Desktop screenshots: 420/600/1440 light/dark for Home and Thread empty/loading/error/populated typed messages, long reasoning/tool output and composer. Check actual line/layout bounds and scrolling.
- Build/test runs are serialized by parent, using `/tmp` build directory. Workers do not build.

## Known remaining backend work

Persisted chat catalog/title/rename/archive, project catalog/folder ownership, attachment transport, session model/permission mutation, remote model catalog and truthful remote run status remain separate service work. Terminal/subagent and Git/Worktree contracts are separate. This slice is complete only after real consumers and scoped state checks pass; compilation alone is insufficient.
