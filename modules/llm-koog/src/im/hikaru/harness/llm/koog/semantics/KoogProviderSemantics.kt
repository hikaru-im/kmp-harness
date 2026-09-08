package im.hikaru.harness.llm.koog

/** Complete Koog semantics bundle for one Harness Provider route. */
class KoogProviderSemantics(
    val provider: String,
    val optionMapper: KoogOptionMapper = BasicKoogOptionMapper(),
    val usageMapper: KoogUsageMapper = DefaultKoogUsageMapper(),
    val finishReasonMapper: KoogFinishReasonMapper =
        StopOnlyKoogFinishReasonMapper(),
    val replayRestorer: KoogReplayRestorer = RejectingKoogReplayRestorer,
    val failureClassifier: KoogProviderFailureClassifier =
        UnclassifiedKoogProviderFailures,
    /** A complete Provider mapper; null selects the replay-aware common mapper. */
    val messageMapper: KoogMessageMapper? = null,
    val toolMapper: KoogToolMapper? = null,
    val reasoningMapper: KoogReasoningMapper? = null,
    val toolStreamMapper: KoogToolStreamMapper? = null,
    val toolCallTerminalPolicy: KoogToolCallTerminalPolicy? = null,
    val textTerminalPolicy: KoogTextTerminalPolicy? = null,
    val replayWriter: KoogReplayWriter = NoopKoogReplayWriter,
) {
    init {
        require(provider.isNotBlank()) {
            "Koog provider semantics id must not be blank"
        }
    }
}
