package im.hikaru.harness.session

import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.LlmCallConfigAdapterDefaults
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.loader.Entry
import im.hikaru.harness.loader.Loader
import im.hikaru.harness.loader.Registry
import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionContractTest {

    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = false
            classDiscriminator = "type"
        }

    private class FixedIds : SessionIdFactory {
        private var next = 1

        override fun create(): SessionId =
            SessionId("session-${next++}")
    }

    private class FixedClock : SessionClock {
        private var next = 100L

        override fun nowEpochMilliseconds(): Long = next++
    }

    @Serializable
    private data class Diagnostic(
        val value: String,
    )

    private suspend fun installStore(runtime: Runtime): SessionStore {
        runtime.install(
            SessionPlugin(
                idFactory = FixedIds(),
                clock = FixedClock(),
            )
        )
        return runtime.context.sessions
    }

    @Test
    fun appendShouldSnapshotAndDeriveOnlySurfaceMessages() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val owner = runtime.context.child()
        val session = owner.createSession()

        val user = createUserMessage(listOf(TextBlock("hello")))
        session.append(SessionEventKeys.UserMessage, UserMessageEvent(1, user))
        session.append(
            SessionEventKeys.TurnStart,
            TurnStartEvent(1),
        )
        session.append(
            SessionEventKeys.AssistantMessage,
            AssistantMessageEvent(
                turn = 1,
                step = 1,
                message = createAssistantMessage(
                    content = listOf(TextBlock("world")),
                    provider = "test",
                    model = "model",
                ),
            ),
        )
        session.append(
            SessionEventKeys.AssistantMessage,
            AssistantMessageEvent(
                turn = 1,
                step = 1,
                message = createAssistantMessage(
                    content = emptyList(),
                    provider = "test",
                    model = "model",
                ),
            ),
        )

        val customKey = SessionEventKey("diagnostic/event", Diagnostic.serializer(), ignorable = true)
        session.append(customKey, Diagnostic("original"))

        val history = session.deriveMessages()
        assertEquals(2, history.size)
        assertEquals("hello", (history[0].content.single() as TextBlock).text)
        assertEquals("world", (history[1].content.single() as TextBlock).text)

        val snapshot = session.events()
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L), snapshot.map { it.seq })
        assertTrue(snapshot.last().ignorable == true)

        val copied = snapshot.toMutableList()
        copied.clear()
        assertEquals(5, session.events().size)

        owner.dispose()
        assertTrue(store.list().isEmpty())
        runtime.context.dispose()
    }

    @Test
    fun invalidAppendShouldNotConsumeSequenceAndSourcesMustBeEarlier() = runTest {
        val runtime = Runtime()
        installStore(runtime)
        val session = runtime.context.createSession()

        assertFailsWith<SessionException> {
            session.append(
                SessionEventKeys.UserMessage,
                UserMessageEvent(
                    turn = 1,
                    message = createUserMessage(listOf(TextBlock("invalid"))),
                ),
                sourceEventSeqs = listOf(0L),
            )
        }
        assertEquals(0, session.events().size)

        session.append(
            SessionEventKeys.UserMessage,
            UserMessageEvent(1, createUserMessage(listOf(TextBlock("valid")))),
        )
        assertFailsWith<SessionException> {
            session.append(
                SessionEventKeys.TurnStart,
                TurnStartEvent(1),
                sourceEventSeqs = listOf(0L, 0L),
            )
        }
        assertEquals(listOf(0L), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun concurrentAppendShouldBeLinearized() = runTest {
        val runtime = Runtime()
        installStore(runtime)
        val session = runtime.context.createSession()

        (0 until 50).map { index ->
            async {
                session.append(
                    SessionEventKeys.TurnStart,
                    TurnStartEvent(index + 1L),
                )
            }
        }.awaitAll()

        assertEquals((0L until 50L).toList(), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun createdFailureShouldRollbackAndEmitDisposed() = runTest {
        val runtime = Runtime()
        val disposed = mutableListOf<SessionId>()
        runtime.context.on(SessionEvents.Disposed) {
            disposed += it.session.id
        }
        runtime.context.on(SessionEvents.Created) {
            error("created veto")
        }

        val store = installStore(runtime)
        val error =
            assertFailsWith<IllegalStateException> {
                runtime.context.createSession()
            }

        assertEquals("created veto", error.message)
        assertEquals(listOf(SessionId("session-1")), disposed)
        assertTrue(store.list().isEmpty())
        runtime.context.dispose()
    }

    @Test
    fun cancellationAfterEnterShouldRollbackOwnedAndDirectCreation() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val owner = runtime.context.child()
        val disposed = mutableListOf<SessionId>()
        var cancellationTarget: Job? = null
        runtime.context.on(SessionEvents.Created) {
            cancellationTarget?.cancel()
        }
        runtime.context.on(SessionEvents.Disposed) {
            disposed += it.session.id
        }

        val ownedCreation =
            async(start = CoroutineStart.LAZY) {
                owner.createSession(id = SessionId("owned-cancelled"))
            }
        cancellationTarget = ownedCreation
        ownedCreation.start()
        assertFailsWith<CancellationException> { ownedCreation.await() }

        val directCreation =
            async(start = CoroutineStart.LAZY) {
                store.create(id = SessionId("direct-cancelled"))
            }
        cancellationTarget = directCreation
        directCreation.start()
        assertFailsWith<CancellationException> { directCreation.await() }

        assertTrue(store.list().isEmpty())
        assertEquals(
            listOf(SessionId("owned-cancelled"), SessionId("direct-cancelled")),
            disposed,
        )
        runtime.context.dispose()
    }

    @Test
    fun oldHandleMustNotDetachReplacementWithSameId() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val first = store.prepare(SessionId("same"))
        val firstHandle = store.enter(first)
        firstHandle.dispose()

        val second = store.prepare(SessionId("same"))
        val secondHandle = store.enter(second)
        firstHandle.dispose()

        assertFalse(store.list().contains(first))
        assertTrue(store.get(SessionId("same")) === second)
        secondHandle.dispose()
        runtime.context.dispose()
    }

    @Test
    fun envelopeShouldHaveStableShapeAndDetachedJsonSnapshots() = runTest {
        val runtime = Runtime()
        installStore(runtime)
        val session = runtime.context.createSession()
        val key = SessionEventKey("diagnostic/event", JsonElement.serializer(), ignorable = true)
        val mutableData =
            linkedMapOf<String, JsonElement>(
                "nested" to JsonArray(listOf(JsonPrimitive("before"))),
            )

        val appended = session.append(key, JsonObject(mutableData))

        assertEquals(
            "{\"type\":\"diagnostic/event\",\"seq\":0,\"time\":101," +
                "\"data\":{\"nested\":[\"before\"]},\"ignorable\":true}",
            json.encodeToString(SessionEventEnvelope.serializer(), appended),
        )
        mutableData["nested"] = JsonArray(listOf(JsonPrimitive("after")))

        val firstSnapshot = session.events().single()
        val secondSnapshot = session.events().single()
        assertEquals(
            JsonPrimitive("before"),
            (firstSnapshot.data.jsonObject.getValue("nested") as JsonArray).single(),
        )
        assertNotSame(appended.data, firstSnapshot.data)
        assertNotSame(firstSnapshot.data, secondSnapshot.data)

        val surface =
            session.append(
                SessionEventKeys.UserMessage,
                UserMessageEvent(1, createUserMessage(listOf(TextBlock("surface")))),
                sourceEventSeqs = listOf(0L),
            )
        val surfaceJson =
            json.encodeToJsonElement(SessionEventEnvelope.serializer(), surface).jsonObject
        assertEquals(
            setOf("type", "seq", "time", "data", "surfaceOp", "sourceEventSeqs"),
            surfaceJson.keys,
        )
        assertEquals(JsonPrimitive("append"), surfaceJson["surfaceOp"])
        assertEquals(JsonArray(listOf(JsonPrimitive(0L))), surfaceJson["sourceEventSeqs"])

        runtime.context.dispose()
    }

    @Test
    fun serializationAndShapeFailuresShouldBeAtomic() = runTest {
        val runtime = Runtime()
        val published = mutableListOf<Long>()
        runtime.context.on(SessionEvents.Appended) { published += it.event.seq }
        installStore(runtime)
        val session = runtime.context.createSession()

        val nonFinite =
            assertFailsWith<SessionException> {
                session.append(
                    SessionEventKey("diagnostic/number", JsonElement.serializer()),
                    JsonPrimitive(Double.NaN),
                )
            }
        assertEquals(SessionErrorCode.INVALID_EVENT, nonFinite.code)

        val wrongCoreShape =
            assertFailsWith<SessionException> {
                session.append(
                    SessionEventKey("turn/start", Diagnostic.serializer()),
                    Diagnostic("not-a-turn"),
                )
            }
        assertEquals(SessionErrorCode.INVALID_EVENT, wrongCoreShape.code)

        val surfaceMismatch =
            assertFailsWith<SessionException> {
                session.append(
                    SurfaceSessionEventKey("diagnostic/event", Diagnostic.serializer()),
                    Diagnostic("visible"),
                )
            }
        assertEquals(SessionErrorCode.INVALID_EVENT, surfaceMismatch.code)

        val replacement =
            assertFailsWith<SessionException> {
                session.append(
                    SurfaceSessionEventKey(
                        SessionEventNames.USER_MESSAGE,
                        UserMessageEvent.serializer(),
                        SessionSurfaceOperation.REPLACE,
                    ),
                    UserMessageEvent(1, createUserMessage(listOf(TextBlock("replace")))),
                )
            }
        assertEquals(SessionErrorCode.UNSUPPORTED_SURFACE_OPERATION, replacement.code)

        val committed =
            session.append(
                SessionEventKeys.TurnStart,
                TurnStartEvent(1),
            )
        assertEquals(0L, committed.seq)
        assertEquals(listOf(0L), published)
        assertEquals(listOf(0L), session.events().map { it.seq })

        runtime.context.dispose()
    }

    @Test
    fun coreMessageRoleAndSourceMustBeValidatedAtAppend() = runTest {
        val runtime = Runtime()
        installStore(runtime)
        val session = runtime.context.createSession()
        val validUser = UserMessageEvent(1, createUserMessage(listOf(TextBlock("user"))))
        val rawUser = json.encodeToJsonElement(UserMessageEvent.serializer(), validUser).jsonObject
        val rawUserMessage = rawUser.getValue("message").jsonObject
        val invalidUser =
            JsonObject(
                rawUser +
                    ("message" to JsonObject(rawUserMessage + ("role" to JsonPrimitive("assistant"))))
            )
        val userError =
            assertFailsWith<SessionException> {
                session.append(
                    SurfaceSessionEventKey(SessionEventNames.USER_MESSAGE, JsonElement.serializer()),
                    invalidUser,
                )
            }
        assertEquals(SessionErrorCode.INVALID_EVENT, userError.code)

        val validAssistant =
            AssistantMessageEvent(
                turn = 1,
                step = 1,
                message =
                    createAssistantMessage(
                        content = listOf(TextBlock("assistant")),
                        provider = "provider",
                        model = "model",
                    ),
            )
        val rawAssistant =
            json.encodeToJsonElement(AssistantMessageEvent.serializer(), validAssistant).jsonObject
        val rawAssistantMessage = rawAssistant.getValue("message").jsonObject
        val pluginSource =
            json.encodeToJsonElement(
                UserMessageEvent.serializer(),
                UserMessageEvent(
                    turn = 1,
                    message =
                        createUserMessage(
                            content = listOf(TextBlock("plugin")),
                            source = PluginMessageSource("fixture"),
                        ),
                ),
            ).jsonObject.getValue("message").jsonObject.getValue("source")
        val invalidAssistant =
            JsonObject(
                rawAssistant +
                    ("message" to JsonObject(rawAssistantMessage + ("source" to pluginSource)))
            )
        val assistantError =
            assertFailsWith<SessionException> {
                session.append(
                    SurfaceSessionEventKey(SessionEventNames.ASSISTANT_MESSAGE, JsonElement.serializer()),
                    invalidAssistant,
                )
            }
        assertEquals(SessionErrorCode.INVALID_EVENT, assistantError.code)
        assertTrue(session.events().isEmpty())

        runtime.context.dispose()
    }

    @Test
    fun messageAndRequestProjectionsShouldMatchFullReplay() = runTest {
        val runtime = Runtime()
        installStore(runtime)
        val session = runtime.context.createSession()
        val firstHeader = requestHeader(listOf("alpha", "beta"))
        val latestHeader = requestHeader(listOf("beta", "alpha"), system = "latest")
        val latestContext = SessionRequestContext("provider-b", "model-b", 16_384)

        session.append(
            SessionEventKeys.UserMessage,
            UserMessageEvent(1, createUserMessage(listOf(TextBlock("hello")))),
        )
        session.append(SessionEventKeys.RequestHeader, RequestHeaderEvent(1, 1, firstHeader))
        session.append(
            SessionEventKeys.RequestContext,
            RequestContextEvent(1, 1, SessionRequestContext("provider-a", "model-a")),
        )
        session.append(SessionEventKeys.RequestHeader, RequestHeaderEvent(1, 2, latestHeader))
        session.append(SessionEventKeys.RequestContext, RequestContextEvent(1, 2, latestContext))

        val events = session.events()
        assertEquals(deriveMessages(events), session.deriveMessages())
        assertEquals(foldRequestHeader(events), session.requestHeader())
        assertEquals(foldRequestContext(events), session.requestContext())
        assertEquals(canonicalHeader(latestHeader), session.requestHeader())
        assertEquals(latestContext, session.requestContext())
        assertFalse(headerEquals(firstHeader, latestHeader))
        assertFalse(
            headerEquals(
                requestHeader(listOf("alpha", "beta")),
                requestHeader(listOf("beta", "alpha")),
            )
        )
        assertNull(
            canonicalHeader(
                requestHeader(emptyList(), system = "").copy(tools = emptyList())
            ).tools
        )

        val projected = session.deriveMessages()
        (projected.single().content as? MutableList)?.clear()
        assertEquals("hello", (session.deriveMessages().single().content.single() as TextBlock).text)

        runtime.context.dispose()
    }

    @Test
    fun postCommitObserverFailuresShouldBeContained() = runTest {
        val runtime = Runtime()
        val failures = mutableListOf<String>()
        runtime.context.on(SessionEvents.Appended) {
            error("first observer")
        }
        runtime.context.on(SessionEvents.Appended) {
            failures += "tail:${it.event.seq}"
        }
        runtime.install(
            SessionPlugin(
                idFactory = FixedIds(),
                clock = FixedClock(),
                observerFailureHandler = SessionObserverFailureHandler { error ->
                    failures += error.message.orEmpty()
                },
            )
        )
        val session = runtime.context.createSession()

        val committed = session.append(SessionEventKeys.TurnStart, TurnStartEvent(1))

        assertEquals(0L, committed.seq)
        assertEquals(listOf("first observer", "tail:0"), failures)
        assertEquals(listOf(0L), session.events().map { it.seq })
        runtime.context.dispose()
    }

    @Test
    fun storeShouldPreserveOrderRejectDuplicatesAndDisposeInReverse() = runTest {
        val runtime = Runtime()
        val created = mutableListOf<SessionId>()
        val disposed = mutableListOf<SessionId>()
        runtime.context.on(SessionEvents.Created) { created += it.session.id }
        runtime.context.on(SessionEvents.Disposed) { error("ignored dispose observer") }
        runtime.context.on(SessionEvents.Disposed) { disposed += it.session.id }
        val fiber =
            runtime.install(
                SessionPlugin(
                    idFactory = FixedIds(),
                    clock = FixedClock(),
                )
            )
        val store = runtime.context.sessions
        val first = runtime.context.createSession(id = SessionId("first"))
        val second = runtime.context.createSession(id = SessionId("second"))
        val third = runtime.context.createSession(id = SessionId("third"))

        val duplicate =
            assertFailsWith<SessionException> {
                runtime.context.createSession(id = first.id)
            }
        assertEquals(SessionErrorCode.DUPLICATE_ID, duplicate.code)
        assertEquals(listOf(first, second, third), store.list())
        assertEquals(listOf(first.id, second.id, third.id), created)
        val repeatedAnnounce =
            assertFailsWith<SessionException> {
                store.announce(first)
            }
        assertEquals(SessionErrorCode.REENTRANT_LIFECYCLE, repeatedAnnounce.code)
        assertEquals(listOf(first.id, second.id, third.id), created)

        runtime.uninstall(fiber)

        assertEquals(listOf(third.id, second.id, first.id), disposed)
        assertFalse(first.isLive())
        val disposedStore = assertFailsWith<SessionException> { store.list() }
        assertEquals(SessionErrorCode.STORE_DISPOSED, disposedStore.code)
        runtime.context.dispose()
    }

    @Test
    fun flushShouldAwaitListenersAndAggregateFailures() = runTest {
        val runtime = Runtime()
        val store = installStore(runtime)
        val first = runtime.context.createSession(id = SessionId("first"))
        val second = runtime.context.createSession(id = SessionId("second"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val settled = mutableListOf<SessionId>()

        runtime.context.on(SessionEvents.Flush) { notice ->
            if (notice.session === first) {
                started.complete(Unit)
                release.await()
            }
            settled += notice.session.id
        }
        runtime.context.on(SessionEvents.Flush) { notice ->
            error("flush-${notice.session.id.value}")
        }

        val flushing = async { runCatching { store.flush() } }
        started.await()
        yield()
        assertFalse(flushing.isCompleted)
        release.complete(Unit)

        val error = flushing.await().exceptionOrNull() as IllegalStateException
        assertEquals("flush-first", error.message)
        assertEquals(listOf(first.id, second.id), settled)
        assertEquals(listOf("flush-second"), error.suppressedExceptions.map { it.message })
        runtime.context.dispose()
    }

    @Test
    fun loaderFixtureShouldInstallCreateReplayAndDetach() = runTest {
        val runtime = Runtime()
        val registry = Registry()
        val registration = SessionPluginDefinition.register(registry)
        val loader = Loader(runtime, registry)
        loader.reconcile(listOf(Entry(id = "session", name = SESSION_PLUGIN_NAME)))
        val store = runtime.context.sessions
        val owner = runtime.context.child()
        val session = owner.createSession()

        session.append(
            SessionEventKeys.UserMessage,
            UserMessageEvent(1, createUserMessage(listOf(TextBlock("fixture")))),
        )
        assertEquals("fixture", (session.deriveMessages().single().content.single() as TextBlock).text)
        assertSame(session, store.get(session.id))

        owner.dispose()
        assertTrue(store.list().isEmpty())
        loader.dispose()
        registration.dispose()
        runtime.context.dispose()
    }

    private fun requestHeader(
        toolNames: List<String>,
        system: String = "system",
    ): EpochHeader =
        EpochHeader(
            config =
                LlmCallConfig(
                    provider = "provider",
                    model = "model",
                    temperature = 0.25,
                    maxTokens = 512,
                    stop = listOf("stop"),
                ),
            adapterDefaults =
                LlmCallConfigAdapterDefaults(
                    reasoningEffort = true,
                    maxTokens = true,
                ),
            system = system,
            tools =
                toolNames.map { name ->
                    ToolSchema(
                        name = name,
                        description = "tool $name",
                        parameters =
                            JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("object"),
                                    "title" to JsonPrimitive(name),
                                )
                            ),
                    )
                },
            reason = EpochReason.INITIAL,
            startsSeries = true,
        )
}
