package im.hikaru.harness.llm

/** Safe diagnostics only: never expose exception messages or credential values. */
enum class LlmSettingsFailure { Credential, Configuration, Load }

data class LlmSettingsApplicationState(
    val applying: Boolean = false,
    val failure: LlmSettingsFailure? = null,
)

/** A settings namespace owns one atomic application, which may cover several providers. */
interface LlmSettingsApplication {
    val applicationState: LlmSettingsApplicationState
    suspend fun retry(): Boolean
}
