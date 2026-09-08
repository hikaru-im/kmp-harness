package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.ReasoningEffortId
import kotlinx.serialization.Serializable

/** Configured reasoning capability for one model. */
@Serializable
sealed interface KoogReasoningEfforts {
    /** Explicitly remove reasoning inherited from the installed catalog. */
    @Serializable
    data object Disabled : KoogReasoningEfforts

    /** Reasoning efforts accepted by the route semantics. */
    @Serializable
    data class Supported(
        val efforts: List<ReasoningEffortId>,
        val defaultEffort: ReasoningEffortId? = null,
    ) : KoogReasoningEfforts {
        init {
            require(efforts.isNotEmpty()) {
                "Koog model reasoning efforts must not be empty"
            }
            require(efforts.distinct().size == efforts.size) {
                "Koog model reasoning efforts must not contain duplicates"
            }
            require(defaultEffort == null || defaultEffort in efforts) {
                "Koog model default reasoning effort must be declared by the model"
            }
        }
    }
}

/** A configured model entry. A non-empty provider `models` list replaces its installed catalog. */
@Serializable
data class KoogModelProfile(
    val id: String,
    /** Protocol template for this model; omission inherits the provider profile. */
    val api: String? = null,
    val name: String? = null,
    val description: String? = null,
    val contextWindow: Long? = null,
    /** Explicit values are both the model capability and the per-request default. */
    val maxTokens: Long? = null,
    val input: List<ModelModality>? = null,
    val reasoningEfforts: KoogReasoningEfforts? = null,
) {
    init {
        require(id.isNotBlank()) { "Koog model profile id must not be blank" }
        require(api == null || api.isNotBlank()) { "Koog model profile api must not be blank" }
        require(name == null || name.isNotBlank()) { "Koog model profile name must not be blank" }
        require(contextWindow == null || contextWindow > 0L) {
            "Koog model profile context window must be positive"
        }
        require(maxTokens == null || maxTokens > 0L) {
            "Koog model profile max tokens must be positive"
        }
        require(input == null || input.distinct().size == input.size) {
            "Koog model profile input must not contain duplicates"
        }
    }
}

/** Partial customization of one model from an installed route catalog. */
@Serializable
data class KoogModelOverride(
    val api: String? = null,
    val name: String? = null,
    val description: String? = null,
    val contextWindow: Long? = null,
    val maxTokens: Long? = null,
    val input: List<ModelModality>? = null,
    val reasoningEfforts: KoogReasoningEfforts? = null,
) {
    internal fun withId(id: String): KoogModelProfile =
        KoogModelProfile(
            id = id,
            api = api,
            name = name,
            description = description,
            contextWindow = contextWindow,
            maxTokens = maxTokens,
            input = input,
            reasoningEfforts = reasoningEfforts,
        )
}
