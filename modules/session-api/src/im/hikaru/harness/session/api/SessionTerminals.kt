package im.hikaru.harness.session.api

import im.hikaru.harness.session.SessionId
import kotlin.jvm.JvmInline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable @JvmInline
public value class TerminalScope(public val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable @JvmInline
public value class TerminalIncarnation(public val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable @JvmInline
public value class TerminalSessionId(public val value: String) {
    init { require(value.isNotBlank()) }
}

/** An adapter must also bind its trusted live Host object; text identity grants no authority. */
@Serializable
public data class TerminalOwner(
    val scope: TerminalScope,
    val session: SessionId,
    val incarnation: TerminalIncarnation,
)

@Serializable
public data class TerminalKey(val owner: TerminalOwner, val terminal: TerminalSessionId)

/** JediTerm's supported minimum is enforced here rather than silently rounding requests. */
@Serializable
public data class TerminalSize(val rows: Int = 24, val columns: Int = 80) {
    init { require(rows in 2..200 && columns in 5..500) }
}

@Serializable
public data class TerminalCreateRequest(
    val owner: TerminalOwner,
    val argv: List<String>,
    val environment: Map<String, String>,
    val size: TerminalSize = TerminalSize(),
    val cwd: String? = null,
)

@Serializable
public sealed interface TerminalLifecycle {
    @Serializable @SerialName("running") public data object Running : TerminalLifecycle
    @Serializable @SerialName("exited") public data class Exited(val code: Int) : TerminalLifecycle
    @Serializable @SerialName("unknown") public data object Unknown : TerminalLifecycle
}

@Serializable
public enum class TerminalInputState { Open, Closed, Unknown }

@Serializable
public data class TerminalMetadata(
    val key: TerminalKey,
    val canonicalCwd: String,
    val argv: List<String>,
    val lifecycle: TerminalLifecycle,
    val input: TerminalInputState,
)

@Serializable
public sealed interface TerminalColorValue {
    @Serializable @SerialName("default") public data object Default : TerminalColorValue
    @Serializable @SerialName("indexed") public data class Indexed(val index: Int) : TerminalColorValue {
        init { require(index in 0..255) }
    }
    @Serializable @SerialName("rgb") public data class Rgb(val red: Int, val green: Int, val blue: Int) : TerminalColorValue {
        init { require(red in 0..255 && green in 0..255 && blue in 0..255) }
    }
}

@Serializable
public enum class TerminalTextAttribute { Bold, Italic, Dim, Inverse, Underlined, Hidden, SlowBlink, RapidBlink }

@Serializable
public data class TerminalTextStyle(
    val foreground: TerminalColorValue = TerminalColorValue.Default,
    val background: TerminalColorValue = TerminalColorValue.Default,
    val attributes: Set<TerminalTextAttribute> = emptySet(),
)

/** span includes terminal continuation cells; text omits internal wide-character sentinels. */
@Serializable
public data class TerminalTextRun(
    val column: Int, val span: Int, val text: String, val style: TerminalTextStyle,
    val columnOffsets: List<Int>? = null,
) {
    init {
        require(column >= 0 && span > 0 && '\u0000' !in text)
        columnOffsets?.let { offsets ->
            require(offsets.size == text.length + 1 && offsets.last() == span)
            require(offsets.all { it in 0..span } && offsets.zipWithNext().all { (a, b) -> a < b })
        }
    }
}

@Serializable
public data class TerminalLineSnapshot(val runs: List<TerminalTextRun>, val wrapped: Boolean = false) {
    init { require(runs.zipWithNext().all { (a, b) -> a.column + a.span <= b.column }) }
    public val text: String get() = buildString {
        var column = 0
        for (run in runs) {
            repeat(run.column - column) { append(' ') }
            append(run.text)
            column = run.column + run.span
        }
    }
}

@Serializable
public data class TerminalCursor(val column: Int, val row: Int, val visible: Boolean) {
    init { require(column >= 0 && row >= 0) }
}

/** Producer snapshots copy every nested collection/value while holding the model and buffer locks. */
@Serializable
public data class TerminalSnapshot(
    val metadata: TerminalMetadata,
    val size: TerminalSize,
    val screen: List<TerminalLineSnapshot>,
    val history: List<TerminalLineSnapshot>,
    val cursor: TerminalCursor,
    val alternate: Boolean,
    val revision: Long,
    val rawTail: String,
    val truncated: Boolean,
) {
    init {
        require(screen.size == size.rows && history.size <= 2_000 && revision >= 0)
        require(cursor.row < size.rows && cursor.column <= size.columns) // pending wrap may be one past last cell
        require((screen + history).all { line -> line.runs.all { it.column + it.span <= size.columns } })
        require(rawTail.length <= 262_144)
        require(rawTail.firstOrNull()?.isLowSurrogate() != true && rawTail.lastOrNull()?.isHighSurrogate() != true)
    }
}

@Serializable
public sealed interface TerminalCreateResult {
    public val owner: TerminalOwner
    @Serializable @SerialName("created") public data class Created(override val owner: TerminalOwner, val metadata: TerminalMetadata) : TerminalCreateResult {
        init { require(metadata.key.owner == owner) }
    }
    @Serializable @SerialName("rejected") public data class Rejected(override val owner: TerminalOwner, val reason: String) : TerminalCreateResult
    @Serializable @SerialName("unavailable") public data class Unavailable(override val owner: TerminalOwner, val reason: String) : TerminalCreateResult
    @Serializable @SerialName("unknown") public data class UnknownOutcome(override val owner: TerminalOwner, val key: TerminalKey, val reason: String) : TerminalCreateResult {
        init { require(key.owner == owner) }
    }
}

@Serializable
public sealed interface TerminalListResult {
    public val owner: TerminalOwner
    @Serializable @SerialName("available") public data class Available(override val owner: TerminalOwner, val terminals: List<TerminalMetadata>) : TerminalListResult {
        init { require(terminals.all { it.key.owner == owner } && terminals.map { it.key }.distinct().size == terminals.size) }
    }
    @Serializable @SerialName("rejected") public data class Rejected(override val owner: TerminalOwner, val reason: String) : TerminalListResult
    @Serializable @SerialName("unavailable") public data class Unavailable(override val owner: TerminalOwner, val reason: String) : TerminalListResult
}

@Serializable
public sealed interface TerminalReadResult {
    public val key: TerminalKey
    @Serializable @SerialName("available") public data class Available(override val key: TerminalKey, val snapshot: TerminalSnapshot) : TerminalReadResult {
        init { require(snapshot.metadata.key == key) }
    }
    @Serializable @SerialName("not_found") public data class NotFound(override val key: TerminalKey) : TerminalReadResult
    @Serializable @SerialName("rejected") public data class Rejected(override val key: TerminalKey, val reason: String) : TerminalReadResult
    @Serializable @SerialName("unavailable") public data class Unavailable(override val key: TerminalKey, val reason: String) : TerminalReadResult
}

@Serializable
public sealed interface TerminalMutationResult {
    public val key: TerminalKey
    @Serializable @SerialName("applied") public data class Applied(override val key: TerminalKey, val bytes: Int = 0, val size: TerminalSize? = null) : TerminalMutationResult {
        init { require(bytes >= 0) }
    }
    @Serializable @SerialName("not_found") public data class NotFound(override val key: TerminalKey) : TerminalMutationResult
    @Serializable @SerialName("rejected") public data class Rejected(override val key: TerminalKey, val reason: String) : TerminalMutationResult
    @Serializable @SerialName("unavailable") public data class Unavailable(override val key: TerminalKey, val reason: String) : TerminalMutationResult
    @Serializable @SerialName("unknown") public data class UnknownOutcome(override val key: TerminalKey, val reason: String) : TerminalMutationResult
}

@Serializable
public sealed interface TerminalShutdownResult {
    public val owner: TerminalOwner
    @Serializable @SerialName("applied") public data class Applied(override val owner: TerminalOwner) : TerminalShutdownResult
    @Serializable @SerialName("rejected") public data class Rejected(override val owner: TerminalOwner, val reason: String) : TerminalShutdownResult
    @Serializable @SerialName("unknown") public data class UnknownOutcome(override val owner: TerminalOwner, val remaining: List<TerminalKey>) : TerminalShutdownResult {
        init { require(remaining.all { it.owner == owner }) }
    }
}

/** Explicit PTY capability. No method other than create may start a process. */
public interface SessionTerminalsService {
    public suspend fun create(request: TerminalCreateRequest): TerminalCreateResult
    public suspend fun list(owner: TerminalOwner): TerminalListResult
    public suspend fun read(key: TerminalKey): TerminalReadResult
    public suspend fun write(key: TerminalKey, input: String): TerminalMutationResult
    public suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Semantic terminal input is unavailable")
    public suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult
    public suspend fun close(key: TerminalKey): TerminalMutationResult
    public suspend fun shutdown(owner: TerminalOwner): TerminalShutdownResult
}
