# ThreadMessage delivery — 2026-10-08

status=implemented; migration_completed=true; write_set_released=true
tests_run=false; builds_run=false

Model: gpt-6.1-sol; designated route: SHUAIAPI/gpt-6.1-sol; provider: opencodex (parent contract; no transport probe).

## Delivered

- Human bubbles require USER + UserMessageSource; tool/plugin/model content remains neutral with truthful source semantics.
- Naturally sized end-aligned bubbles; unboxed assistant content; complete text and recursively ordered tool results.
- Dedicated clickable, keyboard-focusable reasoning/tool headers; body clicks preserve expansion; isError alone labels failure.
- Timeline scope key uses Host ID, member identity and session ID; generation remains excluded.
- New ThreadMessageVisualInteractionTest contains 14 tests using measured owning-root mouse/key/wheel input and 10-second deadlines.

## File hashes

| File | Before SHA256 | After SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt | b4bbb99b0f6a011f7b3f11c6e76f86736185e9a617c2126ddcd39f99d460d263 | e3e6dcf0effe9b18fea7f773a62c6fafdebfcb74fa287d8ed16059b305fc7739 |
| apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt | 4c4051045c76aabeb575d9807b072591a8c4cd03e703d186148d5de86d9f3d1e | 3e1296365fd574f8e3978f63d584020014e3944c2d63c0eec6c8761a18bb1dd6 |
| apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt | absent (new file) | 700e93ff41a4fc714768afe4253b5c56192ff18bee1b415aa7d2dc0d529ea32e |

Before hashes come from /tmp/harness-thread-message-delivery-20261008.before.json. Restricted git diff --check and per-file untracked git diff --no-index --check passed for all three files.

## Parent verification

Run serially after freezing the released write set:

- im.hikaru.harness.desktop.ThreadMessageVisualInteractionTest — 14 new tests
- im.hikaru.harness.desktop.LiveConversationInteractionTest
- im.hikaru.harness.desktop.LiveConversationScreenshotTest

New tests:

- threadMessages600Light
- threadMessages600Dark
- threadMessages1440Light
- threadMessages1440Dark
- typedSourcesNeverTurnToolPluginOrOddRoleMessagesIntoHumanBubbles
- realHeaderMouseTogglesWhileExpandedBodyClicksLeaveDisclosureOpen
- realTabEnterAndSpaceOperateIndependentFocusableHeaders
- sameScopeLoadingFailureAndGenerationRefreshPreserveDisclosureAndScroll
- equalHistorySessionSwitchResetsDisclosureAndScroll
- equalHistoryHostSwitchResetsDisclosureAndScroll
- equalHistoryMemberSwitchResetsDisclosureAndScroll
- unmatchedRepeatedCallIdsAndNestedResultsKeepRawContentAndChronologicalOrder
- onlyIsErrorLabelsFailureAndNeutralResultsInventNoExecutionState
- realWheelTraversesLongWrappedMessagesAndReachesTheActualFinalMarker

## Screenshots and numerical adaptations

No screenshots captured: no tests/builds were run. The four screenshot test methods implement 12 captures (collapsed/expanded/long-content × 600×800/1440×900 × light/dark), actual capture count=0. Set HARNESS_THREAD_MESSAGE_CAPTURE_DIR; default /tmp/harness-thread-message-captures-20261008. Output uses actual owning-root PNG plus SHA256 and geometry JSON at density/fontScale=1.

Content column cap 672dp; human bubble cap min(85% of available column, 560dp); 16dp corners; 16dp horizontal and 10dp vertical padding. These are Harness adaptations; original runtime width/color/spacing and pixel parity remain unverified.

No compilation or runtime validation is claimed. Typed fixtures exercise the production offscreen LiveThreadSurface and do not contact real model/controller/transport. This continuation made no Menu changes. Write set released; all further writes stopped.
