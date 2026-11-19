package im.hikaru.harness.llm

import im.hikaru.harness.runtime.effect.Disposable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Describes a form field; credential values are never part of these descriptors. */
@Serializable
data class LlmConnectionField(
    val id: String,
    val label: String,
    val required: Boolean = false,
    val secret: Boolean = false,
)

@Serializable
data class LlmEndpointProfile(
    val id: String,
    val label: String,
    val baseUrl: String,
    val api: String,
    val region: String? = null,
    val serviceVariant: String? = null,
    val requiresWorkspace: Boolean = false,
)

/** Installed vendor semantics, independent from any configured connection instance. */
@Serializable
data class LlmProviderDescriptor(
    val id: String,
    val displayName: String,
    val protocols: List<String>,
    val defaultEndpoints: Map<String, String> = emptyMap(),
    val supportsDiscovery: Boolean = false,
    val endpointProfiles: List<LlmEndpointProfile> = emptyList(),
    val connectionFields: List<LlmConnectionField> = emptyList(),
    val discoveryStatus: String? = null,
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank()) { "Provider descriptor identity must not be blank" }
        require(protocols.isNotEmpty() && protocols.all(String::isNotBlank) && protocols.distinct().size == protocols.size) {
            "Provider descriptor must declare distinct protocols"
        }
        require(endpointProfiles.map { it.id }.distinct().size == endpointProfiles.size) { "Duplicate endpoint profiles" }
        require(endpointProfiles.all { it.id.isNotBlank() && it.label.isNotBlank() && it.baseUrl.isNotBlank() && it.api in protocols }) {
            "Invalid endpoint profile"
        }
        require(connectionFields.map { it.id }.distinct().size == connectionFields.size &&
            connectionFields.all { it.id.isNotBlank() && it.label.isNotBlank() }) { "Invalid connection fields" }
    }
}

/** A draft or active connection. Host resolves credentialRef into [LlmProviderAccess]. */
@Serializable
data class LlmProviderConnection(
    val provider: String,
    val vendor: String,
    val api: String,
    val baseUrl: String,
    val serviceVariant: String? = null,
    val region: String? = null,
    val workspace: String? = null,
    val credentialRef: String? = null,
    val revision: String? = null,
    val apiVersion: String? = null,
) {
    init {
        require(provider.isNotBlank() && vendor.isNotBlank() && api.isNotBlank() && baseUrl.isNotBlank()) {
            "Provider connection identity, protocol and endpoint must not be blank"
        }
    }
}

/** Ephemeral host-only access. Deliberately not serializable or a data class. */
class LlmProviderAccess(
    val connection: LlmProviderConnection,
    val apiKey: String? = null,
    val models: List<LlmResolvedModelInfo> = emptyList(),
    val displayName: String? = null,
    val requestTimeoutMillis: Long? = null,
    val connectTimeoutMillis: Long? = null,
    val socketTimeoutMillis: Long? = null,
) {
    init {
        require(displayName == null || displayName.isNotBlank()) { "Provider display name must not be blank" }
        require(listOf(requestTimeoutMillis, connectTimeoutMillis, socketTimeoutMillis).all { it == null || it > 0L }) {
            "Provider timeouts must be positive"
        }
    }
    override fun toString(): String = "LlmProviderAccess(provider=${connection.provider}, credentials=<redacted>)"
}

@Serializable
enum class LlmCatalogCompleteness {
    @SerialName("complete") COMPLETE,
    @SerialName("partial") PARTIAL,
    @SerialName("unknown") UNKNOWN,
}

/** Discovery facts, not a promise that the installed adapter can execute every operation. */
@Serializable
data class LlmDiscoveredModel(
    val id: String,
    val name: String = id,
    val contextWindow: Long? = null,
    val reasoningCapabilities: ReasoningCapabilities? = null,
    val maxOutputTokens: Long? = null,
    val description: String? = null,
    val inputModalities: List<ModelModality>? = null,
    val outputModalities: List<String>? = null,
    val supportedOperations: List<String>? = null,
    val metadata: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class LlmCatalogSnapshot(
    val models: List<LlmDiscoveredModel>,
    val source: String,
    val completeness: LlmCatalogCompleteness = LlmCatalogCompleteness.UNKNOWN,
    val warnings: List<String> = emptyList(),
)

fun interface LlmModelDiscovery {
    suspend fun discover(access: LlmProviderAccess): LlmCatalogSnapshot
}

/** Produces effective metadata using versioned rules AND the adapter's executable support. */
interface LlmCapabilityRules {
    val version: String
    fun resolve(connection: LlmProviderConnection, model: LlmResolvedModelInfo): LlmResolvedModelInfo
}

/** One connection instance's resources. Its owner must retire live generation leases before disposal. */
interface LlmProviderAdapter : Disposable {
    val adapter: LlmAdapter
}

fun interface LlmProviderAdapterFactory {
    suspend fun create(access: LlmProviderAccess): LlmProviderAdapter
}

/** Logical contribution to the existing Plugin/EffectScope system, not another loader. */
data class LlmProviderContribution(
    val descriptor: LlmProviderDescriptor,
    val discovery: LlmModelDiscovery? = null,
    val capabilityRules: LlmCapabilityRules? = null,
    val adapterFactory: LlmProviderAdapterFactory? = null,
)

interface ProviderContributionRegistrationHandle : Disposable {
    /** Atomic replacement of this vendor's contribution; the vendor ID must remain stable. */
    suspend fun replace(contribution: LlmProviderContribution)
}

internal fun LlmProviderDescriptor.detachedCopy(): LlmProviderDescriptor = copy(
    protocols = protocols.toList(),
    defaultEndpoints = defaultEndpoints.toMap(),
    endpointProfiles = endpointProfiles.toList(),
    connectionFields = connectionFields.toList(),
)

internal fun LlmProviderContribution.detachedCopy(): LlmProviderContribution = copy(descriptor = descriptor.detachedCopy())

internal fun LlmProviderAccess.detachedCopy(): LlmProviderAccess = LlmProviderAccess(
    connection = connection,
    apiKey = apiKey,
    models = models.map { model -> model.copy(
        inputModalities = model.inputModalities?.toList(),
        reasoning = model.reasoning?.copy(efforts = model.reasoning.efforts.toList()),
        reasoningCapabilities = model.reasoningCapabilities?.detachedCopy(),
        metadata = model.metadata.detachedCopy(),
    ) },
    displayName = displayName,
    requestTimeoutMillis = requestTimeoutMillis,
    connectTimeoutMillis = connectTimeoutMillis,
    socketTimeoutMillis = socketTimeoutMillis,
)

internal fun LlmCatalogSnapshot.detachedCopy(): LlmCatalogSnapshot = copy(
    models = models.map { model ->
        model.copy(
            reasoningCapabilities = model.reasoningCapabilities?.detachedCopy(),
            inputModalities = model.inputModalities?.toList(),
            outputModalities = model.outputModalities?.toList(),
            supportedOperations = model.supportedOperations?.toList(),
            metadata = model.metadata.detachedCopy(),
        )
    },
    warnings = warnings.toList(),
)

internal fun JsonObject.detachedCopy(): JsonObject = JsonObject(mapValues { (_, value) -> value.detachedElement() })

private fun JsonElement.detachedElement(): JsonElement = when (this) {
    is JsonObject -> detachedCopy()
    is JsonArray -> JsonArray(map { it.detachedElement() })
    else -> this
}
