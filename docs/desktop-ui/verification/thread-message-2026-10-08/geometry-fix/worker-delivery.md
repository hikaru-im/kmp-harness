# ThreadMessage repair delivery — 2026-10-08

status=implemented; migration_completed=true; write_set_released=true
tests_run=false; builds_run=false

Actual model: gpt-6.1-sol. Designated route: SHUAIAPI/gpt-6.1-sol; provider=opencodex (parent-supplied route, no independent transport probe).

## Evidence and repair

Parent initial serial run compiled the previous candidate:14 tests,3pass/11fail/0error/0skip. Evidence: /tmp/harness-pages-verified-20261008/thread-message/initial-approved/ThreadMessageVisualInteractionTest/{summary.json,TEST-junit-jupiter.xml,command.log}.

Four capture failures report short bubble end1056 vs1040 at1440,584 vs568 at600. Each16px delta matches human horizontal padding. The message testTag followed padding and measured inner content. The same tag/description now precedes padding and measures the padded bubble frame, retaining its existing production size/style.

Seven other failures stopped at hasVisualOverflow for short text and bodies. That flag was being treated as clipping evidence with TextOverflow.Visible; initial logs do not contain actual paragraph/character dimensions. Fractional float-to-integer size rounding is a possible cause, not claimed as measured. The test now records size, paragraph dimensions, didOverflowWidth/Height, hasVisualOverflow, constraints, overflow/maxLines, each line and nonwhitespace character layout bounds before assertion. Diagnostics go to THREAD_TEXT_GEOMETRY test stdout and screenshot geometry textLayout.

Exact full text, final line end, no ellipsis, wrapping and horizontal containment checks remain. Added size equality, line/character containment in measured text bounds with the existing1px raster rounding tolerance, horizontal character containment in viewport, and viewport-visible character containment in actual clipped node bounds. These checks preserve failures for actual layout escape/cropping. Character layout boxes are layout evidence, not independent raster-ink measurement.

Real mouse/key/wheel input, owning-root measured coordinates and10-second deadlines remain unchanged. No tests added or removed during repair;14 tests total. LiveConversationSurface.kt unchanged in this repair; Menu untouched.

## Original baseline and current hashes

Before SHA256 values remain the original Tooltip-era pre-implementation baseline, not this repair candidate. New test baseline is absent/null.

| Path | before_sha256 | after_sha256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/app/ConversationComponents.kt | b4bbb99b0f6a011f7b3f11c6e76f86736185e9a617c2126ddcd39f99d460d263 | 5941d4f63c0aad58a7561b9d7beb0b38dd61d9f2df4f93c55b2dea1b9122c932 |
| apps/shared/src/im/hikaru/harness/client/app/LiveConversationSurface.kt | 4c4051045c76aabeb575d9807b072591a8c4cd03e703d186148d5de86d9f3d1e | 3e1296365fd574f8e3978f63d584020014e3944c2d63c0eec6c8761a18bb1dd6 |
| apps/jvm-app/test/im/hikaru/harness/desktop/ThreadMessageVisualInteractionTest.kt | null (absent) | 84aa3be76fc78a4d51104b5796413870a7fb4c603e9129adf00b7dd262b9087e |

## Checks and parent verification

Restricted git diff --check plus per-file untracked diff check: passed. No builds or tests run by worker. Repaired candidate requires parent serial compilation/test run.

- im.hikaru.harness.desktop.ThreadMessageVisualInteractionTest —14 tests
- im.hikaru.harness.desktop.LiveConversationInteractionTest
- im.hikaru.harness.desktop.LiveConversationScreenshotTest

## Screenshot and implementation limits

No screenshots captured for repaired candidate; planned12 captures:600×800/1440×900 ×Light/Dark ×collapsed/expanded/long-content. HARNESS_THREAD_MESSAGE_CAPTURE_DIR configures output (default /tmp/harness-thread-message-captures-20261008); density/fontScale1; actual root PNG/SHA256 and geometry JSON.

Retained Harness adaptations:672dp content column, min(85%,560dp) human bubble cap,16dp corners,16dp horizontal/10dp vertical padding. Original same-state pixel parity remains unverified. Source fixtures exercise production LiveThreadSurface without real controller/transport/model.

Write set released; further writes stopped.
