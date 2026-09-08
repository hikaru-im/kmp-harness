package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.LlmException

/** Harness content-block type represented by a Provider stream frame. */
enum class KoogStreamBlockType(
    val harnessType: String,
) {
    TEXT("text"),
    REASONING("reasoning"),
    TOOL_CALL("tool-call"),
}

/** Mutable state for one Provider block while stream frames are being mapped. */
class KoogStreamBlockState(
    val providerIndex: Int?,
    val outputIndex: Int,
    val type: KoogStreamBlockType,
) {
    var started: Boolean = false
        private set

    var completed: Boolean = false
        private set

    var text: String = ""
        private set

    private var toolCallId: String? = null
    private var toolCallName: String? = null
    private var toolCallNameEmitted: Boolean = false
    private var reasoningId: String? = null
    private val reasoningSummary = mutableListOf<String>()
    private var reasoningEncrypted: String? = null

    internal val sourceLabel: String
        get() = providerIndex?.toString() ?: "implicit output $outputIndex"

    /** Marks the block started and returns whether a start chunk is required. */
    fun startIfNeeded(): Boolean {
        ensureOpen()
        if (started) return false
        started = true
        return true
    }

    /** Appends a delta after the block has started. */
    fun append(delta: String) {
        ensureOpen()
        if (!started) {
            invalidState("Cannot append before starting provider block $sourceLabel")
        }
        text += delta
    }

    /** Captures Provider-private reasoning metadata outside durable Harness text. */
    fun observeReasoning(
        id: String?,
        summary: List<String> = emptyList(),
        encrypted: String? = null,
        replaceSummary: Boolean = false,
    ) {
        ensureOpen()
        if (type != KoogStreamBlockType.REASONING) {
            invalidState(
                "Provider block $sourceLabel is ${type.harnessType}, not reasoning"
            )
        }
        val nextId = mergePrivateIdentity("reasoning id", reasoningId, id)
        val nextEncrypted =
            mergePrivateIdentity(
                "reasoning encrypted content",
                reasoningEncrypted,
                encrypted,
            )
        summary.forEach { value ->
            if (value.isBlank()) {
                invalidState("Provider reasoning block $sourceLabel has a blank summary")
            }
        }
        val nextSummary =
            if (replaceSummary) {
                summary.toList()
            } else {
                buildList {
                    addAll(reasoningSummary)
                    addAll(summary)
                }
            }
        reasoningId = nextId
        reasoningEncrypted = nextEncrypted
        reasoningSummary.clear()
        reasoningSummary.addAll(nextSummary)
    }

    /** Returns metadata for a Provider-native replay writer. */
    fun replaySnapshot(): KoogStreamBlockSnapshot =
        KoogStreamBlockSnapshot(
            outputIndex = outputIndex,
            type = type.harnessType,
            text = text,
            toolCallId = toolCallId,
            toolCallName = toolCallName,
            reasoningId = reasoningId,
            reasoningSummary = reasoningSummary.toList(),
            reasoningEncrypted = reasoningEncrypted,
        )

    /** Observes stable tool-call identity fields and rejects conflicts. */
    fun observeToolCallIdentity(
        id: String?,
        name: String?,
    ) {
        ensureOpen()
        ensureToolCallBlock()
        val nextId = mergeToolIdentity("id", toolCallId, id)
        val nextName = mergeToolIdentity("name", toolCallName, name)
        toolCallId = nextId
        toolCallName = nextName
    }

    fun requireToolCallId(): String {
        ensureOpen()
        ensureToolCallBlock()
        return toolCallId
            ?: invalidState("Provider tool block $sourceLabel has no call id")
    }

    fun requireToolCallName(): String {
        ensureOpen()
        ensureToolCallBlock()
        return toolCallName
            ?: invalidState("Provider tool block $sourceLabel has no tool name")
    }

    /** Returns the tool name at most once for delta emission. */
    fun takeToolCallNameForDelta(): String? {
        ensureOpen()
        ensureToolCallBlock()
        if (toolCallNameEmitted) return null
        val name = toolCallName ?: return null
        toolCallNameEmitted = true
        return name
    }

    /** Completes the block, optionally replacing accumulated text with authoritative content. */
    fun complete(finalText: String? = null) {
        ensureOpen()
        if (!started) {
            invalidState("Cannot complete before starting provider block $sourceLabel")
        }
        if (type == KoogStreamBlockType.TOOL_CALL) {
            requireToolCallId()
            requireToolCallName()
        }
        if (completed) {
            invalidState("Provider block $sourceLabel was completed twice")
        }
        finalText?.let { text = it }
        completed = true
    }

    private fun mergePrivateIdentity(
        field: String,
        current: String?,
        incoming: String?,
    ): String? {
        if (incoming == null) return current
        if (incoming.isBlank()) {
            invalidState("Provider block $sourceLabel has a blank $field")
        }
        if (current != null && current != incoming) {
            invalidState(
                "Provider block $sourceLabel changed $field from '$current' to '$incoming'"
            )
        }
        return incoming
    }

    private fun mergeToolIdentity(
        field: String,
        current: String?,
        incoming: String?,
    ): String? {
        if (incoming == null) return current
        if (incoming.isBlank()) {
            invalidState("Provider tool block $sourceLabel has a blank $field")
        }
        if (current != null && current != incoming) {
            invalidState(
                "Provider tool block $sourceLabel changed $field from '$current' to '$incoming'"
            )
        }
        return incoming
    }

    private fun ensureOpen() {
        if (completed) {
            invalidState("Provider block $sourceLabel is already completed")
        }
    }

    private fun ensureToolCallBlock() {
        if (type != KoogStreamBlockType.TOOL_CALL) {
            invalidState(
                "Provider block $sourceLabel is ${type.harnessType}, not tool-call"
            )
        }
    }
}

/** Immutable Provider metadata for one mapped output block. */
data class KoogStreamBlockSnapshot(
    val outputIndex: Int,
    val type: String,
    val text: String,
    val toolCallId: String?,
    val toolCallName: String?,
    val reasoningId: String?,
    val reasoningSummary: List<String>,
    val reasoningEncrypted: String?,
)

internal fun invalidState(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_STREAM_STATE,
    )
