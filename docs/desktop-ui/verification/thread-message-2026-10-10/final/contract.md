# Thread message final release contract

Model: `SHUAIAPI/gpt-6.1-sol`; provider: `opencodex`. This closes the earlier provisional message delivery against the current source. The parent owns dynamic verification.

## Verified baseline

- Current inputs are the accepted terminal page R5 source (2,253 inputs).
- Relative to the provisional message snapshot, `ConversationComponents.kt` differs only by `private fun MessageCard` becoming `internal fun MessageCard` for subagent history reuse. Preserve it.
- `LiveConversationSurface.kt` and `ThreadMessageVisualInteractionTest.kt` still match that provisional snapshot.
- The older worker delivery describes an earlier test SHA and cannot release the current candidate. Preserve all historical evidence.

## Allowed source edit

Only the comment immediately above `val glyphs` in `apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt` may change. Explain that `getBoundingBox()` forwards to `Paragraph.getRectsForRange` with STRUT height and TIGHT width. These are character layout bounds, not independently measured raster glyph ink or baseline bounds. Line metrics provide vertical layout containment, and character bounds provide horizontal containment and diagnostics. Neither establishes original Codex pixel parity.

Preserve every executable byte, import, identifier, test method, assertion, tolerance and fixture. Keep exact text, last-character, no-ellipsis, wrap, containment, real input, scroll and scope isolation assertions. Do not rename `glyphs` in this narrow release. Do not change production, shared helpers, user configuration, workflows, Git state, or unrelated files. Do not run builds, tests, screenshots or native executions, and do not launch another worker.

## Release

Use `/tmp/harness-thread-message-final-baseline-20261010/baseline.json` and `contract.md`. Write new reports `/tmp/harness-thread-message-final-delivery-20261010.json` and `.md`; never overwrite the old delivery. Include:

- Exact model/provider, baseline and contract SHA256.
- All three message source paths with before/after SHA256; only the test comment changes.
- `tests_run=false`, `builds_run=false`, `screenshots_run=false`, `write_set_released=true`, `source_writes_stopped=true`.
- Current test class and all 14 unchanged methods; original provisional 33/33 explicitly labeled historical, current parent rerun pending.
- The single visibility change since provisional production, why it is preserved, and the corrected STRUT interpretation.
- Existing Harness numerical adaptations (672dp column, 85%/560dp human cap) and no native, remote or original same-state pixel acceptance.

After the reports are saved, stop edits and exit. Parent verifies protected bytes and all inputs, then freezes the current candidate and reruns the existing message14/live11/shell8 gate serially. No new tests or broad test expansion is required.
