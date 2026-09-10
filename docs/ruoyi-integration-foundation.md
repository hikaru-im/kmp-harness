# RuoYi Foundation Integration

This candidate is based on the local `ruoyi-kmp` checkout at commit
`b4877d495c3479bf3632f4117a3c62b62c32d209`. No network fetch, database migration,
or user database modification is part of the integration.

## Source and licensing record

The source repository has no tracked `LICENSE`, `COPYING`, or `NOTICE` file at the
fixed commit. Existing source copyright/license headers and the upstream license
URL present in the server configuration are retained. The missing tracked license
artifact is recorded for release review before redistribution; this candidate does
not claim a new license.

Selected backend changes were synchronized by path after reviewing the fixed
commit: OAuth2 user-type token invalidation, WebProperties validation, safe global
error handling, S3 signing endpoint handling, member disable token revocation,
file endpoint authorization, and the sync whitelist/dispatcher tests. Existing
Harness WebSocket raw-content handling, lifecycle listeners, generation semantics,
and tenant context were retained when the upstream versions would remove them.

The following source areas were deliberately excluded: `backend/ui`, database and
deployment scripts, payment/code-generation upgrades, upstream untracked tools,
Admin UI replacement, Host Runtime/Loader internals, `harness-protocol`, and the
existing `harness-sessions.db` ownership path. No source path was copied merely to
make the two repositories look identical.

## Contracts and online sign-in

Contracts remain under `im.hikaru.contracts`. `InfraContracts` content is merged
into the existing `FileContracts.kt`, leaving one `FileInfo` and one
`FilePresignedUrl`; Harness WebSocket and relay contracts remain separate. The
fixed upstream contract tests are included alongside existing contract tests.

`SyncCommandWhitelist` allows only address create/update/delete and member profile
update. Sign-in has no sync handler and is not placed in the whitelist. The
dispatcher rejects an old or newly submitted sign-in command with
`SYNC_NOT_ALLOWED` before receipt replay or business mutation. Online sign-in keeps
its normal point/experience mutation path and has no change-feed append.

## Client foundation

`apps/shared/src/im/hikaru/harness/client/account` exposes the shell-facing
`MemberAccount`, `BackendTenant`, `MemberIdentity`, `AccountState`, typed profile
operations, refresh coordination, and account-owned resource registration.
Requests carry an immutable backend/tenant target and the current Member token;
generation checks prevent late results from being published after target, tenant,
account, or logout changes. Ktor redirects are disabled and cancellation is
re-thrown. A single mutex serializes concurrent refreshes.

Android stores the encrypted session with an Android Keystore AES-GCM key, iOS
uses a Harness-specific Keychain item, and Desktop uses Windows DPAPI, macOS
Keychain, or Linux `secret-tool` when an active session bus is available. If no
secure Desktop provider is available, `MemoryAppSessionStore` keeps the session in
memory only and exposes `MEMORY_ONLY`; no plaintext session JSON fallback exists.
Desktop logout writes a signed-out tombstone before best-effort secure-item
deletion, so a locked keychain cannot resurrect a logged-out account. Host model
credentials and `harness-sessions.db` are outside this namespace.

## Verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Baseline backend JVM tests | passed | `work/baseline-isolated.log`: 52/52 successful, 0 failed; original run also recorded one expected WebSocket listener error log from an existing regression test |
| Backend JVM regression after selected sync/security changes | passed | `work/backend-tests.log`: 62/62 successful, 0 failed, 1 skipped |
| Shared JVM build/tests | passed | `work/shared-tests.log`: 10/10 successful, 0 failed |
| Shared Android build | passed | `work/shared-android-build.log`: build successful |
| Shared iOS source compilation | passed | `work/shared-ios-build.log` reached `compileIosSimulatorArm64TestDebug` |
| Shared iOS test linking/execution | blocked | Linux host cannot link `ios_simulator_arm64` test program; requires Apple/Xcode host |
| Real backend, model provider, Desktop, or Android device E2E | not run | No credentials/device/model endpoint were supplied; fixtures and compilation are not represented as E2E |

The backend Spring context checks use test configuration and H2/embedded mocks;
they do not assert connectivity to the user's running Postgres, Redis, or MySQL.
The existing PostgreSQL integration test remains environment-gated and was not
run with credentials.

## Handoff

The next Child can import the account package from `apps/shared/src/im/hikaru/harness/client/account`.
Use `accountModule(...)` to install it into an application-owned Koin scope. Do
not inject Runtime Service, create a Host on Mobile, or reuse the Host session
database for account state. Remote connections should register an
`AccountResource` with the current `AccountState.generation` and close it on
logout, target changes, and shutdown.
