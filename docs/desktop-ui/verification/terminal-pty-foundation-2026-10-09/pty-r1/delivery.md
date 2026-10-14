# Terminal PTY foundation delivery

Status: implemented; source write set released. Model: `SHUAIAPI/gpt-6.1-sol`; actual model: `gpt-6.1-sol`; provider: `opencodex`.

`builds_run=false` · `tests_run=false` · `screenshots_run=false` · `write_set_released=true` · `source_writes_stopped=true`

## Fixed baseline

Baseline: `/tmp/harness-terminal-pty-foundation-baseline-20261009/baseline.json` (2216 inputs).
Baseline SHA256: `92628c75ee649a161a331779c606334521cff41f3caa0ccce4bc2d5369da225a`.
Contract SHA256: `61399b9af665e1cf8d012c277ec6c722f6e3b01c195ec7f432f115d32a1fd2d0`.

## Five files and exact SHA256

| File | Before SHA256 | After SHA256 |
| --- | --- | --- |
| `modules/exec-jvm/module.yaml` | `5b94c22c4804734b5932caed8286607cb85664e2f948274bf9604b99be9a9eba` | `f9b8354be43f0430e4445522bd255745ee6f5393f023f2e1cf4b9a7d138d20f1` |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt` | null (new file) | `d2b2de4a764270fc5a53d6d5356ed2b963c002fc0b2fdc61e569c81c57377e9d` |
| `modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalsContractTest.kt` | null (new file) | `af77d9e8376a45d502a5c87fb2782a63140bb87cf670e80f218322e26593f32a` |
| `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt` | null (new file) | `acb8358fdaffe0f131239b5902fe9471569c28c32ef836966b1307e609873016` |
| `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessionsTest.kt` | null (new file) | `76394f48e5502552172e5ac92daba4ea40e5c596e38de49b8fa426e71dd6cc1e` |

## Implementation

- `modules/exec-jvm/module.yaml`: Add the verified intellij-dependencies repository, pty4j 0.14.0 and jediterm-core 3.76 only.
- `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt`: Serializable common owner/key, create/list/read/mutation/shutdown results and bounded styled screen/history snapshots; no runtime or Host wiring.
- `modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalsContractTest.kt`: Four fixed contract tests for complete identity, serialization and DTO invariants.
- `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt`: Owned real PTY, persistent UTF-8/ANSI parser, detached snapshots, bounded native IO, kernel/emulator resize, explicit unknown outcomes and verified cleanup. Native future/join/cwd work uses an IO dispatcher under an active NonCancellable outer context.
- `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessionsTest.kt`: Twelve fixed real PTY tests, including a real after-write latch proving single caller-thread responsiveness and explicit UnknownOutcome after caller cancellation in the existing ninth test.

## Lock, dispatcher and cleanup contracts

- ownership: Service-generated UUID incarnation; explicit reserved entry before native startup; canonical cwd confined to the bound Session root; no implicit shell creation on list/read.
- parser: One InputStreamReader UTF-8 decoder and continuous TtyConnector/TtyBasedArrayDataStream/JediEmulator; raw-tail truncation never restarts ANSI decoding.
- lock_order: Model ReentrantLock gate before TerminalTextBuffer lock; connector releases the gate for real blocking read and device reply waits, then reacquires it.
- snapshot: Copy each text run, style, color and line into common value DTOs under both locks; no TerminalLine.copy/native model references; cursor converted from one-based native getters.
- native_io: Single bounded native IO executor serializes actual write+flush/device replies/resize. Applied is returned only after observed completion; failure/timeout/cancellation returns explicit UnknownOutcome and fences further input.
- caller_dispatcher: Bounded future.get and actual Thread.join run on Dispatchers.IO. The IO round trip returns to an active outer NonCancellable Job on the original dispatcher so native delivery can return an explicit unknown result even after caller cancellation.
- cleanup: Capture actual PID and startInstant via ProcessHandle.of(pid), retain only birth-verified owned descendants, close real streams, stop owned process/descendants and join real workers before entry removal. Natural Exited requires actual exit code plus EOF and completed parsing.
- responsiveness_test: Existing ninth method sends RESPOND through the real PTY, holds only the after-native-write return, probes the same single caller thread within one second, cancels caller and requires the independently captured UnknownOutcome. It then checks real ACK, fenced input, explicit close, dead owned processes/workers and empty inventory; test-owned executor is closed in finally.

## New test source: 2 classes / 16 methods

### im.hikaru.harness.session.api.SessionTerminalsContractTest (4)

- `blankIdentityComponentsAreRejected`
- `terminalDtosRoundTripWithCompleteIdentity`
- `snapshotAndSizeInvariantsAreEnforced`
- `resultsRetainRequestedOwnerAndTerminalIdentity`

### im.hikaru.harness.exec.jvm.JvmPtyTerminalSessionsTest (12)

- `readingDoesNotCreatePtyAndCapacityIsBounded`
- `realPtyAndResizeUpdateKernelAndEmulator`
- `realInputAndSplitUtf8RetainDecoderState`
- `splitAnsiKeepsParserAndSnapshotsDetached`
- `carriageReturnCursorAndEraseUpdateScreen`
- `alternateScreenRestoresPrimary`
- `staleScopeSessionIncarnationAndTerminalHaveNoSideEffects`
- `writeFailureAfterDeliveryIsUnknownAndNeverResent`
- `blockingReaderClosesAndOwnedWorkersFinish`
- `naturalExitKeepsFinalSnapshotUntilExplicitClose`
- `scrollbackAndRawTailAreBounded`
- `invalidDimensionsInputAndCwdAreRejectedBeforeIo`

## Parent verification gate

The two new classes (16) plus ExecProcessContractTest (16), SessionApiContractTest (1), SessionDescendantsContractTest (5): five classes / 38 methods. Save actual XML with zero failures/errors/skips and both runtime dependency graphs. No passing test result is claimed here.

## Static checks

- Scoped `git diff --check`: exit 0, no diagnostics.
- All five paths are untracked. Per-file `git diff --no-index --check /dev/null <path>`: exit 1 for added-file differences, no whitespace diagnostics.
- Four Kotlin files: valid literal escapes, balanced delimiters, no NUL bytes; fixed method names/counts verified. These checks do not compile or execute Kotlin.

## Unresolved limits / parent-owned verification

- No build, test or screenshot was run by this worker; the 16 new methods and 38-test parent gate are source counts, not passing results.
- Only macOS x86_64/amd64 is exposed as supported; Windows, Linux, arm64, remote transport and native UI are not verified.
- Host/Gateway/Local/Relay/UI registration and real Host object authorization are deferred; matching text owner identity alone is not a Host capability.
- The constructor synchronously validates canonicalRoot via toRealPath; future adapters must instantiate the capability on IO.
- Native IO/stream close may fail or outlive bounded observation. UnknownOutcome retains ownership/capacity until actual cleanup is confirmed; no automatic input replay.
- The parent must freeze the current manifest, compile, run the five classes/38 methods without failures/errors/skips and archive exec-jvm plus jvm-app runtime dependency graphs for JNA/stdlib resolution.

All source writes stopped after the ninth-test correction. No build/test/screenshot, additional worker/thread, Host/UI/wire change, git mutation or user process operation was performed by this continuation.
