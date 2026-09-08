# Session

`session` is the provider-neutral, append-only event log used by future Agent modules. The log is the only source of
truth; model messages and request metadata are replayed from committed events.

```kotlin
val fiber = runtime.install(SessionPlugin())
val owner = runtime.context.child()
val session = owner.createSession(
    options = CreateSessionOptions(cwd = "/workspace"),
)

session.append(
    SessionEventKeys.UserMessage,
    UserMessageEvent(turn = 1, message = message),
)

val history = session.deriveMessages()
runtime.context.sessions.flush()
owner.dispose()
```

The module does not persist files, invoke providers, run retries, or implement Agent loops. Persistence integrates
later through `session/event` and the awaited `session/flush` barrier.
