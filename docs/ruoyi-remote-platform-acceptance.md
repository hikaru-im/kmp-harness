# RuoYi Remote Platform Delivery

This child implements A13-A20 on top of the integrated foundation and app shell.

## Runtime path

Desktop and Mobile use the authenticated RuoYi `/ws` endpoint. A Desktop Host sends
`harness.host.register`; a client sends `harness.relay.request` after selecting a Host
from `GET /app-api/harness/hosts`. All messages use the existing RuoYi
`WebSocketMessage` outer envelope and `ApiResult` result semantics.

The backend derives `tenantId`, `userType`, and `userId` from the authenticated
security context. Host registry keys and pending requests include that identity and
the current Host generation. Host replacement, disconnection, duplicate request IDs,
timeouts, and client disconnects complete pending requests and release registrations.

The remote client validates the protocol major version before opening a session,
binds every request to the selected Host ID, rejects sensitive methods locally, and
does not retry `session.create` or `session.prompt` after an uncertain result.
Relay events are delivered only to subscribed client WebSocket sessions. The client
deduplicates sequence numbers and exposes a sequence gap to the history recovery
caller; it never treats a gap as a successful contiguous stream.

### Host registration and event forwarding

The Desktop Host connects out to the configured RuoYi backend instead of waiting to be
polled:

- `DesktopHostRelay` follows the authenticated `MemberAccount` scope and registers the Host.
  Login, logout, tenant change, and backend change each start or stop the outbound connection;
  the previous scope's registration is closed before a new one is opened.
- `HostEventStream` turns the local Harness Session event log into ordered `RelayEvent`s
  (stream id is the Session id, sequence is the persisted event sequence) and feeds them to
  the relay adapter. Only appended Session events leave the Host; Agent commands, profile,
  and credentials stay Host-owned.
- `HostRelayConnection` publishes only the streams a client subscribed to. The Relay applies
  the same filter again, so a Host defect cannot widen the audience on its own.
- The Relay records the subscription and forwards `session.events.subscribe` /
  `session.events.unsubscribe` to the owning Host, so the Host learns what to publish. An
  event for an unsubscribed stream is never broadcast.

### Client subscription

Selecting or creating a Session is what makes a client subscribe:

- `SessionStreamDriver` subscribes the selected Session, consumes the forwarded events and the gap
  recoveries, and refreshes the screen from the authoritative history instead of trusting the event
  body.
- The driver follows the connection lifecycle: `RemoteConnectionOwner` publishes every connection it
  installs, and the driver releases the previous subscription and subscribes again on the new one.
  `collectLatest` cancels and joins the previous subscription before the next one starts, so a
  reconnect can never race an unsubscribe against a subscribe, and the screen refresh reads the
  current connection instead of a socket that was already closed.
- A transient history refresh failure is best-effort and never tears the subscription down; the next
  forwarded event or gap recovery retries it.
- The subscription is released when the selection ends, including when the surrounding coroutine is
  cancelled by cancelling the Session, switching Host, or logging out. `RemoteConnectionOwner.select`
  drops this client's subscriptions on a Host switch, and the shell drops the current Session
  selection when the connected Host changes, so the driver cannot re-register the previous selection
  on the new Host.
- The subscription is exactly what the Host and the Relay filter on, so a stream no client subscribed
  to is never published, and a sequence gap is repaired through history instead of being treated as a
  contiguous stream.

### Reconnect and history recovery

`RemoteConnectionOwner` is the only place that reconnects. It re-discovers the Host,
re-handshakes, rebuilds the subscriptions of the current identity scope, and re-pulls history
after a detected gap, using bounded exponential backoff. `session.create` and
`session.prompt` are never replayed on a new connection, because their outcome is uncertain
after a drop; an uncertain result surfaces as unknown and the caller refreshes history instead.

The shell drives every remote Session through `SessionStreamDriver`, so this is a reachable runtime
path rather than a library-only one: the driver is the caller of `subscribe`/`unsubscribe` and the
consumer of the forwarded events and the gap recoveries. It re-subscribes on every connection the
owner installs, so the subscription survives the reconnect path rather than dying with the old socket.

## Security policy

The following methods are rejected before the Host Gateway is reached:

- `credentials.set`
- `credentials.unset`
- `settings.secret.set`
- `llm.models.discover-temporary-key`
- every `file.*` method

The same policy is enforced by the RuoYi Relay and by the Desktop Host adapter.
Payload identity fields are ignored; the authenticated WebSocket principal is the
only source of identity.

## Verification record

Passed:

- `./kotlin test -p jvm -m harness-protocol` (11 tests)
- `./kotlin test -p jvm -m harness` (12 tests, including subscribe forwarding, unsubscribed-stream
  isolation, and missing-stream rejection at the Relay)
- `./kotlin test -p jvm -m shared` (35 tests in this candidate, including sequence tracking, owner
  reconnect, the session stream driver, connection-replacement re-subscription, refresh-failure
  isolation, subscription lifecycle, and the account session persistence and logout cases)
- `./kotlin test -p jvm -m jvm-app` (32 tests; 5 live-provider tests skipped without
  `HARNESS_OPENAI_LIVE_API_KEY`)
- `./kotlin task :shared:compileAndroidDebug :shared:compileIosSimulatorArm64Debug :shared:compileIosArm64Debug :android-app:compileAndroidDebug :ios-app:compileIosSimulatorArm64Debug :ios-app:compileIosArm64Debug :harness:compileJvm :jvm-app:compileJvm`
- `git diff --check`

Not run:

- A real RuoYi backend with Member credentials and an isolated database.
- Desktop-to-backend-to-Android remote exercise with a live model provider.

Blocked:

- Android device lifecycle and network exercise, because no Android device or emulator was attached.
- iOS simulator lifecycle and runtime exercise, because this Linux environment has no Apple SDK/Xcode.
  The Kotlin/Native iOS compilation targets themselves do compile and are listed above.

Fixture tests and compile results are development evidence only. They do not count
as the A19 real-environment exercise.

### Acceptance status

| Item | Status | Evidence |
| --- | --- | --- |
| A13 | Passed (code + tests) | Host registration, discovery, and handshake paths; `shared` connection tests |
| A14 | Passed (code + tests) | Identity scoping and generation guards; `harness` relay tests |
| A15 | Passed (code + tests) | Sensitive-method rejection retained on client, Relay, and Host adapter |
| A16 | Passed (code + tests) | The shell subscribes the selected Session and consumes forwarded events and gap recoveries; a Host switch drops the client subscriptions (`select`) instead of rebuilding them on the new Host, and connection replacement re-subscribes without racing cleanup; covered by `harness` and `shared` tests |
| A17 | Passed (code + tests) | Reconnect, re-handshake, subscription rebuild, history recovery, no command replay; the driver re-subscribes on the replaced connection and refreshes through it |
| A18 | Blocked (not run) | Android/iOS Kotlin compilation passes, which is compile-only evidence; the device and simulator runtime exercise was not run, because no Android device or emulator and no Apple SDK/Xcode are available, so runtime behavior stays blocked |
| A19 | Not run | Needs a real backend, Member login, isolated database, device, and provider |
| A20 | Passed | This document, plus the deployment and rollback notes below |

## Dependencies and compatibility

- The backend `harness` feature depends on `contracts` and `contracts/harness-protocol`; no new
  database migration is introduced.
- The Desktop application now depends on `modules/runtime`, because it registers the Host event
  listener against the Harness runtime context.
- Shared client code uses only multiplatform primitives so the Android and iOS targets keep
  compiling; no JVM-only API is used in `apps/shared`.

## Deployment and rollback

Deploy the backend feature with the existing server module and `yudao.websocket`
configuration. Keep the database unchanged; this feature uses in-memory single-instance
Host and pending registries and does not introduce a migration. Roll back by removing
the child changes and restarting the backend; clients then report disconnected or no
online Host without affecting local Desktop sessions.

For a later upstream sync, pin a new ruoyi-kmp commit, repeat the source/path audit,
review contract and security behavior changes, run the affected JVM/Android checks,
and repeat real backend and device exercises before updating this record.
