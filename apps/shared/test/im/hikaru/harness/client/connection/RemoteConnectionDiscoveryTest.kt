package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.*
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.account.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.*

class RemoteConnectionDiscoveryTest {
    private val host = HostDescription(ProtocolVersion.Current, HostId("discovered-host"), "Discovered Host")
    private fun session(tenant: Long = 1L) = AppSession(MemberIdentity(BackendTenant("https://example.com", tenant), 7L), "fixture-token-$tenant", null, null)
    private fun payload(hosts: List<HostDescription>, code: Int = 0) = Json.encodeToString(
        ApiResult.serializer(ListSerializer(HostDescription.serializer())), ApiResult(code = code, data = hosts))
    private fun client(engine: MockEngine) = HttpClient(engine) { install(ContentNegotiation) { json() }; install(WebSockets) }
    private fun owner(client: HttpClient) = RemoteConnectionOwner(client, { client.close() },
        CoroutineScope(SupervisorJob() + Dispatchers.Default), ReconnectPolicy(initialDelayMillis = 1, maxDelayMillis = 1, maxAttempts = 1))

    @Test fun refreshUsesAuthenticatedScopeCoalescesDuplicatesAndCanRetrySelectionAfterExhaustion() = runBlocking {
        var allowHost = false
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = MockEngine { request ->
            assertEquals("Bearer fixture-token-1", request.headers[HttpHeaders.Authorization])
            assertEquals("1", request.headers["tenant-id"])
            if (allowHost && !entered.isCompleted) { entered.complete(Unit); release.await() }
            respond(payload(if (allowHost) listOf(host) else emptyList()), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val owner = owner(client(engine))
        try {
            owner.start(session(), "test-client")
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }
            assertTrue(owner.hosts.value.isEmpty())
            allowHost = true
            val refresh = launch { owner.refresh(session()) }
            withTimeout(5_000) { entered.await() }
            val count = engine.requestHistory.size
            assertTrue(owner.discovery.value.refreshing)
            owner.refresh(session())
            owner.refresh(session(2))
            assertEquals(count, engine.requestHistory.size)
            release.complete(Unit)
            refresh.join()
            assertEquals(listOf(host), owner.hosts.value)
            assertEquals(RemoteConnectionStatus.DISCONNECTED, owner.status.value, "discovery must not mark a candidate online")
            assertFalse(owner.discovery.value.refreshing)
            owner.subscribe(StreamId("old-host-stream"))
            owner.select(host, session())
            assertTrue(owner.subscribedStreams().isEmpty())
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }
            assertTrue(engine.requestHistory.size > count, "select must restart an exhausted supervisor")
            assertNull(owner.connection.value, "failed handshake must not publish a connection")
        } finally { release.complete(Unit); owner.close() }
    }

    @Test fun logoutDiscardsDelayedDiscoveryAndNewScopePublishesOnlyItsHosts() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val newHost = host.copy(hostId = HostId("new-scope"))
        val engine = MockEngine { request ->
            val old = request.headers["tenant-id"] == "1"
            if (old) withContext(NonCancellable) { entered.complete(Unit); release.await() }
            respond(payload(listOf(if (old) host else newHost)), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val owner = owner(client(engine))
        try {
            owner.start(session(), "test-client")
            withTimeout(5_000) { entered.await() }
            val stop = launch { owner.stop() }
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.IDLE } }
            assertFalse(owner.discovery.value.refreshing)
            assertTrue(owner.hosts.value.isEmpty())
            assertNull(owner.identity.value)
            release.complete(Unit)
            stop.join()
            assertTrue(owner.hosts.value.isEmpty(), "late response cannot survive logout")
            owner.start(session(2), "test-client")
            withTimeout(5_000) { owner.hosts.first { it == listOf(newHost) } }
            assertEquals(session(2).identity, owner.identity.value)
            owner.select(host, session())
            assertEquals(listOf(newHost), owner.hosts.value)
            assertNull(owner.connection.value)
        } finally { release.complete(Unit); owner.close() }
    }

    @Test fun rejectedDiscoveryNeverPublishesItsPayloadAndErrorCanBeRetried() = runBlocking {
        var rejected = true
        val engine = MockEngine {
            respond(payload(listOf(host), if (rejected) 403 else 0), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val owner = owner(client(engine))
        try {
            owner.start(session(), "test-client")
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }
            assertTrue(owner.hosts.value.isEmpty())
            assertNotNull(owner.discovery.value.error)
            rejected = false
            owner.refresh(session())
            assertEquals(listOf(host), owner.hosts.value)
            assertNull(owner.discovery.value.error)
            owner.stop()
            val count = engine.requestHistory.size
            owner.refresh()
            assertEquals(count, engine.requestHistory.size)
        } finally { owner.close() }
    }

    @Test fun disappearingSelectedHostDoesNotFallBackToAnotherHost() = runBlocking {
        var discovered = emptyList<HostDescription>()
        val other = host.copy(hostId = HostId("other-host"))
        val incompatible = host.copy(hostId = HostId("incompatible-host"), protocolVersion = ProtocolVersion(999, 0))
        val engine = MockEngine { request ->
            assertEquals("/app-api/harness/hosts", request.url.encodedPath, "must not open a socket to another Host")
            respond(payload(discovered), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val owner = owner(client(engine))
        try {
            owner.start(session(), "test-client")
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }
            discovered = listOf(host, other, incompatible)
            owner.refresh(session())
            val count = engine.requestHistory.size
            owner.select(incompatible, session())
            assertEquals(count, engine.requestHistory.size)
            assertEquals(RemoteConnectionStatus.DISCONNECTED, owner.status.value)
            discovered = listOf(other)
            owner.select(host, session())
            withTimeout(5_000) { owner.status.first { it == RemoteConnectionStatus.DISCONNECTED } }
            assertEquals(listOf(other), owner.hosts.value)
            assertNull(owner.connection.value)
            assertNotNull(owner.discovery.value.error)
            assertTrue(engine.requestHistory.all { it.url.encodedPath == "/app-api/harness/hosts" })
        } finally { owner.close() }
    }
}
