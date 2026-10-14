# Thread message first implementation contract

Model/provider: SHUAIAPI/gpt-6.1-sol via opencodex. Reuse the current designated worker. Parent owns builds and tests, serially on a frozen source snapshot.

## Source and scope

Use current typed Message/MessageContent from Connection.kt and actual LiveThreadSurface history. No synthetic production messages, turn protocol, service/controller changes or new dependency. ASAR confirms end-aligned capped ordinary user bubbles, rounded-2xl and py-2.5, and separate assistant content; actual runtime width/color/spacing values and original same-state screenshot remain unverified.

Allowed implementation files:

- apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt
- apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt (only composition lifetime key around the timeline)
- apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt (new)

## Required behavior

1. Replace full-width bordered message cards and visible generic avatar/name rows with an end-aligned naturally sized human user bubble and unboxed assistant body. Classify from typed source: only explicit UserMessageSource with USER role is a human bubble; USER + ToolMessageSource must render neutral tool content, and plugin/unknown source must never acquire a human label. Retain truthful accessible source/role description. A bounded content column using existing ContentMaxWidth is a Harness adaptation, not verified Codex pixel parity. Choose narrow-window bounds that preserve all content; document numerical adaptations.
2. Preserve typed content, chronological order and every raw text/argument/result, including nested tool result content. Text must wrap without ellipsis, clipping or line caps. Do not infer running/success/duration from presence of call/result. isError alone may label error results. Neutral tool result icon/color replaces unconditional success. No call/result pairing, result hiding, markdown/file/attachment UI, or fabricated execution states in this slice.
3. Compact reasoning/tool-call disclosures use a dedicated focusable clickable header with expansion semantics and native keyboard handling. Expanded body must not itself toggle on click. Preserve existing collapsed Reasoning · 展开 label, expanded Reasoning label, and tool name text so current tests remain intact. Tool output stays visible without requiring a new disclosure.
4. Scope timeline remembered disclosures/scroll composition to state.host?.hostId, state.host?.identity, state.selectedSessionId using a minimal key block in LiveConversationSurface.kt. A session/Host/member switch must not leak expansion even when IDs/text match. Same-scope history Loading/Failed updates and connection generation refresh must preserve current content/disclosures. No composer or notice/error/retry change.
5. Preserve unmatched results, repeated tool-call IDs, nested content and content order. Use current Message IDs as timeline keys; do not introduce speculative pairing.

## Verification deliverables

New tests exercise production LiveThreadSurface. Normal interactions use real mouse/key/wheel input from measured nodes and their owning root; do not use semantics performClick/performScrollTo/RequestFocus shortcuts. Existing tests remain unchanged. Include meaningful coverage for source classification (especially USER + ToolMessageSource and plugin), compact/wide geometry and full wrapped text, header mouse and keyboard disclosure, body-click stability, same-scope update preservation, session/Host/member isolation with equal IDs/content, unmatched/repeated-ID nested results in order, real wheel reaching the final long-content marker, and isError distinction without invented success/running/time. Fixtures only in tests.

Capture representative collapsed/expanded/long-content screens at 600x800 and 1440x900 in light/dark using HARNESS_THREAD_MESSAGE_CAPTURE_DIR; save bitmap SHA256 and measured bounds/layout geometry, assert expected dimensions and containment. Document offscreen fixture limits. Reuse existing native wheel unit conversion if necessary, without changing shared helpers.

Do not run builds/tests or create other workers, modify Comet/config/git state, or overwrite unrelated dirty work. Capture before/after hashes. Release /tmp/harness-thread-message-delivery-20261008.json and .md with exact changed files and hashes, test count/class, tests_run=false, builds_run=false, write_set_released=true, numerical adaptation notes and remaining boundaries. After release stop edits until parent feedback.
