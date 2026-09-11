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

- Passed: `./kotlin test -p jvm -m harness-protocol`
- Passed: `./kotlin test -p jvm -m harness`
- Passed: `./kotlin task :harness:compileJvm :jvm-app:compileJvm`
- Passed: `./kotlin task :shared:compileAndroidDebug :shared:compileIosSimulatorArm64Debug :shared:compileIosArm64Debug`
- Passed: `git diff --check`
- Not run: a real RuoYi backend with Member credentials and an isolated database.
- Not run: Desktop-to-backend-to-Android remote exercise with a live model provider.
- Blocked: iOS application compile and simulator lifecycle, because this Linux environment has no Apple SDK/Xcode.
- Blocked: Android device lifecycle and network exercise, because no Android device/emulator was attached.

Fixture tests and compile results are development evidence only. They do not count
as the A19 real-environment exercise.

## Deployment and rollback

Deploy the backend feature with the existing server module and `yudao.websocket`
configuration. Keep the database unchanged; this feature uses in-memory single-instance
Host and pending registries and does not introduce a migration. Roll back by removing
the child changes and restarting the backend; clients then report disconnected or no
online Host without affecting local Desktop sessions.

For a later upstream sync, pin a new ruoyi-kmp commit, repeat the source/path audit,
review contract and security behavior changes, run the affected JVM/Android checks,
and repeat real backend and device exercises before updating this record.
