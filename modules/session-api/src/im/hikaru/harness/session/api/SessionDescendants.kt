package im.hikaru.harness.session.api

import im.hikaru.harness.session.SessionId
import im.hikaru.harness.llm.Message
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Only registry members are enumerated; persisted or disposed descendants are not searched. */
@Serializable
public enum class DescendantCoverage { LIVE_REGISTRY }

/** A live idle Agent has no authoritative completed/waiting lifecycle projection. */
@Serializable
public enum class DescendantStatus { RUNNING, UNKNOWN }

@Serializable
public data class SessionDescendant(
    val child: SessionId,
    val parentSession: SessionId,
    val verifiedAncestorPath: List<SessionId>,
    val status: DescendantStatus,
    val startedAtEpochMillis: Long? = null,
    val provider: String? = null,
    val model: String? = null,
    val reasoningEffort: String? = null,
    val objective: String? = null,
    val reasoning: im.hikaru.harness.llm.ReasoningOptions? = null,
) {
    init {
        require(verifiedAncestorPath.size >= 2)
        require(verifiedAncestorPath.last() == child)
        require(verifiedAncestorPath[verifiedAncestorPath.lastIndex - 1] == parentSession)
        require(verifiedAncestorPath.distinct().size == verifiedAncestorPath.size)
        require(startedAtEpochMillis == null || startedAtEpochMillis >= 0)
    }
}

/** Each call is a fresh finite read. No backend revision or completion signal is fabricated. */
@Serializable
public sealed interface SessionDescendantsResult {
    public val parent: SessionId

    @Serializable
    @SerialName("unavailable")
    public data class Unavailable(override val parent: SessionId) : SessionDescendantsResult

    @Serializable
    @SerialName("available")
    public data class Available(
        override val parent: SessionId,
        val children: List<SessionDescendant>,
        val coverage: DescendantCoverage = DescendantCoverage.LIVE_REGISTRY,
        val complete: Boolean = false,
    ) : SessionDescendantsResult {
        init {
            require(!complete) { "Live registry reads cannot establish historical completeness" }
            require(children.all { it.verifiedAncestorPath.first() == parent })
            require(children.map { it.child }.distinct().size == children.size)
        }
    }
}

/** Available means lineage was revalidated for this read; it grants no mutation permission. */
@Serializable
public sealed interface ChildHistoryResult {
    public val parent: SessionId
    public val child: SessionId

    @Serializable
    @SerialName("unavailable")
    public data class Unavailable(
        override val parent: SessionId,
        override val child: SessionId,
    ) : ChildHistoryResult

    @Serializable
    @SerialName("available")
    public data class Available(
        override val parent: SessionId,
        override val child: SessionId,
        val verifiedAncestorPath: List<SessionId>,
        val messages: List<Message>,
    ) : ChildHistoryResult {
        init {
            require(parent != child)
            require(verifiedAncestorPath.size >= 2)
            require(verifiedAncestorPath.first() == parent && verifiedAncestorPath.last() == child)
            require(verifiedAncestorPath.distinct().size == verifiedAncestorPath.size)
        }
    }
}

