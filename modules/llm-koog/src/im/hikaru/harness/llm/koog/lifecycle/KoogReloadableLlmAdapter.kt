package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmAdapter
import im.hikaru.harness.llm.LlmModelInfo
import im.hikaru.harness.llm.LlmProviderInfo
import im.hikaru.harness.llm.LlmResolvedModelInfo
import im.hikaru.harness.llm.PreparedAdapterCall
import im.hikaru.harness.llm.RetryPolicy
import im.hikaru.harness.llm.StreamChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Stable route facade whose calls lease the coordinator's current generation. */
class KoogReloadableLlmAdapter(
    private val coordinator: KoogLlmReloadCoordinator,
) : LlmAdapter {
    private var metadata: RegistrationMetadata? = null

    override fun providerInfo(provider: String): LlmProviderInfo =
        requireMetadata(provider).providerInfo(provider)

    override fun providerRetryPolicy(provider: String): RetryPolicy =
        requireMetadata(provider).providerRetryPolicy(provider)

    override suspend fun listModels(provider: String): List<LlmModelInfo> {
        val lease = coordinator.acquire(provider)
        return try {
            lease.adapter.listModels(provider)
        } finally {
            lease.release()
        }
    }

    override suspend fun resolveModel(
        provider: String,
        model: String,
    ): LlmResolvedModelInfo {
        val lease = coordinator.acquire(provider)
        return try {
            lease.adapter.resolveModel(provider, model)
        } finally {
            lease.release()
        }
    }

    override suspend fun prepareCall(
        provider: String,
        model: String,
    ): PreparedAdapterCall {
        val lease = coordinator.acquire(provider)
        return try {
            val prepared = lease.adapter.prepareCall(provider, model)
            object : PreparedAdapterCall {
                private val releaseMutex = Mutex()
                private var released = false

                override val model: LlmResolvedModelInfo = prepared.model

                override fun stream(options: GenerateOptions): Flow<StreamChunk> =
                    prepared.stream(options)

                override suspend fun dispose() = withContext(NonCancellable) {
                    val shouldRelease =
                        releaseMutex.withLock {
                            if (released) false else true.also { released = true }
                        }
                    if (shouldRelease) try { prepared.dispose() } finally { lease.release() }
                }
            }
        } catch (error: Throwable) {
            lease.release()
            throw error
        }
    }

    override fun stream(options: GenerateOptions): Flow<StreamChunk> =
        flow {
            val lease = coordinator.acquire(options.provider)
            try {
                emitAll(lease.adapter.stream(options))
            } finally {
                lease.release()
            }
        }

    internal fun replaceMetadata(
        adapters: Map<String, LlmAdapter>,
    ): RegistrationMetadata? {
        val previous = metadata
        metadata = adapters.takeIf { it.isNotEmpty() }?.let { RegistrationMetadata(it.toMap()) }
        return previous
    }

    internal fun restoreMetadata(previous: RegistrationMetadata?) {
        metadata = previous
    }

    private fun requireMetadata(provider: String): LlmAdapter =
        metadata?.adapters?.get(provider)
            ?: throw im.hikaru.harness.llm.LlmException(
                message = "Unknown reloadable Koog provider '$provider'",
                code = KoogLlmErrorCode.UNKNOWN_PROVIDER,
            )

    internal data class RegistrationMetadata(
        val adapters: Map<String, LlmAdapter>,
    )
}
