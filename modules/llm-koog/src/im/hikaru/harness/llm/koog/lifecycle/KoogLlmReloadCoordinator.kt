package im.hikaru.harness.llm.koog

import im.hikaru.harness.credentials.CredentialProvider
import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialUpdateListener
import im.hikaru.harness.llm.AdapterRegistrationHandle
import im.hikaru.harness.llm.DirectoryRegistrationHandle
import im.hikaru.harness.llm.LlmConfigurableProvider
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmRuntime
import im.hikaru.harness.llm.LlmSettingsApplication
import im.hikaru.harness.llm.LlmSettingsApplicationState
import im.hikaru.harness.llm.LlmSettingsFailure
import kotlinx.coroutines.flow.MutableStateFlow
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.settings.SettingsScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns live settings, Provider Directory, active routes, and immutable client generations.
 *
 * A settings snapshot is visible only after its executor and adapter have been created. Empty
 * settings keep the plugin and watches alive while removing every active route.
 */
class KoogLlmReloadCoordinator(
    installedRoutes: List<KoogProviderRoute>,
    private val settings: SettingsScope,
    private val credentials: CredentialProvider,
    private val llm: LlmRuntime,
    private val directoryRegistration: DirectoryRegistrationHandle,
    baseDirectory: List<LlmConfigurableProvider>,
    parentScope: CoroutineScope,
    private val connectionFactory: suspend (KoogProviderSettings, KoogProviderRoute) -> im.hikaru.harness.llm.LlmProviderAdapter,
    private val routeResolver: (suspend (KoogLlmSettings) -> List<KoogProviderRoute>)? = null,
) : Disposable, LlmSettingsApplication {
    private val installedRoutes = installedRoutes.map(KoogProviderRoute::detachedCopy)
    private val baseDirectory = baseDirectory.map(LlmConfigurableProvider::detachedCopy)
    private val scope =
        CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
    private val mutex = Mutex()
    private val reloadMutex = Mutex()
    private val adapter = KoogReloadableLlmAdapter(this)
    private var generationsByProvider = emptyMap<String, KoogLlmGeneration>()
    private var adapterRegistration: AdapterRegistrationHandle? = null
    private var directorySnapshot = this.baseDirectory
    private var started = false
    private var disposed = false
    private var settingsWatch: Disposable? = null
    private var credentialsWatch: Disposable? = null
    private val application = MutableStateFlow(LlmSettingsApplicationState())
    override val applicationState: LlmSettingsApplicationState get() = application.value
    override suspend fun retry(): Boolean = reload()

    init {
        require(this.installedRoutes.isNotEmpty()) {
            "Koog plugin must install at least one protocol template"
        }
        require(
            this.installedRoutes.map(KoogProviderRoute::id).distinct().size ==
                this.installedRoutes.size
        ) {
            "Installed Koog protocol template ids must not contain duplicates"
        }
        require(this.baseDirectory.isNotEmpty()) {
            "Dynamic Koog plugin must declare at least one configurable provider"
        }
        require(
            this.baseDirectory.map(LlmConfigurableProvider::provider).distinct().size ==
                this.baseDirectory.size
        ) {
            "Koog configurable provider ids must not contain duplicates"
        }
    }

    suspend fun start(): KoogReloadableLlmAdapter =
        reloadMutex.withLock {
            mutex.withLock {
                check(!disposed) { "Koog LLM reload coordinator is disposed" }
                check(!started) { "Koog LLM reload coordinator is already started" }
            }

            // Invalid saved credentials must not remove the watcher needed to repair them.
            applyCurrentSettings()
            mutex.withLock { started = true }
            settingsWatch = settings.watch { reload() }
            credentialsWatch =
                credentials.watch(
                    CredentialUpdateListener { reference ->
                        if (isReferenced(reference)) {
                            scope.launch { reload() }
                        }
                    }
                )
            adapter
        }

    internal suspend fun acquire(
        provider: String,
    ): KoogLlmGeneration.KoogGenerationLease =
        mutex.withLock {
            if (disposed) {
                throw LlmException(
                    message = "Koog LLM reload coordinator is disposed",
                    code = LlmErrorCode.LLM_DISPOSED,
                )
            }
            generationsByProvider[provider]?.acquire()
                ?: throw LlmException(
                    message = "Unknown reloadable Koog provider '$provider'",
                    code = KoogLlmErrorCode.UNKNOWN_PROVIDER,
                )
        }

    suspend fun reload(): Boolean =
        reloadMutex.withLock {
            if (mutex.withLock { disposed || !started }) return@withLock false
            applyCurrentSettings()
        }

    private suspend fun applyCurrentSettings(): Boolean {
        application.value = LlmSettingsApplicationState(applying = true)
        return try {
            applySnapshot(KoogLlmSettings.fromJson(settings.get()))
            application.value = LlmSettingsApplicationState()
            true
        } catch (cancelled: CancellationException) {
            application.value = LlmSettingsApplicationState(failure = LlmSettingsFailure.Load)
            throw cancelled
        } catch (error: Throwable) {
            application.value = LlmSettingsApplicationState(failure = when {
                error is LlmException && error.code == LlmErrorCode.INVALID_CREDENTIAL -> LlmSettingsFailure.Credential
                error is IllegalArgumentException || error is LlmException && error.code == KoogLlmErrorCode.UNSUPPORTED_OPTION -> LlmSettingsFailure.Configuration
                else -> LlmSettingsFailure.Load
            })
            false
        }
    }

    private suspend fun applySnapshot(nextSettings: KoogLlmSettings) {
        val resolvedRoutes = routeResolver?.invoke(nextSettings) ?: nextSettings.resolveRoutes(installedRoutes)
        val nextDirectory = directoryFor(nextSettings)
        // A Koog provider identifies a protocol, not a Harness connection. Never put two
        // independently configured connections in the SDK's provider-based routing pool.
        val nextGenerations = linkedMapOf<String, KoogLlmGeneration>()
        try {
            resolvedRoutes.forEach { route ->
                nextGenerations[route.id] = createGeneration(
                    KoogLlmSettings(mapOf(route.id to nextSettings.providers.getValue(route.id))),
                    listOf(route),
                )
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                retireGenerations(nextGenerations.values, error)
            }
            throw error
        }

        val previousGenerations: Map<String, KoogLlmGeneration>
        val previousMetadata: KoogReloadableLlmAdapter.RegistrationMetadata?
        val previousDirectory: List<LlmConfigurableProvider>
        val registration: AdapterRegistrationHandle?
        val previousProviderIds: List<String>
        withContext(NonCancellable) { mutex.withLock {
            if (disposed) {
                retireGenerations(nextGenerations.values)
                throw LlmException(
                    message = "Koog LLM reload coordinator is disposed",
                    code = LlmErrorCode.LLM_DISPOSED,
                )
            }
            previousGenerations = generationsByProvider
            previousDirectory = directorySnapshot
            registration = adapterRegistration
            previousProviderIds = previousGenerations.keys.toList()

            // Keep existing routes on their previous generation until the registration commit.
            generationsByProvider = nextGenerations + previousGenerations
            previousMetadata = adapter.replaceMetadata(
                generationsByProvider.mapValues { (_, generation) -> generation.adapter }
            )
        } }

        var directoryReplaced = false
        var routesReplaced = false
        var createdRegistration: AdapterRegistrationHandle? = null
        try {
            directoryRegistration.replace(nextDirectory)
            directoryReplaced = true

            val providerIds = resolvedRoutes.map(KoogProviderRoute::id)
            when {
                    registration != null -> {
                        registration.replace(providerIds)
                        routesReplaced = true
                    }

                    providerIds.isNotEmpty() -> {
                        createdRegistration = llm.registerAdapter(providerIds, adapter)
                        routesReplaced = true
                    }
                }

            mutex.withLock {
                if (createdRegistration != null) {
                    adapterRegistration = createdRegistration
                }
                generationsByProvider = nextGenerations
                adapter.replaceMetadata(nextGenerations.mapValues { (_, generation) -> generation.adapter })
                directorySnapshot = nextDirectory
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                rollbackSnapshot(
                    previousGenerations = previousGenerations,
                    previousMetadata = previousMetadata,
                    previousDirectory = previousDirectory,
                    previousProviderIds = previousProviderIds,
                    registration = registration,
                    createdRegistration = createdRegistration,
                    routesReplaced = routesReplaced,
                    directoryReplaced = directoryReplaced,
                    nextGenerations = nextGenerations.values,
                    primary = cancelled,
                )
            }
            throw cancelled
        } catch (error: Throwable) {
            rollbackSnapshot(
                previousGenerations = previousGenerations,
                previousMetadata = previousMetadata,
                previousDirectory = previousDirectory,
                previousProviderIds = previousProviderIds,
                registration = registration,
                createdRegistration = createdRegistration,
                routesReplaced = routesReplaced,
                directoryReplaced = directoryReplaced,
                nextGenerations = nextGenerations.values,
                primary = error,
            )
            throw error
        }

        withContext(NonCancellable) {
            retireGenerations(previousGenerations.values)
        }
    }

    private suspend fun rollbackSnapshot(
        previousGenerations: Map<String, KoogLlmGeneration>,
        previousMetadata: KoogReloadableLlmAdapter.RegistrationMetadata?,
        previousDirectory: List<LlmConfigurableProvider>,
        previousProviderIds: List<String>,
        registration: AdapterRegistrationHandle?,
        createdRegistration: AdapterRegistrationHandle?,
        routesReplaced: Boolean,
        directoryReplaced: Boolean,
        nextGenerations: Collection<KoogLlmGeneration>,
        primary: Throwable,
    ) {
        mutex.withLock {
            generationsByProvider = previousGenerations
            adapter.restoreMetadata(previousMetadata)
        }
        if (routesReplaced) {
            try {
                if (createdRegistration != null) {
                    createdRegistration.dispose()
                } else {
                    registration?.replace(previousProviderIds)
                }
            } catch (rollbackError: Throwable) {
                if (rollbackError !== primary) primary.addSuppressed(rollbackError)
            }
        }
        if (directoryReplaced) {
            try {
                directoryRegistration.replace(previousDirectory)
            } catch (rollbackError: Throwable) {
                if (rollbackError !== primary) primary.addSuppressed(rollbackError)
            }
        }
        retireGenerations(nextGenerations, primary)
    }

    private suspend fun retireGenerations(
        generations: Collection<KoogLlmGeneration>,
        primary: Throwable? = null,
    ) {
        var failure = primary
        generations.distinct().forEach { generation ->
            try {
                generation.retire()
            } catch (error: Throwable) {
                if (failure == null) failure = error
                else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        if (primary == null) failure?.let { throw it }
    }

    private suspend fun createGeneration(
        nextSettings: KoogLlmSettings,
        resolvedRoutes: List<KoogProviderRoute>,
    ): KoogLlmGeneration {
        val route = resolvedRoutes.single()
        val resource = connectionFactory(nextSettings.providers.getValue(route.id), route)
        return KoogLlmGeneration(resource.adapter, resource, resolvedRoutes)
    }

    private fun directoryFor(nextSettings: KoogLlmSettings): List<LlmConfigurableProvider> {
        val declared = nextSettings.providers
        val baseIds = baseDirectory.mapTo(mutableSetOf(), LlmConfigurableProvider::provider)
        return buildList {
            baseDirectory.forEach { entry ->
                val configured = declared[entry.provider]
                add(
                    entry.copy(
                        displayName = configured?.displayName ?: entry.displayName,
                        settingsPath = entry.settingsPath.toList(),
                        declared = configured != null,
                    )
                )
            }
            declared.values
                .filterNot { provider -> provider.provider in baseIds }
                .forEach { provider ->
                    add(
                        LlmConfigurableProvider(
                            provider = provider.provider,
                            displayName = provider.displayName,
                            settingsNamespace = settings.namespace.value,
                            settingsPath = listOf("providers", provider.provider),
                            declared = true,
                        )
                    )
                }
        }
    }

    private fun isReferenced(reference: CredentialRef): Boolean {
        val currentSettings =
            runCatching { KoogLlmSettings.fromJson(settings.get()) }.getOrNull()
                ?: return true
        return currentSettings.providers.values.any { it.credential?.name == reference.name }
    }

    override suspend fun dispose() = withContext(NonCancellable) {
        settingsWatch?.dispose()
        credentialsWatch?.dispose()
        settingsWatch = null
        credentialsWatch = null
        scope.coroutineContext[Job]?.cancel()

        reloadMutex.withLock {
            val registration: AdapterRegistrationHandle?
            val generations: List<KoogLlmGeneration>
            mutex.withLock {
                if (disposed) return@withContext
                disposed = true
                started = false
                registration = adapterRegistration
                adapterRegistration = null
                generations = generationsByProvider.values.distinct()
                generationsByProvider = emptyMap()
                adapter.replaceMetadata(emptyMap())
            }
            registration?.dispose()
            retireGenerations(generations)
            directoryRegistration.dispose()
        }
    }
}

private fun LlmConfigurableProvider.detachedCopy(): LlmConfigurableProvider =
    copy(settingsPath = settingsPath.toList())
