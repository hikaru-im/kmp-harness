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

### Synchronization manifest

The fixed upstream commit was reviewed by path. The resulting source decisions are:

| Decision | Fixed-source path | Harness path and result |
| --- | --- | --- |
| merged | `contracts/src/app/member/MemberSyncContracts.kt` | `contracts/src/im/hikaru/contracts/app/member/MemberSyncContracts.kt`; retained address/profile sync and removed sign-in sync |
| merged | `contracts/src/infra/InfraContracts.kt` | `contracts/src/im/hikaru/contracts/infra/FileContracts.kt`; retained one `FileInfo` and one `FilePresignedUrl` |
| synchronized | `contracts/test/*.kt` | `contracts/test/*.kt`; added eight wire/default/nullability regression suites |
| synchronized | `backend/common/**/OAuth2TokenCommonApi.kt`, `backend/features/system/**/OAuth2TokenApiImpl.kt` | paired user-type token invalidation contract and implementation |
| merged | `backend/framework/**/YudaoSecurityAutoConfiguration.kt`, `WebProperties.kt`, `GlobalExceptionHandler.kt` | retained Harness `/ws` authorization and added validation/error fixes plus security and tenant regressions |
| synchronized | `backend/features/sync/**/SyncCommandWhitelist.kt`, `SyncCommandDispatcher.kt`, `SyncCommandProcessor.kt` | whitelist-first dispatch; address/profile remain allowed and sign-in is rejected |
| merged | `backend/features/member/**/MemberSignInRecordServiceImpl.kt`, `MemberUserServiceImpl.kt` | retained online sign-in/profile behavior; removed `MemberSignInSyncCommandHandler.kt` |
| synchronized | `backend/features/infra/**/AppFileController.kt`, `S3FileClient.kt` | file authorization and signing changes required by the merged contracts |
| adapted | `apps/shared/src/core/{network,session}/**`, `apps/shared/src/feature/member/**/auth/**` | new `apps/shared/src*/im/hikaru/harness/client/account/**` package with immutable identity targets, serialized refresh and platform secure stores |
| retained | no source replacement | `contracts/harness-protocol/**`, `backend/features/harness/**`, `modules/**`, `apps/jvm-app/**`, and `backend/ui/**` are unchanged from the Harness baseline in this Child |
| excluded | `backend/database/**`, `backend/deploy/**`, `backend/features/pay/**`, codegen, Admin UI, and untracked upstream tools | no files imported and no database or deployment action executed |

The candidate diff from Harness baseline `2f038820c04449915e4ce19a84b562bb99127cae`
contains no changes under `modules/**` or `backend/ui/**`. Existing unrelated
documents outside this delivery record are also absent from the diff.

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

### Secure-store acceptance (A6)

A real platform secure store was not exercised in this Child. The candidate was built in a WSL
Linux environment with no Android device or emulator, no Apple SDK/Xcode, no macOS host, and no
running Secret Service, and the Desktop application was not run on the available Windows host
either. Under the 2026-09-12 decision, A6 is
satisfied by the Desktop-side login persistence and logout semantics tests below plus an
explicit record of every platform item that was not executed. An unexecuted platform item is
never recorded as passed; it stays blocked until its re-verification condition is met on the
real platform.

| Platform item | Status | Reason it was not executed | Re-verification condition |
| --- | --- | --- | --- |
| Android Keystore encrypted session restart recovery | not run (blocked) | No Android device or emulator is attached to this host | Install the app on a device or emulator, sign in, kill the process, restart, and confirm the session is restored from the Keystore-backed record |
| iOS Keychain restart recovery | not run (blocked) | A Linux host has no Apple SDK or Xcode | On a macOS host with Xcode, sign in inside a simulator, restart the app, and confirm recovery from the Harness-specific Keychain item |
| Desktop Windows DPAPI | not run (blocked) | This Child only used the WSL Linux build host and never launched the Desktop application on a Windows host | On Windows, sign in, restart the application, and confirm DPAPI-backed recovery |
| Desktop macOS Keychain | not run (blocked) | No macOS host is available to this Child | On macOS, sign in, restart the application, and confirm Keychain-backed recovery |
| Desktop Linux Secret Service | not run (blocked) | `/usr/bin/secret-tool` is installed, but no Secret Service is running: `secret-tool lookup service harness-test` exits 1 with a connection failure and `busctl --user list` cannot reach the user bus, so no keyring or kwallet owner exists and the store degrades to `MEMORY_ONLY` | With a running Secret Service (gnome-keyring or kwallet), sign in, restart the application, and confirm recovery from the collection item |

Executed Desktop-side evidence for A6:

- `apps/shared/test@jvm/im/hikaru/harness/client/account/DesktopAppSessionStoreTest.kt` asserts that a newly constructed `DesktopAppSessionStore` over the same secure backend restores the written session (restart recovery), and that a signed-out tombstone keeps a logged-out account from being resurrected when the secure backend cannot clear its item.
- `apps/shared/test/im/hikaru/harness/client/account/MemberAccountTest.kt` asserts that a non-durable store reports `PersistenceStatus.MEMORY_ONLY`, that no session is recoverable from a freshly constructed store and account after a restart, and that logout clears the record so no store instance can recover it.

These Desktop tests are contract-level evidence only. They do not demonstrate Keystore, Keychain, DPAPI, or Secret Service behavior, and they do not turn the platform items above into passes.

## Verification record

| Check | Result | Evidence |
| --- | --- | --- |
| Baseline backend JVM tests | passed | Builder command output: 52/52 successful, 0 failed; the run also recorded one expected WebSocket listener error log from an existing regression test |
| Contracts JVM tests | passed | 25/25 successful, including member, file, sync and WebSocket serialization |
| Harness protocol JVM tests | passed | 11/11 successful, including Relay and Host/Client description contracts |
| Member JVM tests | passed | 18/18 successful, including real password login/refresh token persistence, online-only sign-in, address and profile sync |
| Sync JVM tests | passed | 27/27 successful, including sign-in rejection, whitelist completeness, replay and tenant/user isolation |
| System JVM tests | passed | 62/62 successful, 1 PostgreSQL environment-gated test skipped; Admin login, refresh and logout regressions passed |
| Framework JVM tests | passed | 57/57 successful, 1 PostgreSQL environment-gated test skipped; tenant rejection, SecurityFilterChain, raw WebSocket and lifecycle regressions passed |
| Production server context | passed | `YudaoServerApplication` started with H2 and test-only Redis substitutes; `appAuthController` was registered, 1/1 test successful |
| Shared JVM tests | passed | 35/35 successful, 0 failed, including refresh serialization, cancellation, identity generation, logout resource cleanup, account session persistence across a store restart and logout clearing the non-durable record |
| Session persistence JVM tests | passed | 8/8 successful, including event/history restart recovery and real Room duplicate-location close/reopen behavior |
| Shared Android build | passed | Builder command output: build successful |
| Shared iOS source compilation | passed | Builder source compilation completed for `iosSimulatorArm64` |
| Shared iOS test linking/execution | blocked | Current Linux run fails only at `linkIosSimulatorArm64TestDebug`: `PROGRAM` is unavailable for `ios_simulator_arm64` on `linux_x64`; requires Apple/Xcode host |
| Real backend, model provider, Desktop, or Android device E2E | not run | No credentials/device/model endpoint were supplied; fixtures and compilation are not represented as E2E |

The contracts, harness protocol, member, sync, system, framework, production server context,
shared, and session persistence rows were re-run in the repair Child on 2026-09-12 with
`./kotlin test -p jvm -m <module>`; every count above is that run's own output, and
`git diff --check` reported no whitespace errors in the same tree. The baseline backend row and
the Android/iOS build rows are the foundation Child's records and were not re-run here.

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
