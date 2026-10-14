# Parent A1 review gate-r3

- Reviewed all four production/test files against original A1 contract and before snapshots. Short coordinator mutex changes only owner state, exact reservation token, permit membership and counters; owner work/callbacks stay outside it.
- Session append obtains activity before encode/mutex/log mutation, retains it across synchronous notice/failure handler; restore is admitted before reading input. Store create/load retain full catalog activity across callbacks and rollback.
- Explicit permits are identity-bound, cancellation-safe release uses NonCancellable, stale token/handle release cannot open replacement or stopped Store. Owner-stop disposal invalidates reservation and preserves reverse exact-instance cleanup.
- Original Store/Session owner mutex and synchronous notification semantics retained. Existing same-owner callback reentry limitations are not changed. No inherited coroutine-context authorization is added.
- Test repair verified exact two Default dispatcher wait wrappers plus withContext import; all assertions, Deferred barriers, 10-second timeouts and fixed 8 methods retained. Other 2260 inputs unchanged for this repair.
- All 35 fixed methods independently executed in four suites; raw XML names retained, normalized only trailing (). Full JVM app build exit0 and complete frozen/live 2261 input check passed.
- Only Session-layer admission accepted. Agent/Inbox/driver/Room/process/PTY quiescence, archive metadata, quiet restore and UI remain outside this batch. No screenshot/native/original pixel acceptance claimed.
