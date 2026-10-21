package im.hikaru.harness.bundle.desktop

import im.hikaru.harness.credentials.*
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.KoogLlmSettings
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.settings.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopProviderDiscoveryTest {
    @Test
    fun modelSwitchesAndDefaultSurviveRefreshAndNewModelsStartEnabled() = runTest {
        val host = host()
        try {
            host.credentials.set(CredentialRef("KEY"), "fixture-key")
            host.service.save(configuration(), host.revision)
            host.service.discoverModels("connection", host.revision)
            host.service.selectModel("connection", "remote", host.revision)
            assertEquals("remote", host.provider().selectedModelId)
            host.service.setModelEnabled("connection", "remote", false, host.revision)
            assertNull(host.provider().selectedModelId)
            host.discover = { facts.copy(models = facts.models + LlmDiscoveredModel("new-model")) }
            host.service.discoverModels("connection", host.revision)
            assertEquals(setOf("remote"), host.provider().disabledModelIds)
            assertFalse("new-model" in host.provider().disabledModelIds)
            assertFailsWith<IllegalArgumentException> { host.service.selectModel("connection", "remote", host.revision) }
            host.service.selectModel("connection", "new-model", host.revision)
            val roundTrip = KoogLlmSettings.fromJson(KoogLlmSettings(mapOf("connection" to host.provider())).toJson()).providers.getValue("connection")
            assertEquals("new-model", roundTrip.selectedModelId)
            assertEquals(setOf("remote"), roundTrip.disabledModelIds)
            assertNotNull(roundTrip.catalogUpdatedAt)
            host.discover = { facts }
            host.service.discoverModels("connection", host.revision)
            assertEquals("new-model", host.provider().selectedModelId)
            assertEquals(listOf("new-model"), host.provider().retainedModels.map { it.id })
            assertTrue(host.service.read().providers.single().models.first { it.id == "new-model" }.absentFromCatalog)
        } finally { host.close() }
    }

    @Test
    fun defaultSelectionIsUniqueAcrossConnections() = runTest {
        val host = host()
        try {
            host.credentials.set(CredentialRef("KEY"), "fixture-key")
            host.service.save(configuration(), host.revision)
            host.service.discoverModels("connection", host.revision)
            host.service.save(configuration().copy(id = "second"), host.revision)
            host.service.discoverModels("second", host.revision)
            host.service.selectModel("connection", "remote", host.revision)
            host.service.selectModel("second", "remote", host.revision)
            assertEquals(listOf("second"), host.service.read().providers.filter { it.selectedModelId != null }.map { it.id })
        } finally { host.close() }
    }

    @Test
    fun refreshPersistsFactsSeparatelyAndPartialCannotReplaceTheCatalogOrSelection() = runTest {
        val host = host()
        try {
            host.credentials.set(CredentialRef("KEY"), "fixture-key")
            host.service.save(configuration(modelIds = listOf("chosen"), defaultMaxTokens = 512), host.revision)
            host.service.discoverModels("connection", host.revision)
            val saved = host.provider()
            assertEquals(listOf("chosen"), saved.models!!.map { it.id })
            assertEquals(512L, saved.defaultMaxTokens)
            assertEquals(facts, saved.discoveredCatalog)
            assertEquals("standard", saved.discoveredCatalogConnection!!.serviceVariant)
            assertEquals("region-a", saved.discoveredCatalogConnection!!.region)
            val before = host.scope.describe()
            host.discover = { facts.copy(completeness = LlmCatalogCompleteness.PARTIAL, warnings = listOf("Page limit")) }
            assertFailsWith<IllegalArgumentException> { host.service.discoverModels("connection", host.revision) }
            assertEquals(before, host.scope.describe())

            // A complete empty catalog is a successful refresh, while the user's selection remains theirs.
            host.discover = { facts.copy(models = emptyList()) }
            host.service.discoverModels("connection", host.revision)
            assertTrue(host.provider().discoveredCatalog!!.models.isEmpty())
            assertEquals(listOf("chosen"), host.provider().models!!.map { it.id })
        } finally { host.close() }
    }

    @Test
    fun editingPreservesOverridesAndRejectsConflictingModelSelectionBeforePersistence() = runTest {
        val host = host()
        try {
            host.service.save(configuration(), host.revision)
            host.scope.update(buildJsonObject { put("providers", buildJsonObject {
                put("connection", buildJsonObject { put("modelOverrides", buildJsonObject {
                    put("chosen", buildJsonObject { put("name", "My chosen model"); put("maxTokens", 256) })
                }) })
            }) })
            host.service.save(configuration().copy(displayName = "Edited"), host.revision)
            assertEquals(256L, host.provider().modelOverrides.getValue("chosen").maxTokens)
            val before = host.scope.describe()
            assertFailsWith<IllegalArgumentException> {
                host.service.save(configuration(modelIds = listOf("chosen")), host.revision)
            }
            assertEquals(before, host.scope.describe())
            assertEquals("My chosen model", host.provider().modelOverrides.getValue("chosen").name)
        } finally { host.close() }
    }

    @Test
    fun customEndpointPreservesNullScopeAndOnlyAnExactOfficialEndpointMaySupplyDefaults() = runTest {
        val host = host()
        try {
            val custom = configuration().copy(baseUrl = "https://custom.example/v1")
            host.service.save(custom, host.revision)
            assertEquals(custom, host.service.read().providers.single().configuration)
            assertNull(host.provider().region)
            assertNull(host.provider().serviceVariant)
            val scoped = custom.copy(region = "custom-region", serviceVariant = "custom-service")
            host.service.save(scoped, host.revision)
            assertEquals(scoped, host.service.read().providers.single().configuration)

            host.service.save(custom.copy(baseUrl = "https://fixture.example/v1"), host.revision)
            assertEquals("region-a", host.provider().region)
            assertEquals("standard", host.provider().serviceVariant)
        } finally { host.close() }
    }

    @Test
    fun warnedBundledCatalogIsUsableButUnverifiedRemoteCatalogCannotOverwriteIt() = runTest {
        val host = host()
        try {
            host.credentials.set(CredentialRef("KEY"), "fixture-key")
            host.service.save(configuration(), host.revision)
            val bundled = facts.copy(source = "bundled:fixture:2026-10-21",
                completeness = LlmCatalogCompleteness.UNKNOWN, warnings = listOf("Account availability is unverified"))
            host.discover = { bundled }
            host.service.discoverModels("connection", host.revision)
            assertEquals(bundled, host.provider().discoveredCatalog)
            assertEquals(bundled.warnings, host.service.read().providers.single().catalogWarnings)
            val before = host.scope.describe()
            host.discover = { bundled.copy(source = "remote:fixture") }
            assertFailsWith<IllegalArgumentException> { host.service.discoverModels("connection", host.revision) }
            assertEquals(before, host.scope.describe())
        } finally { host.close() }
    }

    @Test
    fun settingsChangesAndCredentialAbaChangesRejectAnInflightRefresh() = runTest {
        val host = host()
        try {
            val reference = CredentialRef("KEY")
            host.credentials.set(reference, "key-a")
            host.service.save(configuration(), host.revision)
            host.discover = {
                host.credentials.set(reference, "key-b")
                host.credentials.set(reference, "key-a")
                facts
            }
            assertFailsWith<DesktopProviderRevisionConflict> { host.service.discoverModels("connection", host.revision) }
            assertNull(host.provider().discoveredCatalog)
            host.discover = {
                host.service.save(configuration().copy(displayName = "Concurrent edit"), host.revision)
                facts
            }
            assertFailsWith<DesktopProviderRevisionConflict> { host.service.discoverModels("connection", host.revision) }
            assertNull(host.provider().discoveredCatalog)
            assertEquals("Concurrent edit", host.provider().displayName)
        } finally { host.close() }
    }

    @Test
    fun draftSaveRetainsMetadataAndCapacityWithoutCreatingAnOutputRequestDefault() = runTest {
        val host = host()
        try {
            val draft = configuration(modelIds = listOf("remote"))
            val discovered = host.service.discoverDraft(draft, "fixture-key")
            assertTrue(discovered.complete)
            assertEquals(64000L, discovered.models.single().maxTokens)
            assertTrue(host.runtime.context.llm.listProviders().isEmpty())
            host.service.saveWithCredential(draft, "fixture-key", host.revision)
            val saved = host.provider()
            assertEquals(facts, saved.discoveredCatalog)
            assertEquals(facts.models.single().metadata, saved.discoveredCatalog!!.models.single().metadata)
            assertEquals(64000L, saved.discoveredCatalog!!.models.single().maxOutputTokens)
            assertNull(saved.defaultMaxTokens)
            assertNull(saved.models!!.single().maxTokens)
            assertEquals("KEY", saved.discoveredCatalogConnection!!.credentialRef)
            assertFalse(host.scope.describe().value.toString().contains("fixture-key"))
            assertEquals("cached:remote:fixture", host.service.read().providers.single().catalogSource)

            host.service.save(draft.copy(baseUrl = "https://other.example/v1"), host.revision)
            assertNull(host.provider().discoveredCatalog)
        } finally { host.close() }
    }

    @Test
    fun draftFactsCannotBeReusedWithADifferentKeyOrConnection() = runTest {
        for (changeEndpoint in listOf(false, true)) {
            val host = host()
            try {
                val draft = configuration()
                host.service.discoverDraft(draft, "discovery-key")
                host.service.saveWithCredential(
                    if (changeEndpoint) draft.copy(baseUrl = "https://other.example/v1") else draft,
                    if (changeEndpoint) "discovery-key" else "different-key", host.revision,
                )
                assertNull(host.provider().discoveredCatalog)
            } finally { host.close() }
        }
    }

    @Test
    fun newerDraftRequestInvalidatesOlderResultEvenWhenItFinishesLast() = runTest {
        val host = host()
        try {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            host.discover = { access ->
                if (access.apiKey == "old-key") {
                    started.complete(Unit)
                    finish.await()
                    facts.copy(source = "remote:old")
                } else facts
            }
            val older = async { runCatching { host.service.discoverDraft(configuration(), "old-key") } }
            started.await()
            host.service.discoverDraft(configuration(), "new-key")
            finish.complete(Unit)
            assertIs<DesktopProviderRevisionConflict>(older.await().exceptionOrNull())
            host.service.saveWithCredential(configuration(), "new-key", host.revision)
            assertEquals("remote:fixture", host.provider().discoveredCatalog!!.source)
        } finally { host.close() }
    }

    @Test
    fun directoryOnlyPluginCanSaveCatalogWithoutActivatingInference() = runTest {
        val host = host(inference = false)
        try {
            host.service.discoverDraft(configuration(), "fixture-key")
            host.service.saveWithCredential(configuration(), "fixture-key", host.revision)
            assertNotNull(host.credentials.resolve(CredentialRef("KEY")))
            assertEquals(facts, host.provider().discoveredCatalog)
            assertFalse(host.service.read().providers.single().active)
            assertTrue(host.runtime.context.llm.listProviders().isEmpty())
        } finally { host.close() }
    }

    @Test
    fun failedCredentialSaveOnlyRemovesAnUnreferencedValueWrittenByThisAttempt() = runTest {
        for (scenario in listOf("partial-write", "revision-conflict", "referenced", "replaced")) {
            val memory = InMemoryCredentialProvider()
            var afterWrite: suspend (CredentialRef) -> Unit = {}
            val credentialProvider = object : CredentialProvider by memory {
                override suspend fun set(reference: CredentialRef, value: String) {
                    memory.set(reference, value)
                    afterWrite(reference)
                }
            }
            val host = host(credentials = credentialProvider)
            try {
                afterWrite = { reference ->
                    when (scenario) {
                        "partial-write" -> error("Simulated write failure after persistence")
                        "revision-conflict" -> host.service.save(configuration().copy(id = "other", credentialName = "OTHER"), host.revision)
                        "referenced" -> host.service.save(configuration().copy(id = "other"), host.revision)
                        "replaced" -> { memory.set(reference, "concurrent-value"); error("Simulated concurrent replacement") }
                    }
                }
                assertFailsWith<IllegalStateException> {
                    host.service.saveWithCredential(configuration(), "new-value", host.revision)
                }
                val remaining = memory.resolve(CredentialRef("KEY"))?.value
                when (scenario) {
                    "referenced" -> assertEquals("new-value", remaining)
                    "replaced" -> assertEquals("concurrent-value", remaining)
                    else -> assertNull(remaining)
                }
                assertNull(KoogLlmSettings.fromJson(host.scope.get()).providers["connection"])
            } finally { host.close() }
        }
    }

    @Test
    fun cancellingAfterSettingsPersistenceKeepsTheCommittedCredentialAndRevisionConsistent() = runTest {
        val memory = InMemorySettingsDocumentStore()
        val persisted = CompletableDeferred<Unit>()
        val finishCommit = CompletableDeferred<Unit>()
        val store = object : SettingsDocumentStore by memory {
            override suspend fun write(document: JsonObject) {
                memory.write(document)
                persisted.complete(Unit)
                finishCommit.await()
            }
        }
        val host = host(store = store)
        try {
            val saving = launch { host.service.saveWithCredential(configuration(), "fixture-key", host.revision) }
            persisted.await()
            saving.cancel(CancellationException("Dialog closed after persistence"))
            finishCommit.complete(Unit)
            saving.join()
            assertEquals("KEY", host.provider().credential!!.name)
            assertEquals("fixture-key", host.credentials.resolve(CredentialRef("KEY"))!!.value)
            assertEquals(host.scope.get(), memory.read()["llm-koog"])
            assertEquals(1L, host.revision)
        } finally { host.close() }
    }

    @Test
    fun connectionUsesTheRequestedProtocolsProfileAndRequiresItsWorkspace() = runTest {
        val host = host()
        try {
            var connection: LlmProviderConnection? = null
            val registration = host.runtime.context.llm.registerProviderContribution(LlmProviderContribution(
                descriptor = LlmProviderDescriptor("two-apis", "Two APIs", listOf("chat", "native"),
                    endpointProfiles = listOf(
                        LlmEndpointProfile("chat", "Chat", "https://chat.example/v1", "chat", "region", "standard"),
                        LlmEndpointProfile("native", "Native", "https://{workspace}.native.example/v2", "native", "region", "standard", requiresWorkspace = true),
                    )),
                discovery = LlmModelDiscovery { connection = it.connection; facts },
            ))
            try {
                val draft = configuration().copy(vendor = "two-apis", api = "native", region = "region")
                assertFailsWith<IllegalArgumentException> { host.service.discoverDraft(draft, "fixture-key") }
                host.service.discoverDraft(draft.copy(workspace = "team"), "fixture-key")
                assertEquals("https://team.native.example/v2", connection!!.baseUrl)
                assertEquals("native", connection!!.api)
                assertEquals("standard", connection!!.serviceVariant)
                host.service.discoverDraft(draft.copy(workspace = "team", baseUrl = "https://team.native.example/v2"), "fixture-key")
                assertEquals("standard", connection!!.serviceVariant)
                host.service.discoverDraft(draft.copy(baseUrl = "https://proxy.example/v2"), "fixture-key")
                assertEquals("https://proxy.example/v2", connection!!.baseUrl)
                assertNull(connection!!.serviceVariant)
                host.service.discoverDraft(draft.copy(api = "chat"), "fixture-key")
                assertEquals("https://chat.example/v1", connection!!.baseUrl)
            } finally { registration.dispose() }
        } finally { host.close() }
    }

    @Test
    fun hostKeyRotationInvalidatesEveryReferencingCatalogBeforePublishingTheKeyAndKeepsUserChoices() = runTest {
        val memory = InMemoryCredentialProvider()
        var onKeyWrite: () -> Unit = {}
        val credentials = object : CredentialProvider by memory {
            override suspend fun set(reference: CredentialRef, value: String) {
                if (value == "new-key") onKeyWrite()
                memory.set(reference, value)
            }
        }
        val host = host(credentials = credentials)
        try {
            memory.set(CredentialRef("KEY"), "old-key")
            memory.set(CredentialRef("OTHER"), "other-key")
            host.service.save(configuration(modelIds = listOf("chosen")), host.revision)
            host.service.save(configuration().copy(id = "same-key"), host.revision)
            host.service.save(configuration().copy(id = "other-key", credentialName = "OTHER"), host.revision)
            host.scope.update(buildJsonObject { put("providers", buildJsonObject {
                put("same-key", buildJsonObject { put("modelOverrides", buildJsonObject {
                    put("chosen", buildJsonObject { put("name", "Preserved override") })
                }) })
            }) })
            for (id in listOf("connection", "same-key", "other-key")) host.service.discoverModels(id, host.revision)
            onKeyWrite = {
                val providers = KoogLlmSettings.fromJson(host.scope.get()).providers
                assertNull(providers.getValue("connection").discoveredCatalog)
                assertNull(providers.getValue("same-key").discoveredCatalog)
            }
            host.service.setCredential("KEY", "new-key")
            val providers = KoogLlmSettings.fromJson(host.scope.get()).providers
            assertNull(providers.getValue("connection").discoveredCatalogConnection)
            assertNull(providers.getValue("same-key").discoveredCatalogConnection)
            assertEquals(facts, providers.getValue("other-key").discoveredCatalog)
            assertEquals(listOf("chosen"), providers.getValue("connection").models!!.map { it.id })
            assertEquals("Preserved override", providers.getValue("same-key").modelOverrides.getValue("chosen").name)
            assertEquals("new-key", memory.resolve(CredentialRef("KEY"))!!.value)
        } finally { host.close() }
    }

    @Test
    fun hostKeyRotationShadowsAnInheritedCatalogWithExplicitNulls() = runTest {
        val binding = LlmProviderConnection("connection", "fixture", "fixture-api", "https://fixture.example/v1",
            region = "region-a", serviceVariant = "standard", credentialRef = "KEY")
        val base = buildJsonObject { put("providers", buildJsonObject { put("connection", buildJsonObject {
            put("vendor", "fixture"); put("api", "fixture-api"); put("baseUrl", binding.baseUrl)
            put("region", "region-a"); put("serviceVariant", "standard")
            put("credential", buildJsonObject { put("name", "KEY") })
            put("models", buildJsonArray { add(buildJsonObject { put("id", "chosen") }) })
            put("discoveredCatalog", Json.encodeToJsonElement(facts))
            put("discoveredCatalogConnection", Json.encodeToJsonElement(binding))
        }) }) }
        val host = host(base = base)
        try {
            host.credentials.set(CredentialRef("KEY"), "old-key")
            assertEquals(facts, host.provider().discoveredCatalog)
            host.service.setCredential("KEY", "new-key")
            assertNull(host.provider().discoveredCatalog)
            assertNull(host.provider().discoveredCatalogConnection)
            assertEquals(JsonNull, host.scope.describe().user!!["providers"]!!.jsonObject["connection"]!!.jsonObject["discoveredCatalog"])
            assertEquals(listOf("chosen"), host.provider().models!!.map { it.id })
        } finally { host.close() }
    }

    @Test
    fun keyRotationDoesNotWaitForTheNetworkAndRejectsTheOlderDiscoveryCommit() = runTest {
        for (hadCatalog in listOf(false, true)) {
            val host = host()
            try {
                host.credentials.set(CredentialRef("KEY"), "old-key")
                host.service.save(configuration(), host.revision)
                if (hadCatalog) host.service.discoverModels("connection", host.revision)
                val started = CompletableDeferred<Unit>()
                val finish = CompletableDeferred<Unit>()
                host.discover = { started.complete(Unit); finish.await(); facts }
                val refresh = async { runCatching { host.service.discoverModels("connection", host.revision) } }
                started.await()
                withTimeout(1000) { host.service.setCredential("KEY", "new-key") }
                finish.complete(Unit)
                assertIs<DesktopProviderRevisionConflict>(refresh.await().exceptionOrNull())
                assertNull(host.provider().discoveredCatalog)
                assertNull(host.provider().discoveredCatalogConnection)
            } finally { host.close() }
        }
    }

    private class Host(val runtime: Runtime, val credentials: CredentialProvider, val scope: SettingsScope) {
        val service = DesktopProviderSettings(runtime.context)
        var discover: suspend (LlmProviderAccess) -> LlmCatalogSnapshot = { facts }
        val revision get() = scope.describe().revision
        fun provider() = KoogLlmSettings.fromJson(scope.get()).providers.getValue("connection")
        suspend fun close() = runtime.context.dispose()
    }

    private suspend fun host(inference: Boolean = true, credentials: CredentialProvider = InMemoryCredentialProvider(),
        store: SettingsDocumentStore = InMemorySettingsDocumentStore(), base: JsonObject = buildJsonObject {}): Host {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(store))
        runtime.install(CredentialsPlugin(credentials))
        runtime.install(LlmPlugin())
        val scope = runtime.context.require(SettingsKey).register(settingsNamespace("llm-koog"),
            defaults = buildJsonObject { put("providers", buildJsonObject {}) },
            base = base,
            validator = SettingsValidator { KoogLlmSettings.fromJson(it) })
        runtime.context.llm.registerSettingsApplication("llm-koog", object : LlmSettingsApplication {
            override val applicationState = LlmSettingsApplicationState()
            override suspend fun retry() = true
        })
        val host = Host(runtime, credentials, scope)
        runtime.context.llm.registerProviderContribution(LlmProviderContribution(
            descriptor = LlmProviderDescriptor("fixture", "Fixture", listOf("fixture-api"), supportsDiscovery = true,
                endpointProfiles = listOf(LlmEndpointProfile("default", "Default", "https://fixture.example/v1",
                    "fixture-api", region = "region-a", serviceVariant = "standard"))),
            discovery = LlmModelDiscovery { host.discover(it) },
            adapterFactory = if (inference) LlmProviderAdapterFactory { error("Discovery must not create an adapter") } else null,
        ))
        return host
    }

    private fun configuration(modelIds: List<String>? = null, defaultMaxTokens: Long? = null) = DesktopProviderConfiguration(
        id = "connection", vendor = "fixture", api = "fixture-api", credentialName = "KEY",
        modelIds = modelIds, defaultMaxTokens = defaultMaxTokens,
    )

    private companion object {
        val facts = LlmCatalogSnapshot(listOf(LlmDiscoveredModel("remote", "Remote Model", contextWindow = 200000,
            maxOutputTokens = 64000, inputModalities = listOf(ModelModality.TEXT, ModelModality.IMAGE),
            metadata = buildJsonObject { put("vendor", "fixture"); put("raw", buildJsonObject {
                put("effort", buildJsonObject { put("supported_levels", buildJsonArray { add("high") }) }) })
            })), source = "remote:fixture", completeness = LlmCatalogCompleteness.COMPLETE)
    }
}
