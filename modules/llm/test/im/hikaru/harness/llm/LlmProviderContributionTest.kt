package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.EffectScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmProviderContributionTest {
    private val descriptor = LlmProviderDescriptor("vendor", "Vendor", listOf("test-api"), supportsDiscovery = true)
    private fun access() = LlmProviderAccess(LlmProviderConnection("personal", "vendor", "test-api", "https://example.test"), "test-secret")
    private val catalog = LlmCatalogSnapshot(listOf(LlmDiscoveredModel("model")), "service", LlmCatalogCompleteness.COMPLETE)

    @Test
    fun discoveryShouldWorkBeforeAnyProviderRouteIsActiveAndFollowScopeLifetime() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val scope = EffectScope()
        scope.add(llm.registerProviderContribution(LlmProviderContribution(descriptor, LlmModelDiscovery { catalog })))
        assertTrue(llm.listProviders().isEmpty())
        assertEquals(catalog, llm.discoverModels("vendor", access()))
        assertFalse(access().toString().contains("test-secret"))
        scope.dispose()
        assertTrue(llm.listProviderDescriptors().isEmpty())
        assertEquals(LlmErrorCode.NO_PROVIDER_CONTRIBUTION, assertFailsWith<LlmException> {
            llm.discoverModels("vendor", access())
        }.code)
        runtime.context.dispose()
    }

    @Test
    fun duplicateAndInvalidReplacementShouldPreserveTheInstalledContribution() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val contribution = LlmProviderContribution(descriptor, LlmModelDiscovery { catalog })
        val handle = llm.registerProviderContribution(contribution)
        assertEquals(LlmErrorCode.DUPLICATE_PROVIDER_CONTRIBUTION, assertFailsWith<LlmException> {
            llm.registerProviderContribution(contribution)
        }.code)
        assertEquals(LlmErrorCode.INVALID_PROVIDER_CONNECTION, assertFailsWith<LlmException> {
            handle.replace(contribution.copy(descriptor = descriptor.copy(id = "another")))
        }.code)
        assertEquals(catalog, llm.discoverModels("vendor", access()))
        handle.dispose()
        llm.registerProviderContribution(contribution)
        handle.dispose()
        assertEquals(1, llm.listProviderDescriptors().size)
        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, assertFailsWith<LlmException> { handle.replace(contribution) }.code)
        runtime.context.dispose()
    }

    @Test
    fun discoveryShouldRejectMismatchedConnectionsBeforeSendingAndMalformedCatalogs() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        var calls = 0
        val handle = llm.registerProviderContribution(LlmProviderContribution(descriptor, LlmModelDiscovery { calls++; catalog }))
        val mismatched = LlmProviderAccess(access().connection.copy(api = "another-api"))
        assertEquals(LlmErrorCode.INVALID_PROVIDER_CONNECTION, assertFailsWith<LlmException> { llm.discoverModels("vendor", mismatched) }.code)
        assertEquals(0, calls)
        handle.replace(LlmProviderContribution(descriptor, LlmModelDiscovery { catalog.copy(models = catalog.models + catalog.models) }))
        assertEquals(LlmErrorCode.INVALID_MODEL_CATALOG, assertFailsWith<LlmException> { llm.discoverModels("vendor", access()) }.code)
        runtime.context.dispose()
    }

    @Test
    fun replacingContributionDuringDiscoveryShouldDiscardItsOldResult() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val handle = llm.registerProviderContribution(LlmProviderContribution(descriptor, LlmModelDiscovery {
            started.complete(Unit)
            finish.await()
            catalog
        }))
        val pending = async { assertFailsWith<LlmException> { llm.discoverModels("vendor", access()) } }
        started.await()
        handle.replace(LlmProviderContribution(descriptor, LlmModelDiscovery { catalog }))
        finish.complete(Unit)
        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, pending.await().code)
        runtime.context.dispose()
    }

    @Test
    fun instanceCreationMustRequireAnInstalledExecutableContribution() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        assertEquals(LlmErrorCode.NO_PROVIDER_CONTRIBUTION, assertFailsWith<LlmException> { llm.createProviderAdapter("vendor", access()) }.code)
        llm.registerProviderContribution(LlmProviderContribution(descriptor, LlmModelDiscovery { catalog }))
        assertEquals(LlmErrorCode.PROVIDER_ADAPTER_UNSUPPORTED, assertFailsWith<LlmException> { llm.createProviderAdapter("vendor", access()) }.code)
        runtime.context.dispose()
    }

    @Test
    fun uninstallShouldBlockNewCallsButKeepPreparedRulesAndResourcesUntilLeaseRelease() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val resource = TestResource()
        val high = ReasoningEffortId("high")
        val rules = object : LlmCapabilityRules {
            override val version = "rules-1"
            override fun resolve(connection: LlmProviderConnection, model: LlmResolvedModelInfo) = model.copy(
                reasoningCapabilities = ReasoningCapabilities(
                    effortSupport = CapabilitySupport.SUPPORTED,
                    efforts = listOf(LlmReasoningEffortInfo(high, "High")),
                ),
            )
        }
        val handle = llm.registerProviderContribution(LlmProviderContribution(descriptor, capabilityRules = rules, adapterFactory = LlmProviderAdapterFactory { resource }))
        val instance = llm.createProviderAdapter("vendor", access())
        llm.registerAdapter(listOf("personal"), instance.adapter)
        val config = LlmCallConfig("personal", "model", reasoning = ReasoningOptions(effort = high))
        val prepared = llm.prepareCall(config)
        assertEquals("rules-1", prepared.reasoningCapabilities?.ruleVersion)
        handle.dispose()
        assertEquals(0, resource.closed)
        assertNull(llm.providerContribution("vendor"))
        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, assertFailsWith<LlmException> { llm.prepareCall(config) }.code)
        assertEquals(listOf(FinishChunk(StopFinishReason)), prepared.stream(GenerateOptions("personal", "model", emptyList(), reasoning = config.reasoning)).toList())
        assertEquals(1, resource.closed)
        instance.dispose()
        assertEquals(1, resource.closed)
        runtime.context.dispose()
    }

    @Test
    fun resourceBuiltAfterContributionRemovalShouldBeClosedInsteadOfPublished() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val resource = TestResource()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val handle = llm.registerProviderContribution(LlmProviderContribution(descriptor, adapterFactory = LlmProviderAdapterFactory {
            started.complete(Unit)
            finish.await()
            resource
        }))
        val pending = async { assertFailsWith<LlmException> { llm.createProviderAdapter("vendor", access()) } }
        started.await()
        handle.dispose()
        finish.complete(Unit)
        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, pending.await().code)
        assertEquals(1, resource.closed)
        runtime.context.dispose()
    }

    @Test
    fun concurrentPreparedReuseMustNotCloseTheRetiredFirstCollectorsResource() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var closes = 0
        val resource = object : LlmProviderAdapter {
            override val adapter = object : LlmAdapter {
                override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
                    started.complete(Unit)
                    finish.await()
                    emit(FinishChunk(StopFinishReason))
                }
            }
            override suspend fun dispose() { closes++ }
        }
        val contribution = llm.registerProviderContribution(LlmProviderContribution(descriptor,
            adapterFactory = LlmProviderAdapterFactory { resource }))
        val instance = llm.createProviderAdapter("vendor", access())
        llm.registerAdapter(listOf("personal"), instance.adapter)
        val prepared = llm.prepareCall(LlmCallConfig("personal", "model"))
        val request = GenerateOptions("personal", "model", emptyList())
        val first = async { prepared.stream(request).toList() }
        started.await()
        contribution.dispose()
        assertEquals(0, closes)

        assertEquals(LlmErrorCode.INVALID_PREPARED_CALL, assertFailsWith<LlmException> {
            prepared.stream(request).toList()
        }.code)
        assertEquals(0, closes, "Rejecting another collector must not release the active request's lease")

        finish.complete(Unit)
        assertEquals(listOf(FinishChunk(StopFinishReason)), first.await())
        assertEquals(1, closes)
        runtime.context.dispose()
    }

    @Test
    fun rejectedFirstCollectorMustStillReleaseItsPreparedResource() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val llm = runtime.context.llm
        val resource = TestResource()
        val contribution = llm.registerProviderContribution(LlmProviderContribution(descriptor,
            adapterFactory = LlmProviderAdapterFactory { resource }))
        val instance = llm.createProviderAdapter("vendor", access())
        llm.registerAdapter(listOf("personal"), instance.adapter)
        val prepared = llm.prepareCall(LlmCallConfig("personal", "model"))
        contribution.dispose()
        assertEquals(0, resource.closed)
        assertEquals(LlmErrorCode.INVALID_PREPARED_CALL, assertFailsWith<LlmException> {
            prepared.stream(GenerateOptions("personal", "changed-model", emptyList())).toList()
        }.code)
        assertEquals(1, resource.closed)
        runtime.context.dispose()
    }

    private class TestResource : LlmProviderAdapter {
        var closed = 0
        override val adapter = object : LlmAdapter {
            override fun stream(options: GenerateOptions): Flow<StreamChunk> = flowOf(FinishChunk(StopFinishReason))
        }
        override suspend fun dispose() { closed++ }
    }
}
