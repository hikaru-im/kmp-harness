package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.*

public sealed interface PtyLoad<out T> {
    public data object NotLoaded : PtyLoad<Nothing>
    public data object Loading : PtyLoad<Nothing>
    public data object Empty : PtyLoad<Nothing>
    public data class Ready<T>(val value: T) : PtyLoad<T>
    public data class Unavailable(val reason: String) : PtyLoad<Nothing>
    public data class Error(val reason: String) : PtyLoad<Nothing>
}

public enum class PtyOperation { Create, Input, Resize, Close }
public enum class PtyOutcome { Pending, Applied, Rejected, Unavailable, Unknown, Unsent }
public data class PtyReceipt(
    val request: Long, val key: TerminalKey?, val operation: PtyOperation, val outcome: PtyOutcome, val message: String,
)

public data class PtyTerminalState(
    val bindingEpoch: Long = 0,
    val selectionEpoch: Long = 0,
    val visible: Boolean = false,
    val discovery: PtyLoad<TerminalDiscoveryResult.Available> = PtyLoad.NotLoaded,
    val inventory: PtyLoad<List<TerminalMetadata>> = PtyLoad.NotLoaded,
    val selectedKey: TerminalKey? = null,
    val screen: PtyLoad<TerminalSnapshot> = PtyLoad.NotLoaded,
    val retainedSnapshot: TerminalSnapshot? = null,
    val viewport: TerminalSize = TerminalSize(),
    val launchUnavailableReason: String? = null,
    val createPending: Boolean = false,
    val createUncertain: Boolean = false,
    val fencedKeys: Set<TerminalKey> = emptySet(),
    val closeAttemptedKeys: Set<TerminalKey> = emptySet(),
    val confirmedSizes: Map<TerminalKey, TerminalSize> = emptyMap(),
    val receipts: List<PtyReceipt> = emptyList(),
) {
    public val snapshot: TerminalSnapshot? get() = (screen as? PtyLoad.Ready)?.value ?: retainedSnapshot
    public val canCreate: Boolean get() = discovery is PtyLoad.Ready && launchUnavailableReason == null &&
        !createPending && !createUncertain
    public val canInput: Boolean get() = visible && selectedKey != null && selectedKey !in fencedKeys &&
        (discovery as? PtyLoad.Ready)?.value?.owner == selectedKey.owner &&
        screen is PtyLoad.Ready && snapshot?.metadata?.key == selectedKey && snapshot?.metadata?.input == TerminalInputState.Open &&
        snapshot?.metadata?.lifecycle == TerminalLifecycle.Running && selectedKey !in closeAttemptedKeys
    public val canClose: Boolean get() = visible && selectedKey != null && selectedKey !in closeAttemptedKeys
}

internal fun TerminalMetadata.detached(): TerminalMetadata = copy(
    key = key.copy(owner = key.owner.copy()), argv = argv.toList(),
)

internal fun TerminalSnapshot.detached(): TerminalSnapshot {
    fun line(value: TerminalLineSnapshot): TerminalLineSnapshot = value.copy(runs = value.runs.map { run ->
        run.copy(style = run.style.copy(attributes = run.style.attributes.toSet()), columnOffsets = run.columnOffsets?.toList())
    })
    return copy(metadata = metadata.detached(), size = size.copy(), screen = screen.map(::line),
        history = history.map(::line), cursor = cursor.copy())
}
