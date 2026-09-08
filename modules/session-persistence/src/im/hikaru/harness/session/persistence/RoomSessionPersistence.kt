package im.hikaru.harness.session.persistence

import androidx.room3.ColumnInfo
import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.room3.withWriteTransaction
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentSessionEventNames
import im.hikaru.harness.agent.Inbox
import im.hikaru.harness.agent.InboxSnapshot
import im.hikaru.harness.agent.InboxSpliceEvent
import im.hikaru.harness.llm.retry.LlmRetryEvent
import im.hikaru.harness.llm.retry.LlmRetrySessionEvents
import im.hikaru.harness.llm.retry.LlmRetryStartedEvent
import im.hikaru.harness.llm.retry.LlmRetryTerminalEvent
import im.hikaru.harness.llm.retry.findPendingLlmRetryRecovery
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.loader.PluginDefinition
import im.hikaru.harness.loader.jsonObjectConfig
import im.hikaru.harness.loader.pluginDefinition
import im.hikaru.harness.session.AssistantChunkEvent
import im.hikaru.harness.session.AssistantMessageEvent
import im.hikaru.harness.session.InterruptedTurnEndReason
import im.hikaru.harness.session.RequestContextEvent
import im.hikaru.harness.session.RequestHeaderEvent
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionEventEnvelope
import im.hikaru.harness.session.SessionEventNames
import im.hikaru.harness.session.SessionException
import im.hikaru.harness.session.SessionErrorCode
import im.hikaru.harness.session.SessionHeader
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.SessionSurfaceOperation
import im.hikaru.harness.session.StepEndEvent
import im.hikaru.harness.session.StepStartEvent
import im.hikaru.harness.session.TurnEndEvent
import im.hikaru.harness.session.TurnStartEvent
import im.hikaru.harness.session.UserMessageEvent
import im.hikaru.harness.session.sessions
import im.hikaru.harness.tools.ToolCallEvent
import im.hikaru.harness.tools.ToolResultEvent
import im.hikaru.harness.tools.ToolSessionEvents
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private const val DATABASE_VERSION = 1

/** Header row for a durable Harness Session. */
@Entity(tableName = "harness_sessions")
data class SessionRow(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val version: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    val cwd: String?,
    @ColumnInfo(name = "parent_session")
    val parentSession: String?,
    @ColumnInfo(name = "seed_length")
    val seedLength: Long?,
    val origin: String?,
    @ColumnInfo(name = "delegation_depth")
    val delegationDepth: Long?,
    @ColumnInfo(name = "agent_preset")
    val agentPreset: String?,
)

/** Append-only event envelope. (session_id, seq) is the durable identity. */
@Entity(
    tableName = "harness_session_events",
    primaryKeys = ["session_id", "seq"],
)
data class SessionEventRow(
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val seq: Long,
    val type: String,
    val time: Long,
    val data: String,
    @ColumnInfo(name = "surface_op")
    val surfaceOp: String?,
    @ColumnInfo(name = "source_event_seqs")
    val sourceEventSeqs: String?,
    val ignorable: Boolean?,
)

@Dao
interface SessionPersistenceDao {
    @Query("SELECT * FROM harness_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun findSession(sessionId: String): SessionRow?

    @Query("SELECT * FROM harness_session_events WHERE session_id = :sessionId ORDER BY seq ASC")
    suspend fun findEvents(sessionId: String): List<SessionEventRow>

    @Query("SELECT COUNT(*) FROM harness_session_events WHERE session_id = :sessionId")
    suspend fun countEvents(sessionId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(row: SessionRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(rows: List<SessionEventRow>)

    @Query("DELETE FROM harness_session_events WHERE session_id = :sessionId AND seq >= :seq")
    suspend fun deleteEventsFrom(sessionId: String, seq: Long)

}

@Database(
    entities = [SessionRow::class, SessionEventRow::class],
    version = DATABASE_VERSION,
    exportSchema = false,
)
@ConstructedBy(SessionPersistenceDatabaseConstructor::class)
abstract class SessionPersistenceDatabase : RoomDatabase() {
    abstract fun sessions(): SessionPersistenceDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SessionPersistenceDatabaseConstructor : RoomDatabaseConstructor<SessionPersistenceDatabase> {
    override fun initialize(): SessionPersistenceDatabase
}

/** Neutral adapter used when an embedding app composes these tables into its own Room database. */
interface SessionDatabaseAccess : Disposable {
    val dao: SessionPersistenceDao
    suspend fun <T> transaction(block: suspend () -> T): T
}

/** Platform code owns the Room builder, SQLite driver, path and dispatcher. */
fun interface SessionPersistenceDatabaseFactory {
    fun create(): SessionDatabaseAccess
}

/** Explicit migration seam; future schema changes must be appended here. */
object SessionPersistenceMigrations {
    val all: Array<Migration> = emptyArray()
}

interface SessionPersistence : Disposable {
    suspend fun flush(session: Session)
    suspend fun load(id: SessionId): Session
    suspend fun inbox(id: SessionId, agentId: AgentId = AgentId(id.value)): InboxSnapshot
}

/** Test-only platform bridge; production launchers should inject their own factory. */
expect fun sessionPersistencePluginForTest(databasePath: String): SimplePlugin

object SessionPersistenceKey : ServiceKey<SessionPersistence>("session-persistence")

val Context.persistence: SessionPersistence
    get() = require(SessionPersistenceKey)

/** Installs the Room-backed persistence service supplied by a platform factory. */
class SessionPersistencePlugin(
    private val databaseFactory: SessionPersistenceDatabaseFactory,
) : SimplePlugin {
    override suspend fun apply(context: Context, scope: EffectScope) {
        val database = databaseFactory.create()
        val provider = RoomSessionPersistence(database, context.sessions)
        scope.add(database)
        scope.add(provider)
        scope.add(context.provide(SessionPersistenceKey, provider))
        scope.add(context.on(im.hikaru.harness.session.SessionEvents.Flush) { notice -> provider.flush(notice.session) })
    }
}

const val SESSION_PERSISTENCE_PLUGIN_NAME: String = "session-persistence"

/** Creates the profile-loadable definition while keeping the platform factory outside config. */
fun sessionPersistencePluginDefinition(factory: SessionPersistenceDatabaseFactory): PluginDefinition =
    pluginDefinition(
        name = SESSION_PERSISTENCE_PLUGIN_NAME,
        plugin = SessionPersistencePlugin(factory),
        config = jsonObjectConfig(allowMissing = true) { value ->
            require(value.isEmpty()) { "session-persistence config does not accept platform fields" }
            Unit
        },
    )

private class RoomSessionPersistence(
    private val database: SessionDatabaseAccess,
    private val store: SessionStore,
) : SessionPersistence {
    private val mutex = Mutex()
    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = false
        classDiscriminator = "type"
    }
    private var disposed = false

    override suspend fun flush(session: Session) {
        mutex.withLock {
            check(!disposed) { "Session persistence is disposed" }
            // Capture the event log while holding the provider lock. This
            // prevents an older concurrent flush from publishing a stale
            // header after a newer append has already been observed.
            val header = session.header
            val events = session.events()
            val sessionRow = header.toRow()
            val eventRows = events.map { it.toRow(session.id, json) }
            database.transaction {
                database.dao.upsertSession(sessionRow)
                database.dao.deleteEventsFrom(session.id.value, eventRows.size.toLong())
                database.dao.upsertEvents(eventRows)
            }
        }
    }

    override suspend fun load(id: SessionId): Session {
        val snapshot = mutex.withLock {
            check(!disposed) { "Session persistence is disposed" }
            readSnapshot(id)
        }
        if (snapshot.header.id != id) {
            throw SessionException("Persisted Session id does not match its row", SessionErrorCode.INVALID_HEADER)
        }
        validateSnapshot(snapshot)
        val handle = store.load(snapshot.header, snapshot.events)
        if (repairOpenTurn(handle.session)) flush(handle.session)
        return handle.session
    }

    override suspend fun inbox(id: SessionId, agentId: AgentId): InboxSnapshot {
        val snapshot = mutex.withLock {
            check(!disposed) { "Session persistence is disposed" }
            readSnapshot(id)
        }
        if (snapshot.header.id != id) {
            throw SessionException("Persisted Session id does not match its row", SessionErrorCode.INVALID_HEADER)
        }
        validateSnapshot(snapshot)
        return Inbox.replay(snapshot.events, agentId)
    }

    private suspend fun readSnapshot(id: SessionId): Snapshot {
        val row = database.dao.findSession(id.value)
            ?: throw SessionException("Persisted Session '${id.value}' was not found", SessionErrorCode.NOT_LIVE)
        return try {
            Snapshot(row.toHeader(), database.dao.findEvents(id.value).map { it.toEnvelope(json) })
        } catch (error: SessionException) {
            throw error
        } catch (error: Throwable) {
            throw SessionException("Persisted Session '${id.value}' is invalid", SessionErrorCode.INVALID_EVENT, error)
        }
    }

    private suspend fun repairOpenTurn(session: Session): Boolean {
        val events = session.events()
        if (findPendingLlmRetryRecovery(events) != null) return false
        var openTurn: Long? = null
        var openStep: Long? = null
        for (event in events) {
            when (event.type) {
                SessionEventNames.TURN_START -> openTurn = event.data.jsonObject["turn"]?.jsonPrimitive?.longOrNull
                SessionEventNames.STEP_START -> openStep = event.data.jsonObject["step"]?.jsonPrimitive?.longOrNull
                SessionEventNames.STEP_END -> openStep = null
                SessionEventNames.TURN_END -> { openTurn = null; openStep = null }
            }
        }
        val turn = openTurn ?: return false
        openStep?.let { session.append(im.hikaru.harness.session.SessionEventKeys.StepEnd, StepEndEvent(turn, it)) }
        session.append(im.hikaru.harness.session.SessionEventKeys.TurnEnd, TurnEndEvent(turn, InterruptedTurnEndReason))
        return true
    }

    private fun validateSnapshot(snapshot: Snapshot) {
        snapshot.events.forEachIndexed { index, event ->
            if (event.seq != index.toLong() || event.seq < 0L || event.time < 0L) {
                throw SessionException("Session event sequence is not contiguous", SessionErrorCode.INVALID_EVENT)
            }
            if (event.sourceEventSeqs?.any { it < 0L || it >= event.seq } == true) {
                throw SessionException("Session event provenance is invalid", SessionErrorCode.INVALID_EVENT)
            }
            val expectedSurface = event.type == SessionEventNames.USER_MESSAGE || event.type == SessionEventNames.ASSISTANT_MESSAGE
            if ((event.surfaceOp != null) != expectedSurface || (expectedSurface && event.surfaceOp != SessionSurfaceOperation.APPEND) ||
                (event.type in knownEventNames && event.ignorable == true)
            ) throw SessionException("Session event '${event.type}' has invalid envelope metadata", SessionErrorCode.INVALID_EVENT)
            try {
                when (event.type) {
                    SessionEventNames.TURN_START -> decode(TurnStartEvent.serializer(), event)
                    SessionEventNames.TURN_END -> decode(TurnEndEvent.serializer(), event)
                    SessionEventNames.STEP_START -> decode(StepStartEvent.serializer(), event)
                    SessionEventNames.STEP_END -> decode(StepEndEvent.serializer(), event)
                    SessionEventNames.USER_MESSAGE -> decode(UserMessageEvent.serializer(), event)
                    SessionEventNames.ASSISTANT_CHUNK -> decode(AssistantChunkEvent.serializer(), event)
                    SessionEventNames.ASSISTANT_MESSAGE -> decode(AssistantMessageEvent.serializer(), event)
                    SessionEventNames.REQUEST_HEADER -> decode(RequestHeaderEvent.serializer(), event)
                    SessionEventNames.REQUEST_CONTEXT -> decode(RequestContextEvent.serializer(), event)
                    AgentSessionEventNames.INBOX_SPLICED -> decode(InboxSpliceEvent.serializer(), event)
                    ToolSessionEvents.Call.name -> decode(ToolCallEvent.serializer(), event)
                    ToolSessionEvents.Result.name -> decode(ToolResultEvent.serializer(), event)
                    LlmRetrySessionEvents.Retry.name -> decode(LlmRetryEvent.serializer(), event)
                    LlmRetrySessionEvents.RetryStarted.name -> decode(LlmRetryStartedEvent.serializer(), event)
                    LlmRetrySessionEvents.Terminal.name -> decode(LlmRetryTerminalEvent.serializer(), event)
                    else -> if (event.ignorable != true) throw SessionException("Unknown required Session event '${event.type}'", SessionErrorCode.INVALID_EVENT)
                }
            } catch (error: SessionException) {
                throw error
            } catch (error: Throwable) {
                throw SessionException("Session event '${event.type}' has invalid data", SessionErrorCode.INVALID_EVENT, error)
            }
        }
    }

    private fun <T : Any> decode(serializer: kotlinx.serialization.KSerializer<T>, event: SessionEventEnvelope) {
        json.decodeFromJsonElement(serializer, event.data)
    }

    override suspend fun dispose() { mutex.withLock { disposed = true } }

    private data class Snapshot(val header: SessionHeader, val events: List<SessionEventEnvelope>)

    private companion object {
        val knownEventNames = setOf(
            SessionEventNames.TURN_START, SessionEventNames.TURN_END, SessionEventNames.STEP_START, SessionEventNames.STEP_END,
            SessionEventNames.USER_MESSAGE, SessionEventNames.ASSISTANT_CHUNK, SessionEventNames.ASSISTANT_MESSAGE,
            SessionEventNames.REQUEST_HEADER, SessionEventNames.REQUEST_CONTEXT, AgentSessionEventNames.INBOX_SPLICED,
            ToolSessionEvents.Call.name, ToolSessionEvents.Result.name, LlmRetrySessionEvents.Retry.name,
            LlmRetrySessionEvents.RetryStarted.name, LlmRetrySessionEvents.Terminal.name,
        )
    }
}

private fun SessionHeader.toRow() = SessionRow(
    sessionId = id.value, version = version, createdAt = createdAt, cwd = cwd,
    parentSession = parentSession?.value, seedLength = seedLength, origin = origin?.name,
    delegationDepth = delegationDepth, agentPreset = agentPreset,
)

private fun SessionRow.toHeader() = SessionHeader(
    version = version, id = SessionId(sessionId), createdAt = createdAt, cwd = cwd,
    parentSession = parentSession?.let(::SessionId), seedLength = seedLength,
    origin = origin?.let { im.hikaru.harness.session.SessionOrigin.valueOf(it) },
    delegationDepth = delegationDepth, agentPreset = agentPreset,
)

private fun SessionEventEnvelope.toRow(sessionId: SessionId, json: Json) = SessionEventRow(
    sessionId = sessionId.value, seq = seq, type = type, time = time,
    data = json.encodeToString(JsonElement.serializer(), data), surfaceOp = surfaceOp?.name,
    sourceEventSeqs = sourceEventSeqs?.let { json.encodeToString(ListSerializerLong, it) }, ignorable = ignorable,
)

private fun SessionEventRow.toEnvelope(json: Json): SessionEventEnvelope {
    val operation = surfaceOp?.let { SessionSurfaceOperation.valueOf(it) }
    val sources = sourceEventSeqs?.let { json.decodeFromString(ListSerializerLong, it) }
    return SessionEventEnvelope(
        type = type, seq = seq, time = time,
        data = json.decodeFromString(JsonElement.serializer(), data), surfaceOp = operation,
        sourceEventSeqs = sources, ignorable = ignorable,
    )
}

private val ListSerializerLong = kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<Long>())
