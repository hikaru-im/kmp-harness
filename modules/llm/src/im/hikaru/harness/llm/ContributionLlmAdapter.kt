package im.hikaru.harness.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Registry-owned instance: retirement blocks new work but never closes a prepared call's resources. */
internal class ContributionLlmAdapter(
    private val resource: LlmProviderAdapter,
    private val connection: LlmProviderConnection,
    private val rules: LlmCapabilityRules?,
    private val ruleVersion: String?,
    private val onRetired: suspend (ContributionLlmAdapter) -> Unit = {},
) : LlmProviderAdapter {
    private val mutex = Mutex()
    private var leases = 0
    private var retired = false
    private var closed = false

    override val adapter: LlmAdapter = object : LlmAdapter {
        override fun providerInfo(provider: String): LlmProviderInfo {
            checkProvider(provider)
            return resource.adapter.providerInfo(provider)
        }

        override fun providerRetryPolicy(provider: String): RetryPolicy {
            checkProvider(provider)
            return resource.adapter.providerRetryPolicy(provider)
        }

        override suspend fun listModels(provider: String): List<LlmModelInfo> {
            checkProvider(provider)
            acquire()
            return try { resource.adapter.listModels(provider) } finally { release() }
        }

        override suspend fun resolveModel(provider: String, model: String): LlmResolvedModelInfo {
            checkProvider(provider)
            acquire()
            return try { resolve(resource.adapter.resolveModel(provider, model)) } finally { release() }
        }

        override suspend fun prepareCall(provider: String, model: String): PreparedAdapterCall {
            checkProvider(provider)
            acquire()
            var prepared: PreparedAdapterCall? = null
            try {
                val call = resource.adapter.prepareCall(provider, model)
                prepared = call
                val resolved = resolve(call.model)
                return object : PreparedAdapterCall {
                    private val callMutex = Mutex()
                    private var released = false
                    override val model = resolved

                    override fun stream(options: GenerateOptions): Flow<StreamChunk> = call.stream(options)

                    override suspend fun dispose() = withContext(NonCancellable) {
                        val releaseNow = callMutex.withLock {
                            if (released) false else true.also { released = true }
                        }
                        if (releaseNow) {
                            try { call.dispose() } finally { release() }
                        }
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    try { prepared?.dispose() } finally { release() }
                }
                throw error
            }
        }

        override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
            val call = prepareCall(options.provider, options.model)
            try { emitAll(call.stream(options)) } finally { call.dispose() }
        }
    }

    private fun checkProvider(provider: String) {
        if (provider != connection.provider) throw LlmException(
            "Provider instance does not match its registered connection", LlmErrorCode.INVALID_PROVIDER_CONNECTION,
        )
    }

    private fun resolve(model: LlmResolvedModelInfo): LlmResolvedModelInfo {
        val resolved = rules?.resolve(connection, model) ?: model
        if (resolved.provider != connection.provider || resolved.id != model.id || resolved.name.isBlank()) {
            throw LlmException("Capability rules changed the model identity", LlmErrorCode.INVALID_MODEL_INFO)
        }
        return resolved.copy(
            inputModalities = resolved.inputModalities?.toList(),
            reasoning = resolved.reasoning?.copy(efforts = resolved.reasoning.efforts.toList()),
            reasoningCapabilities = resolved.reasoningCapabilities?.detachedCopy()?.let { capabilities ->
                if (ruleVersion == null) capabilities else capabilities.copy(ruleVersion = ruleVersion)
            },
            metadata = resolved.metadata.detachedCopy(),
        )
    }

    private suspend fun acquire() = mutex.withLock {
        if (retired) throw LlmException("Provider contribution instance is retired", LlmErrorCode.REGISTRATION_DISPOSED)
        leases++
    }

    private suspend fun release() = withContext(NonCancellable) {
        val closeNow = mutex.withLock {
            check(leases > 0) { "Provider instance lease underflow" }
            leases--
            markClosedIfReady()
        }
        if (closeNow) resource.dispose()
    }

    override suspend fun dispose() = withContext(NonCancellable) {
        val (retiredNow, closeNow) = mutex.withLock {
            val retiredNow = !retired
            retired = true
            retiredNow to markClosedIfReady()
        }
        try {
            if (retiredNow) onRetired(this@ContributionLlmAdapter)
        } finally {
            if (closeNow) resource.dispose()
        }
    }

    private fun markClosedIfReady(): Boolean =
        if (retired && leases == 0 && !closed) true.also { closed = true } else false
}
