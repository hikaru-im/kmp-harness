# Session persistence

`session-persistence` is a Room 3 KMP provider. It stores one `harness_sessions`
header row and an append-only `harness_session_events` row for every envelope.
The event log remains the only source of truth; messages, Inbox and retry state
are rebuilt by the existing Session/Agent code.

Platform launchers construct the database with their own Room builder, bundled
SQLite driver, query context and file location, then inject a
`SessionPersistenceDatabaseFactory` into `SessionPersistencePlugin`. The plugin
does not read environment variables or access platform contexts. Desktop's
default file is normally `harness-sessions.db`, separate from an embedding
app's database.

The old JVM JSON provider is intentionally removed. Existing JSON files are not
migrated; a new Room database starts empty. Schema changes must use explicit
migrations and never destructive migration.
