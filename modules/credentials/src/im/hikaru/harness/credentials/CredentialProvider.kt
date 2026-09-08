package im.hikaru.harness.credentials

import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin

fun interface CredentialUpdateListener {
    fun onUpdated(reference: CredentialRef)
}

interface CredentialProvider : Disposable {
    suspend fun resolve(reference: CredentialRef): ResolvedCredential?

    suspend fun describe(reference: CredentialRef): CredentialInfo

    suspend fun set(
        reference: CredentialRef,
        value: String,
    )

    suspend fun unset(reference: CredentialRef)

    fun watch(listener: CredentialUpdateListener): Disposable =
        Disposable {}
}

suspend fun CredentialProvider.resolveRequired(
    reference: CredentialRef,
): String {
    val value = resolve(reference)?.value
    require(!value.isNullOrEmpty()) {
        "Credential '${reference.name}' is unavailable"
    }
    return value
}

class InMemoryCredentialProvider(
    initial: Map<CredentialRef, String> = emptyMap(),
) : CredentialProvider {
    private val values =
        initial.toMutableMap()

    private val listeners =
        mutableListOf<CredentialUpdateListener>()

    override suspend fun resolve(reference: CredentialRef): ResolvedCredential? =
        values[reference]
            ?.takeUnless(String::isEmpty)
            ?.let { value -> ResolvedCredential(value, "memory") }

    override suspend fun describe(reference: CredentialRef): CredentialInfo =
        if (values[reference].orEmpty().isNotEmpty()) {
            CredentialInfo(configured = true, source = "memory", writable = true)
        } else {
            CredentialInfo(configured = false, writable = true)
        }

    override suspend fun set(
        reference: CredentialRef,
        value: String,
    ) {
        require(value.isNotEmpty()) {
            "An empty credential cannot be stored; use unset"
        }
        values[reference] = value
        listeners.toList().forEach { it.onUpdated(reference) }
    }

    override suspend fun unset(reference: CredentialRef) {
        if (values.remove(reference) != null) {
            listeners.toList().forEach { it.onUpdated(reference) }
        }
    }

    override fun watch(listener: CredentialUpdateListener): Disposable {
        listeners += listener
        return Disposable {
            listeners.remove(listener)
        }
    }

    override suspend fun dispose() {
        listeners.clear()
        values.clear()
    }
}

class CredentialsPlugin(
    private val provider: CredentialProvider = InMemoryCredentialProvider(),
) : SimplePlugin {
    override suspend fun apply(
        context: im.hikaru.harness.runtime.Context,
        scope: EffectScope,
    ) {
        scope.add(context.provide(CredentialsKey, provider))
        scope.add(provider)
    }
}
