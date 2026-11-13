package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 适配器注册返回的生命周期句柄，并支持原子替换同一适配器的 routes。 */
interface AdapterRegistrationHandle : Disposable {
    suspend fun replace(providers: List<String>)
}

private class AdapterOwner(
    val adapter: LlmAdapter,
) {
    var released: Boolean = false
}

private class DirectoryOwner {
    var released: Boolean = false
}

private class ContributionOwner(val vendor: String) {
    var released: Boolean = false
    val instances = mutableListOf<ContributionLlmAdapter>()
}

private data class ContributionEntry(
    val owner: ContributionOwner,
    val contribution: LlmProviderContribution,
)

private data class AdapterRoute(
    val owner: AdapterOwner,
    val adapter: LlmAdapter,
    val provider: LlmProviderInfo,
    val retryPolicy: RetryPolicy,
)

private data class DirectoryEntry(
    val owner: DirectoryOwner,
    val provider: LlmConfigurableProvider,
)

private data class ResolvedCall(
    val config: LlmCallConfig,
    val adapterDefaults: LlmCallConfigAdapterDefaults,
    val context: LlmModelContext?,
    val reasoningCapabilities: ReasoningCapabilities?,
)

/**
 * provider-neutral LLM Service：适配器注册表、模型元数据查询和统一流式调用入口。
 */
class LlmRuntime internal constructor(
    private val context: Context,
) : Disposable {

    private val mutex =
        Mutex()

    private val routes =
        linkedMapOf<String, AdapterRoute>()

    private val directory =
        linkedMapOf<String, DirectoryEntry>()

    private val contributions = linkedMapOf<String, ContributionEntry>()

    private var disposed =
        false

    private val settingsApplications = mutableMapOf<String, LlmSettingsApplication>()

    suspend fun registerSettingsApplication(namespace: String, application: LlmSettingsApplication): Disposable {
        mutex.withLock {
            checkActive()
            check(namespace !in settingsApplications) { "Settings application already registered" }
            settingsApplications[namespace] = application
        }
        return Disposable {
            mutex.withLock {
                if (settingsApplications[namespace] === application) settingsApplications.remove(namespace)
            }
        }
    }

    suspend fun settingsApplication(namespace: String): LlmSettingsApplication? =
        mutex.withLock { settingsApplications[namespace] }

    /** Register installed vendor capabilities independently from active connection routes. */
    suspend fun registerProviderContribution(
        contribution: LlmProviderContribution,
    ): ProviderContributionRegistrationHandle {
        val candidate = contribution.detachedCopy()
        val owner = ContributionOwner(candidate.descriptor.id)
        mutex.withLock {
            checkActive()
            if (owner.vendor in contributions) {
                throw LlmException("Provider contribution '${owner.vendor}' is already registered", LlmErrorCode.DUPLICATE_PROVIDER_CONTRIBUTION)
            }
            contributions[owner.vendor] = ContributionEntry(owner, candidate)
        }
        emitAdaptersUpdated()
        return ContributionHandle(this, owner)
    }

    suspend fun listProviderDescriptors(): List<LlmProviderDescriptor> = mutex.withLock {
        checkActive()
        contributions.values.map { it.contribution.descriptor.detachedCopy() }
    }

    suspend fun providerContribution(vendorId: String): LlmProviderContribution? = mutex.withLock {
        checkActive()
        contributions[vendorId]?.contribution?.detachedCopy()
    }

    /** Discovery works for draft connections; it never requires or activates an adapter route. */
    suspend fun discoverModels(vendorId: String, access: LlmProviderAccess): LlmCatalogSnapshot {
        val entry = mutex.withLock {
            checkActive()
            contributions[vendorId] ?: throw LlmException(
                "No provider contribution registered for '$vendorId'", LlmErrorCode.NO_PROVIDER_CONTRIBUTION,
            )
        }
        val contribution = entry.contribution
        if (access.connection.vendor != vendorId || access.connection.api !in contribution.descriptor.protocols) {
            throw LlmException("Discovery connection does not match the registered vendor/protocol", LlmErrorCode.INVALID_PROVIDER_CONNECTION)
        }
        val discovery = contribution.discovery ?: throw LlmException(
            "Provider '$vendorId' does not supply model discovery", LlmErrorCode.MODEL_DISCOVERY_UNSUPPORTED,
        )
        val snapshot = discovery.discover(access).detachedCopy()
        if (snapshot.source.isBlank() || snapshot.models.map { it.id }.distinct().size != snapshot.models.size ||
            snapshot.models.any { it.id.isBlank() || it.name.isBlank() ||
                (it.contextWindow != null && it.contextWindow <= 0L) ||
                (it.maxOutputTokens != null && it.maxOutputTokens <= 0L) }) {
            throw LlmException("Provider returned an invalid model catalog", LlmErrorCode.INVALID_MODEL_CATALOG)
        }
        mutex.withLock {
            checkActive()
            if (contributions[vendorId] !== entry) {
                throw LlmException("Provider contribution changed during discovery", LlmErrorCode.REGISTRATION_DISPOSED)
            }
        }
        return snapshot
    }

    /** The sole vendor factory boundary: captures this contribution's rules and owns its retirement. */
    suspend fun createProviderAdapter(vendorId: String, access: LlmProviderAccess): LlmProviderAdapter {
        val entry = mutex.withLock {
            checkActive()
            contributions[vendorId] ?: throw LlmException(
                "No provider contribution registered for '$vendorId'", LlmErrorCode.NO_PROVIDER_CONTRIBUTION,
            )
        }
        if (access.connection.vendor != vendorId || access.connection.api !in entry.contribution.descriptor.protocols) {
            throw LlmException("Adapter connection does not match the registered vendor/protocol", LlmErrorCode.INVALID_PROVIDER_CONNECTION)
        }
        val factory = entry.contribution.adapterFactory ?: throw LlmException(
            "Provider '$vendorId' does not supply an executable adapter", LlmErrorCode.PROVIDER_ADAPTER_UNSUPPORTED,
        )
        val rules = entry.contribution.capabilityRules
        val ruleVersion = rules?.version
        if (ruleVersion != null && ruleVersion.isBlank()) {
            throw LlmException("Capability rules must have a version", LlmErrorCode.INVALID_MODEL_REASONING)
        }
        val instance = ContributionLlmAdapter(
            factory.create(access.detachedCopy()), access.connection, rules, ruleVersion,
            onRetired = { retired -> mutex.withLock { entry.owner.instances.remove(retired) } },
        )
        try {
            mutex.withLock {
                checkActive()
                if (contributions[vendorId] !== entry) {
                    throw LlmException("Provider contribution changed while creating its adapter", LlmErrorCode.REGISTRATION_DISPOSED)
                }
                entry.owner.instances += instance
            }
        } catch (error: Throwable) {
            withContext(NonCancellable) { instance.dispose() }
            throw error
        }
        return instance
    }

    /**
     * 为一组 provider routes 注册同一个适配器。
     *
     * 整组候选会先完成验证；任一 route 冲突时不会注册其中任何一项。
     */
    suspend fun registerAdapter(
        providers: List<String>,
        adapter: LlmAdapter,
    ): AdapterRegistrationHandle {
        if (providers.isEmpty()) {
            throw LlmException(
                message = "An adapter must register at least one provider",
                code = LlmErrorCode.INVALID_ADAPTER,
            )
        }

        val owner =
            AdapterOwner(adapter)

        mutex.withLock {
            checkActive()
            val prepared =
                prepareRoutes(
                    providers = providers,
                    owner = owner,
                )
            validateConflicts(prepared, owner)
            commitRoutes(owner, prepared)
        }
        emitAdaptersUpdated()

        return RegistrationHandle(
            runtime = this,
            owner = owner,
        )
    }

    /** 按注册顺序返回当前生效 provider route 的脱离副本。 */
    suspend fun listProviders(): List<LlmProviderInfo> =
        mutex.withLock {
            checkActive()
            routes.values.map { route -> route.provider.copy() }
        }

    /** Registers configurable providers independently from currently active adapter routes. */
    suspend fun registerConfigurableProviders(
        entries: List<LlmConfigurableProvider>,
    ): DirectoryRegistrationHandle {
        if (entries.isEmpty()) {
            throw LlmException(
                message = "A configurable-provider registration must declare at least one provider",
                code = LlmErrorCode.INVALID_DIRECTORY,
            )
        }

        val owner = DirectoryOwner()
        mutex.withLock {
            checkActive()
            val prepared = prepareDirectory(entries, owner)
            validateDirectoryConflicts(prepared, owner)
            commitDirectory(owner, prepared)
        }
        emitAdaptersUpdated()

        return DirectoryHandle(this, owner)
    }

    /** Lists every configurable provider, including routes that are currently dormant. */
    suspend fun listConfigurableProviders(): List<LlmConfigurableProvider> =
        mutex.withLock {
            checkActive()
            directory.values.map { entry -> entry.provider.detachedCopy() }
        }

    /** 查询一个 provider 的建议性模型目录；目录不是调用白名单。 */
    suspend fun listModels(provider: String): List<LlmModelInfo> {
        val route = currentRoute(provider)
        val models = route.adapter.listModels(provider)
        val seen = mutableSetOf<String>()

        return models.map { model ->
            if (
                model.provider != provider ||
                model.id.isBlank() ||
                model.name.isBlank() ||
                !seen.add(model.id)
            ) {
                throw LlmException(
                    message = "Adapter returned invalid or duplicate model metadata for provider '$provider'",
                    code = LlmErrorCode.INVALID_MODEL_INFO,
                )
            }
            model.copy(
                inputModalities = model.inputModalities?.toList(),
            )
        }
    }

    /** 查询一个精确 provider/model 的权威能力。 */
    suspend fun resolveModelInfo(
        provider: String,
        model: String,
    ): LlmResolvedModelInfo {
        require(model.isNotBlank()) {
            "Model must not be blank"
        }

        return resolveModelInfo(
            route = currentRoute(provider),
            model = model,
        )
    }

    /** 校验调用配置，并补入适配器为精确模型提供的默认值。 */
    suspend fun resolveCallConfig(config: LlmCallConfig): LlmCallConfig {
        val route = currentRoute(config.provider)
        return resolveCall(route, config).config
    }

    /**
     * 在同一次解析中捕获适配器 registration、模型能力和重试策略。
     *
     * 返回值只能调用一次；之后 route 被替换也不会把这次请求切到新适配器。
     */
    suspend fun prepareCall(config: LlmCallConfig): PreparedLlmCall {
        val route = currentRoute(config.provider)
        val adapterCall = route.adapter.prepareCall(config.provider, config.model)
        val resolved =
            try {
                resolveCall(
                    route = route,
                    config = config,
                    modelInfo = normalizeModelInfo(route, config.model, adapterCall.model),
                )
            } catch (error: Throwable) {
                adapterCall.dispose()
                throw error
            }

        return PreparedLlmCall(
            config = resolved.config,
            retryPolicy = route.retryPolicy,
            context = resolved.context,
            adapterDefaults = resolved.adapterDefaults,
            dispatch = { options ->
                dispatch(
                    options = options,
                    route = route,
                    adapterCall = adapterCall,
                    expectedConfig = resolved.config,
                )
            },
            release = adapterCall::dispose,
            reasoningCapabilities = resolved.reasoningCapabilities,
        )
    }

    /** 通过 llm/stream waterfall 调用当前 provider route。 */
    fun stream(options: GenerateOptions): Flow<StreamChunk> {
        val request = options.detachedCopy()
        return context.waterfall(
            key = LlmStreamEvent,
            event = request,
        ) { request ->
            flow {
                val route =
                    try {
                        currentRoute(request.provider)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        emit(errorFinish(error))
                        return@flow
                    }
                val adapterCall =
                    try {
                        route.adapter.prepareCall(request.provider, request.model)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        emit(errorFinish(error))
                        return@flow
                    }
                try {
                    val resolved = try {
                        resolveCall(
                            route,
                            request.callConfig(),
                            normalizeModelInfo(route, request.model, adapterCall.model),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        emit(errorFinish(error))
                        return@flow
                    }
                    emitAll(adapterStream(route, request.withConfig(resolved.config), adapterCall))
                } finally {
                    adapterCall.dispose()
                }
            }
        }
    }

    override suspend fun dispose() {
        val instances = mutex.withLock {
            if (disposed) {
                return
            }
            disposed = true
            val instances = contributions.values.flatMap { entry -> entry.owner.instances.toList() }
            contributions.values.forEach { entry -> entry.owner.instances.clear() }
            routes.clear()
            directory.clear()
            contributions.clear()
            settingsApplications.clear()
            instances
        }
        retireInstances(instances)
    }

    private fun dispatch(
        options: GenerateOptions,
        route: AdapterRoute,
        adapterCall: PreparedAdapterCall,
        expectedConfig: LlmCallConfig,
    ): Flow<StreamChunk> {
        val request = options.detachedCopy()
        return context.waterfall(
            key = LlmStreamEvent,
            event = request,
        ) { request ->
            if (request.callConfig() != expectedConfig) {
                throw LlmException("Prepared LLM call config changed in middleware", LlmErrorCode.INVALID_PREPARED_CALL)
            }
            adapterStream(route, request, adapterCall)
        }
    }

    private fun adapterStream(
        route: AdapterRoute,
        options: GenerateOptions,
        adapterCall: PreparedAdapterCall? = null,
    ): Flow<StreamChunk> =
        flow {
            val upstream =
                try {
                    adapterCall?.stream(options) ?: route.adapter.stream(options)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    emit(errorFinish(error))
                    return@flow
                }

            emitAll(
                upstream.catch { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    emit(errorFinish(error))
                }
            )
        }

    private fun errorFinish(error: Throwable): FinishChunk {
        val failure = normalizeLlmFailure(error)
        val reason =
            if (error is LlmException && error.code == LlmErrorCode.ABORTED) {
                AbortedFinishReason(failure)
            } else {
                ErrorFinishReason(failure)
            }
        return FinishChunk(reason)
    }

    private suspend fun currentRoute(provider: String): AdapterRoute =
        mutex.withLock {
            checkActive()
            routes[provider]
                ?: throw LlmException(
                    message = "No adapter registered for provider '$provider'",
                    code = LlmErrorCode.NO_ADAPTER,
                )
        }

    private suspend fun resolveModelInfo(
        route: AdapterRoute,
        model: String,
    ): LlmResolvedModelInfo {
        val provider = route.provider.id
        val resolved = route.adapter.resolveModel(provider, model)

        return normalizeModelInfo(route, model, resolved)
    }

    private fun normalizeModelInfo(
        route: AdapterRoute,
        model: String,
        resolved: LlmResolvedModelInfo,
    ): LlmResolvedModelInfo {
        val provider = route.provider.id

        if (
            resolved.provider != provider ||
            resolved.id != model ||
            resolved.name.isBlank()
        ) {
            throw LlmException(
                message = "Adapter returned invalid identity for provider '$provider' model '$model'",
                code = LlmErrorCode.INVALID_MODEL_INFO,
            )
        }

        if (resolved.defaultMaxTokens != null && resolved.defaultMaxTokens <= 0L) {
            throw LlmException(
                message = "Adapter returned invalid max tokens for provider '$provider' model '$model'",
                code = LlmErrorCode.INVALID_MODEL_MAX_TOKENS,
            )
        }

        if (resolved.maxOutputTokens != null && resolved.maxOutputTokens <= 0L) {
            throw LlmException("Adapter returned invalid model output capacity", LlmErrorCode.INVALID_MODEL_MAX_TOKENS)
        }

        resolved.reasoning?.let { reasoning ->
            val ids = reasoning.efforts.map { effort -> effort.id }
            if (
                reasoning.efforts.isEmpty() ||
                reasoning.efforts.any { effort -> effort.name.isBlank() } ||
                ids.distinct().size != ids.size ||
                reasoning.defaultEffort?.let(ids::contains) == false
            ) {
                throw LlmException(
                    message = "Adapter returned invalid reasoning metadata for provider '$provider' model '$model'",
                    code = LlmErrorCode.INVALID_MODEL_REASONING,
                )
            }
        }

        return resolved.copy(
            inputModalities = resolved.inputModalities?.toList(),
            reasoning = resolved.reasoning?.copy(
                efforts = resolved.reasoning.efforts.toList(),
            ),
            reasoningCapabilities = resolved.reasoningCapabilities?.detachedCopy(),
            metadata = resolved.metadata.detachedCopy(),
        )
    }

    private suspend fun resolveCall(
        route: AdapterRoute,
        config: LlmCallConfig,
    ): ResolvedCall =
        resolveCall(route, config, resolveModelInfo(route, config.model))

    private fun resolveCall(
        route: AdapterRoute,
        config: LlmCallConfig,
        modelInfo: LlmResolvedModelInfo,
    ): ResolvedCall {
        val info = modelInfo
        var resolved = config.copy(stop = config.stop?.toList())
        var defaultMaxTokens = false
        var defaultReasoning = false

        if (resolved.maxTokens == null && info.defaultMaxTokens != null) {
            resolved = resolved.copy(maxTokens = info.defaultMaxTokens)
            defaultMaxTokens = true
        }

        if (resolved.reasoning != null) {
            validateReasoningOptions(resolved.reasoning, modelCapabilities(info), resolved.maxTokens)
            return ResolvedCall(
                config = resolved,
                adapterDefaults = LlmCallConfigAdapterDefaults(maxTokens = defaultMaxTokens),
                context = info.context,
                reasoningCapabilities = modelCapabilities(info)?.detachedCopy(),
            )
        }

        val reasoning = info.reasoning
        val requested = resolved.reasoningEffort
        if (reasoning == null) {
            if (requested != null) {
                if (info.reasoningCapabilities != null) {
                    validateReasoningOptions(ReasoningOptions(effort = requested), info.reasoningCapabilities, resolved.maxTokens)
                } else {
                    unsupportedReasoning(config, requested)
                }
            }
        } else {
            val effective = requested ?: reasoning.defaultEffort
            if (effective != null) {
                if (reasoning.efforts.none { effort -> effort.id == effective }) {
                    unsupportedReasoning(config, effective)
                }
                if (requested == null) {
                    resolved = resolved.copy(reasoningEffort = effective)
                    defaultReasoning = true
                }
                info.reasoningCapabilities?.let { capabilities ->
                    validateReasoningOptions(ReasoningOptions(effort = effective), capabilities, resolved.maxTokens)
                }
            }
        }

        return ResolvedCall(
            config = resolved,
            adapterDefaults = LlmCallConfigAdapterDefaults(
                reasoningEffort = defaultReasoning,
                maxTokens = defaultMaxTokens,
            ),
            context = info.context,
            reasoningCapabilities = modelCapabilities(info)?.detachedCopy(),
        )
    }

    private fun unsupportedReasoning(
        config: LlmCallConfig,
        effort: ReasoningEffortId,
    ): Nothing {
        throw LlmException(
            message = "Provider '${config.provider}' model '${config.model}' does not support reasoning effort '${effort.value}'",
            code = LlmErrorCode.UNSUPPORTED_REASONING_EFFORT,
        )
    }

    private fun modelCapabilities(info: LlmResolvedModelInfo): ReasoningCapabilities? =
        info.reasoningCapabilities ?: info.reasoning?.let { legacy ->
            ReasoningCapabilities(
                effortSupport = CapabilitySupport.SUPPORTED,
                efforts = legacy.efforts.toList(),
            )
        }

    private fun prepareRoutes(
        providers: List<String>,
        owner: AdapterOwner,
    ): List<AdapterRoute> {
        val unique = mutableSetOf<String>()

        return providers.map { provider ->
            if (provider.isBlank()) {
                throw LlmException(
                    message = "Adapter provider names must not be blank",
                    code = LlmErrorCode.INVALID_ADAPTER,
                )
            }
            if (!unique.add(provider)) {
                throw LlmException(
                    message = "Adapter provider '$provider' is duplicated",
                    code = LlmErrorCode.DUPLICATE_ADAPTER,
                )
            }

            val info = owner.adapter.providerInfo(provider)
            if (info.id != provider || info.name.isBlank()) {
                throw LlmException(
                    message = "Adapter metadata for provider '$provider' must preserve its id and have a name",
                    code = LlmErrorCode.INVALID_ADAPTER,
                )
            }

            AdapterRoute(
                owner = owner,
                adapter = owner.adapter,
                provider = info.copy(),
                retryPolicy = owner.adapter.providerRetryPolicy(provider).detachedCopy(),
            )
        }
    }

    private fun validateConflicts(
        prepared: List<AdapterRoute>,
        owner: AdapterOwner,
    ) {
        prepared.forEach { route ->
            val current = routes[route.provider.id]
            if (current != null && current.owner !== owner) {
                throw LlmException(
                    message = "An adapter for provider '${route.provider.id}' is already registered",
                    code = LlmErrorCode.DUPLICATE_ADAPTER,
                )
            }
        }
    }

    private fun commitRoutes(
        owner: AdapterOwner,
        prepared: List<AdapterRoute>,
    ) {
        routes.entries.removeAll { entry -> entry.value.owner === owner }
        prepared.forEach { route ->
            routes[route.provider.id] = route
        }
    }

    private suspend fun replace(
        owner: AdapterOwner,
        providers: List<String>,
    ) {
        mutex.withLock {
            checkActive()
            if (owner.released) {
                throw LlmException(
                    message = "A disposed adapter registration cannot replace its routes",
                    code = LlmErrorCode.REGISTRATION_DISPOSED,
                )
            }
            val prepared = prepareRoutes(providers, owner)
            validateConflicts(prepared, owner)
            commitRoutes(owner, prepared)
        }
        emitAdaptersUpdated()
    }

    private suspend fun release(owner: AdapterOwner) {
        val releasedNow = mutex.withLock {
            if (owner.released) {
                return@withLock false
            }
            owner.released = true
            routes.entries.removeAll { entry -> entry.value.owner === owner }
            true
        }
        if (releasedNow) emitAdaptersUpdated()
    }

    private fun prepareDirectory(
        entries: List<LlmConfigurableProvider>,
        owner: DirectoryOwner,
    ): List<DirectoryEntry> {
        val unique = mutableSetOf<String>()
        return entries.map { entry ->
            if (
                entry.provider.isBlank() ||
                entry.displayName.isBlank() ||
                entry.settingsNamespace.isBlank() ||
                entry.settingsPath.any(String::isBlank)
            ) {
                throw LlmException(
                    message = "Configurable providers require a provider, display name, settings namespace, and valid path",
                    code = LlmErrorCode.INVALID_DIRECTORY,
                )
            }
            if (!unique.add(entry.provider)) {
                throw LlmException(
                    message = "Configurable provider '${entry.provider}' is duplicated",
                    code = LlmErrorCode.DUPLICATE_DIRECTORY,
                )
            }
            DirectoryEntry(owner, entry.detachedCopy())
        }
    }

    private fun validateDirectoryConflicts(
        prepared: List<DirectoryEntry>,
        owner: DirectoryOwner,
    ) {
        prepared.forEach { entry ->
            val current = directory[entry.provider.provider]
            if (current != null && current.owner !== owner) {
                throw LlmException(
                    message = "Configurable provider '${entry.provider.provider}' is already declared",
                    code = LlmErrorCode.DUPLICATE_DIRECTORY,
                )
            }
        }
    }

    private fun commitDirectory(
        owner: DirectoryOwner,
        prepared: List<DirectoryEntry>,
    ) {
        directory.entries.removeAll { entry -> entry.value.owner === owner }
        prepared.forEach { entry ->
            directory[entry.provider.provider] = entry
        }
    }

    private suspend fun replaceDirectory(
        owner: DirectoryOwner,
        entries: List<LlmConfigurableProvider>,
    ) {
        mutex.withLock {
            checkActive()
            if (owner.released) {
                throw LlmException(
                    message = "A disposed directory registration cannot replace its providers",
                    code = LlmErrorCode.REGISTRATION_DISPOSED,
                )
            }
            val prepared = prepareDirectory(entries, owner)
            validateDirectoryConflicts(prepared, owner)
            commitDirectory(owner, prepared)
        }
        emitAdaptersUpdated()
    }

    private suspend fun releaseDirectory(owner: DirectoryOwner) {
        val releasedNow = mutex.withLock {
            if (owner.released) return@withLock false
            owner.released = true
            directory.entries.removeAll { entry -> entry.value.owner === owner }
            true
        }
        if (releasedNow) emitAdaptersUpdated()
    }

    private fun emitAdaptersUpdated() {
        context.events.emitContained(LlmAdaptersUpdatedEvent, Unit)
    }

    private suspend fun replaceContribution(owner: ContributionOwner, contribution: LlmProviderContribution) {
        val candidate = contribution.detachedCopy()
        val instances = mutex.withLock {
            checkActive()
            if (owner.released) throw LlmException("Provider contribution registration is disposed", LlmErrorCode.REGISTRATION_DISPOSED)
            if (candidate.descriptor.id != owner.vendor) {
                throw LlmException("Provider contribution replacement must preserve vendor identity", LlmErrorCode.INVALID_PROVIDER_CONNECTION)
            }
            contributions[owner.vendor] = ContributionEntry(owner, candidate)
            owner.instances.toList().also { owner.instances.clear() }
        }
        emitAdaptersUpdated()
        retireInstances(instances)
    }

    private suspend fun releaseContribution(owner: ContributionOwner) {
        val instances = mutex.withLock {
            if (owner.released) return
            owner.released = true
            if (contributions[owner.vendor]?.owner === owner) contributions.remove(owner.vendor)
            owner.instances.toList().also { owner.instances.clear() }
        }
        emitAdaptersUpdated()
        retireInstances(instances)
    }

    private suspend fun retireInstances(instances: List<ContributionLlmAdapter>) = withContext(NonCancellable) {
        var failure: Throwable? = null
        for (instance in instances) {
            try { instance.dispose() } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private fun checkActive() {
        if (disposed) {
            throw LlmException(
                message = "LlmRuntime is disposed",
                code = LlmErrorCode.LLM_DISPOSED,
            )
        }
    }

    private class RegistrationHandle(
        private val runtime: LlmRuntime,
        private val owner: AdapterOwner,
    ) : AdapterRegistrationHandle {

        override suspend fun replace(providers: List<String>) {
            runtime.replace(owner, providers)
        }

        override suspend fun dispose() {
            runtime.release(owner)
        }
    }

    private class DirectoryHandle(
        private val runtime: LlmRuntime,
        private val owner: DirectoryOwner,
    ) : DirectoryRegistrationHandle {
        override suspend fun replace(entries: List<LlmConfigurableProvider>) {
            runtime.replaceDirectory(owner, entries)
        }

        override suspend fun dispose() {
            runtime.releaseDirectory(owner)
        }
    }

    private class ContributionHandle(
        private val runtime: LlmRuntime,
        private val owner: ContributionOwner,
    ) : ProviderContributionRegistrationHandle {
        override suspend fun replace(contribution: LlmProviderContribution) = runtime.replaceContribution(owner, contribution)
        override suspend fun dispose() = runtime.releaseContribution(owner)
    }
}

private fun RetryPolicy.detachedCopy(): RetryPolicy =
    when (this) {
        is NormalRetryPolicy ->
            copy(retryableCodes = retryableCodes.toList())

        is AlwaysRetryPolicy -> copy()
    }

private fun GenerateOptions.detachedCopy(): GenerateOptions =
    copy(
        messages = messages.map(::copyMessage),
        tools = tools?.map { tool ->
            tool.copy(
                parameters = kotlinx.serialization.json.JsonObject(tool.parameters.toMap())
            )
        },
        stop = stop?.toList(),
    )

private fun GenerateOptions.withConfig(config: LlmCallConfig): GenerateOptions = copy(
    reasoningEffort = config.reasoningEffort,
    reasoning = config.reasoning,
    temperature = config.temperature,
    maxTokens = config.maxTokens,
    stop = config.stop,
)

/** 一次性、绑定到精确适配器 registration 的已准备调用。 */
class PreparedLlmCall internal constructor(
    val config: LlmCallConfig,
    val retryPolicy: RetryPolicy,
    val context: LlmModelContext?,
    val adapterDefaults: LlmCallConfigAdapterDefaults,
    private val dispatch: (GenerateOptions) -> Flow<StreamChunk>,
    private val release: suspend () -> Unit = {},
    val reasoningCapabilities: ReasoningCapabilities? = null,
) : Disposable {
    private val mutex = Mutex()
    private var used = false
    private var released = false

    fun stream(options: GenerateOptions): Flow<StreamChunk> =
        flow {
            var ownsRelease = false
            try {
                mutex.withLock {
                    if (used || released) {
                        throw LlmException(
                            message = "Prepared LLM call was reused or its config changed",
                            code = LlmErrorCode.INVALID_PREPARED_CALL,
                        )
                    }
                    used = true
                    ownsRelease = true
                    if (options.callConfig() != config) {
                        throw LlmException(
                            message = "Prepared LLM call config changed",
                            code = LlmErrorCode.INVALID_PREPARED_CALL,
                        )
                    }
                }
                emitAll(dispatch(options))
            } finally {
                // A rejected second collector must not release the first collector's live lease.
                if (ownsRelease) dispose()
            }
        }

    override suspend fun dispose() = withContext(NonCancellable) {
        val shouldRelease =
            mutex.withLock {
                if (released) false else true.also { released = true }
            }
        if (shouldRelease) release()
    }
}
