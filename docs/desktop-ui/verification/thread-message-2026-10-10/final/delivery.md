# Thread message final delivery

- Model: `SHUAIAPI/gpt-6.1-sol`; provider: `opencodex`.
- Baseline: `/tmp/harness-thread-message-final-baseline-20261010/baseline.json`.
- Baseline SHA256: `771bf13fa74f9acf174674a3aa384e71d612ac59b5177ef649e5964db1215d19`.
- Contract: `/tmp/harness-thread-message-final-baseline-20261010/contract.md`; workspace copy: `/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/reverse-engineering/thread-message-final-release-contract-2026-10-10.md`.
- Contract SHA256: `b350d40ef0b8b462d0cda1c4cba8f575aa16319548713b6027d7fd083e284193` (both copies match).

## Source hashes

| Path | Before SHA256 | After SHA256 | Changed |
| --- | --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt` | `abd62534b2b3624827b223fd1ed58bb79631d477f4496f1d7ffe7faccb96b0e4` | `abd62534b2b3624827b223fd1ed58bb79631d477f4496f1d7ffe7faccb96b0e4` | false |
| `apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt` | `3e1296365fd574f8e3978f63d584020014e3944c2d63c0eec6c8761a18bb1dd6` | `3e1296365fd574f8e3978f63d584020014e3944c2d63c0eec6c8761a18bb1dd6` | false |
| `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt` | `eda6918f092e6622da0b595e772034fc045279660d581e84641d4a840377b9ef` | `354ac828631a9e31796789dbcc87e2f6dfacf9c889d5636182141422a9c7ba38` | true |

## Static release evidence

- Before edit: all 2,253 current inputs matched the baseline.
- After edit: 2,252 inputs unchanged; only the test comment changed. No unexpected input differences.
- Exact comment replacement verified against the accepted R5 snapshot; every byte outside that comment is identical.
- Protected bytes before and after SHA256: `d0da6e305887a4cf51ae823dc917d62d6b4b4ee153dc52d3a9ff4d345e564737`.
- Executable bytes, imports, identifiers (including `glyphs`), methods, assertions, tolerances and fixtures are preserved.
- Exact text, last-character, no-ellipsis, wrap, containment, real input, scroll and scope isolation assertions are preserved.
- Both production sources are byte-identical to the baseline.
- The contract documents the single production change since provisional as `private fun MessageCard` becoming `internal fun MessageCard` for accepted subagent history reuse. Current `internal` visibility is verified and preserved.

## Corrected comment

getBoundingBox() forwards to Paragraph.getRectsForRange with STRUT height and TIGHT width. The results are character layout bounds, not independently measured raster glyph ink or baseline bounds. Line metrics provide vertical layout containment; character bounds provide horizontal containment and diagnostics. Neither establishes original Codex pixel parity.

```diff
--- baseline/apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt
+++ current/apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt
@@ -373,9 +373,10 @@
             Rect(text.getLineLeft(line), text.getLineTop(line), text.getLineRight(line), text.getLineBottom(line))
         }
         // TextOverflow.Visible can report fractional paragraph overflow against rounded IntSize.
-        // getBoundingBox() is a glyph box in paragraph coordinates: glyph ascenders and
-        // descenders may extend outside the line box. Use line metrics for vertical clipping
-        // and retain glyph boxes as horizontal/diagnostic evidence.
+        // getBoundingBox() forwards to Paragraph.getRectsForRange with STRUT height and TIGHT width.
+        // These character layout bounds are not independently measured raster glyph ink or baseline bounds.
+        // Line metrics provide vertical layout containment; character bounds provide horizontal
+        // containment and diagnostics. Neither establishes original Codex pixel parity.
         val glyphs = expected.indices.filter { !expected[it].isWhitespace() }.map { offset ->
             offset to text.getBoundingBox(offset)
         }
```

## Current test inventory

Class: `im.hikaru.harness.desktop.ThreadMessageVisualInteractionTest`. All 14 methods are unchanged:

1. `threadMessages600Light`
2. `threadMessages600Dark`
3. `threadMessages1440Light`
4. `threadMessages1440Dark`
5. `typedSourcesNeverTurnToolPluginOrOddRoleMessagesIntoHumanBubbles`
6. `realHeaderMouseTogglesWhileExpandedBodyClicksLeaveDisclosureOpen`
7. `realTabEnterAndSpaceOperateIndependentFocusableHeaders`
8. `sameScopeLoadingFailureAndGenerationRefreshPreserveDisclosureAndScroll`
9. `equalHistorySessionSwitchResetsDisclosureAndScroll`
10. `equalHistoryHostSwitchResetsDisclosureAndScroll`
11. `equalHistoryMemberSwitchResetsDisclosureAndScroll`
12. `unmatchedRepeatedCallIdsAndNestedResultsKeepRawContentAndChronologicalOrder`
13. `onlyIsErrorLabelsFailureAndNeutralResultsInventNoExecutionState`
14. `realWheelTraversesLongWrappedMessagesAndReachesTheActualFinalMarker`

## Verification status and acceptance limits

- Original provisional **33/33 is historical**; it is not current-candidate verification.
- **Current parent rerun pending** after this worker process exits. Parent verifies protected bytes and all inputs, freezes the current candidate, then runs the existing `message14` / `live11` / `shell8` gate serially.
- Existing Harness adaptations remain a 672dp column and an 85%/560dp human-message cap.
- No native, remote or original same-state pixel acceptance is established.
- No new tests or broad test expansion.

## Release flags

`tests_run=false`, `builds_run=false`, `screenshots_run=false`, `write_set_released=true`, `source_writes_stopped=true`.

No native executions, additional workers, configuration edits, Git mutations or historical report overwrites. Source writes have stopped; release is handed to the parent for its pending gate.
